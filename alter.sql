ALTER TABLE businesses ADD COLUMN IF NOT EXISTS password TEXT;
ALTER TABLE businesses ADD CONSTRAINT unique_name UNIQUE (name);
