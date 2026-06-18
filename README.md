# NestBLR Backend

NestBLR is a two-sided PG/coliving marketplace for Bengaluru. Tenants search for paying-guest
accommodation directly from owners — no brokers, no fake listings — while PG owners list their
properties, manage photos and room availability, and see who's inquiring. This repo is the backend:
a Ktor API over a PostGIS-enabled Postgres database, with Firebase used to authenticate both sides.
The Android client lives in a separate repo (linked below).

## Stack

- Ktor 3.5.0 — HTTP server and routing
- PostgreSQL 16 + PostGIS 3.4 — relational store with spatial queries (`ST_DWithin`)
- Exposed 1.0 — Kotlin SQL framework, JDBC flavor (`org.jetbrains.exposed.v1.jdbc.*`)
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
| Static files | `/uploads/<filename>` |

All non-public routes expect a Firebase ID token in the `Authorization: Bearer <token>` header.

## Running locally

```bash
cd ~/Desktop/nestblr-backend
docker compose up -d     # Postgres + PostGIS; data persists in a Docker volume
./gradlew run            # starts Ktor on :8080
```

`firebase-service-account.json` must be placed at the backend root before running. It is gitignored
and not included here — supply your own from the Firebase console.

Health check once the server is up:

```bash
curl http://localhost:8080/api/v1/listings/health
```

The database lives in a Docker volume, so it survives `docker compose down` (use `-v` to wipe it).

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

## What's not included

- No deployment configs — no Dockerfile for the app, no CI, no infra-as-code. Runs on a laptop.
- No tests.
- Rate-limiting is configured as a plugin but not actually applied to any route.
- Photo storage is on the local filesystem under `uploads/` — no S3/R2/CDN.

## Related repo

Android client: `<github-username>/NestBLR` (replace with the real URL).
