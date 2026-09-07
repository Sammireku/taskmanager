# Cobby AI - Smart Task Manager & Habit Assistant

A modern, offline-first Android task manager and habit tracker powered by Jetpack Compose, Room DB, Firebase Firestore, WorkManager, and Gemini AI.

---

## 🌟 Key Features

### 1. 🔄 Automated Habit Recurrence
- When a task flagged as a habit (`isHabit = true`) is marked complete, Cobby AI automatically calculates the next occurrence based on its frequency (*Daily*, *Weekly*, *Weekdays*, *Monthly*).
- Automatically inserts the new habit cycle into Room DB & Firestore and sets upcoming WorkManager reminders.

### 2. 🗑️ Soft-Delete Mechanism & 30-Day Trash Buffer
- Deleted tasks are moved to a **Trash Buffer** with an `isDeleted = true` flag and `deletedAt` timestamp instead of immediate hard removal.
- **Background Purge Job**: A periodic WorkManager worker (`TrashPurgeWorker`) runs every 24 hours to automatically purge tasks soft-deleted over 30 days ago.
- **Trash Bin UI**: Accessible from the top settings menu, allowing users to inspect deleted items, restore accidentally removed tasks, or manually empty the trash bin.

### 3. ☁️ Real-time Firestore Synchronization
- Integrated `FirestoreRepository` with `addSnapshotListener` scoped to the current `FirebaseAuth` user.
- Local Room DB remains the instant offline source of truth, while Firestore seamlessly mirrors tasks bidirectionally whenever internet connection is available.

### 4. 🛡️ Secure API Key Proxy (Firebase Cloud Functions)
- Moves secret API keys (`GEMINI_API_KEY`, `PLACES_API_KEY`) out of client-side Android source code into serverless Firebase Cloud Functions (`/api/gemini/generate`, `/api/places/autocomplete`, `/api/places/details`).
- Prevents reverse-engineering or key extraction from compiled APKs.

### 5. ⚙️ CI/CD & Security Verification (GitHub Actions)
- Automated `.github/workflows/android_ci.yml` workflow:
  - Scans repository for exposed API keys.
  - Runs unit tests (`testDebugUnitTest`).
  - Executes Android Lint (`lintDebug`).
  - Builds debug APK (`assembleDebug`).

---

## 🏗️ Architecture & Stack

- **UI Framework**: Jetpack Compose with Material Design 3 (Dynamic Color support)
- **Local Database**: Room DB (Schema Version 6)
- **Cloud Database**: Firebase Firestore
- **Authentication**: Firebase Auth & Google Identity Credentials
- **Background Processing**: WorkManager & AlarmManager
- **Networking**: Retrofit & KotlinX Serialization
- **Cloud Backend**: Node.js Firebase Cloud Functions (v18)

---

## 🚀 Getting Started

### Local Android Build

```bash
# Clone repository
git clone https://github.com/your-org/cobbyai.git
cd cobbyai

# Run unit tests
gradle testDebugUnitTest

# Build Debug APK
gradle assembleDebug
```

### Firebase Cloud Functions Deployment

To deploy the secure API proxy serverless functions:

```bash
cd functions

# Install dependencies
npm install

# Set Cloud Function config secrets
firebase functions:config:set gemini.key="YOUR_GEMINI_API_KEY" places.key="YOUR_PLACES_API_KEY"

# Deploy functions to Firebase
firebase deploy --only functions
```

### Updating Client Retrofit Base URL to Cloud Proxy
Update `RetrofitClient.kt` base URL to your deployed Firebase Cloud Functions URL:
```kotlin
private const val PROXY_BASE_URL = "https://us-central1-YOUR_PROJECT_ID.cloudfunctions.net/api/"
```

---

## 🧪 Testing & CI/CD
All pull requests automatically trigger GitHub Actions to verify unit tests, Android Lint, and ensure no raw API keys are committed to source control.
