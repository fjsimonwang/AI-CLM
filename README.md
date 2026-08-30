# Enterprise CLM Platform

An AI-first Contract Lifecycle Management platform built to the specification in
[`clm-system-plan_1.md`](clm-system-plan_1.md). It runs entirely in Docker.

- **Backend** — Java 21 / Spring Boot 3.3, PostgreSQL 16 + `pgvector`, Flyway, JWT auth
- **Frontend** — React 18 + TypeScript + Vite, TanStack Query, Recharts, design-token layer
- **AI** — provider-agnostic `LlmClient`; OpenAI-compatible Chat Completions client, with a
  deterministic offline mock so every AI surface works with zero configuration

## Run it

```bash
cp .env.example .env
docker compose up --build
```

Then open **http://localhost:3000**.

| Service        | URL                                   |
|----------------|---------------------------------------|
| Frontend       | http://localhost:3000                 |
| Backend        | http://localhost:8081/actuator/health |
| Word editor    | http://localhost:3100 (embedded in each contract's Document tab) |
| Postgres       | localhost:5433 (user/pass from `.env`) |

> The `word-editor` service builds from the sibling project at `../word-editor`. If you moved it,
> update `build.context` for the `word-editor` service in `docker-compose.yml`.

First boot builds both images (a few minutes) and runs all Flyway migrations + demo seed data.

### Demo accounts

All demo users share the password **`demo1234`** (the login screen lists them):

| Email                       | Role                             |
|-----------------------------|----------------------------------|
| `gc@acme.example`           | General Counsel / Admin          |
| `lawyer.emea@acme.example`  | EMEA Counsel / Approver          |
| `finance@acme.example`      | Finance / Approver               |
| `procurement@acme.example`  | Requester                        |
| `sales@acme.example`        | Requester                        |

### Enable a real LLM

Edit `.env` and set an OpenAI-compatible endpoint, then `docker compose up -d --build backend`:

```
LLM_BASE_URL=https://your-endpoint/v1
LLM_API_KEY=sk-...
LLM_MODEL=your-model
LLM_EMBEDDING_MODEL=your-embedding-model   # optional
```

Until then the platform uses the built-in mock — the "AI: mock" pill in the header shows which is active.

## What's implemented

| Plan area | Where |
|---|---|
| Model-driven contract types (JSONB field schema, config not code) | `contract_type_definition`, Admin screen |
| Contract core + hierarchy + amendments + `EffectiveTermsResolver` (§5.3) | `service/EffectiveTermsResolver.java`, contract Terms tab |
| Clause library: concept / variant / playbook tiers / usage-by-variant (§5.4) | Clause library screen |
| Template composition + precedent-aware assembly (§6B.3) | `service/DraftingService.java`, contract Document tab |
| AI-first conversational intake, infer-before-asking, live structured panel (§6, §6A) | New request screen, `service/IntakeService.java` |
| Precedent matching — hybrid structural scoring, recency decay (§6B.2) | `service/PrecedentService.java` |
| Triage scoring → self-service / standard / legal review (§6.3) | `service/TriageService.java` |
| Custom JSON state-machine workflow engine, role assignment, guards, SLA (§7) | `service/WorkflowService.java`, Approvals screen |
| Obligation tracking with visible extraction confidence (§8.2) | Obligations screen |
| Natural-language inquiry — figures from queries, interpreted query shown (§10A) | Inquiry screen, `service/SearchService.java` |
| `AI_INTERACTION` log — model, prompt version, confidence, outcome, one-click revert (§6A.4) | AI activity screen |
| Append-only `AUDIT_EVENT` (DB-trigger enforced) | `db/migration/V3`, every service |
| Shared `AiAffordance` component contract (§6A.2, §16.3) | `frontend/src/components/AiAffordance.tsx` |
| Design tokens, dark mode, tabular figures, restrained motion (§16) | `frontend/src/index.css`, `tailwind.config.js` |

## Also implemented

| Area | Where |
|---|---|
| **Role-based access** — role→permission map, gated menus / routes / API endpoints | `backend/.../config/Permissions.java`, `frontend/src/App.tsx`, `components/Layout.tsx` |
| **Left vertical sidebar** with an inline SVG icon set, collapsible, grouped | `frontend/src/components/Layout.tsx`, `components/icons.tsx` |
| **Deep contract-type schemas** (6 types, grouped fields, enums, money/date/multiline) | `db/migration/V6`, `service/FieldCatalog.java` |
| **Master-data admin** — CRUD for entities, teams, users, parties, contract types, clause concepts/variants, templates, signing authority, assignment rules, workflows | `api/MasterDataController.java`, `pages/Admin.tsx` |
| **Template library** — browsable, previewable, editable | `pages/Templates.tsx` |
| **Contract documents** — every contract gets a document assembled from its template; edited inline via the embedded [word-editor](../word-editor) (view/edit by role + status) | `service/WordEditorClient.java`, `api/DocumentController.java`, `components/DocumentPanel.tsx` |
| **Threaded rich-text discussion** — multiple participants, bold/italic/lists/links, resolve/reopen, sanitized server-side | `api/CommentController.java`, `components/CommentThreads.tsx`, contract Discussion tab + Approvals |

## Notes / scope

This is a working vertical slice of a system the plan scopes at ~15 months. Deliberately simplified
versus the full spec: authn is JWT with seeded users (not Keycloak/OIDC); search is in-process
filtering (not OpenSearch); events are in-process (not Kafka); e-signature dispatch is a workflow
guard rather than a live DocuSign adapter; OCR/migration ingestion is represented by the data model
and confidence surfacing rather than a running Tika/Tesseract pipeline.

The `word-editor` integration required one change to that project: a new `EMBED_ALLOW_ORIGINS`
env var in its `server.js` that swaps the `X-Frame-Options: SAMEORIGIN` lock for a `frame-ancestors`
CSP so the CLM app can embed it.
