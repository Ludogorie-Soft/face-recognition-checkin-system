import Dexie, { type Table } from 'dexie'

export interface WorkerRecord {
  id: string
  siteId: string
  name: string
  descriptor: number[] | null
}

export interface CheckpointInfo {
  id: string
  name: string | null
  lat: number
  lng: number
  radiusMeters: number
  lat2: number | null
  lng2: number | null
  checkpointType: 'POINT' | 'LINE'
}

export interface SiteInfo {
  id: string
  name: string
  lat: number
  lng: number
  radiusMeters: number
  workStartTime: string | null
  workEndTime: string | null
  checkpoints: CheckpointInfo[]
}

export interface PendingAttendance {
  id?: number
  clientEventId: string  // stable UUID per event — enables idempotent server-side dedup
  createdOffline: boolean // navigator.onLine was false at scan time
  clientDeviceId: string  // stable per-device id (audit metadata)
  appVersion: string      // frontend build version at record time
  workerId: string
  siteId: string
  type: 'CHECK_IN' | 'CHECK_OUT'
  lat: number
  lng: number
  /** GPS accuracy radius in metres at scan time. Undefined for records queued before v7. */
  accuracyMeters?: number
  locationValid: boolean
  faceConfidence: number | null
  manualOverride: boolean
  recordedAt: string  // "YYYY-MM-DDTHH:mm:ss" local time — no timezone suffix
  status: 'pending' | 'syncing'
}

// Last known check-in/out state per WORKER, persisted so the terminal's decision survives page
// reloads while offline. Not per site: a worker has one session at a time, so a terminal at another
// site must offer to close it, not to open a second one. serverConfirmed distinguishes a value
// fetched from the server (authoritative for today) from a local-only guess.
export interface LocalSessionStatus {
  workerId: string
  /** Where the status was last observed — kept for display/debugging, not part of the key. */
  siteId: string
  type: 'CHECK_IN' | 'CHECK_OUT'
  date: string             // "YYYY-MM-DD" local — the day this status applies to
  serverConfirmed: boolean
}

class GarantDB extends Dexie {
  workers!: Table<WorkerRecord>
  siteInfo!: Table<SiteInfo>
  pending!: Table<PendingAttendance>
  sessionStatus!: Table<LocalSessionStatus>

  constructor() {
    super('garant-db')

    this.version(1).stores({
      workers: 'id, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
    })

    // v2: workers get a siteId field for per-site filtering
    this.version(2).stores({
      workers: 'id, siteId, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
    })

    // v3: drop workers — IndexedDB cannot change primary key in-place.
    this.version(3).stores({
      workers: null,
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
    })

    // v4: recreate workers with compound primary key [id+siteId] so a worker
    // assigned to multiple sites gets one entry per site (no BulkError).
    this.version(4).stores({
      workers: '[id+siteId], siteId, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
    })

    // v5: SiteInfo gains checkpoints[] — no schema change needed (BLOB field),
    // but bump version so existing records get the default empty array on read.
    this.version(5).stores({
      workers: '[id+siteId], siteId, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
    })

    // v6: pending gains clientEventId (non-indexed BLOB field) and a new
    // sessionStatus table keyed by [workerId+siteId] for a reload-safe local
    // record of each worker's last check-in/out state.
    this.version(6).stores({
      workers: '[id+siteId], siteId, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
      sessionStatus: '[workerId+siteId], siteId',
    })

    // v7: pending gains accuracyMeters (non-indexed) so the server can widen a site's zone by the
    // fix's own error margin. Records queued before this upgrade simply carry undefined and get no
    // allowance, which is exactly how they were already evaluated.
    this.version(7).stores({
      workers: '[id+siteId], siteId, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
      sessionStatus: '[workerId+siteId], siteId',
    })

    // v8: sessionStatus is re-keyed by workerId alone — a worker has one session, not one per site.
    // IndexedDB cannot change a primary key in place, so the table is dropped and recreated. Only a
    // same-day offline cache is lost, and it is rebuilt on the next status refresh.
    this.version(8).stores({ sessionStatus: null })
    this.version(9).stores({
      workers: '[id+siteId], siteId, name',
      siteInfo: 'id',
      pending: '++id, siteId, status, recordedAt',
      sessionStatus: 'workerId, siteId',
    })
  }
}

export const db = new GarantDB()
