-- ============================================================================
-- V6__contract_bu_and_adjustment_consumption.sql
-- 1. Associate vendor_contracts with business_units for strict contract scoping
-- 2. Link adjustments to consuming settlement runs for exactly-once future delta payout
-- ============================================================================

-- 1. Contract Scoping: Link vendor_contracts to business_units
ALTER TABLE vendor_contracts
    ADD COLUMN business_unit_id UUID REFERENCES business_units(id);

-- Backfill existing seeded contract CNT-VND01-2026 to BU-BLR-TECH-PARK
UPDATE vendor_contracts
SET business_unit_id = '22222222-2222-2222-2222-222222222222'
WHERE business_unit_id IS NULL;

ALTER TABLE vendor_contracts
    ALTER COLUMN business_unit_id SET NOT NULL;

CREATE INDEX idx_vendor_contracts_scope
    ON vendor_contracts(vendor_id, business_unit_id, status);

-- 2. Adjustment Consumption: Link adjustments to consuming settlement run
ALTER TABLE adjustments
    ADD COLUMN consumed_run_id UUID REFERENCES settlement_runs(id) ON DELETE SET NULL;

ALTER TABLE adjustments
    ADD COLUMN consumed_settlement_id UUID REFERENCES settlements(id) ON DELETE SET NULL;

CREATE INDEX idx_adjustments_unconsumed
    ON adjustments(settlement_id, status, consumed_run_id);
