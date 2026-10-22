# RailReserve

RailReserve is a native Android Java train-search and reservation-workflow demonstration. It uses XML Android Views and SQLite for local demo data. The app opens to **Live Home**, where station and scheduled timetable searches are sent through the Node.js backend to RailCore. The **Tickets** section displays locally saved demo bookings and links to PNR lookup and cancellation.

Demo bookings are stored only on the device. They are not IRCTC reservations, do not issue real tickets, and do not process payments. Live search returns scheduled timetable information, with on-demand per-train fare estimates and seat-availability checks; live running status and real booking are not enabled. Provider requests may use RailCore credits.

## Features

- Live station suggestions, route/date train search, and scheduled train details.
- On-demand seat availability by train and class with General quota.
- Local demo train search, reservations, PNR lookup, and cancellation.
- A Tickets section that displays saved demo tickets and refreshes when you return to it.
- Java, XML layouts, and SQLite persistence.

## Requirements

- Android Studio with Android SDK Platform 37 installed (or allow Android Studio to install it).
- JDK 17 or newer. Android Studio's bundled JDK is suitable.
- Node.js 24.5 or newer and npm, if you want live station/train search.
- A RailCore API key for the backend. Keep it private; it does not belong in the Android app or source control.

The Gradle Wrapper uses Gradle 9.5.0 and downloads it automatically on the first build.

## Build a debug APK

Open PowerShell in the project root (the directory containing `gradlew.bat`) and run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

This default build points to `http://10.0.2.2:3000`, which is the Android emulator's route to a backend running on the development computer. For a build without a working backend, the APK can still be installed, but live searches will not work until the backend is reachable.

### Install on an emulator or USB-connected phone

Start an emulator or connect a phone with USB debugging enabled, then run:

```powershell
.\gradlew.bat :app:installDebug
```

Alternatively, install the APK directly with Android Debug Bridge (ADB):

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Enable live search

The backend must be running and reachable by the device. Configure it once from PowerShell:

```powershell
Set-Location .\backend
Copy-Item .env.example .env
```

Open `backend\.env` in a text editor, set `RAILCORE_API_KEY` to your private RailCore key, and save it. The app's live search requires the `stations:read`, `journeys:read`, and `schedule:read` key scopes. Do not paste the key into Gradle, the Android app, or a committed file. Install backend dependencies and start the server:

```powershell
npm install
npm start
```

The server listens on port 3000 and binds to all network interfaces. Verify it on the development computer:

```powershell
curl.exe --noproxy "*" http://127.0.0.1:3000/health
```

The response should contain `"status":"ok"`. To verify station lookup:

```powershell
curl.exe --noproxy "*" "http://127.0.0.1:3000/api/stations/search?q=Delhi"
```

Keep the backend process running while using live search. Backend setup details and configuration are in [backend/README.md](backend/README.md).

### Use a physical phone on Wi-Fi

The emulator URL `10.0.2.2` does **not** work on a physical phone. Connect the phone and development computer to the same reachable Wi-Fi network, find the computer's current Wi-Fi IPv4 address with `ipconfig`, then build with that address. The current development Wi-Fi address is `172.19.10.228`; use the address shown by `ipconfig` if it changes:

```powershell
.\gradlew.bat :app:assembleDebug -PrailreserveDebugBackendUrl=http://192.168.1.20:3000
```

Replace `192.168.1.20` with the computer's current Wi-Fi IPv4 address. Install the newly built APK; an already-installed APK retains the backend address it was built with. On the phone, open `http://192.168.1.20:3000/health` in a browser. If it does not load, allow inbound TCP port 3000 through Windows Firewall and check that the Wi-Fi network does not isolate connected devices. The computer's address can change when it reconnects to Wi-Fi, so rebuild with the new address if needed.

This Wi-Fi build does not require an ADB reverse tunnel. If the phone cannot load the backend health URL, open PowerShell as Administrator and allow the backend only from the local subnet:

```powershell
New-NetFirewallRule -DisplayName "RailReserve backend (local subnet)" -Direction Inbound -Action Allow -Program "C:\Program Files\nodejs\node.exe" -Protocol TCP -LocalPort 3000 -RemoteAddress LocalSubnet -Profile Public
```

Also check that the Wi-Fi network permits device-to-device traffic. The phone and computer must stay on the same Wi-Fi network, and the backend must remain running.

### Use a physical phone over USB

When Wi-Fi routing is unavailable, connect the phone with USB debugging enabled and create an ADB reverse tunnel. Build the app with the tunnel's phone-side address, then install it:

```powershell
adb reverse tcp:3000 tcp:3000
.\gradlew.bat :app:installDebug -PrailreserveDebugBackendUrl=http://127.0.0.1:3000
```

Keep the backend running on the computer. The reverse mapping is removed when the USB debugging connection ends, so recreate it after reconnecting the phone. The emulator default `10.0.2.2` and a USB build using `127.0.0.1` are not interchangeable.

Debug builds permit cleartext HTTP for local development. This permission is disabled for release builds.

### Build a release APK

Release builds require an HTTPS backend URL. Replace the example URL with your deployed backend:

```powershell
.\gradlew.bat :app:assembleRelease -PrailreserveBackendUrl=https://your-backend.example.com
```

The release APK output is under `app\build\outputs\apk\release\`. Configure release signing before distributing the APK. Never embed the RailCore API key in the release APK; keep it in the backend's private environment.

## Tests

Run the Android unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Run the backend tests and syntax checks:

```powershell
Set-Location .\backend
npm test
npm run lint
```

Backend unit tests use mocked provider responses. The optional provider smoke check can make real provider calls and use account quota; see [backend/README.md](backend/README.md) before running it.

## Troubleshooting

- **Station suggestions do not appear:** Confirm the backend is running, check `/health` from the phone or emulator, and confirm the app was built with the correct backend URL.
- **Phone cannot open the health URL:** Check the laptop's current Wi-Fi IPv4 address, Windows Firewall inbound access to TCP 3000, and Wi-Fi client isolation.
- **No station suggestion is selected:** Type at least two characters and select one of the returned station suggestions before searching trains.
- **Seat availability is missing:** Open live train results, choose a class, and tap **Check seat availability** on a train. Each check can consume 5 RailCore credits and requires the key's `availability:read` scope.
- **Backend will not start:** Confirm Node.js is 24.5 or newer and that `backend\.env` contains a valid `RAILCORE_API_KEY`.
- **Gradle cannot find Android SDK:** Open the project in Android Studio, install Android SDK Platform 37 from SDK Manager, then sync and retry.

## Project documentation

- [Live-mode setup](docs/live-mode-setup.md)
- [Phase 1 contracts](docs/architecture/PHASE1_CONTRACTS.md)
- [Phase 2 contracts](docs/architecture/PHASE2_CONTRACTS.md)
- [Backend setup and API](backend/README.md)
