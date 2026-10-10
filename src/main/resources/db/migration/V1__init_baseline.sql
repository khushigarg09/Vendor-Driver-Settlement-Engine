-- Baseline migration for MoveInSync Settlement Engine
CREATE TABLE IF NOT EXISTS system_metadata (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    property_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_metadata (id, property_value) VALUES ('SCHEMA_VERSION', '1.0.0-PHASE-02');
