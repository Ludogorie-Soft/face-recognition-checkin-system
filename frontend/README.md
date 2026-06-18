# AttendTrack — Frontend

A Progressive Web App (PWA) for worker attendance tracking using facial recognition and GPS verification.

Built with **Next.js 16**, **React 19**, **TypeScript**, **Tailwind CSS**, and **face-api.js**.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Node.js | 18+ |
| npm | 9+ |

---

## Local Setup

### 1. Install dependencies

```bash
cd frontend
npm install
```

### 2. Configure environment

Create a `.env.local` file in the `frontend/` directory:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 3. Download face recognition models

The face-api.js models must be present in `public/models/`. Download them from the [face-api.js repository](https://github.com/justadudewhohacks/face-api.js/tree/master/weights) and place the following files:

```
public/models/
  ssd_mobilenetv1_model-weights_manifest.json
  ssd_mobilenetv1_model-shard1
  face_landmark_68_model-weights_manifest.json
  face_landmark_68_model-shard1
  face_recognition_model-weights_manifest.json
  face_recognition_model-shard1
```

### 4. Start the development server

```bash
npm run dev
```

The app is available at `http://localhost:3000`.

### 5. Production build

```bash
npm run build
npm run start
```

> **Note:** PWA features (service worker, installability) are disabled in development mode. Run the production build to test offline functionality and the "Install App" prompt in the browser.

---

## Application Roles

| Role | Access |
|------|--------|
| **ADMIN** | Full access — manage sites, workers, reports, dashboard |
| **MANAGER** | Verify worker attendance at assigned sites |
| **WORKER** | No web access — attendance is recorded by a manager |

Default admin credentials (created automatically on first backend startup):
- Email: `admin@example.com`
- Password: `Admin@123!`

---

## Features

### Authentication

- JWT-based login with automatic token validation on app load
- On open, the app reads the stored token and redirects directly to the correct dashboard based on role — ADMIN goes to `/dashboard`, MANAGER goes to `/verify`
- Tokens are stored in `localStorage` and validated on every protected route

---

### Admin — Dashboard

- Displays real-time statistics: total sites, total workers, workers present today, workers missing today
- Stats auto-refresh every 60 seconds

---

### Admin — Sites

- Create, edit, and deactivate construction sites
- Each site has: name, address, GPS coordinates (selected via interactive Leaflet map), geofence radius in meters, and optional work shift start/end times
- Assign and remove managers and workers per site
- The map picker renders the geofence circle in real time as the radius is adjusted

---

### Admin — Workers

- Create, edit, and deactivate user accounts with roles: ADMIN, MANAGER, WORKER
- Register or delete a worker's face descriptor via webcam
- Face registration captures a 128-dimensional neural network descriptor — no photo is stored on the server
- Uniqueness check: registering a face that already belongs to another worker is blocked (Euclidean distance threshold: 0.45)

---

### Admin — Reports

**Attendance report**
- Filter by site and date range
- Columns: worker name, site, date, recorded time, check-in/out type, GPS coordinates (clickable link to Google Maps), location validity, face confidence score, manual override flag
- Export to Excel (.xlsx)

**Missing workers report**
- Select a site and a date
- Lists all workers assigned to the site who have not checked in on that date

---

### Manager — Verify (Attendance Camera)

The core feature of the application. Designed as a full-screen mobile experience.

**Flow:**
1. Manager selects an assigned site
2. App syncs site data and worker face descriptors from the backend (works offline after first sync — data is cached in IndexedDB)
3. Camera activates with a face guide oval overlay
4. face-api.js continuously scans the video feed every 500ms

**Detection states — oval border color:**
- White — no face detected in the frame
- Yellow — face detected, but not recognized (worker not registered or confidence too low)
- Green — worker identified

**Bottom panel (never obstructs the camera view):**
- Shows the current scanning state, unknown face warning, or recognized worker name with confidence score
- Smart action buttons that adapt to the session history:
  - First scan of this worker → both "Check In" and "Check Out" buttons shown
  - Already checked in → only "Check Out" button (full width) + "Already checked in" badge
  - Already checked out → only "Check In" button (full width) + "Already checked out" badge
- Manual override — the manager can record attendance for any worker without face recognition (e.g. camera failure, poor lighting)
- Session log resets when switching to a different site

**GPS verification:**
- The worker's GPS coordinates are recorded at the moment of confirmation
- The system checks whether the coordinates fall within the site's geofence radius
- Location validity is stored with the attendance record and visible in reports
- An out-of-radius warning is shown, but does not block the attendance from being recorded

**Offline support:**
- Attendance records are saved locally in IndexedDB (Dexie) when there is no network
- A sync banner appears notifying the manager of pending unsynced records
- Records are sent to the backend automatically when connectivity is restored

---

### Internationalization (i18n)

- Supported languages: **Bulgarian** and **English**
- Language can be switched at any time via the toggle in the navigation bar
- All UI strings, error messages, and backend error codes are fully translated in both languages

---

### PWA / Offline

- Installable as a desktop or mobile app via the browser's "Install" prompt (requires production build)
- Service worker caches static assets for offline use
- Offline attendance queue is synced automatically on reconnection

---

## Tech Stack

| Category | Technology |
|----------|-----------|
| Framework | Next.js 16 (App Router) |
| UI components | Tailwind CSS v4, Shadcn UI (Radix UI primitives) |
| State / data fetching | TanStack Query v5 |
| Forms | React Hook Form |
| Face recognition | face-api.js (SSD MobileNet v1 + FaceNet descriptor) |
| Maps | Leaflet + React Leaflet |
| Offline storage | Dexie (IndexedDB wrapper) |
| HTTP client | Axios |
| Internationalization | next-intl |
| PWA | @ducanh2912/next-pwa (Workbox) |
| Toast notifications | Sonner |
