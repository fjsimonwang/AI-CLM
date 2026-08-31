# AI-CLM — contributor notes

## Keep the help chat's knowledge current

The floating help chat is grounded **only** in hand-written docs, not the code
itself. Its entire knowledge base is:

- `backend/src/main/java/com/acme/clm/ai/PageDocs.java`
  - `add("/route", "...")` — one functional guide per page (sections, visible
    controls, what they do, permission gates).
  - `features()` — cross-cutting features that live in shared components or span
    many pages (e.g. agent collaboration / "Agent talk", auto-rejection). Always
    included in the prompt.
- The navigation map inside `AiService.helpChat(...)`.

**Every PR that adds or changes a user-facing feature must update the relevant
guide and/or `features()` in the same PR** — new route → new `add(...)` entry
and a nav-map line; new control on an existing page → extend that page's string;
new shared feature → extend `features()`. If the help chat can't answer "how do
I use X?" after your change, the docs are incomplete.

A quick check: run the app, open the help bubble, and ask it about the feature
you just built.

## Build / run

Runs via `docker compose`; backend compiles in the docker build (no local
Maven). Rebuild the changed service after edits:

```bash
docker compose build backend frontend && docker compose up -d backend frontend
```
