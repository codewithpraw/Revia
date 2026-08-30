# ContextSwitch backend

FastAPI service that turns a captured interruption into a one-line resumption summary.

## Run

```bash
cd backend
python3 -m venv .venv
./.venv/bin/pip install -r requirements.txt
./.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

`--host 0.0.0.0` is required: the default (`127.0.0.1`) is only reachable from the
laptop itself, not from a phone on the same WiFi.

Interactive docs: http://127.0.0.1:8000/docs

## Enabling real summaries

Without a key the service returns template summaries ("You were in Notes - ...").
With one, it uses Claude:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

`GET /health` reports which mode is active (`"summarizer": "claude"` or `"template"`).
Restart uvicorn after setting the key.

## Pointing the app at it

| Target | Base URL |
|---|---|
| Emulator | `http://10.0.2.2:8000/` (the app's default) |
| Real phone, same WiFi | `http://<laptop-ip>:8000/` |
| Venue WiFi blocks device-to-device | `ngrok http 8000`, use the https URL |

Find the laptop IP with `ipconfig getifaddr en0`.

Debug builds permit plaintext HTTP via `app/src/debug/res/xml/network_security_config.xml`.
Release builds do not - an `https://` URL (e.g. ngrok) is required there.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness + which summarizer is active |
| POST | `/resume` | Log an interruption, return its summary |
| GET | `/history?limit=20` | Recent interruptions, newest first |

History is stored in `contextswitch.db` (SQLite, created on first run). Delete the
file to reset.
