-- ============================================================================
-- V3__seed_reference_data.sql
-- Deterministic Reference and Seed Data for Testing & Demonstration
-- ============================================================================

-- 1. Seed Vendor
INSERT INTO vendors (id, vendor_code, name, tax_identifier, status, created_at, updated_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'VND-BLR-01',
    'QuickCabs Logistics Private Limited',
    'AAACQ1234F',
    'ACTIVE',
    TIMESTAMP '2026-01-01 00:00:00+00',
    TIMESTAMP '2026-01-01 00:00:00+00'
);

-- 2. Seed Business Unit
INSERT INTO business_units (id, unit_code, name, timezone, is_active, created_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'BU-BLR-TECH-PARK',
    'Bengaluru Outer Ring Road Campus',
    'Asia/Kolkata',
    TRUE,
    TIMESTAMP '2026-01-01 00:00:00+00'
);

-- 3. Seed Vendor Contract
INSERT INTO vendor_contracts (id, vendor_id, contract_number, billing_type, start_date, status, created_at)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'CNT-VND01-2026',
    'PER_TRIP',
    DATE '2026-01-01',
    'ACTIVE',
    TIMESTAMP '2026-01-01 00:00:00+00'
);

-- 4. Seed Effective-Dated Contract Rates (Rate Changes on October 15th, 2026)
-- Rate Period 1: Oct 01 to Oct 15 (exclusive) = ₹250.00
INSERT INTO contract_rates (id, contract_id, vehicle_category, rate_amount, effective_from, effective_to, created_at)
VALUES (
    '55555555-5555-5555-5555-555555555551',
    '33333333-3333-3333-3333-333333333333',
    'SEDAN',
    250.00,
    TIMESTAMP '2026-10-01 00:00:00+05:30',
    TIMESTAMP '2026-10-15 00:00:00+05:30',
    TIMESTAMP '2026-09-25 00:00:00+00'
);

-- Rate Period 2: Oct 15 onwards = ₹280.00
INSERT INTO contract_rates (id, contract_id, vehicle_category, rate_amount, effective_from, effective_to, created_at)
VALUES (
    '55555555-5555-5555-5555-555555555552',
    '33333333-3333-3333-3333-333333333333',
    'SEDAN',
    280.00,
    TIMESTAMP '2026-10-15 00:00:00+05:30',
    NULL,
    TIMESTAMP '2026-10-10 00:00:00+00'
);

-- 5. Seed Drivers
INSERT INTO drivers (id, vendor_id, driver_code, first_name, last_name, phone_number, pan_number, bank_account_number, ifsc_code, status, created_at)
VALUES (
    '44444444-4444-4444-4444-444444444441',
    '11111111-1111-1111-1111-111111111111',
    'DRV-10042',
    'Rajesh',
    'Kumar',
    '+919876543210',
    'ABCDE1234F',
    '123456789012',
    'HDFC0001234',
    'ACTIVE',
    TIMESTAMP '2026-01-01 00:00:00+00'
);

INSERT INTO drivers (id, vendor_id, driver_code, first_name, last_name, phone_number, pan_number, bank_account_number, ifsc_code, status, created_at)
VALUES (
    '44444444-4444-4444-4444-444444444442',
    '11111111-1111-1111-1111-111111111111',
    'DRV-10043',
    'Suresh',
    'Patel',
    '+919876543211',
    'BCDEF2345G',
    '987654321098',
    'ICIC0005678',
    'ACTIVE',
    TIMESTAMP '2026-01-01 00:00:00+00'
);

-- 6. Seed Sample Completed Trips
-- Driver 1 trip BEFORE rate change (Oct 10 -> Should resolve to ₹250.00)
INSERT INTO trips (id, source_trip_id, driver_id, vendor_id, business_unit_id, vehicle_category, started_at, completed_at, status, delay_minutes, settlement_status, created_at)
VALUES (
    '66666666-6666-6666-6666-666666666661',
    'TRIP-2026-10-001',
    '44444444-4444-4444-4444-444444444441',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'SEDAN',
    TIMESTAMP '2026-10-10 08:00:00+05:30',
    TIMESTAMP '2026-10-10 09:15:00+05:30',
    'COMPLETED',
    0,
    'PENDING',
    TIMESTAMP '2026-10-10 09:15:00+00'
);

-- Driver 1 trip AFTER rate change (Oct 16 -> Should resolve to ₹280.00)
INSERT INTO trips (id, source_trip_id, driver_id, vendor_id, business_unit_id, vehicle_category, started_at, completed_at, status, delay_minutes, settlement_status, created_at)
VALUES (
    '66666666-6666-6666-6666-666666666662',
    'TRIP-2026-10-002',
    '44444444-4444-4444-4444-444444444441',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'SEDAN',
    TIMESTAMP '2026-10-16 17:30:00+05:30',
    TIMESTAMP '2026-10-16 18:45:00+05:30',
    'COMPLETED',
    0,
    'PENDING',
    TIMESTAMP '2026-10-16 18:45:00+00'
);

-- 7. Seed Sample Violation
INSERT INTO violations (id, source_violation_id, driver_id, trip_id, violation_type, occurred_at, penalty_amount, status, created_at)
VALUES (
    '77777777-7777-7777-7777-777777777771',
    'VIO-2026-10-099',
    '44444444-4444-4444-4444-444444444441',
    '66666666-6666-6666-6666-666666666662',
    'OVER_SPEED',
    TIMESTAMP '2026-10-16 18:10:00+05:30',
    500.00,
    'PENDING',
    TIMESTAMP '2026-10-16 18:10:00+00'
);

-- 8. Seed Vendor Claimed Invoice
INSERT INTO vendor_invoices (id, vendor_id, business_unit_id, invoice_number, billing_period, claimed_amount, invoice_date, status, created_at)
VALUES (
    '88888888-8888-8888-8888-888888888881',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'INV-VND01-2026-10',
    '2026-10',
    530.00,
    DATE '2026-11-02',
    'SUBMITTED',
    TIMESTAMP '2026-11-02 10:00:00+00'
);
