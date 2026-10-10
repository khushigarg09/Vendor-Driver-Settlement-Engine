package com.moveinsync.settlement.service;

import com.moveinsync.settlement.domain.calculation.SettlementCalculationEngine;
import com.moveinsync.settlement.domain.calculation.model.*;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.dto.PayslipResponse;
import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.SettlementRunResponse;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.entity.enums.*;
import com.moveinsync.settlement.exception.ConflictException;
import com.moveinsync.settlement.exception.ResourceNotFoundException;
import com.moveinsync.settlement.exception.SettlementDomainException;
import com.moveinsync.settlement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementRunServiceImpl implements SettlementRunService {

    private final SettlementRunRepository settlementRunRepository;
    private final SettlementRepository settlementRepository;
    private final VendorRepository vendorRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final VendorContractRepository vendorContractRepository;
    private final ContractRateRepository contractRateRepository;
    private final TripRepository tripRepository;
    private final ViolationRepository violationRepository;
    private final SettlementCalculationEngine calculationEngine;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SettlementRunResponse executeSettlementRun(SettlementRunRequest request, String createdBy) {
        log.info("Initiating settlement run for vendor={}, businessUnit={}, period={}, requestedBy={}",
                request.getVendorId(), request.getBusinessUnitId(), request.getBillingPeriod(), createdBy);

        // 1. Idempotency Check: Return existing completed/finalized run if already present
        Optional<SettlementRun> existingRunOpt = settlementRunRepository
                .findByVendorIdAndBusinessUnitIdAndBillingPeriod(
                        request.getVendorId(), request.getBusinessUnitId(), request.getBillingPeriod()
                );

        if (existingRunOpt.isPresent()) {
            SettlementRun existingRun = existingRunOpt.get();
            if (existingRun.getStatus() == SettlementRunStatus.CALCULATED || existingRun.getStatus() == SettlementRunStatus.FINALIZED) {
                log.info("Idempotent settlement replay: Run {} already completed with status {}",
                        existingRun.getId(), existingRun.getStatus());
                return mapToResponse(existingRun, true);
            } else if (existingRun.getStatus() == SettlementRunStatus.STARTED) {
                throw new ConflictException("Settlement run for this scope is already in progress.");
            }
        }

        // 2. Validate Vendor and Business Unit Existence
        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + request.getVendorId()));

        BusinessUnit businessUnit = businessUnitRepository.findById(request.getBusinessUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Business Unit not found: " + request.getBusinessUnitId()));

        // 3. Retrieve Active Vendor Contract
        List<VendorContract> contracts = vendorContractRepository.findByVendorIdAndStatus(vendor.getId(), "ACTIVE");
        if (contracts.isEmpty()) {
            throw new ResourceNotFoundException("No active contract found for vendor: " + vendor.getVendorCode());
        }
        VendorContract contract = contracts.getFirst();

        // 4. Retrieve Contract Rates and Map to Domain Records
        List<ContractRate> contractRates = contractRateRepository.findByContractId(contract.getId());
        if (contractRates.isEmpty()) {
            throw new ResourceNotFoundException("No contract rates defined for contract: " + contract.getContractNumber());
        }
        List<ContractRateRecord> rateRecords = contractRates.stream()
                .map(r -> new ContractRateRecord(
                        r.getId(),
                        r.getContract().getId(),
                        r.getVehicleCategory(),
                        Money.of(r.getRateAmount()),
                        r.getEffectiveFrom(),
                        r.getEffectiveTo()
                ))
                .toList();

        // 5. Compute Billing Period Window in Business Unit Timezone
        YearMonth yearMonth = YearMonth.parse(request.getBillingPeriod());
        ZoneId zoneId = ZoneId.of(businessUnit.getTimezone() != null ? businessUnit.getTimezone() : "Asia/Kolkata");
        Instant periodStart = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant periodEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();

        // 6. Query Eligible Completed Trips
        List<Trip> eligibleTrips = tripRepository.findEligibleTripsForSettlement(
                vendor.getId(),
                businessUnit.getId(),
                TripStatus.COMPLETED,
                TripSettlementStatus.PENDING,
                periodStart,
                periodEnd
        );

        if (eligibleTrips.isEmpty()) {
            throw new SettlementDomainException("No unsettled eligible trips found for period " + request.getBillingPeriod());
        }

        // 7. Group Trips by Driver
        Map<Driver, List<Trip>> tripsByDriver = eligibleTrips.stream()
                .collect(Collectors.groupingBy(Trip::getDriver));

        // 8. Create SettlementRun Entity with Status STARTED
        SettlementRun settlementRun = SettlementRun.builder()
                .id(UUID.randomUUID())
                .vendor(vendor)
                .businessUnit(businessUnit)
                .billingPeriod(request.getBillingPeriod())
                .runNumber(1)
                .status(SettlementRunStatus.STARTED)
                .createdBy(createdBy != null ? createdBy : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        List<Settlement> settlements = new ArrayList<>();
        Money runTotalGross = Money.ZERO;
        Money runTotalIncentives = Money.ZERO;
        Money runTotalPenalties = Money.ZERO;
        Money runTotalTds = Money.ZERO;
        Money runTotalNet = Money.ZERO;

        // 9. Process Each Driver Settlement
        for (Map.Entry<Driver, List<Trip>> entry : tripsByDriver.entrySet()) {
            Driver driver = entry.getKey();
            List<Trip> driverTrips = entry.getValue();

            // Map trips to domain calculation records
            List<TripRecord> tripRecords = driverTrips.stream()
                    .map(t -> new TripRecord(
                            t.getId(),
                            t.getSourceTripId(),
                            driver.getId(),
                            t.getVehicleCategory(),
                            t.getStartedAt(),
                            t.getCompletedAt(),
                            t.getDelayMinutes() != null ? t.getDelayMinutes() : 0,
                            t.getStatus()
                    ))
                    .toList();

            // Query pending violations for this driver
            List<Violation> violations = violationRepository.findPendingViolationsForDriverInWindow(
                    driver.getId(),
                    ViolationStatus.PENDING,
                    periodStart,
                    periodEnd
            );

            List<ViolationRecord> violationRecords = violations.stream()
                    .map(v -> new ViolationRecord(
                            v.getId(),
                            v.getSourceViolationId(),
                            driver.getId(),
                            v.getViolationType(),
                            v.getOccurredAt(),
                            Money.of(v.getPenaltyAmount())
                    ))
                    .toList();

            // Execute Pure Domain Financial Calculation
            DriverSettlementCalculationResult calcResult = calculationEngine.computeDriverSettlement(
                    driver.getId(),
                    request.getBillingPeriod(),
                    tripRecords,
                    rateRecords,
                    violationRecords,
                    contract.getBillingType(),
                    IncentiveConfig.defaultConfig(),
                    PenaltyConfig.defaultConfig(),
                    TdsConfig.defaultConfig(),
                    Money.ZERO, // No previous withholding in primary run
                    false,      // attendance flag
                    zoneId
            );

            // Construct Settlement Entity
            Settlement settlement = Settlement.builder()
                    .id(UUID.randomUUID())
                    .settlementRun(settlementRun)
                    .driver(driver)
                    .billingPeriod(request.getBillingPeriod())
                    .tripsCount(calcResult.tripsCount())
                    .grossBasePay(calcResult.grossBasePay().getAmount())
                    .incentivesTotal(calcResult.incentivesTotal().getAmount())
                    .penaltiesTotal(calcResult.penaltiesTotal().getAmount())
                    .tdsTaxTotal(calcResult.tdsTaxTotal().getAmount())
                    .netPayout(calcResult.netPayout().getAmount())
                    .status(SettlementStatus.CALCULATED)
                    .createdAt(Instant.now())
                    .build();

            // Construct Signed Ledger Lines
            for (SignedLineItem lineItem : calcResult.lineItems()) {
                SettlementLine line = SettlementLine.builder()
                        .id(UUID.randomUUID())
                        .settlement(settlement)
                        .lineType(lineItem.lineType())
                        .signedAmount(lineItem.signedAmount().getAmount())
                        .description(lineItem.description())
                        .sourceReferenceId(lineItem.sourceReferenceId())
                        .createdAt(Instant.now())
                        .build();
                settlement.addLine(line);
            }

            settlements.add(settlement);

            // Update status of processed trips and violations
            for (Trip trip : driverTrips) {
                trip.setSettlementStatus(TripSettlementStatus.SETTLED);
            }
            for (Violation violation : violations) {
                violation.setStatus(ViolationStatus.DEDUCTED);
            }

            // Accumulate Run Totals
            runTotalGross = runTotalGross.add(calcResult.grossBasePay());
            runTotalIncentives = runTotalIncentives.add(calcResult.incentivesTotal());
            runTotalPenalties = runTotalPenalties.add(calcResult.penaltiesTotal());
            runTotalTds = runTotalTds.add(calcResult.tdsTaxTotal());
            runTotalNet = runTotalNet.add(calcResult.netPayout());
        }

        // 10. Finalize SettlementRun Entity
        settlementRun.setStatus(SettlementRunStatus.CALCULATED);
        settlementRun.setTotalGross(runTotalGross.getAmount());
        settlementRun.setTotalIncentives(runTotalIncentives.getAmount());
        settlementRun.setTotalPenalties(runTotalPenalties.getAmount());
        settlementRun.setTotalTds(runTotalTds.getAmount());
        settlementRun.setTotalNetPayout(runTotalNet.getAmount());
        settlementRun.setSettlements(settlements);

        // 11. Persist Run, Settlements, and Lines atomically
        SettlementRun savedRun = settlementRunRepository.save(settlementRun);
        tripRepository.saveAll(eligibleTrips);

        log.info("Settlement run {} completed successfully: {} drivers settled, net payout = ₹{}",
                savedRun.getId(), settlements.size(), savedRun.getTotalNetPayout());

        return mapToResponse(savedRun, false);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementRunResponse getSettlementRunById(UUID id) {
        SettlementRun run = settlementRunRepository.findByIdWithSettlements(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement run not found: " + id));
        return mapToResponse(run, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementRunResponse> listSettlementRuns() {
        return settlementRunRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> mapToResponse(r, false))
                .toList();
    }

    @Override
    @Transactional
    public SettlementRunResponse approveSettlementRun(UUID id, String approvedBy) {
        SettlementRun run = settlementRunRepository.findByIdWithSettlements(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement run not found: " + id));

        if (run.getStatus() == SettlementRunStatus.FINALIZED) {
            log.info("Settlement run {} is already finalized/approved.", id);
            return mapToResponse(run, false);
        }

        run.setStatus(SettlementRunStatus.FINALIZED);
        for (Settlement settlement : run.getSettlements()) {
            settlement.setStatus(SettlementStatus.APPROVED);
        }

        SettlementRun approvedRun = settlementRunRepository.save(run);
        log.info("Settlement run {} approved by {}", id, approvedBy);
        return mapToResponse(approvedRun, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipResponse getDriverPayslip(UUID driverId, String billingPeriod) {
        Settlement settlement = settlementRepository.findByDriverIdAndBillingPeriodWithLines(driverId, billingPeriod)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Payslip not found for driver %s in period %s", driverId, billingPeriod)
                ));

        BigDecimal linesSum = settlement.getLines().stream()
                .map(SettlementLine::getSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isReconciled = linesSum.compareTo(settlement.getNetPayout()) == 0;

        List<PayslipResponse.PayslipLineItemResponse> lineItems = settlement.getLines().stream()
                .map(l -> PayslipResponse.PayslipLineItemResponse.builder()
                        .id(l.getId())
                        .lineType(l.getLineType())
                        .description(l.getDescription())
                        .sourceReferenceId(l.getSourceReferenceId())
                        .signedAmount(l.getSignedAmount())
                        .build())
                .toList();

        Driver driver = settlement.getDriver();

        return PayslipResponse.builder()
                .settlementId(settlement.getId())
                .settlementRunId(settlement.getSettlementRun().getId())
                .driverId(driver.getId())
                .driverCode(driver.getDriverCode())
                .driverName(driver.getFirstName() + " " + driver.getLastName())
                .billingPeriod(settlement.getBillingPeriod())
                .status(settlement.getStatus())
                .tripsCount(settlement.getTripsCount())
                .summary(PayslipResponse.PayslipSummary.builder()
                        .grossBasePay(settlement.getGrossBasePay())
                        .incentivesTotal(settlement.getIncentivesTotal())
                        .penaltiesTotal(settlement.getPenaltiesTotal())
                        .tdsTaxWithheld(settlement.getTdsTaxTotal())
                        .netPayout(settlement.getNetPayout())
                        .build())
                .lineItems(lineItems)
                .verification(PayslipResponse.PayslipVerification.builder()
                        .linesSum(linesSum)
                        .netPayout(settlement.getNetPayout())
                        .isReconciled(isReconciled)
                        .build())
                .createdAt(settlement.getCreatedAt())
                .build();
    }

    private SettlementRunResponse mapToResponse(SettlementRun run, boolean idempotentReplay) {
        int driverCount = (run.getSettlements() != null) ? run.getSettlements().size() : 0;
        return SettlementRunResponse.builder()
                .id(run.getId())
                .vendorId(run.getVendor().getId())
                .businessUnitId(run.getBusinessUnit().getId())
                .billingPeriod(run.getBillingPeriod())
                .runNumber(run.getRunNumber())
                .status(run.getStatus())
                .driversSettledCount(driverCount)
                .totalGrossBasePay(run.getTotalGross())
                .totalIncentives(run.getTotalIncentives())
                .totalPenalties(run.getTotalPenalties())
                .totalTdsWithheld(run.getTotalTds())
                .totalNetPayout(run.getTotalNetPayout())
                .idempotentReplay(idempotentReplay)
                .createdAt(run.getCreatedAt())
                .createdBy(run.getCreatedBy())
                .build();
    }
}
