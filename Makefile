# Convenience commands. Run `make help` to list them.
# Java targets need JDK 25 (set JAVA_HOME); Docker targets only need Docker.
MVNW = ./mvnw -B -ntp

.PHONY: help env up down logs restart ps build test verify run run-testdb

help:       ## List commands
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-11s %s\n", $$1, $$2}'

env:        ## Create .env from .env.example with a random JWT secret
	@test ! -f .env || { echo ".env already exists"; exit 1; }
	@sed "s|^APP_JWT_SECRET=.*|APP_JWT_SECRET=$$(openssl rand -base64 48)|" .env.example > .env
	@echo "Created .env: now set APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD and the database port."

up:         ## Build the image and start the API in Docker (the database must be up)
	docker compose up -d --build --wait

down:       ## Stop the API container
	docker compose down

logs:       ## Follow the API logs
	docker compose logs -f api

restart:    ## Restart the API container
	docker compose restart api

ps:         ## Show the API container and its health
	docker compose ps

build:      ## Package the jar (skips tests)
	$(MVNW) -DskipTests package

test:       ## Run the unit tests
	$(MVNW) test

verify:     ## Run unit and integration tests (needs Docker and ../db-postgresql-music)
	$(MVNW) verify

run:        ## Run the API locally against the database configured in .env
	$(MVNW) spring-boot:run

run-testdb: ## Run the API locally against a throwaway Testcontainers database
	$(MVNW) spring-boot:test-run
