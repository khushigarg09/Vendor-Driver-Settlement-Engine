package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, UUID> {
    Optional<VendorInvoice> findByVendorIdAndInvoiceNumber(UUID vendorId, String invoiceNumber);
    List<VendorInvoice> findByVendorIdAndBillingPeriod(UUID vendorId, String billingPeriod);
}
