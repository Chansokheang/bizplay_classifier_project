# CI/CD Setup

This repository now includes:

- `.github/workflows/ci.yml`
- `.github/workflows/deploy.yml`
- `compose.prod.yaml`

## What happens on push to `main`

1. GitHub Actions builds the Spring Boot app.
2. GitHub Actions builds and pushes the Docker image to `ghcr.io`.
3. GitHub Actions copies `compose.prod.yaml` to the server.
4. GitHub Actions connects to the server over SSH.
5. The server pulls the latest image and restarts the containers.

## Required GitHub secrets

Add these repository secrets before enabling deployment:

- `SERVER_HOST`
- `SERVER_PORT`
- `SERVER_USER`
- `SERVER_PASSWORD`
- `GHCR_USERNAME`
- `GHCR_PAT`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_ENCRYPTION_BASE64_KEY`
- `APP_JWT_BASE64_SECRET`
- `APP_AUTH_MODE`
- `APP_AUTH_STATIC_TOKEN`
- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `APP_STORAGE_MINIO_ENDPOINT`
- `APP_STORAGE_MINIO_ACCESS_KEY`
- `APP_STORAGE_MINIO_SECRET_KEY`
- `APP_STORAGE_MINIO_BUCKET`
- `APP_STORAGE_MINIO_REQUEST_FOLDER`
- `APP_STORAGE_MINIO_FAIL_FAST`
- `APP_STORAGE_PUBLIC_BASE_PATH`
- `APP_AI_FALLBACK_ENABLED`
- `APP_AI_FALLBACK_URL`
- `APP_AI_FALLBACK_API_KEY`
- `APP_AI_FALLBACK_CONNECT_TIMEOUT_MS`
- `APP_AI_FALLBACK_READ_TIMEOUT_MS`
- `APP_AI_FALLBACK_MAX_CONTEXT_CANDIDATES`
- `APP_AI_FALLBACK_OPENAI_URL`
- `APP_AI_FALLBACK_GEMINI_URL_TEMPLATE`
- `APP_AI_FALLBACK_CLAUDE_URL`
- `SPRING_MVC_ASYNC_REQUEST_TIMEOUT`
- `SERVER_MAX_HTTP_REQUEST_HEADER_SIZE`

## Notes

- `GHCR_PAT` should be a personal access token with package read permission for the server deployment step.
- Password-based SSH works, but SSH keys are the better long-term choice.
- `application.properties` and `compose.yaml` still contain hardcoded local/default secrets. Those should be rotated and cleaned up before production use.
