/*******************************************************************************
   api-spring-music - auth schema
   Owned by the API (applied by Flyway at startup). The music schema in "public"
   is owned by db-postgresql-music and is never changed from here.

   - role:          lookup table of account roles (listener < curator < admin)
   - user_account:  password hash and role of an app_user
   - refresh_token: rotating refresh tokens, stored only as SHA-256 hashes

   Rows reference public.app_user with ON DELETE CASCADE, so deleting an account
   (DELETE /me) also removes its credentials and sessions.
********************************************************************************/

CREATE TABLE auth.role
(
    code        VARCHAR(20) NOT NULL,
    description TEXT        NOT NULL,
    CONSTRAINT role_pkey PRIMARY KEY (code)
);

INSERT INTO auth.role (code, description) VALUES
    ('listener', 'Regular account: playlists, library and follows'),
    ('curator',  'Can also edit the catalog: artists, albums, tracks, credits and genres'),
    ('admin',    'Can also manage accounts: roles and passwords');

CREATE TABLE auth.user_account
(
    user_id             INT         NOT NULL,
    role                VARCHAR(20) NOT NULL DEFAULT 'listener',
    password_hash       TEXT,                   -- NULL: no password login (for example the seed users)
    password_changed_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_account_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_account_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT user_account_role_fkey
        FOREIGN KEY (role) REFERENCES auth.role (code) ON UPDATE CASCADE
);

-- One row per issued refresh token. Every token from one login shares a family;
-- presenting a token that was already used revokes the whole family (reuse detection).
CREATE TABLE auth.refresh_token
(
    token_id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    family_id  UUID        NOT NULL,
    user_id    INT         NOT NULL,
    token_hash BYTEA       NOT NULL,            -- SHA-256 of the token; the token itself is never stored
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,                     -- exchanged for a new token (rotation)
    revoked_at TIMESTAMPTZ,                     -- logout, password change or detected reuse
    CONSTRAINT refresh_token_pkey PRIMARY KEY (token_id),
    CONSTRAINT refresh_token_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT refresh_token_expiry_check CHECK (expires_at > issued_at)
);

CREATE UNIQUE INDEX refresh_token_token_hash_key ON auth.refresh_token (token_hash);
CREATE INDEX refresh_token_user_id_idx   ON auth.refresh_token (user_id);
CREATE INDEX refresh_token_family_id_idx ON auth.refresh_token (family_id);
CREATE INDEX refresh_token_expires_at_idx ON auth.refresh_token (expires_at);
