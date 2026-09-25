-- ============================================================================
-- GodownTrack POS - Supabase Database Schema
-- Multi-Tenant Architecture with Realtime Publication Support
-- ============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Businesses Table (Tenants)
CREATE TABLE IF NOT EXISTS businesses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Staff Accounts Table (PIN-Based Authentication)
CREATE TABLE IF NOT EXISTS staff (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    pin TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'staff',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Products Catalog Table (General-Purpose Warehouse Inventory)
CREATE TABLE IF NOT EXISTS products (
    id TEXT NOT NULL,
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    grade TEXT DEFAULT 'Units',
    unit TEXT DEFAULT 'Units',
    weight_per_bag_kg DOUBLE PRECISION DEFAULT 50.0,
    default_rate_per_bag DOUBLE PRECISION DEFAULT 0.0,
    bay_location TEXT DEFAULT 'Godown 1',
    current_stock_bags INTEGER DEFAULT 0,
    batch_no TEXT DEFAULT 'NEW',
    image_url TEXT,
    is_active BOOLEAN DEFAULT true,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (id, business_id)
);

-- 4. Parties Table (Customers & Vendors)
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

-- 5. Transactions Table (Dispatch Outward & Stock-In Inward Slips)
CREATE TABLE IF NOT EXISTS transactions (
    id TEXT NOT NULL,
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    slip_no TEXT,
    type TEXT CHECK (type IN ('OUTWARD', 'INWARD')),
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

-- 6. Enable Realtime Broadcasting on Core Tables
ALTER PUBLICATION supabase_realtime ADD TABLE products;
ALTER PUBLICATION supabase_realtime ADD TABLE transactions;
ALTER PUBLICATION supabase_realtime ADD TABLE parties;

-- 7. Indexes for High-Concurrency Multi-Tenant Lookups
CREATE INDEX IF NOT EXISTS idx_products_business_id ON products(business_id);
CREATE INDEX IF NOT EXISTS idx_transactions_business_id ON transactions(business_id);
CREATE INDEX IF NOT EXISTS idx_transactions_slip_no ON transactions(slip_no);
CREATE INDEX IF NOT EXISTS idx_parties_business_id ON parties(business_id);
CREATE INDEX IF NOT EXISTS idx_staff_business_id ON staff(business_id);
