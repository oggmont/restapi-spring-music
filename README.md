# api-spring-music

A REST API for the **music-db** playlist platform: a music catalog (artists, albums, tracks, songs, people, genres, labels) plus users, playlists with collaborators, libraries and follows. It implements the API of [design-music-system](https://github.com/oggmont/design-music-system), including what its web UI needs (favorites, limits, random picks, song search), on top of the PostgreSQL database from [db-postgresql-music](https://github.com/oggmont/db-postgresql-music), and adds JWT authentication with roles.

**The design comes first.** Everything here is based on [design-music-system](https://github.com/oggmont/design-music-system), and changes start there:

| Design document | What it decides here |
|---|---|
| [API-Design.md](https://github.com/oggmont/design-music-system/blob/main/API-Design.md) | The contract: every endpoint, its body, its errors and the SQL behind it. Endpoints marked **(UI)** are the web UI's additions |
| [REQUIREMENTS.md](https://github.com/oggmont/design-music-system/blob/main/REQUIREMENTS.md) | Every requirement with an ID, and where this implementation differs from the design |
| [DB-Design.md](https://github.com/oggmont/design-music-system/blob/main/DB-Design.md) | The database changes behind the UI (DB-00 to DB-10), applied in db-postgresql-music |
| [UI-Design.md](https://github.com/oggmont/design-music-system/blob/main/UI-Design.md) | The web UI (React) that calls this API: its rules (BR-xx) and the API changes it needs (G-xx) |

- **Spring Boot 4.1 / Spring Framework 7 / Java 25**, Spring MVC on virtual threads, Spring Security 7, Jackson 3
- **SQL-first data access** with `JdbcClient`: the schema's views, functions and triggers do the heavy lifting, and its errors (by SQLSTATE and constraint name) become HTTP errors
- **JWT authentication**: short-lived access tokens, rotating refresh tokens, roles (`listener`, `curator`, `admin`) and a super user created at startup
- **Optimistic locking** for playlists through `ETag` / `If-Match`
- **Docker**: layered image with a Java 25 AOT cache, non-root, health-checked, on the database's Docker network
- **OpenAPI** docs with Swagger UI; **tests** against a real copy of the database (Testcontainers)

## Contents

- [Requirements](#requirements)
- [Quick start (Docker)](#quick-start-docker)
- [Try it](#try-it)
- [Authentication and roles](#authentication-and-roles)
- [API conventions](#api-conventions)
- [Endpoints](#endpoints)
- [Configuration](#configuration)
- [Local development](#local-development)
- [Tests](#tests)
- [How the database is used](#how-the-database-is-used)
- [Docker image](#docker-image)
- [Environments](#environments)
- [Deployment (Google Cloud)](#deployment-google-cloud)
- [Project layout](#project-layout)
- [Before going to production](#before-going-to-production)

## Requirements

- [Docker](https://docs.docker.com/get-docker/) with Compose v2
- The **database**: a running [db-postgresql-music](https://github.com/oggmont/db-postgresql-music), cloned next to this project (`../db-postgresql-music`); `make up` there starts it
- For local development only: **JDK 25** (e.g. [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25)) with `JAVA_HOME` pointing at it. Maven is not needed: use the wrapper, `./mvnw`.

## Quick start (Docker)

```bash
# 1. Start the database (in ../db-postgresql-music)
make up

# 2. Configure the API (in this project)
make env        # creates .env from .env.example with a random APP_JWT_SECRET
                # then edit .env: super user (APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD), DB_PORT

# 3. Build and start it
make up         # or: docker compose up -d --build --wait
```

| | URL |
|---|---|
| API | http://localhost:8081/api/v1 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI document | http://localhost:8081/v3/api-docs |
| Health | http://localhost:8081/actuator/health |

On the first start the API checks its `auth` schema with Flyway and makes sure the super user exists and has a password; the log says which (`Created super user ...` or `Super user ... is ready`). `make logs` follows the log, `make down` stops the API (the database keeps running).

## Try it

```bash
API=http://localhost:8081/api/v1

# Public catalog reads, no token needed
curl $API/artists/the-beatles
curl "$API/search?q=come%20together&types=song,track&limit=5"

# Log in as the super user (values from .env)
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username": "admin", "password": "change-me"}' | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
curl $API/me -H "Authorization: Bearer $TOKEN"

# Create a playlist; the response carries its version as ETag: "2"
curl -i -X POST $API/playlists -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name": "Sunday Morning", "visibility": "private", "track_ids": [3674, 3669, 4037]}'

# Add a track at position 2 (use the playlist_id from the response); If-Match must be the current version
curl -i -X POST $API/playlists/25/tracks -H "Authorization: Bearer $TOKEN" -H 'If-Match: "2"' \
  -H 'Content-Type: application/json' -d '{"track_ids": [3735], "position": 2}'
```

Swagger UI has every endpoint with its schema; use **Authorize** to paste an access token.

## Authentication and roles

The API is its own token issuer (see [Authentication](https://github.com/oggmont/design-music-system/blob/main/API-Design.md#authentication-auth) in API-Design.md):

| Endpoint | What it does |
|---|---|
| `POST /users` | Sign up: `username`, `display_name`, `email`, `password` (8+ characters), optional `country_code` |
| `POST /auth/login` | `username` (or email) and `password` → `access_token`, `refresh_token`, `expires_in` |
| `POST /auth/refresh` | Exchange a refresh token for new tokens. The old refresh token stops working |
| `POST /auth/logout` | End the session a refresh token belongs to |
| `PUT /me/password` | Change your password (needs the current one); signs out your other sessions |

- **Access tokens** are JWTs signed with HMAC-SHA256 (`APP_JWT_SECRET`), valid 15 minutes by default. Send them as `Authorization: Bearer <token>`. Their `sub` claim is the account's `app_user.auth_subject`; the API looks the user up on every request, so a deleted account's tokens stop working.
- **Refresh tokens** are opaque, valid 14 days, single-use and stored only as SHA-256 hashes. Presenting a used one again (a sign it was stolen) revokes the whole session.
- **Roles** live in the API-owned `auth` schema and are read from the database on every request, so changes apply at once:

| Role | Can also |
|---|---|
| `listener` | everything under `/me`, playlists, library, follows |
| `curator` | edit the catalog: `/admin/artists`, `/admin/albums`, `/admin/songs` |
| `admin` | manage accounts: `/admin/users/{username}/role` and `/admin/users/{username}/password` |

- **Super user**: on startup the API creates `APP_ADMIN_USERNAME` with role `admin` (or makes an existing account with that name an admin). `APP_ADMIN_PASSWORD` is only applied to an account without a password, so a password changed through the API is kept across restarts. A short password logs a warning.
- **Seed users** (`ana.garcia`, `ben.carter`, …) have no password. An admin can give one a password with `PUT /admin/users/{username}/password` to try the design's examples as that user.

## API conventions

These follow [API-Design.md's conventions](https://github.com/oggmont/design-music-system/blob/main/API-Design.md#conventions):

- **Base URL** `/api/v1`. The version is resolved by Spring Framework's API versioning (`/api/1/...` works too; `/api/v2/...` answers `400`).
- **JSON** in and out, `snake_case` field names, timestamps in ISO 8601 UTC (`"2026-11-05T20:43:11.123456Z"`), durations as `duration_ms` plus a display `duration` (`"4:20"`).
- **Pagination**: `?limit=` (default 20, max 100) and `?offset=`, answering `{ "items": [...], "limit": 20, "offset": 0, "total": 193 }`. Keyset cursors where lists change often: `?after_position=` on playlist entries, `?before=<saved_at>&before_id=<id>` on the library (`before_id` breaks ties between items saved at the same instant).
- **Partial updates**: `PATCH` changes only the fields sent; `null` clears a nullable field.
- **Visibility**: a playlist you can't view answers `404`, not `403`, so private playlists don't reveal that they exist.
- **Visibility follows favorites**: nobody chooses it. A playlist is `public` while at least one user (its owner or anyone else) has it as a favorite, and `private` when nobody does; new playlists are private. Sending `visibility` to `POST` or `PATCH /playlists` answers `422 visibility_follows_favorites`.
- **Optimistic locking**: playlist responses carry `ETag: "<version>"`. Track-list writes need `If-Match` with it: `428` without, `412` when someone else changed the playlist (reload and retry). `PATCH` and `DELETE` on a playlist check `If-Match` when it is sent.
- **Errors** always use one envelope; validation errors list the fields:

```json
{ "error": { "code": "validation_failed", "message": "'name' must not be blank.",
             "details": [ { "field": "name", "message": "must not be blank" } ] } }
```

The database enforces most rules, and its errors are translated: unique violations → `409` (`username_taken`, `slug_taken`…), foreign keys, checks and trigger rules → `422` (`unknown_track`, `cannot_follow_self`, `integrity_violation`…), `insufficient_privilege` → `403`.

### Rules of the web UI

From [Favorites, visibility and limits](https://github.com/oggmont/design-music-system/blob/main/API-Design.md#favorites-visibility-and-limits). A **favorite** is a playlist follow, and a **song** in the UI is a **track** here.

| Rule | Error when broken |
|---|---|
| At most **5 favorites** per user, own playlists included. `PUT /me/following/playlists/{id}` with `{"replaces": <id>}` swaps one out in one transaction | `422 favorite_limit_reached` |
| At most **20 tracks** per playlist (and per request) | `422 playlist_full`, saying how many still fit |
| At most **20 playlists** owned per user (collaborations don't count) | `422 playlist_limit_reached` |
| Visibility follows favorites (above) | `422 visibility_follows_favorites` |

Triggers in the database enforce all four, so they hold for every client; the API checks the limits first only to give clearer messages. `GET /me` returns `owned_playlist_count` and `favorite_count`, so the UI can show "9 of 20" before the limit is reached.

## Endpoints

All of [API-Design.md's endpoint index](https://github.com/oggmont/design-music-system/blob/main/API-Design.md#endpoint-index) is implemented, including the **(UI)** endpoints, plus authentication and account administration. "–" means no token is needed, "– / ✓" works without one for public playlists.

| Area | Endpoints | Auth |
|---|---|---|
| **Auth** | `POST /auth/login`, `/auth/refresh`, `/auth/logout` | – |
| **Account** | `POST /users` · `GET`, `PATCH`, `DELETE /me` · `PUT /me/password` · `GET /me/playlists?role=&sort=name\|last_added\|created&contains_track=` · `GET /me/feed` | – (sign-up), ✓ |
| **Users** | `GET /users/{username}` · `/playlists` · `/favorites?sort=name\|favorited_at` · `/followers` · `/following` | – |
| **Playlists** | `POST /playlists` · `GET`, `PATCH`, `DELETE /playlists/{id}` · `GET /playlists/random?limit=&exclude=` · `GET /playlists/shared/{share_token}` · `POST /playlists/{id}/share-token` · `POST /playlists/{id}/copy` | – / ✓, owner |
| **Playlist tracks** | `GET`, `POST /playlists/{id}/tracks` · `POST /playlists/{id}/tracks/move` · `DELETE /playlists/{id}/tracks/{position}` · `DELETE /playlists/{id}/tracks?track_id=` · `GET /playlists/shared/{share_token}/tracks` | – / ✓, owner and editors |
| **Collaborators** | `GET /playlists/{id}/collaborators` · `PUT`, `DELETE /playlists/{id}/collaborators/{username}` | owner, collaborators |
| **Library** | `GET /me/tracks` · `PUT`, `DELETE /me/tracks/{track_id}` · `GET /me/tracks/contains?ids=` · `GET /me/albums` · `PUT`, `DELETE /me/albums/{album_id}` | ✓ |
| **Follows** (favorites) | `PUT`, `DELETE /me/following/{playlists/{id} \| artists/{slug} \| users/{username}}` · `GET /me/following/artists` · `GET /me/following/playlists?sort=name\|followed_at` · `GET /playlists/{id}/followers` | ✓ |
| **Catalog** | `GET /artists` · `/artists/{slug}` · `/artists/{slug}/albums` · `/artists/{slug}/members` · `/artists/{slug}/albums/{album_slug}` · `/albums/{id}` · `/albums/{id}/tracks` · `/tracks?q=` (song search) · `/tracks/random?limit=&exclude=` · `/tracks/{id}` · `/songs/{id}` · `/songs/{id}/recordings` · `/people/{id}` · `/genres` · `/genres/{id}/albums` · `/labels/{slug}` | – |
| **Search, reference** | `GET /search?q=&types=&limit=` · `GET /reference/{name}` | – |
| **Catalog administration** | `POST /admin/artists` · `PATCH /admin/artists/{id}` · `POST /admin/albums` · `POST /admin/albums/{id}/tracks` · `PUT /admin/albums/{id}/genres` · `POST /admin/songs/{id}/credits` | curator |
| **Account administration** | `PUT /admin/users/{username}/role` · `PUT /admin/users/{username}/password` | admin |

Where this implementation adds to or adapts the design:

- **Sign-up** (`POST /users`) takes a `password` and needs no token, because the API issues its own tokens. The response is the profile, as in the design.
- **`GET /playlists/shared/{share_token}/tracks`** lets someone with a share link read the entries (the design only covers the details). Share links only open public playlists for now: under "visibility follows favorites" no playlist is `unlisted`.
- **`GET /me`** also returns the account's `role`.
- **Favorites**: `PUT` and `DELETE /me/following/playlists/{id}` answer `200` with the playlist's new state (`is_favorite`, `visibility`, `follower_count`, `my_favorite_count`, and `replaced` after a swap). `DELETE` of a playlist you can't view and never had as a favorite answers `404`, so it doesn't reveal private playlists.
- **Nine seed playlists are older than the 20-track limit** (all owned by `musicdb`: *Music*, *90's Music*, *The Beatles: Original Albums*, *Classical*, *Brazilian Music*, *Heavy Metal Classic* and the three *Classical 101* playlists): nothing can be added to them or copied from them whole, but their entries can still be moved and removed.
- **`GET /tracks?q=`** ranks matches in the track's name (1.0, 0.9, 0.8) above matches in its artist or album only (0.5 to 0.75), and also answers `422 query_too_short` when `q` is missing.
- **Roles and `/admin/users/...`**: the design notes that catalog administration needs a role the schema doesn't have; it lives in the API's `auth` schema.

## Configuration

Everything is set through environment variables, normally in `.env` (copy [.env.example](.env.example)). `docker compose` reads it, and so does `./mvnw spring-boot:run` (Spring Boot imports `.env` as properties).

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST`, `DB_PORT` | `localhost`, `5432` | The database as seen from your machine (local runs). Docker uses `DB_DOCKER_HOST` (`music-db-postgres`) on port 5432 instead |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `music_db`, `music`, `music` | Database and credentials (db-postgresql-music's defaults) |
| `MUSIC_DB_NETWORK` | `music-db_default` | Docker network of the database's compose project |
| `SERVER_PORT` | `8081` (in `.env`) | Port the API is reached on from your machine |
| `APP_JWT_SECRET` | – | Base64 HMAC key, at least 256 bits (`openssl rand -base64 48`). Required in Docker; locally, empty means a random key per start |
| `APP_JWT_ACCESS_TOKEN_TTL`, `APP_JWT_REFRESH_TOKEN_TTL` | `15m`, `14d` | Token lifetimes |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | – | The super user; nothing is created when unset |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_DISPLAY_NAME` | `<username>@localhost`, the username | Details of a newly created super user |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Browser origins allowed to call the API |
| `APP_API_DOCS_ENABLED` | `true` | Serve Swagger UI and `/v3/api-docs` |
| `JAVA_OPTS` | – | Extra JVM flags for the container |

If port 5432 is taken on your machine and db-postgresql-music publishes PostgreSQL on another port (its `POSTGRES_PORT`), set `DB_PORT` to that port. Docker doesn't need it.

## Local development

```bash
export JAVA_HOME=/path/to/jdk-25   # e.g. $(/usr/libexec/java_home -v 25) on macOS
make run          # ./mvnw spring-boot:run, against the database configured in .env
make run-testdb   # against a throwaway Testcontainers database, no running database needed
make build        # target/api-spring-music-<version>.jar
```

## Tests

```bash
make test     # unit tests (./mvnw test)
make verify   # unit + integration tests (./mvnw verify)
```

The integration tests (`*IT`) start PostgreSQL 17 with Testcontainers and load the real schema and seed from `../db-postgresql-music/db/init`, then exercise the whole application through MockMvc, security included. They need Docker; point `MUSIC_DB_INIT_DIR` elsewhere if the database project lives somewhere else.

## How the database is used

- The **music schema** (`public`) belongs to db-postgresql-music. The API never changes its structure. It reads through its views (`track_detail`, `album_summary`, `artist_summary`, `playlist_summary`) and functions (`playlist_can_view`, `playlist_can_edit`, `search_catalog`, `search_tracks`, `random_tracks`, `random_playlists`), and leaves derived data (positions, versions, follower counts, slugs, composer text, playlist visibility) and the UI's limits to its triggers.
- **Song search** reads the materialized view `track_search`. The database's seed fills it; the API refreshes it (`CONCURRENTLY`, after the commit) when `/admin` writes change what it matches: renaming an artist or adding tracks.
- The **`auth` schema** holds the API's data: `auth.role`, `auth.user_account` (password hash and role) and `auth.refresh_token`. Its rows reference `app_user` with `ON DELETE CASCADE`, so `DELETE /me` removes an account's credentials and sessions too.
- db-postgresql-music's init scripts create the `auth` schema and seed the super user `oscargm` (user 8, role `admin`, **no password**). The API's Flyway migration [V1__auth_schema.sql](src/main/resources/db/migration/V1__auth_schema.sql) has the same DDL: on a database loaded by those scripts Flyway just records version 1 as its baseline, and on an older database it creates the schema. Keep the two in sync: a change to `auth` is a new migration here plus the same change at the end of `db/init/01_schema.sql` there.
- On start the API sets the super user's password from `APP_ADMIN_PASSWORD` when the account has none, so passwords are never stored in the database project.
- After `make reset` in db-postgresql-music, restart the API (`make restart`) so it sets the super user's password again. Accounts signed up through the API, and sessions, are lost with the reset.

## Docker image

[Dockerfile](Dockerfile) follows the Spring Boot reference:

- **Multi-stage**: the JDK stage builds the jar with the Maven wrapper; the runtime stage is a JRE only (`bellsoft/liberica-openjre-debian:25-cds`).
- **Layered**: the jar is extracted with `-Djarmode=tools` into dependency, loader and application layers, so a code change rebuilds and ships only the small application layer.
- **AOT cache** (Java 25): a training run during the build records loaded and linked classes, which shortens startup.
- **Runs as a non-root user**, sizes its heap from the container's memory limit (`-XX:MaxRAMPercentage=75`, 768 MB limit in compose) and reports health from `/actuator/health/readiness`.

[docker-compose.yml](docker-compose.yml) joins the database's `music-db_default` network, so the API reaches PostgreSQL as `music-db-postgres:5432` without going through the host.

## Environments

Runlist has two environments. **Use dev for everything except real use**, so prod stays inside the GCP free tier and doesn't get billed.

| Environment | Where it runs | What it's for |
|---|---|---|
| **dev** | Docker on the owner's Mac (not GCP): the database (db-postgresql-runlist, `make up`), the API (restapi-spring-runlist, `make up`, http://localhost:8081) and the UI (ui-react-runlist, `make up`, http://localhost:5173) | Development, testing, trying changes, demos, end-to-end and load tests. Free; use it as much as you like. |
| **prod** | GCP project `runlist-514701` (free tier): the UI at https://runlist-ui-894040023319.us-central1.run.app, the API on Cloud Run (internal only), PostgreSQL on the e2-micro VM `runlist-db` | Real use, a few sessions a week. |

Rules for prod:

- **Keep usage light.** Don't point tests, scripts, load tests or e2e suites at prod, and don't use it to debug. Reproduce problems in dev. Heavy traffic can push it out of the free tier: Cloud Run CPU time, outbound traffic and logs are billed beyond the free amounts.
- **Every push to `main` of the UI or the API deploys to prod**, through the GitHub Actions workflows (pushes that only change Markdown files don't). Check changes in dev first (`make test`, `make verify`, and the API at http://localhost:8081), and group them instead of pushing many small deploys.
- **Prod data is real.** It holds what people did in the app. Don't reset or edit it to test something; back it up before any change (db-postgresql-runlist's workflow, `backup` action).
- A budget alert (about $1 a month) emails the billing account if prod starts costing money. It only alerts; it doesn't stop anything.

Swagger UI is only available in dev (http://localhost:8081/swagger-ui.html): prod's API isn't reachable from the internet.

## Deployment (Google Cloud)

The API runs on Cloud Run as `runlist-api`, inside the GCP free tier. Its ingress is **internal**: only the web UI reaches it, through the UI's own `/api` proxy and the VPC. The internet can't reach its URL, Swagger UI and `/v3/api-docs` are off (`APP_API_DOCS_ENABLED=false`), and login and sign-up are rate-limited by the UI's nginx. Use Swagger UI on a local run.

- **Service:** 1 vCPU and 1 GiB, scales from 0 to 2 instances, billed per request, with startup CPU boost. Expect a few seconds on the first request after a quiet period.
- **Database:** PostgreSQL 17 in Docker on the free e2-micro VM `runlist-db`. The API reaches it at `10.128.0.10:5432` through Direct VPC egress. The VM has no public IP.
- **Settings:** set by the workflow:
  - environment: `DB_HOST`, `DB_POOL_SIZE=5`, `APP_CORS_ALLOWED_ORIGINS` (the UI's URL), `APP_ADMIN_USERNAME=oscargm`
  - Secret Manager: `DB_PASSWORD` (`runlist-db-password`), `APP_JWT_SECRET` (`runlist-jwt-secret`), `APP_ADMIN_PASSWORD` (`runlist-admin-password`)
- **Super user:** `oscargm`. Get its password with `gcloud secrets versions access latest --secret runlist-admin-password --project runlist-514701`.

[.github/workflows/deploy.yml](.github/workflows/deploy.yml) does the following:

- **On every push and pull request:** runs the unit tests and the integration tests. The integration tests use db-postgresql-runlist's `db/init`, checked out by the workflow. Only the unit tests gate the deploy for now: many integration tests still assume the old seed and fail against the Spotify seed.
- **On pushes to `main`, or when you run it by hand:** builds the [Dockerfile](Dockerfile), pushes the image to Artifact Registry (`us-central1-docker.pkg.dev/runlist-514701/runlist/api`) and deploys it.

The workflow authenticates without keys, through Workload Identity Federation.

The infrastructure, costs, operations (logs, SQL shell, backups) and what to avoid so it stays free are in db-postgresql-runlist's [deploy/gcp/README.md](https://github.com/oggmont/db-postgresql-runlist/blob/main/deploy/gcp/README.md).

## Project layout

```
src/main/java/com/oggmont/music/
├── auth/        login, tokens, refresh-token rotation, roles, super user bootstrap
├── account/     sign-up and /me
├── user/        public profiles, followers
├── playlist/    playlists, entries, collaborators, share links, feed
├── library/     liked tracks and saved albums
├── follow/      follows of playlists (favorites), artists and users
├── catalog/     artists, albums, tracks (song search, random), songs, people, genres, labels
├── search/      search_catalog()
├── reference/   lookup tables
├── admin/       catalog and account administration
├── common/      error envelope, database error mapping, paging, ETag/If-Match, PATCH bodies
└── config/      security, web (API versioning), OpenAPI
src/main/resources/
├── application.yaml
└── db/migration/V1__auth_schema.sql
src/test/java/   unit tests (*Test) and integration tests (*IT)
```

Each module has a controller (HTTP), a service where there are rules or transactions, and a repository with the SQL.

## Before going to production

- Use a long, unique `APP_ADMIN_PASSWORD` (or change it right away with `PUT /me/password`) and keep `APP_JWT_SECRET` secret. Rotating the secret signs everyone out.
- Serve the API over HTTPS behind a reverse proxy, and rate-limit `/auth/*` there.
- Connect with a least-privilege database role (DML on `public`, owner of `auth`) instead of the database owner.
- Set `APP_CORS_ALLOWED_ORIGINS` to your front end's origin.
