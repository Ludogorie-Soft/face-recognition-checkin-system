# AttendTrack — User Guide

AttendTrack is a digital attendance management system designed for construction sites, warehouses, and any workplace where workers need to be tracked daily. The system uses **facial recognition** and **GPS location** to verify check-ins and check-outs, and provides detailed reports on attendance and worked hours.

---

## Who Is the System For?

The system has two types of users:

**Administrator** — has full access to everything. Can create and manage sites, workers, and administrators. Can view all reports, correct working hours, and export data to Excel.

**Worker** — does not log in to the system. Attendance is recorded through a tablet or phone at the worksite by an authorized administrator.

---

## How the System Works — Step by Step

### 1. Setting Up Sites

Before you can start tracking attendance, you need to create the worksites. Each site has:

- **Name** — e.g. "Building 5 – Sofia"
- **Address** — for reference
- **Location on the map** — the GPS coordinates of the site. The administrator clicks on the map to set the location
- **GPS radius** — defines the zone within which check-ins are accepted as valid (e.g. 200 meters from the centre)
- **Shift start and end time** — e.g. 08:00 – 17:00
- **Checkpoints** — specific locations within the site (e.g. entrance, warehouse). Workers must be within range of at least one checkpoint for their location to be valid

### 2. Adding Workers and Administrators

In the **Workers** section, the administrator can:

- **Add a worker** — requires only a full name. Email, phone, and company are optional. Workers do not log in, so no password is needed
- **Add an administrator** — requires full name, email, phone, and a password. The administrator uses these credentials to log in
- **Register a worker's face** — after the worker is created, the administrator opens the camera and photographs the worker's face. The system saves a mathematical profile of the face (not an image) used for recognition. One person can only be registered once — the system refuses duplicate face registrations
- **Assign workers to sites** — each worker can be assigned to one or more sites

### 3. Recording Attendance (Verify)

The **Verify** section is used daily at the worksite — typically on a tablet or phone.

**Workflow:**

1. The administrator opens the Verify screen and selects the site
2. The system synchronises data with the server (or loads from local cache if offline)
3. The camera starts and begins scanning faces
4. When a worker stands in front of the camera, the system automatically recognises them and shows their name with a confidence percentage
5. The administrator presses **Check In** or **Check Out**
6. The system verifies the GPS location — if the worker is outside the allowed radius, a warning is shown and the administrators are notified

**If the face is not recognised:**

The administrator can select the worker manually from a dropdown list and confirm the attendance record. This is marked as a "manual confirmation" in the reports.

**Offline mode:**

If there is no internet connection, attendance records are saved locally on the device. They are automatically sent to the server the next time a connection is available.

---

## Dashboard

The dashboard shows a quick overview for the current day:

- **Total sites** — how many active worksites exist
- **Total workers** — how many active workers are in the system
- **Present today** — how many workers have checked in today across all sites
- **Missing today** — how many assigned workers have not yet checked in

---

## Reports

The Reports section is available only to administrators. Three types of reports are available, and for each you can choose a period: **Today**, **This Week**, **This Month**, or **Custom** (from date – to date).

---

### Report 1: Attendance

Shows all check-in and check-out records for a selected site and period.

**Columns shown:**
- Worker name
- Site
- Date
- Record type (Check In / Check Out)
- Time of the record
- GPS coordinates
- Whether the location was valid (within the site radius)
- Face recognition confidence (percentage)
- Whether the record was confirmed manually

**Filtering:** Select a specific site from the dropdown.

**Export:** The entire report can be downloaded as an **Excel file (.xlsx)**.

---

### Report 2: Missing Workers

Shows which workers assigned to a specific site did not check in on a given day.

Useful for quickly identifying absences or for administrative purposes.

---

### Report 3: Worked Hours

The most detailed report. Shows the number of hours worked by each worker, grouped by day and site.

**Two views available:**

**By Site** — shows worked hours for one selected site. For each worker and each day, you see:
- Check-in time
- Check-out time
- Calculated hours

**Overall (All Sites)** — shows a summary per worker across all sites. If a worker worked on multiple sites on the same day, each appears as a separate row. At the end, the total hours for the entire period are shown.

**Special labels in the report:**

- **(AUTO)** — the check-out was created automatically by the system (see Auto-checkout below)
- **(→)** — the check-out time was inferred from when the worker checked in at a different site (meaning they left the first site without checking out)
- **OPEN SHIFT** — the worker checked in but never checked out

**Correcting hours:**

If a record contains an error (e.g. a worker forgot to check out and the automatic checkout is incorrect), the administrator can correct the hours by clicking the pencil icon on that row. A corrected value and a note can be entered. Corrected hours override the calculated hours in the total.

**Export:** Works hours reports can also be exported to **Excel**.

---

## Automatic Check-Out

If a worker checked in at a site but never checked out by the end of the work day, the system **automatically creates a check-out** at the scheduled end of shift time.

This happens every night at 00:01 for the previous day. It only applies to sites that have a configured shift end time.

Automatic check-outs are clearly marked as **(AUTO)** in the reports so they are distinguishable from regular check-outs.

If the worker checked in after the shift end time (which can happen if working overnight), no automatic check-out is created.

---

## Notifications

The system can send two types of notifications — by **email** and **push notification** (browser notification):

**Missing workers notification** — sent 30 minutes after the end of a site's shift if any assigned workers have not checked in that day. All administrators receive this notification.

**Suspicious check-in notification** — sent immediately when a worker checks in from outside the site's GPS zone. All administrators are alerted.

---

## Offline Support

The system is built to work in locations with poor or no internet connectivity:

- On the Verify screen, when you select a site and click **Sync**, all worker data and face profiles are downloaded to the device
- Check-in and check-out records are saved locally if offline
- Records are automatically uploaded to the server when the connection is restored
- If the list of sites cannot be loaded, the last cached version is used

---

## Language

The system is available in **English** and **Bulgarian**. The language is selected automatically based on the browser settings, or can be changed via the URL (e.g. `/bg/...` for Bulgarian, `/en/...` for English).

---

## Summary of Key Features

| Feature | Description |
|---|---|
| Facial recognition | Automatic worker identification via camera |
| GPS geofencing | Location validation at check-in/check-out |
| Offline mode | Works without internet, syncs when back online |
| Attendance reports | Full history of check-ins and check-outs |
| Missing workers report | Shows who did not show up on a given day |
| Worked hours report | Calculates daily hours with corrections |
| Automatic check-out | Closes open shifts at end of work day |
| Excel export | Download any report as a spreadsheet |
| Push notifications | Browser and email alerts for absences and suspicious activity |
| Two languages | Bulgarian and English interface |
