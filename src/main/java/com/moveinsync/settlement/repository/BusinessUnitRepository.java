package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.BusinessUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, UUID> {
    Optional<BusinessUnit> findByUnitCode(String unitCode);
}
