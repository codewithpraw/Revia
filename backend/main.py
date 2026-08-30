"""ContextSwitch backend.

Turns a captured interruption (app name + on-screen text + last notification)
into a one-line resumption summary, and keeps a history of them.

Runs with or without an Anthropic API key: without one it falls back to a
template summary so a demo never hard-fails on venue WiFi.
"""

import os
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

DB_PATH = Path(__file__).parent / "contextswitch.db"
MODEL = "claude-opus-5"

app = FastAPI(title="ContextSwitch API", version="1.0")

# The phone is a different origin from the laptop; without this the browser-based
# /docs page and any future web dashboard can't call the API.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------------------
# Wire format - these field names must match the Kotlin @SerializedName values
# in InterruptionEvent.kt / SummaryResponse.kt exactly.
# ---------------------------------------------------------------------------

class InterruptionEvent(BaseModel):
    app_name: str
    clipboard_text: Optional[str] = None
    last_notification: Optional[str] = None
    timestamp: Optional[str] = None


class SummaryResponse(BaseModel):
    id: int
    summary: str


class HistoryItem(BaseModel):
    id: int
    app_name: str
    summary: str
    timestamp: str


class HealthResponse(BaseModel):
    status: str
    summarizer: str = Field(description="'claude' when an API key is present, else 'template'")


# ---------------------------------------------------------------------------
# Storage - stdlib sqlite3 so there is no ORM to install or migrate.
# ---------------------------------------------------------------------------

@contextmanager
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


@app.on_event("startup")
def init_db():
    with get_db() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS interruptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                app_name TEXT NOT NULL,
                context TEXT,
                last_notification TEXT,
                summary TEXT NOT NULL,
                timestamp TEXT NOT NULL
            )
            """
        )


# ---------------------------------------------------------------------------
# Summarization
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """You reconstruct what someone was doing before an interruption.

You get an app name, text that was on screen, and the notification that pulled \
them away. Write ONE sentence, starting with "You were", that would let them \
resume instantly.

Rules:
- Maximum 18 words.
- Describe the task, not the app's UI.
- Use only what you're given; never invent specifics.
- No preamble, no quotes. Output the sentence and nothing else."""


def template_summary(event: InterruptionEvent) -> str:
    """Offline fallback - mirrors the Kotlin client's own fallback wording."""
    preview = (event.clipboard_text or "").strip()
    if not preview:
        return f"You were in {event.app_name}"
    if len(preview) > 60:
        preview = preview[:60].rstrip() + "..."
    return f"You were in {event.app_name} - {preview}"


def claude_summary(event: InterruptionEvent) -> Optional[str]:
    """One-line summary via Claude. Returns None so the caller can fall back."""
    try:
        import anthropic
    except ImportError:
        return None

    if not (os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_AUTH_TOKEN")):
        return None

    user_content = "\n".join(
        [
            f"App: {event.app_name}",
            f"On screen: {event.clipboard_text or '(nothing captured)'}",
            f"Interrupted by: {event.last_notification or '(unknown)'}",
        ]
    )

    try:
        client = anthropic.Anthropic()
        # effort=low because this is a one-sentence rewrite, not a reasoning task -
        # it keeps the round trip short enough for a phone waiting on the response.
        response = client.messages.create(
            model=MODEL,
            max_tokens=2000,
            output_config={"effort": "low"},
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": user_content}],
        )
    except Exception:
        return None

    if response.stop_reason == "refusal":
        return None

    text = "".join(b.text for b in response.content if b.type == "text").strip()
    return text or None


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health", response_model=HealthResponse)
def health():
    has_key = bool(
        os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_AUTH_TOKEN")
    )
    return HealthResponse(status="ok", summarizer="claude" if has_key else "template")


@app.post("/resume", response_model=SummaryResponse)
def resume(event: InterruptionEvent):
    summary = claude_summary(event) or template_summary(event)
    timestamp = event.timestamp or datetime.now(timezone.utc).isoformat()

    with get_db() as conn:
        cursor = conn.execute(
            """
            INSERT INTO interruptions (app_name, context, last_notification, summary, timestamp)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                event.app_name,
                event.clipboard_text,
                event.last_notification,
                summary,
                timestamp,
            ),
        )
        row_id = cursor.lastrowid

    return SummaryResponse(id=row_id, summary=summary)


@app.get("/history", response_model=list[HistoryItem])
def history(limit: int = 20):
    limit = max(1, min(limit, 200))
    with get_db() as conn:
        rows = conn.execute(
            """
            SELECT id, app_name, summary, timestamp
            FROM interruptions
            ORDER BY id DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
    return [HistoryItem(**dict(row)) for row in rows]
