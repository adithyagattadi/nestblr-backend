-- Seed data for NestBLR — 30 PGs across major Bengaluru localities
-- Real coordinates for: Koramangala, HSR, BTM, Indiranagar, Whitefield, Marathahalli, Bellandur

-- First, create a dummy owner user
INSERT INTO users (id, firebase_uid, phone, full_name, role, is_verified)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'seed_owner_1', '+919999999001', 'Demo Owner One', 'OWNER', true),
    ('22222222-2222-2222-2222-222222222222', 'seed_owner_2', '+919999999002', 'Demo Owner Two', 'OWNER', true),
    ('33333333-3333-3333-3333-333333333333', 'seed_tenant_1', '+919999999003', 'Demo Tenant', 'TENANT', true);

-- Helper: ST_MakePoint takes (longitude, latitude) — order matters!

INSERT INTO listings (owner_id, title, description, address_line, locality, pincode, location, gender_preference, pg_type, food_type, avg_rating, review_count) VALUES
-- Koramangala (12.9352, 77.6245)
('11111111-1111-1111-1111-111111111111', 'Sai Krishna PG for Men', 'Spacious rooms near Forum Mall, walking distance to BMTC bus stop.', '5th Block, Koramangala', 'Koramangala', '560095', ST_GeogFromText('POINT(77.6245 12.9352)'), 'MALE', 'PG', 'BOTH', 4.2, 28),
('11111111-1111-1111-1111-111111111111', 'Comfort Stay Ladies PG', 'Safe and secure PG for working women. Female warden 24/7.', '6th Block, Koramangala', 'Koramangala', '560095', ST_GeogFromText('POINT(77.6280 12.9320)'), 'FEMALE', 'PG', 'VEG', 4.5, 41),
('22222222-2222-2222-2222-222222222222', 'Urban Coliving Koramangala', 'Premium coliving with shared kitchen and lounge.', '7th Block, Koramangala', 'Koramangala', '560095', ST_GeogFromText('POINT(77.6210 12.9380)'), 'COED', 'COLIVING', 'BOTH', 4.7, 19),

-- HSR Layout (12.9120, 77.6470)
('11111111-1111-1111-1111-111111111111', 'HSR Boys Hostel', 'Budget-friendly PG near HSR Layout sector 2.', 'Sector 2, HSR Layout', 'HSR Layout', '560102', ST_GeogFromText('POINT(77.6470 12.9120)'), 'MALE', 'PG', 'BOTH', 3.9, 15),
('11111111-1111-1111-1111-111111111111', 'Elite Women PG HSR', 'Modern PG for women with all amenities.', 'Sector 1, HSR Layout', 'HSR Layout', '560102', ST_GeogFromText('POINT(77.6440 12.9100)'), 'FEMALE', 'PG', 'VEG', 4.3, 33),
('22222222-2222-2222-2222-222222222222', 'Stay Inn HSR Sector 7', 'Quiet residential area, ideal for IT professionals.', 'Sector 7, HSR Layout', 'HSR Layout', '560102', ST_GeogFromText('POINT(77.6510 12.9080)'), 'MALE', 'PG', 'NON_VEG', 4.0, 22),
('22222222-2222-2222-2222-222222222222', 'Urban Nest HSR Coliving', 'Premium coliving with gym, lounge and rooftop access.', 'Sector 1, HSR Layout', 'HSR Layout', '560102', ST_GeogFromText('POINT(77.6420 12.9140)'), 'COED', 'COLIVING', 'BOTH', 4.6, 12),

-- BTM Layout (12.9165, 77.6101)
('11111111-1111-1111-1111-111111111111', 'BTM Budget PG', 'Affordable PG for students and freshers.', '2nd Stage BTM', 'BTM Layout', '560076', ST_GeogFromText('POINT(77.6101 12.9165)'), 'MALE', 'PG', 'VEG', 3.5, 18),
('11111111-1111-1111-1111-111111111111', 'Lakeview Ladies PG', 'Near BTM lake, peaceful environment.', '1st Stage BTM', 'BTM Layout', '560029', ST_GeogFromText('POINT(77.6080 12.9180)'), 'FEMALE', 'PG', 'VEG', 4.1, 25),
('22222222-2222-2222-2222-222222222222', 'BTM Coliving Hub', 'Tech-park focused coliving space.', '16th Main BTM', 'BTM Layout', '560076', ST_GeogFromText('POINT(77.6120 12.9150)'), 'COED', 'COLIVING', 'BOTH', 4.4, 16),

-- Indiranagar (12.9716, 77.6412)
('11111111-1111-1111-1111-111111111111', 'Indiranagar Premium PG', 'Upscale PG in central Indiranagar.', '100 Feet Road', 'Indiranagar', '560038', ST_GeogFromText('POINT(77.6412 12.9716)'), 'MALE', 'PG', 'BOTH', 4.6, 35),
('22222222-2222-2222-2222-222222222222', 'Indiranagar Ladies Stay', 'Safe and stylish PG for women near CMH Road.', 'CMH Road, Indiranagar', 'Indiranagar', '560038', ST_GeogFromText('POINT(77.6395 12.9730)'), 'FEMALE', 'PG', 'BOTH', 4.5, 29),

