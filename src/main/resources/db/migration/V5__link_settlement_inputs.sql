-- ============================================================================
-- V5__link_settlement_inputs.sql
-- Explicitly link settlements to contributing trip and violation items
-- Enforce single-target constraint on disputes
-- ============================================================================

-- 1. Junction table linking settlements to contributing trips
CREATE TABLE settlement_trip_items (
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (settlement_id, trip_id)
);

CREATE INDEX idx_settlement_trips_trip_id ON settlement_trip_items(trip_id);

-- 2. Junction table linking settlements to contributing violations
CREATE TABLE settlement_violation_items (
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    violation_id UUID NOT NULL REFERENCES violations(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (settlement_id, violation_id)
);

CREATE INDEX idx_settlement_violations_violation_id ON settlement_violation_items(violation_id);

-- 3. Check constraint: exactly one dispute target must be specified
ALTER TABLE disputes ADD CONSTRAINT chk_dispute_single_target 
    CHECK (
        (trip_id IS NOT NULL AND violation_id IS NULL) OR 
        (trip_id IS NULL AND violation_id IS NOT NULL)
    );
