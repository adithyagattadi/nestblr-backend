-- 03_seed_reviews.sql
-- Backfills honest review rows for the 25 seeded listings so their displayed
-- avg_rating / review_count are backed by real reviews rows. Without this, the
-- first real review on a seeded listing collapses "4.2 (28)" to "5.0 (1)".
--
-- Additive + idempotent: phantom users use ON CONFLICT (firebase_uid) DO NOTHING,
-- review rows use ON CONFLICT (listing_id, user_id) DO NOTHING. Re-running is safe.
--
-- Only seed_owner_1 / seed_owner_2 listings are touched (matched by title). The
-- app-created listings (weewe, vsr colive, Adithya Co-live, wwww) stay at 0 reviews.

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 1 — 15 phantom TENANT users (FK targets for reviews; never log in).
-- phone uses the same `email:<uid>` shape as the existing email-auth users
-- (phone is NOT NULL + UNIQUE), so each is distinct.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO users (firebase_uid, email, phone, full_name, role, is_verified) VALUES
    ('seed-reviewer-001', 'seed-reviewer-001@example.com', 'email:seed-reviewer-001', 'Amit S.',    'TENANT', FALSE),
    ('seed-reviewer-002', 'seed-reviewer-002@example.com', 'email:seed-reviewer-002', 'Priya R.',   'TENANT', FALSE),
    ('seed-reviewer-003', 'seed-reviewer-003@example.com', 'email:seed-reviewer-003', 'Karthik M.', 'TENANT', FALSE),
    ('seed-reviewer-004', 'seed-reviewer-004@example.com', 'email:seed-reviewer-004', 'Sneha P.',   'TENANT', FALSE),
    ('seed-reviewer-005', 'seed-reviewer-005@example.com', 'email:seed-reviewer-005', 'Rahul V.',   'TENANT', FALSE),
    ('seed-reviewer-006', 'seed-reviewer-006@example.com', 'email:seed-reviewer-006', 'Divya N.',   'TENANT', FALSE),
    ('seed-reviewer-007', 'seed-reviewer-007@example.com', 'email:seed-reviewer-007', 'Arjun K.',   'TENANT', FALSE),
    ('seed-reviewer-008', 'seed-reviewer-008@example.com', 'email:seed-reviewer-008', 'Meera J.',   'TENANT', FALSE),
    ('seed-reviewer-009', 'seed-reviewer-009@example.com', 'email:seed-reviewer-009', 'Vikram T.',  'TENANT', FALSE),
    ('seed-reviewer-010', 'seed-reviewer-010@example.com', 'email:seed-reviewer-010', 'Ananya D.',  'TENANT', FALSE),
    ('seed-reviewer-011', 'seed-reviewer-011@example.com', 'email:seed-reviewer-011', 'Rohit B.',   'TENANT', FALSE),
    ('seed-reviewer-012', 'seed-reviewer-012@example.com', 'email:seed-reviewer-012', 'Pooja H.',   'TENANT', FALSE),
    ('seed-reviewer-013', 'seed-reviewer-013@example.com', 'email:seed-reviewer-013', 'Sandeep G.', 'TENANT', FALSE),
    ('seed-reviewer-014', 'seed-reviewer-014@example.com', 'email:seed-reviewer-014', 'Nisha L.',   'TENANT', FALSE),
    ('seed-reviewer-015', 'seed-reviewer-015@example.com', 'email:seed-reviewer-015', 'Kiran A.',   'TENANT', FALSE)
