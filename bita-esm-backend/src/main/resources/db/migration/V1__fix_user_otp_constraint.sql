-- Fix user_otp table constraint to allow PASSWORD_RESET value

-- Step 1: Delete all existing OTP records (they are temporary and expire anyway)
DELETE FROM user_otp;

-- Step 2: Drop the existing constraint if it exists
ALTER TABLE user_otp DROP CONSTRAINT IF EXISTS user_otp_otp_type_check;

-- Step 3: Add the new constraint with both REGISTER and PASSWORD_RESET values
ALTER TABLE user_otp ADD CONSTRAINT user_otp_otp_type_check 
    CHECK (otp_type::text = ANY (ARRAY['REGISTER'::character varying, 'PASSWORD_RESET'::character varying]::text[]));
