# 📞 Call Capture Bot

**Welcome to the Call Capture Bot!** 
This project is a lightweight, fully automated Android application that acts as your personal receptionist. It quietly sits on your business phone, listens for calls, and instantly logs every single one of them straight into a Google Sheet.

No manual data entry. No missed leads. Just seamless automation.

---

## 🎯 What Does It Do?
Whenever your phone receives a call (whether you answer it, miss it, or even if you make an outgoing call), the app springs into action. 

The moment the call ends, the app:
1. Wakes up in the background.
2. Checks the phone's Call Log to grab the precise details (Phone Number, Date, Time, Duration, and Type of call).
3. Securely beams this information directly to your Google Sheet in real-time.

---

## 🧩 How It Works (The Simple Version)
You can think of this system in two halves: **The Phone** and **The Spreadsheet**.

### 1. The Android App
Built with modern Kotlin, this app is designed to be invisible but reliable. 
- It asks for permission to read your phone state and call logs. 
- It uses an in-built `AppLogger` so you can open the app at any time and see exactly what it has been doing (a live log of calls and sheet uploads).
- It doesn't drain your battery. It only activates the exact second a phone call finishes.

### 2. The Google Apps Script (The Bridge)
Google Sheets can't talk to a phone directly, so we use a tiny piece of code called an "Apps Script". 
- This script acts like a bouncer at the door of your spreadsheet. 
- When the phone sends the call data over the internet, the Apps Script catches it, neatly formats it, and inserts it into **Row 2** of your spreadsheet (right below your headers) so you never have to scroll down to find new leads.

---

## 🚀 Setup Guide

If you ever need to set this up on a new phone or a new spreadsheet, here is your playbook.

### Step 1: Prepare the Google Sheet
1. Open your Google Sheet.
2. Go to **Extensions > Apps Script** in the top menu.
3. Paste the provided script (which takes the incoming data and places it into Columns B, C, D, and E).
4. Click **Deploy > New Deployment**.
5. Set the type to **Web App**, Execute as **Me**, and Who has access to **Anyone**.
6. Copy the **Web App URL** it gives you.

### Step 2: Build the App
1. Open this project folder (`WA-Receptionist-APK`) in **Android Studio**.
2. If you changed your Google Sheet, paste your new **Web App URL** into the `CallLogService.kt` file.
3. Plug your Android phone into your computer via USB (make sure USB Debugging is on).
4. Go to the top menu and click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Send the generated `.apk` file to your phone and install it.

### Step 3: Run It!
1. Open the app on your phone.
2. Grant the permissions it asks for.
3. **Make a test call** to the phone. 
4. Hang up, and watch the magic happen as the Google Sheet updates instantly!

---

## 🛠️ Built-in Troubleshooting
If things ever stop working, you don't need to be a programmer to figure out why.
Just open the app on your phone and click **"Refresh Logs"**. 

- If it says **"Error reading call log"**, the phone permissions might have been revoked by the Android system.
- If it says **"Network Error"**, the phone probably lost its internet connection.
- If you see a **Code 200**, the app is working perfectly and the data has been successfully sent to Google!

---
*Built with ❤️ to keep your business leads organized automatically.*
