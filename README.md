# NestBLR Backend

NestBLR is a two-sided PG/coliving marketplace for Bengaluru. Tenants search for paying-guest
accommodation directly from owners — no brokers, no fake listings — while PG owners list their
properties, manage photos and room availability, and see who's inquiring. This repo is the backend:
a Ktor API over a PostGIS-enabled Postgres database, with Firebase used to authenticate both sides.
The Android client lives in a separate repo (linked below).

## 🚀 Live

- **Backend:** [nestblr-backend.onrender.com](https://nestblr-backend.onrender.com)
- **Health:** [/api/v1/listings/health](https://nestblr-backend.onrender.com/api/v1/listings/health)
- **Storage:** photos served from Supabase Storage (see example below)

Try it:
```bash
curl https://nestblr-backend.onrender.com/api/v1/listings/health
# → {"status": "ok"}
```

An example uploaded photo:
`https://elzvjsrhydpzakveecqy.supabase.co/storage/v1/object/public/listing-photos/{uuid}.jpg`

## Architecture

```
Android app (Coil for images, Retrofit for HTTP)
  │
  │  HTTPS + Firebase Bearer token
  ▼
Render (Singapore) — Ktor 3.5.0 fat JAR in Docker
  │
  ├─ HikariCP (Session pooler, IPv4) ─────────► Supabase Postgres 17 + PostGIS 3.3.7 (Mumbai)
  │
  └─ Ktor Client (REST, service_role JWT) ────► Supabase Storage — bucket "listing-photos"
                                                    │
                                                    ▼
                                              Public CDN URL fetched by Android via Coil
```

**Auth**: Firebase Auth verifies ID tokens on both sides. Backend never sees passwords.

**Data flow for photo upload**: multipart request → Ktor buffers in memory (capped at 5 MB) → REST POST to Supabase Storage with service_role JWT → returned public URL is stored in `listing_photos.url` → Android's Coil loads the URL directly from Supabase's CDN.

## Stack

- Ktor 3.5.0 — HTTP server and routing
- PostgreSQL — 17 + PostGIS 3.3.7 in production (Supabase, Mumbai), 16 + 3.4 locally in Docker
- Exposed 1.0 — Kotlin SQL framework, JDBC flavor (`org.jetbrains.exposed.v1.jdbc.*`)
- Ktor Client (CIO engine) — REST calls to Supabase Storage
- Supabase — managed Postgres + S3-compatible Storage (production only)
- Render — container hosting (free tier, Singapore)
- HikariCP — JDBC connection pooling
- Firebase Admin SDK 9.9.0 — verifies tenant/owner ID tokens
- Docker — runs Postgres + PostGIS locally
- JDK 21

## API surface

| Access | Endpoints |
| --- | --- |
| Public | `GET /api/v1/listings/health` |
| Tenant (authed) | `/listings/search`, `/listings/{id}`, `/me/favorites/*`, `/listings/{id}/reviews`, `/listings/{id}/inquiries` |
| Owner (authed) | `/owner/listings/*` (CRUD), `/owner/listings/{id}/photos/*`, `/owner/listings/{id}/rooms/{roomId}` (availability), `/owner/inquiries/summary` |
| Static files | `/uploads/<filename>` (local dev only — production serves photos from Supabase URLs stored in DB) |

All non-public routes expect a Firebase ID token in the `Authorization: Bearer <token>` header.

## Deployment

### Infrastructure

| Layer | Service | Region | Cost | Notes |
|---|---|---|---|---|
| Compute | Render Web Service | Singapore | Free tier | 512 MB RAM, spins down after 15 min idle |
| Database | Supabase Postgres | Mumbai (ap-south-1) | Free tier | 500 MB storage, 60 direct connections |
| Storage | Supabase Storage | Mumbai | Free tier | 1 GB, S3-compatible API |
| Auth | Firebase Auth (Google) | Global | Free tier | Email/password + password reset |

**Total: $0/month** at portfolio scale. Every layer has a real free tier that fits the app's needs.

### Environment variables (production)

The backend reads all config from env vars — no secrets in code. Render's dashboard holds these:

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL for Supabase Session pooler (IPv4-compatible) |
| `DATABASE_USER` | `postgres.<project-id>` for pooler routing |
| `DATABASE_PASSWORD` | Supabase DB password |
| `DATABASE_MAX_POOL_SIZE` | Capped at 5 to stay well under Supabase's 60-connection limit |
| `PHOTO_STORAGE` | `supabase` in production, `local` (default) for dev |
| `SUPABASE_URL` | Project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Legacy JWT for Storage API (not the newer `sb_secret_` format — Storage still requires JWT) |
| `SUPABASE_STORAGE_BUCKET` | `listing-photos` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Full JSON blob (not a file path) so the container has no secret files baked in |

### Free-tier reality

- **Cold start**: Render's free tier spins down after 15 min idle. First request after that takes 30-60 seconds while the container wakes. Every subsequent request is fast (<200 ms).
- **Supabase auto-pause**: free-tier projects pause after 7 days of no DB traffic. Manual unpause from the dashboard, or a cron ping keeps it warm. Not automated here.
- **Session pooler over Direct**: Supabase's Direct connection is IPv6-default; Render is IPv4-only. Session pooler (PgBouncer) is the free workaround. Prepared statements still work.
- **Storage uses legacy JWT**: Supabase Storage API still requires the JWT-format `service_role` key, not the newer `sb_secret_*` API keys. This will likely change.

## Running locally

Two modes — Docker Postgres + local disk (default), or point at Supabase (matches production).

### Mode 1 — Local Docker (default, no env vars needed)

```bash
cd ~/Desktop/nestblr-backend
docker compose up -d     # Postgres + PostGIS; data persists in a Docker volume
./gradlew run            # starts Ktor on :8080
```

`firebase-service-account.json` must be at the backend root before running. It is gitignored — supply your own from the Firebase console.

Health check once up:
```bash
curl http://localhost:8080/api/v1/listings/health
```

### Mode 2 — Point at Supabase (for testing production behavior)

Set env vars from a local `.env.supabase.local` (gitignored), then run:

```bash
source .env.supabase.local
export FIREBASE_SERVICE_ACCOUNT_JSON="$(cat firebase-service-account.json)"
./gradlew run
```

The env var syntax `$VAR:default` in `application.yaml` means unset vars fall back to local Docker defaults — so switching between the two modes just requires setting or unsetting the vars.

## Schema overview

Defined in `db/init/01_schema.sql`:

- `listings` — core PG/coliving listing (location, rent, gender, food, PG type)
- `room_options` — per-listing room types with availability counts
- `listing_amenities` — amenity flags per listing
- `listing_photos` — uploaded photos, ordered, with a cover flag
- `users` — tenant and owner records keyed by Firebase UID
- `reviews` — tenant reviews (star rating + comment) with aggregate recompute
- `favorites` — tenant-to-listing saves
- `inquiries` — logged "call owner" events from tenants

## What's honest to note

- **No tests** — the backend has zero test coverage. Deferred with intent, documented as a real gap.
- **Rate-limiting is a plugin but not applied** — the config exists but no route uses it.
- **Legacy photos on the local `uploads/` path** — the `/uploads/{filename}` static route still exists for local dev, but production data lives entirely in Supabase Storage.
- **No CI** — deploys trigger on `git push` to `main` via Render's auto-deploy, but there's no test/lint/build check first. If a bad commit passes the Docker build, it deploys.
- **Cold start on free tier** — see the "Free-tier reality" section above.
- **`amenities` table auto-seeded** — the 12 amenity reference rows are inserted by `01_schema.sql`. If you re-run migrations against a populated DB, you'll get duplicate-key errors.

## Related repo

Android client: [adithyagattadi/nestblr](https://github.com/adithyagattadi/nestblr).
