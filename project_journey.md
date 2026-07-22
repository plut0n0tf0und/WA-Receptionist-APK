# WA Receptionist Project Journey

This document serves as the official biography and journey log of the WA Receptionist project. It tracks our progress, decisions, and the overall architecture of the system. **(Updated only on command)**

## July 21, 2026
*   **Form Redirection & User Experience:** We updated the Vercel-hosted lead capture form (`test_form.html`). Instead of a dead-end, upon successful submission, it displays a "Submission Successful!" message and automatically redirects the user back to the WhatsApp app after 2.5 seconds using the `whatsapp://send` intent.
*   **AI Chat Flow Update:** We updated the `WhatsAppNotificationService.kt` prompt to reflect a new greeting structure. 
    *   The bot now asks: *"How can we help? Reply with number of the service you need: 1. Promo Website - Requirement, 2. Customer care - Raise ticket"*.
    *   If the user replies with 1 or 2, the bot provides the link to the Vercel form.
*   **Form Submission Notification (Architecture):** The user clarified that the backend logic for handling form submissions is already implemented in Google Apps Script. When a user submits the form, the number goes into a sheet, and the architecture is designed so that the Android app will be notified/triggered to send a confirmation message to the client ("We got your submission, rest assured...") completely organically from the business phone.

*   **Near-Instant Lead Syncing (Foreground Service Migration):** We encountered an OS-level limitation with Android's `WorkManager`, which enforces a minimum 15-minute wait between background jobs. Because we wanted the form confirmation message to send almost instantly (within seconds), we evaluated three options: 1) The 15-min background job, 2) Firebase Cloud Messaging Webhooks (instant, but requires complex backend config), and 3) An Android Foreground Service. We chose the **Foreground Service Polling** approach because it delivers near-instant results (10-second polling) and is easiest to implement for a dedicated business device that is permanently connected to power. We implemented `ForegroundLeadsSyncService.kt` to replace the old `LeadsSyncWorker.kt`.

*   **Vercel Form Overhaul (UserXpert Theme):** Completely redesigned the web form (`test_form.html`) into a modern, mobile-first 2-step stepper form to capture extensive lead requirements (Business Name, Expected Outcomes, etc.). Matched the UI with the official UserXpert brand identity, including integrating their official logo image and exact magenta theme (`#B512B8`). 
*   **Apps Script Database Expansion & Optimization:** Upgraded the Google Apps Script (`doPost`) to handle 13 distinct data points sent by the new form. Optimized the Android Lead Sync logic: since the new form explicitly captures the user's WhatsApp number in Column B, the Apps Script no longer has to execute a slow cross-reference search against the "Call Greetins" sheet, drastically speeding up the sync worker payload.
*   **Android Notification Display Name Limitation (Bug Fix):** We attempted to auto-fill the client's phone number into the form URL via the Android Bot. However, we discovered an Android OS limitation: for saved contacts, `Notification.EXTRA_TITLE` returns the Contact's Display Name (e.g., "Name 💫"), completely hiding the underlying phone number. This resulted in names/emojis being injected into the form URL instead of numbers. To resolve this, we removed the phone URL parameter, requiring clients to enter their WhatsApp number manually in the new form UI.

## July 22, 2026
*   **Customer Support Form Addition:** Created a new `ticket_form.html` to handle the "Customer care - Raise ticket" flow (Option 2). Both forms are now hosted in a unified `vercel-frontend` directory.
*   **Google Apps Script Updates:** 
    *   Added `submit_ticket` logic to save new support tickets to a "Customer Support" sheet, automatically recording a detailed Date & Time Timestamp using `.toLocaleString()`.
    *   **Infinite Loop Bug Fix:** Fixed a critical bug in the `get_new_leads` polling logic where Google Sheets automatically converted the string `"TRUE"` to a boolean `true`, causing the Android Sync Service to endlessly spam failed WhatsApp intents to dummy test numbers. Fixed by enforcing a strict check for both data types (`!== "TRUE" && !== true`).
*   **AI Pre-filled CTA Prioritization:** Updated the AI prompt in `WhatsAppNotificationService.kt` to flawlessly handle pre-filled website inquiries (e.g. "Hi UserXpert, I want to discuss the Growth Plan"). Implemented a strict rule hierarchy where these messages completely bypass the standard 1/2 option menu, sending the user directly to the correct form with a warm, human-like acknowledgment.
*   **Phone Number Pre-fill Logic Fix:** Added logic to `index.html` and `ticket_form.html` to auto-fill the user's WhatsApp number from the URL's `lead_id` parameter (passed by the bot). Implemented a specific exclusion check (`!leadId.startsWith('L-')`) to prevent the forms from accidentally populating the input box with `L-` prefixed tracking IDs generated by the missed call `CallLogService`.

---
*Note to AI: Append new entries above or below as instructed, and only modify this file when explicitly commanded by the user.*
