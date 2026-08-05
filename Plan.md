# Android Call Capture App

## Objective
Build an Android application that automatically captures incoming call details and sends them to a Google Sheet. This app is intended for a single business phone used to receive customer enquiries from advertisements.

---

# Primary Goal

Automatically capture every answered or missed incoming call without any manual action.

The captured data will later be used to send WhatsApp messages and manage customer leads.

---

# Target Workflow

Incoming Call
↓
Call Ends
↓
Detect Call Completion
↓
Detect New Call Log Entry
↓
Read Latest Call Log
↓
Extract Required Information
↓
Send Data to Google Sheets

---

# Preferred Technical Approach

Use official Android APIs as much as possible.

Primary components:

- Phone State Listener
- Content Observer
- Android Call Log API

Avoid relying on:

- Accessibility Service
- Notification Listener
- Root access
- Custom Dialer App

These should only be considered if official APIs prove unreliable.

---

# Data to Capture

Required:

- Phone Number
- Call Type
    - Incoming
    - Missed
    - Outgoing
- Date
- Time
- Duration

Optional:

- Contact Name (if available)

---

# Required Permissions

- READ_CALL_LOG
- READ_PHONE_STATE
- INTERNET
- FOREGROUND_SERVICE (if continuous monitoring is required)

Only request permissions that are actually necessary.

---

# Google Sheets Integration

The application should NOT communicate directly with Google Sheets APIs.

Instead:

Android App
↓
HTTP POST Request
↓
Google Apps Script Web App
↓
Google Sheet

This keeps authentication simple and lightweight.

---

# Reliability Requirements

The application should:

- Work after device reboot
- Continue monitoring in the background
- Avoid duplicate entries
- Ignore failed or incomplete reads
- Retry if sending fails
- Queue unsent records until internet is available

---

# Compatibility Goal

Target Android 10+

Test compatibility with different manufacturers including:

- Samsung
- Xiaomi
- Vivo
- Oppo
- OnePlus
- Realme

The implementation should avoid depending on manufacturer-specific Phone applications.

---

# Code Quality

Structure the project cleanly.

Suggested modules:

- Permission Manager
- Call Detection
- Call Log Reader
- Data Model
- Local Queue
- Network Sender
- Settings
- Logger

Keep each responsibility separated.

---

# Error Handling

Handle:

- Permission denied
- Call log unavailable
- Background restrictions
- Internet unavailable
- Duplicate call detection
- API failures

Application should never crash because of these situations.

---

# Future Expansion

The architecture should make it easy to add:

- WhatsApp automation
- Lead status updates
- Contact synchronization
- CRM integration
- Local database
- Cloud backend
- Multiple business phones

No major rewrite should be required.

---

# Development Principles

- Use Kotlin
- Follow modern Android architecture
- Minimize battery usage
- Keep memory usage low
- Keep permissions minimal
- Write modular, reusable code
- Add comments only where necessary
- Follow Android best practices

---

# Expected Output

Every completed call should generate one record similar to:

Phone Number
Call Type
Date
Time
Duration

This record will then be sent to the Google Apps Script endpoint for storage in Google Sheets.

---

# Success Criteria

The application is successful when:

- Every incoming or missed call is captured.
- No duplicate records are created.
- Data is reliably delivered to Google Sheets.
- The app continues working in the background.
- The solution works across most modern Android devices without requiring root access.