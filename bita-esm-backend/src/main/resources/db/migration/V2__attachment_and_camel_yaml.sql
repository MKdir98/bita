-- Attachment unified table and Camel YAML schema
-- Plan: Attachment یکپارچه و Camel YAML

-- 1. Create attachment_file_type enum
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'attachment_file_type') THEN
        CREATE TYPE attachment_file_type AS ENUM ('JAR', 'PROPERTIES', 'XML', 'OTHER');
    END IF;
END$$;

-- 2. Create attachment table
CREATE TABLE IF NOT EXISTS attachment (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type attachment_file_type NOT NULL DEFAULT 'JAR',
    file_path VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Add attachment_ids to template and instance tables
ALTER TABLE endpoint_template ADD COLUMN IF NOT EXISTS attachment_ids BIGINT[] DEFAULT '{}';
ALTER TABLE component_template ADD COLUMN IF NOT EXISTS attachment_ids BIGINT[] DEFAULT '{}';
ALTER TABLE route_template ADD COLUMN IF NOT EXISTS attachment_ids BIGINT[] DEFAULT '{}';
ALTER TABLE endpoint ADD COLUMN IF NOT EXISTS attachment_ids BIGINT[] DEFAULT '{}';
ALTER TABLE component ADD COLUMN IF NOT EXISTS attachment_ids BIGINT[] DEFAULT '{}';
ALTER TABLE route ADD COLUMN IF NOT EXISTS attachment_ids BIGINT[] DEFAULT '{}';

-- 4. EndpointTemplate: add camel_yaml, migrate uri_pattern, drop uri_pattern
ALTER TABLE endpoint_template ADD COLUMN IF NOT EXISTS camel_yaml TEXT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'endpoint_template' AND column_name = 'uri_pattern') THEN
        UPDATE endpoint_template
        SET camel_yaml = '- from:' || E'\n' || '    uri: "' || COALESCE(REPLACE(uri_pattern, '"', '\"'), '') || '"' || E'\n' || '    steps: []'
        WHERE uri_pattern IS NOT NULL AND (camel_yaml IS NULL OR camel_yaml = '');
        ALTER TABLE endpoint_template DROP COLUMN uri_pattern;
    END IF;
END$$;

-- 5. ComponentTemplate: camel_yaml already added via step 4 pattern - add if not exists
ALTER TABLE component_template ADD COLUMN IF NOT EXISTS camel_yaml TEXT;

-- 6. RouteTemplate: camel_yaml
ALTER TABLE route_template ADD COLUMN IF NOT EXISTS camel_yaml TEXT;

-- 7. Endpoint: resolved_camel_yaml
ALTER TABLE endpoint ADD COLUMN IF NOT EXISTS resolved_camel_yaml TEXT;
