# NestBLR Backend

Ktor backend for the NestBLR PG/coliving marketplace.

## Stack

- **Ktor 3.5.0** (latest stable, May 2026)
- **Exposed 1.0** (Kotlin ORM, stable since Jan 2026)
- **PostgreSQL 16 + PostGIS 3.4** for spatial queries
- **HikariCP** connection pooling
- **Kotlin 2.1 / JVM 17**

## Day 1-2 deliverables (current)

- [x] Project scaffold
- [x] Docker Compose with PostGIS
- [x] Schema + seed data (30 PGs across Bengaluru)
- [x] First endpoint: `GET /api/v1/listings/search`
- [x] PostGIS geo-search with `ST_DWithin` (uses GIST index)

## How to run

### Prereqs
- Docker Desktop installed
- JDK 17+
- Gradle (or use the wrapper once generated)

### Steps

```bash
# 1. Start Postgres + PostGIS + seed data
docker compose up -d

# 2. Verify DB is up (should print "accepting connections")
docker exec nestblr-postgres pg_isready -U nestblr

# 3. Run the Ktor server
./gradlew run
# (first time: gradle wrapper --gradle-version 8.10 to generate wrapper)

# 4. Test the endpoint — Koramangala center, 3km radius
curl "http://localhost:8080/api/v1/listings/search?lat=12.9352&lng=77.6245&radius_km=3"

# Filter examples:
curl "http://localhost:8080/api/v1/listings/search?lat=12.9352&lng=77.6245&radius_km=5&gender=FEMALE"
curl "http://localhost:8080/api/v1/listings/search?lat=12.9352&lng=77.6245&radius_km=5&max_rent=10000"
curl "http://localhost:8080/api/v1/listings/search?lat=12.9352&lng=77.6245&radius_km=10&pg_type=COLIVING"
```

## Project structure

```
nestblr-backend/
├── build.gradle.kts
├── docker-compose.yml
├── db/init/                       # Auto-runs on first DB start
│   ├── 01_schema.sql              # Tables, indexes, PostGIS extension
│   └── 02_seed.sql                # 30 fake PGs across Bengaluru
└── src/main/kotlin/com/nestblr/
    ├── Application.kt             # Entrypoint
    ├── config/
    │   └── DatabaseFactory.kt     # HikariCP + Exposed init
    ├── plugins/
    │   ├── Routing.kt
    │   ├── Serialization.kt
    │   └── StatusPages.kt         # Global error handler
    ├── routes/
    │   └── ListingRoutes.kt       # GET /api/v1/listings/search
    ├── repositories/
    │   └── ListingRepository.kt   # PostGIS spatial query
    └── models/dto/
        └── ListingDto.kt
```

## Reference links

- Ktor 3.5 release notes: https://ktor.io/docs/whats-new-350.html
- Exposed 1.0 announcement: https://blog.jetbrains.com/kotlin/2026/01/exposed-1-0-is-now-available/
- PostGIS `ST_DWithin`: https://postgis.net/docs/ST_DWithin.html
- PostGIS Docker image: https://registry.hub.docker.com/r/postgis/postgis
- Ktor project generator: https://start.ktor.io/

## Next steps (Week 1 continued)

- [ ] `GET /api/v1/listings/{id}` — full listing detail with photos, rooms, amenities
- [ ] Performance test: load 5000 listings, measure search latency
- [ ] Add CORS plugin (Android emulator won't need it, but a web admin will)
- [ ] Auth: Firebase Admin SDK token verification
