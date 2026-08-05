# 🏗️ Technical Architecture: Android App to Google Sheets Integration

This document outlines the technical flow of data from the Android Call Capture application to Google Sheets. It is designed to be easily understood by technical stakeholders, clients, and developers.

---

## 📊 1. The High-Level Architecture

Instead of having the Android application communicate directly with the complex Google Sheets API, we utilize a **Webhook Architecture**. 

The system consists of three main components:
1. **The Client (Android APK)**: Detects the end of a phone call, reads the local device database, and packages the data.
2. **The Middleware (Google Apps Script Web App)**: A lightweight, serverless endpoint hosted securely on Google's infrastructure.
3. **The Database (Google Sheets)**: The final destination where the data is permanently stored.

```text
[ Android Device ] --(HTTP POST via Internet)--> [ Google Apps Script ] --(Internal Google API)--> [ Google Sheets ]
```

---

## 💬 2. How They Talk to Each Other

### The Communication Protocol: HTTP POST
When a call ends, the Android app constructs an **HTTP POST request**. This is the exact same standard protocol that web browsers use to submit form data to a website.

The app takes the call details and packages them into a lightweight text format called **JSON** (JavaScript Object Notation). 

**Example of the Payload sent by the App:**
```json
{
  "phoneNumber": "+1234567890",
  "callType": "Incoming",
  "date": "2023-10-25",
  "time": "14:30:15",
  "duration": "45"
}
```

### The Receiver: Google Apps Script Web App
Google allows us to write Javascript code (`Apps Script`) that lives directly inside the Google Sheet. By deploying this script as a **"Web App"**, Google gives us a unique URL.

When the Android app sends the JSON payload to this URL:
1. The Apps Script intercepts the incoming `HTTP POST` request.
2. It unpacks the JSON text back into distinct variables (Phone Number, Date, Time, etc.).
3. It utilizes Google's internal `SpreadsheetApp` API to insert a brand new row at the top of the sheet and maps the variables to the exact columns.

---

## 🛡️ 3. Why Was This Architecture Chosen?

When presenting to clients or stakeholders, these are the primary benefits of this specific setup:

### A. Security & Authentication (Zero-Credential Policy)
If the Android app talked directly to the Google Sheets API, we would be forced to embed sensitive Google OAuth credentials or Service Account keys directly inside the Android APK. If someone reverse-engineered the APK, they could steal those keys.
* **Our Solution**: By using an Apps Script Web App, the Android app only knows a public URL. It holds zero credentials. The Google Sheet remains 100% secure.

### B. Lightweight & Battery Efficient
Directly integrating Google's official API SDKs into an Android app drastically increases the app's file size and memory consumption. 
* **Our Solution**: Sending a simple text POST request uses virtually zero memory and completes in milliseconds, preserving the phone's battery life.

### C. Agility & Maintenance
If the client decides to change the format of the Google Sheet (e.g., adding a new column or changing the order of columns), we **do not need to update the Android app**. 
* **Our Solution**: We simply edit 3 lines of code in the Google Apps Script in the cloud. The Android app continues sending the exact same raw data, and the script handles the new formatting on the fly.

---

## ⚙️ 4. The Apps Script Deployment Explained

When the Apps Script was deployed, two critical settings were chosen:
1. **Execute as: "Me" (The Sheet Owner)**: This means the script has the authority to write to the spreadsheet using the Owner's permissions, regardless of who triggered the script.
2. **Who has access: "Anyone"**: This opens the Web App URL so that it can receive data from the internet. It acts as an open mailbox. The Android app drops the letter (data) into the box, and the script securely files it into the spreadsheet. 

This combination ensures a frictionless, secure, and highly scalable data pipeline.