ON CONFLICT (firebase_uid) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 2 — generate review rows.
--
-- How the rating distributions are generated:
--   * Each listing has an explicit integer[] of ratings (the `targets` CTE).
--     The multiset is hand-built so its mean ≈ the originally-seeded avg_rating,
--     scaled to 3..14 reviews to fit the 15-user phantom set. e.g.
--       Comfort Stay (4.5/41 -> 14): [8x5, 5x4, 1x3] = 63/14 = 4.50
--       Sai Krishna  (4.2/28 -> 12): [4x5, 6x4, 2x3] = 50/12 = 4.17
--       Urban Coliving (4.7/19 -> 10): [7x5, 3x4]    = 47/10 = 4.70
--       BTM Budget   (3.5/18 ->  8): [1x5,3x4,3x3,1x2] = 28/8 = 3.50
--   * unnest WITH ORDINALITY turns each array into rows; `pos` (1..count) picks a
--     distinct phantom user via ((listing_offset + pos - 1) % 15) + 1 — distinct
--     within a listing because count <= 14, and rotated per listing for spread.
--   * Comments are tone-aligned by rating (5/4/3/2 pools) and assigned uniquely:
--     the k-th review of a given rating gets the k-th comment of that rating's
--     pool (row_number), so no comment is ever reused.
--   * created_at is staggered backwards by ~1 day per row (+ rotating hours),
--     spreading the rows across the past ~5 months.
-- ─────────────────────────────────────────────────────────────────────────────
WITH phantom AS (
    SELECT id, row_number() OVER (ORDER BY firebase_uid) AS un   -- 1..15
    FROM users
    WHERE firebase_uid LIKE 'seed-reviewer-%'
),
targets(title, ratings) AS (
    VALUES
        ('Bellandur Lakeview PG',        ARRAY[5,5,5,4,4,3]),
        ('BTM Budget PG',                ARRAY[5,4,4,4,3,3,3,2]),
        ('Comfort Stay Ladies PG',       ARRAY[5,5,5,5,5,5,5,5,4,4,4,4,4,3]),
        ('E-City Ladies PG',             ARRAY[5,4,4,4]),
        ('E-City Phase 1 PG',            ARRAY[5,4,4,4,3]),
        ('Elite Women PG HSR',           ARRAY[5,5,5,4,4,3]),
        ('HSR Boys Hostel',              ARRAY[5,4,4,3]),
        ('Indiranagar Premium PG',       ARRAY[5,5,5,4,4]),
        ('JP Nagar Budget Stay',         ARRAY[5,4,3,3]),
        ('Lakeview Ladies PG',           ARRAY[5,5,4,4,4,4,3]),
        ('Marathahalli Budget PG',       ARRAY[5,4,4,3,3]),
        ('Sai Krishna PG for Men',       ARRAY[5,5,5,5,4,4,4,4,4,4,3,3]),
        ('Sarjapur Ladies Hostel',       ARRAY[5,4,4,4,3]),
        ('Whitefield Ladies PG',         ARRAY[5,5,4,3,3]),
        ('Whitefield Tech Park PG',      ARRAY[5,5,4,4,4,3]),
        ('Bellandur Coliving Space',     ARRAY[5,5,4,4]),
        ('BTM Coliving Hub',             ARRAY[5,5,4,4,4]),
        ('Indiranagar Ladies Stay',      ARRAY[5,5,5,4,4,4]),
        ('Jayanagar 4th Block PG',       ARRAY[5,5,4,4,4]),
        ('Marathahalli Ladies Hostel',   ARRAY[5,5,4,4,4,3]),
        ('Sarjapur Road Coliving',       ARRAY[5,5,5,4,4]),
        ('Stay Inn HSR Sector 7',        ARRAY[5,5,4,3,3]),
        ('Urban Coliving Koramangala',   ARRAY[5,5,5,5,5,5,5,4,4,4]),
        ('Urban Nest HSR Coliving',      ARRAY[5,5,5,4,4]),
        ('Whitefield Coliving',          ARRAY[5,5,4,4,4])
),
-- Resolve titles to live listing ids; lst_idx gives each listing a user-offset.
listing_match AS (
    SELECT l.id AS listing_id, t.ratings,
           (row_number() OVER (ORDER BY t.title))::int - 1 AS offset
    FROM targets t
    JOIN listings l ON l.title = t.title
),
expanded AS (
    SELECT lm.listing_id, lm.offset, e.rating, e.pos
    FROM listing_match lm
    CROSS JOIN LATERAL unnest(lm.ratings) WITH ORDINALITY AS e(rating, pos)
),
with_keys AS (
    SELECT
        ex.listing_id,
        ex.rating,
        ((ex.offset + ex.pos - 1) % 15) + 1 AS un,                                   -- distinct user per listing
        row_number() OVER (PARTITION BY ex.rating ORDER BY ex.listing_id, ex.pos) AS rn_rating,  -- unique comment slot
        row_number() OVER (ORDER BY ex.listing_id, ex.pos) AS gseq                   -- date stagger
    FROM expanded ex
),
comments(rating, ord, comment) AS (
    VALUES
        -- ── 5★ (enthusiastic) ──
        (5, 1,  'Loved my stay here, rooms were spotless and staff super friendly'),
        (5, 2,  'Best PG I have lived in, food tastes just like home'),
        (5, 3,  'Owner is genuinely caring and sorts out any issue within hours'),
        (5, 4,  'Spacious rooms, great ventilation, and the location cannot be beaten'),
        (5, 5,  'Housekeeping is daily and thorough, the place always smells fresh'),
        (5, 6,  'Food variety is excellent, I never got bored of the menu'),
        (5, 7,  'Felt safe and comfortable from day one, highly recommend it'),
        (5, 8,  'Wifi is fast and reliable, perfect for my work-from-home days'),
        (5, 9,  'Amazing value for the price, everything just works smoothly'),
        (5, 10, 'The warden treats us like family, such a warm place'),
        (5, 11, 'Clean washrooms, hot water round the clock, zero complaints'),
        (5, 12, 'Quiet and peaceful, ideal for students who need to focus'),
        (5, 13, 'Power backup never failed even during the long outages last month'),
        (5, 14, 'Rooms come with solid furniture and plenty of storage space'),
        (5, 15, 'Tasty home-style meals three times a day, loved the variety'),
        (5, 16, 'Five stars, the maintenance team responds almost instantly'),
        (5, 17, 'Great community vibe, I made some lifelong friends here'),
        (5, 18, 'Walking distance to the metro, my commute became effortless'),
        (5, 19, 'Spotless kitchen and dining area, hygiene is taken seriously'),
        (5, 20, 'Comfortable beds and a really calm atmosphere for sleeping'),
        (5, 21, 'The staff went out of their way to help me settle in'),
        (5, 22, 'Excellent security with CCTV everywhere, my parents are relieved'),
        (5, 23, 'Honestly exceeded my expectations, would happily stay again'),
        (5, 24, 'Beautiful property, well-lit corridors and a lovely common area'),
        (5, 25, 'Food is fresh and never repetitive, the cook is fantastic'),
        (5, 26, 'Everything from laundry to cleaning is handled without any fuss'),
        (5, 27, 'Prime location with shops and eateries right around the corner'),
        (5, 28, 'Top-notch facilities and the rent is very reasonable for it'),
        (5, 29, 'The owner upgraded our wifi the same week we asked, brilliant'),
        (5, 30, 'Bright airy rooms and a terrace that is perfect for evenings'),
        (5, 31, 'Hot water, clean rooms, good food, what more could I want'),
        (5, 32, 'Really well-managed place, the rules are fair and clearly communicated'),
        (5, 33, 'My room gets lovely morning sunlight, I sleep so well here'),
        (5, 34, 'Friendly roommates and a manager who actually listens to feedback'),
        (5, 35, 'The mess food reminds me of my mom''s cooking, simply great'),
        (5, 36, 'Super convenient location, everything I need is within five minutes'),
        (5, 37, 'Impeccably clean and the staff are polite and quick to help'),
        (5, 38, 'Best decision moving here, comfortable and totally stress-free'),
        (5, 39, 'Great safety for women, entry is monitored and well controlled'),
        (5, 40, 'The double-sharing room is roomy and surprisingly private'),
        (5, 41, 'Reliable power, fast internet, and spotless common bathrooms'),
        (5, 42, 'Lovely place with a homely feel, the caretaker is wonderful'),
        (5, 43, 'Food hygiene is excellent and the portions are generous'),
        (5, 44, 'Never had a single issue in eight months, smooth experience'),
        (5, 45, 'The gym and common room are a great bonus for the price'),
        (5, 46, 'Peaceful neighbourhood yet close to all the offices nearby'),
        (5, 47, 'Staff cleaned and repainted before I moved in, very professional'),
        (5, 48, 'Affordable, clean, and safe, it ticks every box for a student'),
        (5, 49, 'The biometric entry makes me feel really secure at night'),
        (5, 50, 'Delicious South Indian breakfast every morning, I am spoilt'),
        (5, 51, 'Quick maintenance, friendly people, and a genuinely cosy room'),
        (5, 52, 'Outstanding service, the manager checks in to see we are okay'),
        (5, 53, 'Big windows, cross ventilation, and no mosquito problem at all'),
        (5, 54, 'The location near the tech park saves me an hour daily'),
        (5, 55, 'Everything is well maintained and the people here are lovely'),
        (5, 56, 'Clean linen every week and the rooms are always tidy'),
        (5, 57, 'Great food, great wifi, great people, I have no complaints'),
        (5, 58, 'Felt at home instantly, the staff are warm and approachable'),
        (5, 59, 'Excellent water pressure and hot water even at peak hours'),
        (5, 60, 'Comfortable, secure, and spotless, exactly what I wanted'),
        (5, 61, 'The owner is transparent about everything, no hidden charges'),
        (5, 62, 'Spotless rooms and a kitchen that is always sparkling clean'),
        (5, 63, 'Honestly a wonderful place to live, recommending it to friends'),
        -- ── 4★ (solid, minor caveat) ──
        (4, 1,  'Really good place overall, just wish the wifi was a bit faster'),
        (4, 2,  'Comfortable rooms and decent food, happy with my stay so far'),
        (4, 3,  'Clean and well-kept, though the kitchen gets crowded at peak hours'),
        (4, 4,  'Good value for money, the location makes up for the small rooms'),
        (4, 5,  'Friendly staff and tasty food, parking could be better organised'),
        (4, 6,  'Nice and safe, food is good though the menu repeats some weeks'),
        (4, 7,  'Solid PG, rooms are clean and the owner is approachable'),
        (4, 8,  'Pretty happy here, hot water timing could be a little longer'),
        (4, 9,  'Good experience, the common area is nice but fills up in evenings'),
        (4, 10, 'Decent rooms and reliable power, food is mostly enjoyable'),
        (4, 11, 'Comfortable stay, the laundry just takes a day longer than promised'),
        (4, 12, 'Well maintained and secure, wifi drops occasionally but rarely'),
        (4, 13, 'Good food and clean rooms, though the walls are a bit thin'),
        (4, 14, 'Reasonable rent for the area, staff are helpful most of the time'),
        (4, 15, 'Nice place, housekeeping is regular and the rooms stay tidy'),
        (4, 16, 'Happy overall, would prefer a few more veg options at dinner'),
        (4, 17, 'Clean and quiet, though morning water pressure can be weak'),
        (4, 18, 'Good location and friendly people, rooms are slightly compact'),
        (4, 19, 'Reliable and safe, the food is fine but nothing extraordinary'),
        (4, 20, 'Decent facilities, the manager resolves issues within a day or two'),
        (4, 21, 'Comfortable beds and clean sheets, wifi could use an upgrade'),
        (4, 22, 'Good PG for working professionals, the office commute is easy'),
        (4, 23, 'Mostly great, just the power backup flickers during long cuts'),
        (4, 24, 'Clean rooms, polite staff, food portions are a little small'),
        (4, 25, 'Nice and homely, occasional road noise but quite manageable'),
        (4, 26, 'Good hygiene and decent meals, parking space is a bit tight'),
        (4, 27, 'Satisfied with the stay, hot water is consistent in the mornings'),
        (4, 28, 'Well-run place, the rules are strict but fair enough'),
        (4, 29, 'Good food variety, though breakfast options could be expanded'),
        (4, 30, 'Clean and secure, the only gripe is a slow lift at peak hours'),
        (4, 31, 'Solid choice near the metro, rooms are tidy and well lit'),
        (4, 32, 'Decent and affordable, staff could respond a touch faster'),
        (4, 33, 'Comfortable and safe, food is homely though a bit oily sometimes'),
        (4, 34, 'Good value, the common bathrooms are kept reasonably clean'),
        (4, 35, 'Nice ambience and friendly roommates, wifi is okay most days'),
        (4, 36, 'Happy with cleanliness and food, the AC could cool a bit better'),
        (4, 37, 'Reliable place, maintenance is prompt and the staff are courteous'),
        (4, 38, 'Good rooms and location, just wish housekeeping came twice daily'),
        (4, 39, 'Decent stay overall, food is tasty but the timings are rigid'),
        (4, 40, 'Clean, safe, and well priced, minor issues get sorted quickly'),
        (4, 41, 'Good for students, quiet enough to study and close to college'),
        (4, 42, 'Comfortable rooms, the water heater occasionally needs a restart'),
        (4, 43, 'Nice property, food is satisfying and the caretaker is helpful'),
        (4, 44, 'Pretty good, the internet handles video calls without much trouble'),
        (4, 45, 'Well kept and secure, the menu could use more variety though'),
        (4, 46, 'Decent facilities and a friendly owner, parking is the downside'),
        (4, 47, 'Good experience so far, rooms are airy and reasonably spacious'),
        (4, 48, 'Clean and comfortable, hot water runs out if you are late'),
        (4, 49, 'Solid PG, the food is homely and the location is convenient'),
        (4, 50, 'Happy here, just the common-area TV gets a bit noisy at night'),
        (4, 51, 'Good cleanliness and safety, wifi speed dips in the evenings'),
        (4, 52, 'Reliable meals and tidy rooms, staff are polite and helpful'),
        (4, 53, 'Nice place to stay, the laundry service is dependable enough'),
        (4, 54, 'Comfortable and central, rooms are small but very well maintained'),
        (4, 55, 'Good value near the tech park, the commute is short and easy'),
        (4, 56, 'Decent rooms, clean washrooms, food is better than I expected'),
        (4, 57, 'Safe and well managed, occasional water issues but quickly fixed'),
        (4, 58, 'Good PG, friendly people, just the rent rose a little this year'),
        (4, 59, 'Clean and quiet, food is wholesome though dessert is rare'),
        (4, 60, 'Satisfied overall, the owner is fair and the rooms are neat'),
        (4, 61, 'Nice stay, power backup works and the wifi is mostly stable'),
        (4, 62, 'Good hygiene, comfortable beds, the kitchen could be larger'),
        (4, 63, 'Reliable and tidy, staff handle complaints without much delay'),
        (4, 64, 'Decent food and clean rooms, the location is the real winner'),
        (4, 65, 'Comfortable place, just wish the parking had a little more space'),
        (4, 66, 'Good overall, security is tight and the corridors are well lit'),
        (4, 67, 'Happy with the food and cleanliness, wifi is acceptable for work'),
        (4, 68, 'Solid and affordable, a few small fixes and it would be perfect'),
        -- ── 3★ (mixed / mildly critical) ──
        (3, 1,  'It is okay, rooms are clean but the food gets repetitive fast'),
        (3, 2,  'Average stay, wifi is unreliable and slows down every evening'),
        (3, 3,  'Decent location but the rooms are smaller than the photos suggest'),
        (3, 4,  'Food is hit or miss, some days great and some days bland'),
        (3, 5,  'Fine for the price, though hot water is a daily struggle'),
        (3, 6,  'The place is clean but maintenance takes far too long to respond'),
        (3, 7,  'Okay PG, but the walls are thin and nights can be noisy'),
        (3, 8,  'Mediocre food and slow wifi, but the location is convenient'),
        (3, 9,  'Rooms are alright, housekeeping is irregular and often skips days'),
        (3, 10, 'Average experience, the power backup struggles during long outages'),
        (3, 11, 'Not bad, but the common bathrooms could be cleaner in the mornings'),
        (3, 12, 'It serves the purpose, though the menu really needs more variety'),
        (3, 13, 'Decent enough, but parking is a constant headache here'),
        (3, 14, 'Okay for short stays, the rooms feel a bit cramped over time'),
        (3, 15, 'Food quality dipped recently, otherwise the place is manageable'),
        (3, 16, 'Average, staff are polite but slow to fix recurring issues'),
        (3, 17, 'The location is great but the rooms need better upkeep'),
        (3, 18, 'Fine overall, though water pressure is weak on the upper floors'),
        (3, 19, 'It is passable, wifi and hot water are the main pain points'),
        (3, 20, 'Reasonable rent but the food and cleanliness are just average'),
        (3, 21, 'Okay place, the crowd gets noisy and rules are loosely enforced'),
        (3, 22, 'Decent but nothing special, expected a bit more for the rent'),
        (3, 23, 'Average stay, comfortable enough but the upkeep is inconsistent'),
        -- ── 2★ (critical, civil) ──
        (2, 1,  'Disappointed, the wifi rarely works and the food quality is poor'),
        (2, 2,  'Rooms were not as clean as promised and repairs were ignored'),
        (2, 3,  'Hot water and power backup were unreliable throughout my stay')
)
INSERT INTO reviews (listing_id, user_id, rating, comment, created_at)
SELECT
    wk.listing_id,
    p.id,
    wk.rating,
    c.comment,
    now() - make_interval(days => wk.gseq::int, hours => (wk.gseq * 7 % 24)::int)
FROM with_keys wk
JOIN phantom  p ON p.un = wk.un
JOIN comments c ON c.rating = wk.rating AND c.ord = wk.rn_rating
ON CONFLICT (listing_id, user_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 3 — recompute listing aggregates from the actual rows (same query the
-- runtime recompute uses), so avg_rating / review_count are honest even if the
-- seeded multiset math was slightly off-target.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE listings l SET
    avg_rating   = (SELECT COALESCE(AVG(rating)::numeric(3,2), 0) FROM reviews WHERE listing_id = l.id),
    review_count = (SELECT COUNT(*) FROM reviews WHERE listing_id = l.id)
WHERE l.id IN (SELECT DISTINCT listing_id FROM reviews);
