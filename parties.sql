CREATE TABLE IF NOT EXISTS parties (
    id TEXT NOT NULL,
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('customer', 'vendor')),
    name TEXT NOT NULL,
    phone TEXT,
    gstin TEXT,
    default_destination TEXT,
    account_no TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (id, business_id)
);
