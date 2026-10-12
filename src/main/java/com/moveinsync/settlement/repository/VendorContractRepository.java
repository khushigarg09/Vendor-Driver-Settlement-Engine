package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.VendorContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorContractRepository extends JpaRepository<VendorContract, UUID> {
    Optional<VendorContract> findByContractNumber(String contractNumber);
    List<VendorContract> findByVendorIdAndStatus(UUID vendorId, String status);
    List<VendorContract> findByVendorIdAndBusinessUnitIdAndStatus(UUID vendorId, UUID businessUnitId, String status);
}
