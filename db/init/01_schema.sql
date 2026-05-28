-- NestBLR schema (v1)
-- Runs automatically when Postgres container starts for the first time

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ========== USERS ==========
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid TEXT UNIQUE NOT NULL,
    phone TEXT UNIQUE NOT NULL,
    full_name TEXT,
    email TEXT,
    role TEXT NOT NULL CHECK (role IN ('TENANT', 'OWNER')),
    profile_photo_url TEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_firebase_uid ON users(firebase_uid);

-- ========== LISTINGS ==========
CREATE TABLE listings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    address_line TEXT NOT NULL,
    locality TEXT NOT NULL,
    city TEXT NOT NULL DEFAULT 'Bengaluru',
    pincode TEXT,
    contact_phone TEXT,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    gender_preference TEXT CHECK (gender_preference IN ('MALE', 'FEMALE', 'COED')),
    pg_type TEXT CHECK (pg_type IN ('PG', 'HOSTEL', 'COLIVING')),
    food_type TEXT CHECK (food_type IN ('VEG', 'NON_VEG', 'BOTH', 'NONE')),
    rules JSONB,
    status TEXT DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'DELETED')),
    avg_rating NUMERIC(2,1) DEFAULT 0,
    review_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_listings_location ON listings USING GIST (location);
CREATE INDEX idx_listings_owner ON listings(owner_id);
CREATE INDEX idx_listings_status ON listings(status);
CREATE INDEX idx_listings_locality ON listings(locality);

-- ========== ROOM OPTIONS ==========
CREATE TABLE room_options (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    sharing_type TEXT CHECK (sharing_type IN ('SINGLE', 'DOUBLE', 'TRIPLE', 'QUAD')),
    monthly_rent INTEGER NOT NULL,
    security_deposit INTEGER NOT NULL,
    total_beds INTEGER NOT NULL,
    available_beds INTEGER NOT NULL,
    notice_period_days INTEGER DEFAULT 30
);

CREATE INDEX idx_room_options_listing ON room_options(listing_id);
CREATE INDEX idx_room_options_rent ON room_options(monthly_rent);

-- ========== AMENITIES ==========
CREATE TABLE amenities (
    id SERIAL PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    icon_key TEXT
);

CREATE TABLE listing_amenities (
    listing_id UUID REFERENCES listings(id) ON DELETE CASCADE,
    amenity_id INTEGER REFERENCES amenities(id),
    PRIMARY KEY (listing_id, amenity_id)
);

-- ========== PHOTOS ==========
CREATE TABLE listing_photos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    display_order INTEGER DEFAULT 0,
    uploaded_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_photos_listing ON listing_photos(listing_id, display_order);

-- ========== FAVORITES ==========
CREATE TABLE favorites (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    listing_id UUID REFERENCES listings(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, listing_id)
);

-- ========== REVIEWS ==========
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    stayed_from DATE,
    stayed_until DATE,
    is_flagged BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(listing_id, user_id)
);

CREATE INDEX idx_reviews_listing ON reviews(listing_id);

-- ========== INQUIRIES ==========
CREATE TABLE inquiries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id UUID NOT NULL REFERENCES listings(id),
    tenant_id UUID NOT NULL REFERENCES users(id),
    message TEXT,
    status TEXT DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RESPONDED', 'CLOSED')),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_inquiries_listing ON inquiries(listing_id);
CREATE INDEX idx_inquiries_tenant ON inquiries(tenant_id);

-- Seed amenities
INSERT INTO amenities (name, icon_key) VALUES
    ('WiFi', 'wifi'),
    ('AC', 'ac_unit'),
    ('Laundry', 'local_laundry_service'),
    ('Parking', 'local_parking'),
    ('Power Backup', 'bolt'),
    ('CCTV', 'videocam'),
    ('Hot Water', 'shower'),
    ('Refrigerator', 'kitchen'),
    ('Housekeeping', 'cleaning_services'),
    ('TV', 'tv'),
    ('Gym', 'fitness_center'),
    ('Food Included', 'restaurant');
