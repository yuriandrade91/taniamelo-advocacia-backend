-- Create table to store client situation history
-- Run by Flyway on application startup

CREATE TABLE IF NOT EXISTS client_situation_history (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    previous_situation VARCHAR(100),
    new_situation VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    changed_by INTEGER
);

CREATE INDEX IF NOT EXISTS idx_client_situation_history_client_id ON client_situation_history(client_id);
