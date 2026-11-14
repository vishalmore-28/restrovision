# ==============================================================================
# RESTROVISION v2.0 - Production Frontend Multi-Stage Dockerfile (BUG-023)
# Base: Node 22 Alpine (builder) -> Nginx Alpine (runtime)
# Features: Self-contained build, non-root permissions, health check probe
# ==============================================================================

# --- Stage 1: Build & Bundle ---
FROM node:22-alpine AS builder

WORKDIR /app

# Cache package manifests layer
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build production assets
COPY . .
RUN npm run build

# --- Stage 2: Hardened Runtime Container ---
FROM nginx:alpine

LABEL maintainer="RESTROVISION Platform Engineering <devops@restrovision.com>"
LABEL version="2.0.0"
LABEL description="RESTROVISION Production React SPA with Nginx"

# Install curl for reliable container health check probes
RUN apk add --no-cache curl

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy production static assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Set proper ownership and file permissions
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

EXPOSE 80

# Production Health Check Probe
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:80/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
