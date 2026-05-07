-- BITA Database Initialization Script
-- This script runs automatically when PostgreSQL container starts for the first time

-- Create additional schemas if needed
CREATE SCHEMA IF NOT EXISTS audit;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA public TO bita;
GRANT ALL PRIVILEGES ON SCHEMA audit TO bita;

-- Create extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE 'BITA database initialized successfully';
END $$;
