-- ============================================================================
-- GodownTrack POS - Supabase Database Schema
-- Multi-Tenant Architecture with Realtime Publication Support
-- ============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- pgcrypto: bcrypt password/PIN hashing used by the auth RPCs
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Businesses Table (Tenants)
-- Passwords are bcrypt-hashed server-side (pgcrypto); plaintext is never stored.
-- Verification happens in the SECURITY DEFINER RPCs defined in
-- supabase/migrations/001_hashed_credentials.sql (connect_business).
CREATE TABLE IF NOT EXISTS businesses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Staff Accounts Table (PIN-Based Authentication)
-- PINs are bcrypt-hashed server-side; never stored or transferred in plaintext.
-- auth_user_id binds a staff profile to the device's Supabase auth uid
-- (anonymous sign-in); migration 002's RLS scopes all data to this binding.
CREATE TABLE IF NOT EXISTS staff (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    pin_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'staff',
    is_active BOOLEAN DEFAULT true,
    auth_user_id UUID,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
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
    customer_phone TEXT,
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

-- 7. Row Level Security (baseline, pre-auth)
-- NOTE: The app authenticates tenants through SECURITY DEFINER RPCs
-- (supabase/migrations/001_hashed_credentials.sql) with server-side bcrypt
-- verification. Strict per-tenant isolation is applied by
-- supabase/migrations/002_strict_tenant_rls.sql, which replaces the permissive
-- policies below with policies scoped to staff.auth_user_id = auth.uid().
-- Do NOT run 002 until every device runs the RPC-based app version.
ALTER TABLE businesses ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE parties ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "anon_all_businesses" ON businesses FOR ALL TO anon USING (true) WITH CHECK (true);
CREATE POLICY "anon_all_staff" ON staff FOR ALL TO anon USING (true) WITH CHECK (true);
CREATE POLICY "anon_all_products" ON products FOR ALL TO anon USING (true) WITH CHECK (true);
CREATE POLICY "anon_all_parties" ON parties FOR ALL TO anon USING (true) WITH CHECK (true);
CREATE POLICY "anon_all_transactions" ON transactions FOR ALL TO anon USING (true) WITH CHECK (true);

-- 7. Indexes for High-Concurrency Multi-Tenant Lookups
CREATE INDEX IF NOT EXISTS idx_products_business_id ON products(business_id);
CREATE INDEX IF NOT EXISTS idx_transactions_business_id ON transactions(business_id);
CREATE INDEX IF NOT EXISTS idx_transactions_slip_no ON transactions(slip_no);
CREATE INDEX IF NOT EXISTS idx_parties_business_id ON parties(business_id);
CREATE INDEX IF NOT EXISTS idx_staff_business_id ON staff(business_id);
