-- Add gender and date of birth columns to users table
-- Both are nullable: gender is optional in the signup form, dob is application-required
-- but DB-nullable to keep existing users (who don't have one) valid.

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS gender TEXT,
  ADD COLUMN IF NOT EXISTS dob DATE;

-- Optional value constraint on gender — match the four signup options
ALTER TABLE users
  ADD CONSTRAINT users_gender_check
  CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'));