-- Whitefield (12.9698, 77.7500)
('11111111-1111-1111-1111-111111111111', 'Whitefield Tech Park PG', 'Near ITPL, ideal for IT employees.', 'ITPL Main Road', 'Whitefield', '560066', ST_GeogFromText('POINT(77.7500 12.9698)'), 'MALE', 'PG', 'BOTH', 4.2, 31),
('11111111-1111-1111-1111-111111111111', 'Whitefield Ladies PG', 'Female-only PG with curfew rules.', 'Hope Farm Junction', 'Whitefield', '560066', ST_GeogFromText('POINT(77.7480 12.9710)'), 'FEMALE', 'PG', 'VEG', 4.0, 24),
('22222222-2222-2222-2222-222222222222', 'Whitefield Coliving', 'Modern coliving for working professionals.', 'Varthur Road', 'Whitefield', '560066', ST_GeogFromText('POINT(77.7460 12.9680)'), 'COED', 'COLIVING', 'BOTH', 4.4, 20),

-- Marathahalli (12.9591, 77.6974)
('11111111-1111-1111-1111-111111111111', 'Marathahalli Budget PG', 'Affordable accommodation near Marathahalli bridge.', 'Outer Ring Road', 'Marathahalli', '560037', ST_GeogFromText('POINT(77.6974 12.9591)'), 'MALE', 'PG', 'NON_VEG', 3.8, 19),
('22222222-2222-2222-2222-222222222222', 'Marathahalli Ladies Hostel', 'Safe hostel for women working in nearby tech parks.', 'AECS Layout', 'Marathahalli', '560037', ST_GeogFromText('POINT(77.6990 12.9600)'), 'FEMALE', 'HOSTEL', 'VEG', 4.1, 27),

-- Bellandur (12.9258, 77.6753)
('11111111-1111-1111-1111-111111111111', 'Bellandur Lakeview PG', 'Lake-facing rooms with great ventilation.', 'Outer Ring Road, Bellandur', 'Bellandur', '560103', ST_GeogFromText('POINT(77.6753 12.9258)'), 'MALE', 'PG', 'BOTH', 4.3, 22),
('22222222-2222-2222-2222-222222222222', 'Bellandur Coliving Space', 'Premium coliving for tech professionals.', 'Bellandur Gate', 'Bellandur', '560103', ST_GeogFromText('POINT(77.6770 12.9240)'), 'COED', 'COLIVING', 'BOTH', 4.5, 14),

-- Electronic City (12.8456, 77.6603)
('11111111-1111-1111-1111-111111111111', 'E-City Phase 1 PG', 'Near Infosys campus.', 'Phase 1, Electronic City', 'Electronic City', '560100', ST_GeogFromText('POINT(77.6603 12.8456)'), 'MALE', 'PG', 'BOTH', 4.0, 21),
('11111111-1111-1111-1111-111111111111', 'E-City Ladies PG', 'Female PG near Wipro Gate.', 'Phase 2, Electronic City', 'Electronic City', '560100', ST_GeogFromText('POINT(77.6650 12.8420)'), 'FEMALE', 'PG', 'VEG', 4.2, 18),

-- Jayanagar (12.9250, 77.5938)
('22222222-2222-2222-2222-222222222222', 'Jayanagar 4th Block PG', 'Traditional PG in family-friendly area.', '4th Block Jayanagar', 'Jayanagar', '560011', ST_GeogFromText('POINT(77.5938 12.9250)'), 'FEMALE', 'PG', 'VEG', 4.4, 30),

-- JP Nagar (12.9077, 77.5851)
('11111111-1111-1111-1111-111111111111', 'JP Nagar Budget Stay', 'Affordable PG near JP Nagar 6th Phase.', '6th Phase JP Nagar', 'JP Nagar', '560078', ST_GeogFromText('POINT(77.5851 12.9077)'), 'MALE', 'PG', 'VEG', 3.7, 16),

-- Sarjapur Road (12.9010, 77.6870)
('22222222-2222-2222-2222-222222222222', 'Sarjapur Road Coliving', 'New-age coliving near Wipro Sarjapur.', 'Sarjapur Road', 'Sarjapur Road', '560035', ST_GeogFromText('POINT(77.6870 12.9010)'), 'COED', 'COLIVING', 'BOTH', 4.6, 11),
('11111111-1111-1111-1111-111111111111', 'Sarjapur Ladies Hostel', 'Safe hostel for working women.', 'Sarjapur Road', 'Sarjapur Road', '560035', ST_GeogFromText('POINT(77.6850 12.9030)'), 'FEMALE', 'HOSTEL', 'VEG', 4.0, 23);

-- Add room options for each listing (just one option per for simplicity in seed)
INSERT INTO room_options (listing_id, sharing_type, monthly_rent, security_deposit, total_beds, available_beds)
SELECT id, 'DOUBLE', 8000 + (random() * 7000)::int, 15000 + (random() * 10000)::int, 4, 1 + (random() * 3)::int
FROM listings;

-- Single sharing premium option
INSERT INTO room_options (listing_id, sharing_type, monthly_rent, security_deposit, total_beds, available_beds)
SELECT id, 'SINGLE', 14000 + (random() * 8000)::int, 25000 + (random() * 15000)::int, 1, 1
FROM listings
WHERE pg_type IN ('COLIVING', 'PG');

-- Link some amenities (every listing gets WiFi and Power Backup; random others)
INSERT INTO listing_amenities (listing_id, amenity_id)
SELECT l.id, a.id
FROM listings l
CROSS JOIN amenities a
WHERE a.name IN ('WiFi', 'Power Backup');

INSERT INTO listing_amenities (listing_id, amenity_id)
SELECT l.id, a.id
FROM listings l
CROSS JOIN amenities a
WHERE a.name IN ('AC', 'Laundry', 'CCTV', 'Hot Water')
  AND random() > 0.4
ON CONFLICT DO NOTHING;
