# ── Stage 1: install dependencies ────────────────────────────────────────────
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
COPY scripts/ ./scripts/
RUN npm install

# ── Stage 2: build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Re-run postinstall to populate public/ with WASM + MJS files from node_modules.
# These are gitignored and therefore absent from the COPY . . layer.
RUN node scripts/copy-wasm.js

# Download ML models (gitignored — not committed to the repository).
# face_landmarker.task : MediaPipe, Apache 2.0
# mobilefacenet.onnx   : InsightFace w600k_mbf (from buffalo_sc.zip), MIT
RUN apk add --no-cache unzip && \
    mkdir -p public/models && \
    wget -q -O public/models/face_landmarker.task \
      "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task" && \
    wget -q -O /tmp/buffalo_sc.zip \
      "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_sc.zip" && \
    unzip -p /tmp/buffalo_sc.zip w600k_mbf.onnx > public/models/mobilefacenet.onnx && \
    rm /tmp/buffalo_sc.zip

RUN npm run build

# ── Stage 3: run ──────────────────────────────────────────────────────────────
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production

# standalone output includes only what's needed to run
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
