-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Businesses Table (Tenants)
CREATE TABLE IF NOT EXISTS businesses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Staff Accounts Table
CREATE TABLE IF NOT EXISTS staff (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    pin TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'staff',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Cement Products Table
CREATE TABLE IF NOT EXISTS cement_products (
    id TEXT NOT NULL,
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    grade TEXT,
    weight_per_bag_kg DOUBLE PRECISION,
    default_rate_per_bag DOUBLE PRECISION,
    bay_location TEXT,
    current_stock_bags INTEGER,
    batch_no TEXT,
    image_url TEXT,
    is_active BOOLEAN DEFAULT true,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (id, business_id)
);

-- 4. Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    id TEXT NOT NULL,
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    slip_no TEXT,
    type TEXT,
    timestamp BIGINT,
    date_str TEXT,
    time_str TEXT,
    party_name TEXT,
    vehicle_no TEXT,
    driver_name TEXT,
    driver_phone TEXT,
    challan_no TEXT,
    ewb_no TEXT,
    destination_site TEXT,
    total_bags INTEGER,
    total_metric_tons DOUBLE PRECISION,
    total_amount DOUBLE PRECISION,
    dispatched_by TEXT,
    items JSONB,
    PRIMARY KEY (id, business_id)
);

-- Note: RLS (Row Level Security) will be enforced by the frontend passing the business_id for now 
-- until we integrate full JWT auth.
