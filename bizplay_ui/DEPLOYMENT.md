# Production Deployment Guide

## Configuration Changes for Production

### 1. Environment Variables

**File: `.env.production`** (already created)

Update these values:
- `BACKEND_URL` - Your production backend API URL
- `AUTH_URL` - Your production frontend URL (where this Next.js app will be hosted)

### 2. Files Already Configured for Production ✓

These files are already production-ready:
- `src/service/api.js` - Uses `/api/proxy` (works in production)
- `src/app/api/proxy/[...path]/route.js` - Reads `BACKEND_URL` from environment
- All service files - Use relative paths through the proxy

### 3. Backend CORS Configuration

**IMPORTANT:** Update your backend's CORS configuration to allow your production domain.

**File: `../bizplay_classifier_api/src/main/java/com/api/bizplay_classifier_api/controller/CompanyController.java`**

Change line 23 from:
```java
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
```

To include your production URL:
```java
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "https://your-production-domain.com"})
```

Update CORS in **ALL controllers**:
- `CompanyController.java`
- `CategoryController.java`
- `TransactionController.java`
- `RuleController.java`
- `BotConfigController.java`
- `FileStorageController.java`
- `DataController.java`

## Deployment Steps

### Option 1: Build and Run Locally

```bash
# Build for production
npm run build

# Run production server
npm start
```

The app will run on port 3000 by default.

### Option 2: Docker Deployment

Create `Dockerfile`:
```dockerfile
FROM node:20-alpine AS base

FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000

CMD ["node", "server.js"]
```

Update `next.config.mjs` to enable standalone output:
```javascript
const nextConfig = {
  output: 'standalone',
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'images.unsplash.com',
        pathname: '/**',
      },
    ],
  },
};
```

Build and run:
```bash
docker build -t bizplay-classifier-ui .
docker run -p 3000:3000 --env-file .env.production bizplay-classifier-ui
```

### Option 3: Vercel/Netlify Deployment

1. Push code to GitHub
2. Connect to Vercel/Netlify
3. Set environment variables in the platform:
   - `BACKEND_URL`
   - `AUTH_SECRET`
   - `AUTH_URL`
4. Deploy

## Environment Variables Summary

| Variable | Development | Production | Description |
|----------|-------------|------------|-------------|
| `BACKEND_URL` | `http://10.255.78.89:9008/api/v1` | `http://203.255.78.89:9008/api/v1` or your production API URL | Backend API endpoint (server-side only) |
| `AUTH_URL` | `http://localhost:3000` | `https://your-domain.com` | Frontend URL for NextAuth callbacks |
| `AUTH_SECRET` | (same) | (same) | Secret for JWT signing (keep secure!) |

## Security Checklist

- [ ] Update `AUTH_URL` to your production domain
- [ ] Ensure `AUTH_SECRET` is strong and kept secret
- [ ] Update CORS origins in backend controllers
- [ ] Use HTTPS in production (recommended)
- [ ] Consider using environment-specific secrets
- [ ] Review and update CORS configuration on backend
- [ ] Test file uploads with production setup
- [ ] Verify authentication works with production URLs

## Troubleshooting

### CORS Errors
If you see CORS errors in production:
1. Check backend CORS configuration includes your production domain
2. Ensure the proxy is working: check `/api/proxy/` routes
3. Verify `BACKEND_URL` is set correctly in `.env.production`

### File Upload Timeouts
- The proxy timeout is set to 10 minutes (`maxDuration = 600`)
- For very large files, you may need to increase this further
- Check backend processing performance

### Authentication Issues
- Verify `AUTH_URL` matches your actual domain
- Check that cookies are being set correctly
- Ensure HTTPS is used in production for secure cookies
