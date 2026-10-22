# RailReserve backend

Small Node.js adapter that keeps the RailCore credential on the server and exposes a stable API for the Android app. Live search reads station, route-train, and scheduled-timetable data. The app does not use this backend to book tickets.

## Requirements and local setup

- Node.js 24.5+ (native `fetch`, `AbortSignal.timeout`, `--env-file-if-exists`, and `--use-env-proxy`).
- npm.

From this directory:

```powershell
Copy-Item .env.example .env
npm install
npm test
npm run lint
npm start
```

Set `RAILCORE_API_KEY` and, optionally, `RAILCORE_BASE_URL=https://ir.railcore.tech/v1` in the private `.env`. Never add the key to source control or the Android app. The start command enables Node's environment-proxy support for configured `HTTP_PROXY` and `HTTPS_PROXY` variables.

Create a RailCore key with the `stations:read`, `journeys:read`, and `schedule:read` scopes for the app's live search. Fare and availability compatibility endpoints additionally require `fares:read` and `availability:read`. RailCore bills credits per operation (documented as 1 for station search, 5 for route search, and 2 for schedule); station autocomplete therefore uses provider credits. Check your plan and balance before enabling live searches.

The backend listens on `0.0.0.0:3000` by default. `GET /health` checks only the local process and does not contact RailCore.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `RAILCORE_API_KEY` | required | Private RailCore API key with the scopes needed by enabled endpoints. |
| `RAILCORE_BASE_URL` | `https://ir.railcore.tech/v1` | HTTPS RailCore API base URL. |
| `PORT` | `3000` | HTTP listener port. |
| `REQUEST_TIMEOUT_MS` | `20000` | Upstream request timeout, accepted range 500–30000 ms. |
| `RATE_LIMIT_WINDOW_MS` | `900000` | Local backend API rate-limit window. |
| `RATE_LIMIT_MAX` | `60` | Requests per client IP per window on `/api`. |

## RailReserve endpoints

Successful responses use `{ "success": true, "data": ..., "meta": { "requestId": "..." } }`; errors use a sanitized error envelope.

### Health

```http
GET /health
```

This local status check does not contact RailCore.

### Station search

```http
GET /api/stations/search?q=Delhi&limit=10
```

Searches RailCore's `GET /v1/stations/search` endpoint and returns canonical station code, name, and city values.

### Trains between stations

```http
GET /api/trains/between?from=NDLS&to=HWH&date=2026-10-25
```

Requires distinct station codes and a valid `YYYY-MM-DD` date. The backend calls RailCore's `GET /v1/routes/trains` endpoint. RailCore returns the selected station codes but not their names in this response, so route results display the codes; station suggestions and full schedule stops include names.

### Train schedule

```http
GET /api/trains/12345/schedule
```

Returns the ordered route and schedule from RailCore's `GET /v1/trains/{train_number}/schedule` endpoint.

### Fare and seat availability

The Android app can request `/api/trains/:number/fare` and `/api/trains/:number/availability` on demand for a selected train and class. Fare responses are estimates only and are not a confirmed booking price, reservation, or ticket.

## Optional provider smoke check

The smoke script calls the local RailReserve backend, not RailCore directly. It checks health, stations, route trains, schedule, fare, and availability. Provider calls may consume quota or credits; check your account before running it. Backend unit tests use mocked responses and do not make provider requests.

Configure the non-secret values in `.env.example` or the ignored `.env`: `SMOKE_STATION_QUERY`, `SMOKE_FROM`, `SMOKE_TO`, `SMOKE_TRAIN_NUMBER`, `SMOKE_JOURNEY_DATE`, `SMOKE_CLASS_CODE`, and `SMOKE_QUOTA_CODE`. Then run:

```powershell
npm run smoke:provider
```

The script reports checks as `PASS`, `FAIL`, or `SKIPPED`; incomplete setup is not reported as a pass.

## Errors

Input errors return 400 `INVALID_INPUT`. Missing or rejected provider credentials, rate limiting, not-found data, timeout, and upstream outage remain distinct safe error codes. Upstream response bodies and credentials are not forwarded.

## Deployment

1. Set the service root to `backend`, use Node 24 LTS, and install with `npm ci`.
2. Set the start command to `npm start`.
3. Configure `RAILCORE_API_KEY` with the required endpoint scopes in the host's private environment settings. Keep it out of source, build commands, and Android.
4. Serve the deployed backend over HTTPS and configure health check `/health`.
