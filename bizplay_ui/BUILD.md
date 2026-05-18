# Docker Deployment Guide

## Quick Start

### 1. Build and Run with Docker Compose

```bash
# Build and start the container
docker compose up --build -d

# Check logs
docker compose logs -f

# Stop the container
docker compose down
```

The application will be available at: **http://10.255.78.89:9009**

### 2. Manual Docker Build (Alternative)

```bash
# Build the image
docker build -t bizplay-classifier-ui .

# Run the container
docker run -d \
  --name bizplay-classifier-ui \
  -p 9009:9009 \
  --env-file .env.production \
  bizplay-classifier-ui

# Check logs
docker logs -f bizplay-classifier-ui

# Stop and remove
docker stop bizplay-classifier-ui
docker rm bizplay-classifier-ui
```

## Files Created

1. **`Dockerfile`** - Multi-stage Docker build for optimal image size
2. **`docker-compose.yml`** - Docker Compose configuration
3. **`.dockerignore`** - Files to exclude from Docker build
4. **`next.config.mjs`** - Updated with `output: 'standalone'`

## Configuration

### Environment Variables

The application reads from `.env.production`:

```env
BACKEND_URL=http://10.255.78.89:9008/api/v1
AUTH_SECRET=81aedb8faa3e964887e1d2d82f1ccb02e1e18b1e7f386e09f17d4a9a2cdd3097
AUTH_URL=http://10.255.78.89:9009
```

### Port Configuration

- **Container Internal Port**: 9009
- **Host Port**: 9009
- **Access URL**: http://10.255.78.89:9009

## Docker Commands Reference

```bash
# Build without cache
docker compose build --no-cache

# View running containers
docker compose ps

# View logs
docker compose logs -f app

# Restart the service
docker compose restart

# Stop and remove everything
docker compose down -v

# Access container shell
docker compose exec app sh
```

## Production Deployment Checklist

- [x] Created Dockerfile
- [x] Created docker-compose.yml
- [x] Created .dockerignore
- [x] Updated next.config.mjs with standalone output
- [x] Configured .env.production
- [ ] Update backend CORS to allow http://10.255.78.89:9009
- [ ] Test file uploads in production
- [ ] Verify authentication flow works
- [ ] Check all API endpoints through proxy

## Backend CORS Configuration

**IMPORTANT**: Update your backend controllers to allow the production URL:

```java
@CrossOrigin(origins = {
    "http://localhost:3000",
    "http://localhost:3001",
    "http://10.255.78.89:9009"  // Add this!
})
```

Update in these files:
- `CompanyController.java`
- `CategoryController.java`
- `TransactionController.java`
- `RuleController.java`
- `BotConfigController.java`
- `FileStorageController.java`
- `DataController.java`

Then rebuild the backend:
```bash
cd ../bizplay_classifier_api
docker compose down
docker compose up --build -d
```

## Troubleshooting

### Port Already in Use
```bash
# Find what's using port 9009
lsof -i :9009

# Kill the process
kill -9 <PID>
```

### Cannot Connect to Backend
1. Check backend is running: `docker compose ps` (in backend directory)
2. Verify BACKEND_URL in .env.production
3. Check backend logs: `docker compose logs -f app` (in backend directory)

### Build Fails
```bash
# Clean everything and rebuild
docker compose down -v
docker system prune -f
docker compose up --build
```

### Image Size Too Large
The Dockerfile uses multi-stage builds to minimize image size:
- Base image: node:20-alpine (lightweight)
- Standalone output: Only includes necessary files
- No development dependencies

Expected image size: ~200-300MB

## Network Configuration

The docker-compose creates a bridge network `bizplay-network` for container communication.

To connect with the backend network (if needed):
```yaml
networks:
  bizplay-network:
    external: true
    name: bizplay_classifier_api_default
```

## Health Checks (Optional Enhancement)

Add to docker-compose.yml:
```yaml
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:9009/api/auth/csrf"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```
