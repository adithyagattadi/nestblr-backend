-- 04_inquiries_dedup.sql
-- Adds dedup support to the inquiries table:
--   * last_attempt_at — bumped on every repeat inquiry (created_at stays the first attempt)
--   * UNIQUE (tenant_id, listing_id) — one row per (tenant, listing), enables ON CONFLICT upsert
--
-- Deviations from the originally-specified SQL, both deliberate:
--   * TIMESTAMPTZ (not TIMESTAMP) — the rest of the schema uses `timestamp with time zone`
--     (incl. inquiries.created_at), and the owner summary surfaces UTC instants.
--   * The constraint add is guarded so the migration is idempotent (re-runnable). The
--     inquiries table is empty at apply time, so there are no existing duplicates to reject.

ALTER TABLE inquiries ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'inquiries_tenant_listing_unique'
    ) THEN
        ALTER TABLE inquiries
            ADD CONSTRAINT inquiries_tenant_listing_unique UNIQUE (tenant_id, listing_id);
    END IF;
END $$;
