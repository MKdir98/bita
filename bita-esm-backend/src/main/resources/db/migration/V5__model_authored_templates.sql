-- Templates the model added through groovy_template: gateways compile their scripts in the ESB sandbox
ALTER TABLE groovy_template ADD COLUMN IF NOT EXISTS model_authored BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE groovy_template_aud ADD COLUMN IF NOT EXISTS model_authored BOOLEAN;
