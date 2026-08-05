# WA Receptionist - Project Documentation

## 1. Project Overview (Frontend vs Backend)

**Is there a Frontend and Backend?**
In traditional web development, you have a Frontend (what you see) and a Backend (a server far away that does the thinking). 
In this Android App (Kotlin), **everything is bundled together on the phone**. 
- The **Frontend** is the app's user interface (the toggles, the log screen, the buttons).
- The **Backend** logic (the AI thinking, database saving, and automated clicking) runs entirely in the background as Android Services on your actual phone. 
- We do connect to two external "Backend" APIs over the internet: **Groq** (for the AI Brain) and **Google Sheets** (for saving leads).

**What is Kotlin?**
Kotlin is the official programming language for modern Android apps. It is very similar to Java but much cleaner.

---

## 2. Core Files Explained

Here is a breakdown of every important Kotlin file in the `app/src/main/java/com/wareceptionist/app` folder and what it does:

    ### The User Interface (Frontend)
    *   **`MainActivity.kt`**: This is the main screen of the app. It handles the UI, the neon buttons, the Rive animation, and asking the user for system permissions (like Calendar and Call logs). It also saves your toggle preferences (like turning the AI ON or OFF).
    *   **`FullLogActivity.kt`**: A simple screen that pops up when you click "View Full Logs". It just displays the entire history of what the bot has been doing.

### The Brains & Automation (Background Services)
*   **`WhatsAppNotificationService.kt`**: **(The Core AI Brain)** This file listens to your phone's notifications. When a WhatsApp message arrives, it intercepts it, checks the chat history, and sends the conversation to the Groq AI (Llama 3.3). It then uses Android's "Quick Reply" hidden feature to silently send the AI's response back to the client without ever opening the WhatsApp app. It also handles the "Tools" (Booking appointments and saving leads to Google Sheets).
*   **`WhatsAppAccessibilityService.kt`**: **(The Ghost Clicker)** This file is only used for the Missed Call flow. Because we can't silently send a brand new message to someone who hasn't messaged you first, the app physically opens WhatsApp. This script acts as a ghost finger, scanning your screen for the WhatsApp "Send" button and clicking it instantly.
*   **`CallReceiver.kt`**: This listens to your phone's hardware. The exact second your phone rings or misses a call, this file wakes up and tells the `CallLogWorker` to get to work.
*   **`CallLogWorker.kt`**: When told a call was missed, this script formats a greeting message (e.g., "Sorry I missed your call!") and physically opens WhatsApp on your phone screen with that text pre-filled. It then relies on the `WhatsAppAccessibilityService` to click the Send button.

### Utilities & Helpers
*   **`CalendarHelper.kt`**: A small utility script that talks directly to your Android phone's built-in calendar database. When the AI decides to book an appointment, it uses this file to silently insert a 30-minute block into your calendar (which automatically syncs to Google).
*   **`AppLogger.kt`**: This handles saving all the green text logs you see on the main screen. It ensures that everything the bot does is recorded for you to read.

### The Database (`db/` folder)
The app has a local SQLite database (using a framework called Room) to remember past conversations so the AI has memory.
*   **`AppDatabase.kt`**: The main configuration for the local database.
*   **`ChatSession.kt` & `ChatMessage.kt`**: These define the structure of the tables (like columns in an Excel sheet). They store the phone number, the message, and whether it was the "user" or the "model" (AI) speaking.
*   **`ChatDao.kt`**: The "Data Access Object". This contains the SQL commands to read, write, and delete messages from the database.
