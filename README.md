<img src="REVIA_Logo_Full.png" alt="Revia — Find Your Way Back" width="520">

# Revia

**Passive context recovery for Android.** When something interrupts you, Revia notices, and
when you come back it tells you what you were doing — in a sentence, generated on the phone
itself.

No screen recording. No server. No account. Nothing leaves the device.

---

## What it does

You're reading an article. A message arrives, you reply, you come back. A card appears over
the app you returned to:

<img src="docs/card-over-chrome.png" alt="Revia's resumption card over a Chrome article, reading &quot;You were reading about Calvin C. Newport's background and education.&quot;" width="420">

That sentence is real output from an iQOO 13 — written by Gemini Nano running on the phone's
NPU, from text Revia read off the screen before the interruption. Not "You were in Chrome".
Not a screenshot to squint at.

Tap **Jump back in** and you're back in the app. Ignore it and it disappears on its own.

## Why not just record the screen

Recall and Rewind answer this problem by screenshotting everything you do, continuously —
every message, every banking app, every private thing on your screen — and searching it later.
That is a large privacy cost for a small convenience, and it needs a machine powerful enough
to store and index it all.

Revia takes the opposite approach. It reads three lightweight signals that Android already
exposes, keeps them on the device, and throws them away once they've been used:

| Signal | Source | Used for |
|---|---|---|
| Which app is in front | `UsageStatsManager` | Noticing the interruption and the return |
| Text on screen | Accessibility service | Knowing *what* you were doing |
| Last notification | `NotificationListenerService` | Knowing what pulled you away |

## How it works

```
app switch detected  ─▶  capture screen text  ─▶  store (template summary)
                                                        │
   user returns to that app  ◀───────────────────────────┘
             │
             ▶  card shown over the app  ─▶  Gemini Nano writes the real summary
```

Capture is deliberately cheap — it has to finish before the user comes back, which can be
seconds. The actual sentence is written when the card appears.

Summaries fall back in two tiers, so the app degrades instead of breaking:

1. **Gemini Nano**, on-device — private, free, works offline
2. **A template** — `You were in Notes — [captured text]`

There is deliberately no third, server-backed tier. Revia ships no networking code of its
own — nothing it captures has anywhere to go.

## On-device AI

Summaries run through [ML Kit's GenAI Prompt API](https://developers.google.com/ml-kit/genai/prompt/android/get-started),
backed by Gemini Nano in Android's AICore. Verified working on an **iQOO 13** (Snapdragon 8
Elite, `AICORE_QC_SM8750`), Android 16.

Two findings worth knowing if you build on this:

- **AICore refuses inference for a background process** (`GenAiException ErrorCode 30`).
  Capture happens in a foreground *service*, which is not enough. This is why the card is a
  transparent Activity rather than a window overlay — an Activity is genuinely foreground, so
  the summary can be generated while the card is on screen. A `TYPE_APPLICATION_OVERLAY`
  window does **not** lift the process out of "background".
- **The summarization API is the wrong tool** for this. It wants 400+ characters of prose and
  emits bullet points; our input is a handful of UI labels and we want one sentence. The
  Prompt API takes free-form instructions and fits.

Whether Nano is available is reported in **Settings → On-device AI**, along with the outcome
of recent attempts.

## Build and run

```bash
git clone https://github.com/codewithpraw/Revia.git
cd Revia
./gradlew :app:assembleDebug
```

Or open the folder in Android Studio and run. `local.properties` is generated on first open.

| | |
|---|---|
| Language | Kotlin 2.1.0, Jetpack Compose |
| Min / target SDK | 29 (Android 10) / 34 |
| Build | AGP 8.5.0, KSP 2.1.0-1.0.29 |
| Storage | Room 2.6.1, DataStore |
| On-device AI | ML Kit GenAI Prompt 1.0.0-beta2 |

On first run, open **Settings → On-device AI** and tap **Download on-device model** if it
reports `downloadable`. The download takes a few minutes and is not instant.

## Permissions

All four are granted manually in system settings; none are runtime dialogs.

| Permission | Why | Required? |
|---|---|---|
| Usage access | Detect app switches | **Yes** — nothing works without it |
| Accessibility | Read on-screen text | No, but summaries are vague without it |
| Notification access | Read the interrupting notification | No |
| Draw over other apps | Show the card over the app you return to | No — card still appears inside Revia |

Only usage access blocks onboarding. The system can revoke accessibility at any time, and
losing it shouldn't lock you out of the app.

## Privacy

- **Password fields are never captured** — nodes flagged `isPassword` are skipped.
- **Captured text is consumed when read**, so it can't describe a later, unrelated interruption.
- Data lives in the app's private database. Clear it any time from Settings.
- **Screen content never leaves the phone.** Revia has no networking code and no server to
  talk to; captured text goes to the on-device model or nowhere.
- **The app does hold `INTERNET`, and it is worth being precise about why.** It is not
  declared in Revia's manifest — it arrives through `com.google.mlkit:genai-prompt`, which
  depends on Google's `transport-backend-cct`. ML Kit needs network access to download the
  Gemini Nano model, and that library carries Google's own telemetry. That is a Google
  dependency doing Google things, not a path for anything Revia captures.

This is a hackathon prototype, and honest about it: the local database is not encrypted,
`allowBackup` is still on, and there is no retention limit. Those are the things to fix
before anyone real uses it.

## Project structure

```
app/src/main/kotlin/com/revia/
├── service/      detection (UsageStats), accessibility capture, notifications
├── data/
│   ├── db/       Room entity, DAO, database
│   ├── summary/  Gemini Nano via ML Kit Prompt API
│   └── repository/
├── ui/
│   ├── screen/   onboarding, home, history, settings
│   ├── overlay/  the card shown over other apps
│   └── theme/
└── viewmodel/
```

## Known limitations

- **Summary quality follows what's on screen.** A Wikipedia article reads well; a camera
  viewfinder has almost nothing to work with, and the summary stays thin.
- **Devices without AICore fall back** to the template summary. Gemini Nano is not on every
  phone, and there is no server-side path by design.
- **Aggressive OEM power management** (vivo, OPPO, Xiaomi) can kill the detection service. Set
  Revia to Unrestricted battery and lock it in Recents.
- **vivo and OPPO suppress third-party logcat output**, which is why diagnostics surface in
  Settings rather than the log.
- History filters, swipe-to-delete and the fuller stats bar from the original spec are not
  built.
- There are no automated tests.

## Status

Built for the iQOO Hackathon Battle Ground Hyderbad, productivity track. The full loop —
detect, capture, summarize on-device, display over the app — works end to end on real
hardware.
