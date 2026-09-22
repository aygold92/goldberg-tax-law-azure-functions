-- Managed-agent provenance: the splitter runs per file, the extraction agents run per classification.
ALTER TABLE files ADD COLUMN anthropic_file_id   VARCHAR(64) NULL;
ALTER TABLE files ADD COLUMN splitter_session_id VARCHAR(64) NULL;

-- bank_statements.bates_stamps and checks.bates_stamp stay for the Azure pipeline; the agent path keeps a
-- classification's stamps here instead of copying them onto every statement and check under it.
ALTER TABLE classifications ADD COLUMN bank_name             VARCHAR(255) NULL;
ALTER TABLE classifications ADD COLUMN extraction_session_id VARCHAR(64)  NULL;
ALTER TABLE classifications ADD COLUMN bates_stamps          TEXT         NULL;

CREATE INDEX files_splitter_session_id ON files (splitter_session_id);
CREATE INDEX classifications_extraction_session_id ON classifications (extraction_session_id);
