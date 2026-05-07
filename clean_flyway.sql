-- Clean up failed Flyway migration
-- Run this script manually before restarting the application

-- Delete the failed migration record from Flyway history
DELETE FROM flyway_schema_history WHERE version = '1';

-- Or if you want to start completely fresh, drop the entire history table:
-- DROP TABLE IF EXISTS flyway_schema_history;
