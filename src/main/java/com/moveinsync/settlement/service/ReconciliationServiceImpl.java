package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.ReconciliationResponse;
import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import com.moveinsync.settlement.dto.VendorInvoiceResponse;
import com.moveinsync.settlement.entity.BusinessUnit;
import com.moveinsync.settlement.entity.Reconciliation;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.entity.VendorInvoice;
import com.moveinsync.settlement.entity.enums.ReconciliationStatus;
import com.moveinsync.settlement.exception.ConflictException;
import com.moveinsync.settlement.exception.ResourceNotFoundException;
import com.moveinsync.settlement.exception.SettlementDomainException;
import com.moveinsync.settlement.repository.BusinessUnitRepository;
import com.moveinsync.settlement.repository.ReconciliationRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.VendorInvoiceRepository;
import com.moveinsync.settlement.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("100.00");

    private final VendorInvoiceRepository vendorInvoiceRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final VendorRepository vendorRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public VendorInvoiceResponse submitInvoice(VendorInvoiceRequest request) {
        log.info("Submitting invoice: vendor={}, number={}, amount=₹{}",
                request.getVendorId(), request.getInvoiceNumber(), request.getClaimedAmount());

        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + request.getVendorId()));

        BusinessUnit businessUnit = businessUnitRepository.findById(request.getBusinessUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Business Unit not found: " + request.getBusinessUnitId()));

        Optional<VendorInvoice> existingOpt = vendorInvoiceRepository
                .findByVendorIdAndInvoiceNumber(vendor.getId(), request.getInvoiceNumber());

        if (existingOpt.isPresent()) {
            throw new ConflictException(String.format(
                    "Invoice %s already submitted for vendor %s", request.getInvoiceNumber(), vendor.getVendorCode()
            ));
        }

        VendorInvoice invoice = VendorInvoice.builder()
                .id(UUID.randomUUID())
                .vendor(vendor)
                .businessUnit(businessUnit)
                .invoiceNumber(request.getInvoiceNumber())
                .billingPeriod(request.getBillingPeriod())
                .claimedAmount(request.getClaimedAmount())
                .invoiceDate(request.getInvoiceDate())
                .status("SUBMITTED")
                .createdAt(Instant.now())
                .build();

        VendorInvoice saved = vendorInvoiceRepository.save(invoice);
        log.info("Invoice {} registered successfully for vendor {}", saved.getInvoiceNumber(), vendor.getVendorCode());

        auditService.recordEvent(
                "VENDOR_INVOICE_SUBMITTED",
                "VENDOR_INVOICE",
                saved.getId().toString(),
                "SYSTEM",
                "ROLE_ADMIN",
                Map.of(
                        "invoiceNumber", saved.getInvoiceNumber(),
                        "vendorId", saved.getVendor().getId().toString(),
                        "claimedAmount", saved.getClaimedAmount(),
                        "billingPeriod", saved.getBillingPeriod()
                )
        );

        return mapToInvoiceResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorInvoiceResponse getInvoiceById(UUID id) {
        VendorInvoice invoice = vendorInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor invoice not found: " + id));
        return mapToInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorInvoiceResponse> listInvoices(UUID vendorId, String billingPeriod) {
        List<VendorInvoice> list;
        if (vendorId != null && billingPeriod != null) {
            list = vendorInvoiceRepository.findByVendorIdAndBillingPeriod(vendorId, billingPeriod);
        } else {
            list = vendorInvoiceRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::mapToInvoiceResponse).toList();
    }

    @Override
    @Transactional
    public ReconciliationResponse reconcile(ReconciliationRequest request, String reconciledBy) {
        log.info("Initiating reconciliation: settlementRun={}, invoice={}, requestedBy={}",
                request.getSettlementRunId(), request.getVendorInvoiceId(), reconciledBy);

        SettlementRun run = settlementRunRepository.findById(request.getSettlementRunId())
                .orElseThrow(() -> new ResourceNotFoundException("Settlement run not found: " + request.getSettlementRunId()));

        VendorInvoice invoice = vendorInvoiceRepository.findById(request.getVendorInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor invoice not found: " + request.getVendorInvoiceId()));

        // Validate scope alignment
        if (!run.getVendor().getId().equals(invoice.getVendor().getId())) {
            throw new SettlementDomainException(String.format(
                    "Reconciliation vendor mismatch: Run vendor=%s, Invoice vendor=%s",
                    run.getVendor().getId(), invoice.getVendor().getId()
            ));
        }

        if (!run.getBillingPeriod().equals(invoice.getBillingPeriod())) {
            throw new SettlementDomainException(String.format(
                    "Reconciliation billing period mismatch: Run period=%s, Invoice period=%s",
                    run.getBillingPeriod(), invoice.getBillingPeriod()
            ));
        }

        // Idempotency: Return existing reconciliation if already performed
        Optional<Reconciliation> existingReconOpt = reconciliationRepository
                .findBySettlementRunIdAndVendorInvoiceId(run.getId(), invoice.getId());

        if (existingReconOpt.isPresent()) {
            log.info("Reconciliation already performed for run {} and invoice {}. Returning existing outcome.",
                    run.getId(), invoice.getId());
            return mapToReconciliationResponse(existingReconOpt.get());
        }

        // Financial Variance Calculation
        BigDecimal systemAmount = run.getTotalNetPayout();
        BigDecimal invoiceAmount = invoice.getClaimedAmount();
        BigDecimal varianceAmount = invoiceAmount.subtract(systemAmount);
        BigDecimal tolerance = (request.getTolerance() != null && request.getTolerance().compareTo(BigDecimal.ZERO) >= 0)
                ? request.getTolerance()
                : DEFAULT_TOLERANCE;

        BigDecimal absVariance = varianceAmount.abs();

        ReconciliationStatus status;
        String explanation;

        if (absVariance.compareTo(BigDecimal.ZERO) == 0) {
            status = ReconciliationStatus.EXACT_MATCH;
            explanation = "Claimed amount matches system calculation exactly down to ₹0.00.";
        } else if (absVariance.compareTo(tolerance) <= 0) {
            status = ReconciliationStatus.MATCHED_WITHIN_TOLERANCE;
            explanation = String.format(
                    "Variance of ₹%s is within configured tolerance ceiling of ₹%s.",
                    absVariance.toPlainString(), tolerance.toPlainString()
            );
        } else {
            status = ReconciliationStatus.DISCREPANCY_FLAGGED;
            explanation = String.format(
                    "Variance of ₹%s exceeds tolerance ceiling of ₹%s. Manual dispute investigation required.",
                    absVariance.toPlainString(), tolerance.toPlainString()
            );
        }

        Reconciliation reconciliation = Reconciliation.builder()
                .id(UUID.randomUUID())
                .settlementRun(run)
                .vendorInvoice(invoice)
                .systemAmount(systemAmount)
                .invoiceAmount(invoiceAmount)
                .varianceAmount(varianceAmount)
                .toleranceApplied(tolerance)
                .status(status)
                .explanation(explanation)
                .reconciledAt(Instant.now())
                .reconciledBy(reconciledBy != null ? reconciledBy : "SYSTEM")
                .build();

        Reconciliation savedRecon = reconciliationRepository.save(reconciliation);

        // Update Invoice status to reflect reconciliation outcome
        invoice.setStatus(status.name());
        vendorInvoiceRepository.save(invoice);

        log.info("Reconciliation {} completed: status={}, variance=₹{}",
                savedRecon.getId(), status, varianceAmount);

        auditService.recordEvent(
                "RECONCILIATION_PERFORMED",
                "RECONCILIATION",
                savedRecon.getId().toString(),
                reconciledBy,
                "ROLE_APPROVER",
                Map.of(
                        "settlementRunId", run.getId().toString(),
                        "vendorInvoiceId", invoice.getId().toString(),
                        "status", status.name(),
                        "varianceAmount", varianceAmount
                )
        );

        return mapToReconciliationResponse(savedRecon);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResponse getReconciliationById(UUID id) {
        Reconciliation recon = reconciliationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation record not found: " + id));
        return mapToReconciliationResponse(recon);
    }

    private VendorInvoiceResponse mapToInvoiceResponse(VendorInvoice invoice) {
        return VendorInvoiceResponse.builder()
                .id(invoice.getId())
                .vendorId(invoice.getVendor().getId())
                .businessUnitId(invoice.getBusinessUnit().getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .billingPeriod(invoice.getBillingPeriod())
                .claimedAmount(invoice.getClaimedAmount())
                .invoiceDate(invoice.getInvoiceDate())
                .status(invoice.getStatus())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private ReconciliationResponse mapToReconciliationResponse(Reconciliation recon) {
        return ReconciliationResponse.builder()
                .reconciliationId(recon.getId())
                .settlementRunId(recon.getSettlementRun().getId())
                .vendorInvoiceId(recon.getVendorInvoice().getId())
                .systemAmount(recon.getSystemAmount())
                .invoiceAmount(recon.getInvoiceAmount())
                .varianceAmount(recon.getVarianceAmount())
                .toleranceApplied(recon.getToleranceApplied())
                .status(recon.getStatus())
                .explanation(recon.getExplanation())
                .reconciledAt(recon.getReconciledAt())
                .reconciledBy(recon.getReconciledBy())
                .build();
    }
}
