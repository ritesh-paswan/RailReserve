# RailReserve

RailReserve is a native Android Java train search and reservation-workflow demonstration for a B.Tech Semester 3 Object-Oriented Methodology project. It uses XML Android Views and a local SQLite database. The app opens to Live Home for station and scheduled timetable searches through the Node.js backend and RailRadar provider. The Tickets section retains the local demo booking, PNR lookup, and cancellation workflow. Demo reservations are not IRCTC bookings and do not issue real tickets.

The current provider plan does not supply fares or live seat availability. Live train results display scheduled information only; they do not provide live running status or booking. Local demo bookings use fictional schedules, fares, and seats.

## Run

Open this existing project in Android Studio, allow Gradle sync, select an emulator or connected Android device, and run the `app` configuration. The app targets Android 37, supports Android 8.0/API 26 and newer, seeds demo schedules on a background executor, and stores demo reservations locally in SQLite.

Build from PowerShell with ` .\gradlew.bat :app:assembleDebug ` and run unit tests with ` .\gradlew.bat :app:testDebugUnitTest `.

For a physical phone, keep the backend running on the same reachable network as the phone and build with the computer's LAN address, for example:

`.\gradlew.bat :app:assembleDebug -PrailreserveDebugBackendUrl=http://192.168.1.20:3000`

Use `http://10.0.2.2:3000` (the debug default) for the Android emulator. Cleartext HTTP is enabled only in debug builds; release builds require an HTTPS backend URL.

See [Phase 1 contracts](docs/architecture/PHASE1_CONTRACTS.md) for package responsibilities, API signatures, and Intent extras.
See [Phase 2 contracts](docs/architecture/PHASE2_CONTRACTS.md) for booking contracts, migration behavior, and Intent extras.
