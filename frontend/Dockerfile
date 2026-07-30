# Build context is ./frontend
#   docker build -f frontend/Dockerfile -t badmintonhub/frontend:dev ./frontend
#
# NO build args on purpose: the app derives its API/WS origin from window.location at
# runtime (same-origin, proxied by nginx), so ONE image works in every environment.
# That matters because the ALB DNS name changes on every `terraform apply`.

# ---------------------------------------------------------------- build
# Debian (glibc) rather than alpine: Tailwind v4 pulls the native @tailwindcss/oxide
# binary, and musl + cross-architecture builds routinely resolve the wrong optional
# platform package — a failure that only surfaces at `vite build`.
FROM node:22-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# `npm run build` = tsc -b && vite build  ->  type errors fail the image build (good).
RUN npm run build

# -------------------------------------------------------------- runtime
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
