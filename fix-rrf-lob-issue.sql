-- Fix RRF LOB issue in PostgreSQL
-- This script removes the Large Object references and converts columns to TEXT

-- Step 1: Drop and recreate the rrf_content column as TEXT (removes LOB reference)
ALTER TABLE request_for_requisition DROP COLUMN IF EXISTS rrf_content;
ALTER TABLE request_for_requisition ADD COLUMN rrf_content TEXT;

-- Step 2: Drop and recreate the specifications column as TEXT (removes LOB reference)
ALTER TABLE request_for_requisition DROP COLUMN IF EXISTS specifications;
ALTER TABLE request_for_requisition ADD COLUMN specifications TEXT;

-- Step 3: Verify the changes
SELECT 
    column_name, 
    data_type, 
    character_maximum_length,
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'request_for_requisition'
AND column_name IN ('rrf_content', 'specifications')
ORDER BY column_name;

-- Note: This will delete existing RRF data. If you have important data,
-- you may need to export it first and re-import after the schema change.
