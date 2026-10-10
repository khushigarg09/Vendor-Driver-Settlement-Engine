-- ============================================================================
-- V2__create_settlement_tables.sql
-- Relational Schema for MoveInSync Vendor & Driver Settlement Engine
-- ============================================================================

-- 1. Vendors
CREATE TABLE vendors (
    id UUID PRIMARY KEY,
    vendor_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    tax_identifier VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. Business Units
CREATE TABLE business_units (
    id UUID PRIMARY KEY,
    unit_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    timezone VARCHAR(50) DEFAULT 'Asia/Kolkata' NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. Drivers
CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    driver_code VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    pan_number VARCHAR(20) NOT NULL,
    bank_account_number VARCHAR(50) NOT NULL,
    ifsc_code VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 4. Vendor Contracts
CREATE TABLE vendor_contracts (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    contract_number VARCHAR(100) NOT NULL UNIQUE,
    billing_type VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 5. Effective-Dated Contract Rates
CREATE TABLE contract_rates (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL REFERENCES vendor_contracts(id),
    vehicle_category VARCHAR(50) NOT NULL,
    rate_amount NUMERIC(12, 2) NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_contract_rates_lookup 
    ON contract_rates(contract_id, effective_from, effective_to);

-- 6. Operational Completed Trips
CREATE TABLE trips (
    id UUID PRIMARY KEY,
    source_trip_id VARCHAR(100) NOT NULL UNIQUE,
    driver_id UUID NOT NULL REFERENCES drivers(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    business_unit_id UUID NOT NULL REFERENCES business_units(id),
    vehicle_category VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    delay_minutes INTEGER DEFAULT 0 NOT NULL,
    settlement_status VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_trips_driver_period 
    ON trips(driver_id, completed_at, settlement_status);

-- 7. Operational Violations & Telemetry Breaches
CREATE TABLE violations (
    id UUID PRIMARY KEY,
    source_violation_id VARCHAR(100) NOT NULL UNIQUE,
    driver_id UUID NOT NULL REFERENCES drivers(id),
    trip_id UUID REFERENCES trips(id),
    violation_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    penalty_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_violations_driver_period 
    ON violations(driver_id, occurred_at, status);

-- 8. Settlement Runs (Orchestration & Idempotency Scope)
CREATE TABLE settlement_runs (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    business_unit_id UUID NOT NULL REFERENCES business_units(id),
    billing_period VARCHAR(7) NOT NULL,
    run_number INTEGER DEFAULT 1 NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_gross NUMERIC(14, 2) DEFAULT 0.00 NOT NULL,
    total_incentives NUMERIC(14, 2) DEFAULT 0.00 NOT NULL,
    total_penalties NUMERIC(14, 2) DEFAULT 0.00 NOT NULL,
    total_tds NUMERIC(14, 2) DEFAULT 0.00 NOT NULL,
    total_net_payout NUMERIC(14, 2) DEFAULT 0.00 NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_settlement_run_scope UNIQUE (vendor_id, business_unit_id, billing_period)
);

-- 9. Individual Driver Settlements (Payslips)
CREATE TABLE settlements (
    id UUID PRIMARY KEY,
    settlement_run_id UUID NOT NULL REFERENCES settlement_runs(id),
    driver_id UUID NOT NULL REFERENCES drivers(id),
    billing_period VARCHAR(7) NOT NULL,
    trips_count INTEGER DEFAULT 0 NOT NULL,
    gross_base_pay NUMERIC(12, 2) NOT NULL,
    incentives_total NUMERIC(12, 2) NOT NULL,
    penalties_total NUMERIC(12, 2) NOT NULL,
    tds_tax_total NUMERIC(12, 2) NOT NULL,
    net_payout NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_settlement_driver_run UNIQUE (settlement_run_id, driver_id)
);

CREATE INDEX idx_settlements_driver_period 
    ON settlements(driver_id, billing_period);

-- 10. Signed Itemized Payslip Lines (Financial Ledger)
CREATE TABLE settlement_lines (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES settlements(id),
    line_type VARCHAR(50) NOT NULL,
    signed_amount NUMERIC(12, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    source_reference_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_settlement_lines_parent 
    ON settlement_lines(settlement_id);

-- 11. Vendor Claimed Invoices
CREATE TABLE vendor_invoices (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    business_unit_id UUID NOT NULL REFERENCES business_units(id),
    invoice_number VARCHAR(100) NOT NULL,
    billing_period VARCHAR(7) NOT NULL,
    claimed_amount NUMERIC(14, 2) NOT NULL,
    invoice_date DATE NOT NULL,
    status VARCHAR(30) DEFAULT 'SUBMITTED' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_vendor_invoice_number UNIQUE (vendor_id, invoice_number)
);

-- 12. Reconciliations (System Payout vs Vendor Invoice)
CREATE TABLE reconciliations (
    id UUID PRIMARY KEY,
    settlement_run_id UUID NOT NULL REFERENCES settlement_runs(id),
    vendor_invoice_id UUID NOT NULL REFERENCES vendor_invoices(id),
    system_amount NUMERIC(14, 2) NOT NULL,
    invoice_amount NUMERIC(14, 2) NOT NULL,
    variance_amount NUMERIC(14, 2) NOT NULL,
    tolerance_applied NUMERIC(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    explanation VARCHAR(500),
    reconciled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reconciled_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_reconciliation_pair UNIQUE (settlement_run_id, vendor_invoice_id)
);

-- 13. Disputes (Post-Finalization Challenge on Trip or Penalty)
CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES settlements(id),
    trip_id UUID REFERENCES trips(id),
    violation_id UUID REFERENCES violations(id),
    reason VARCHAR(500) NOT NULL,
    claimed_delta NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) DEFAULT 'OPEN' NOT NULL,
    resolved_by VARCHAR(100),
    resolution_notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 14. Adjustments & Delta Lines (Immutable Delta Posting)
CREATE TABLE adjustments (
    id UUID PRIMARY KEY,
    dispute_id UUID NOT NULL REFERENCES disputes(id),
    settlement_id UUID NOT NULL REFERENCES settlements(id),
    adjustment_type VARCHAR(50) NOT NULL,
    delta_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) DEFAULT 'APPROVED' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE adjustment_lines (
    id UUID PRIMARY KEY,
    adjustment_id UUID NOT NULL REFERENCES adjustments(id),
    signed_amount NUMERIC(12, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 15. Audit Events
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    entity_name VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    details_json TEXT
);

CREATE INDEX idx_audit_events_lookup 
    ON audit_events(entity_name, entity_id);
