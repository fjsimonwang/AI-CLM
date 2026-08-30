# Enterprise Contract Lifecycle Management (CLM) Platform — System Plan

**Version:** 2.0 — AI-first revision
**Status:** Design specification for implementation
**Target stack:** Java 21 / Spring Boot 3.x, React 18 + TypeScript, PostgreSQL 16
**Deployment:** Container-based, cloud-portable, on-prem capable (Kubernetes)

> **What changed in 2.0:** AI is repositioned from an assistive side-tool to the primary interaction model across every surface. The traditional form and grid UI remains fully functional as a familiar fallback during transition, but it is no longer the default path. Adds §6A (AI-first interaction model), §6B (precedent-driven prefill and drafting), §10A (conversational inquiry), and §16 (design language and UX standards). Accuracy, transparency, and traceability requirements are strengthened throughout.

---

## 1. Purpose and scope

Build a multi-entity, multi-jurisdiction contract lifecycle management platform that:

1. Manages contracts end-to-end: intake → drafting → negotiation → approval → signature → obligation tracking → renewal/expiry.
2. Absorbs a legacy corpus of existing contracts (hundreds to low thousands of records, mixed digital and scanned) into the same structured model.
3. Provides a versioned, multi-language, multi-jurisdiction clause and template library.
4. **Makes AI the primary way users interact with the system** — not a feature bolted onto forms. Every surface (intake, drafting, review, approval, inquiry, reporting, administration) leads with AI, with a traditional control available beside it.
5. Supports configurable workflows, approval routing, and lawyer assignment rules across multiple legal entities and countries.
6. Integrates with e-signature (DocuSign and others), HR systems, customer/vendor master data, and ERP/finance.
7. Delivers comprehensive reporting, ad-hoc inquiry, and business intelligence.

### Non-goals (explicitly out of scope for v1)

- Replacing the document editor. Word remains the negotiation surface; the platform round-trips documents.
- Building a proprietary e-signature engine.
- Acting as system of record for customer/vendor master data.
- Automated legal advice. AI proposes; humans with authority approve.

---

## 2. Architectural principles

| Principle | Implication |
|---|---|
| Model-driven configuration | Contract types, workflows, forms, rules, and templates are data, not code. Adding a contract type requires no deployment. |
| Event-driven core | State changes publish domain events. Integrations, notifications, AI enrichment, and analytics subscribe. No synchronous cross-module coupling. |
| AI-first, human-accountable | AI is the default interaction path on every surface, not an optional assistant. Every AI output carries a confidence score, source provenance, and a reversible action; AI never holds final approval authority. |
| Nothing unsourced | No AI-produced value reaches a contract without a traceable source — a prior contract, an approved clause variant, a master data record, or an explicit user statement. Ungrounded generation is a defect. |
| Every AI action is reversible and attributed | Each AI-applied change is individually undoable, shows what it changed and why, and is recorded in the audit trail with model, prompt version, and inputs. |
| Two doors, one room | Every capability is reachable both conversationally and through a conventional control. Neither is a degraded version of the other; they operate on the same state. |
| Provider-agnostic AI | LLM, embedding, and OCR access sits behind interfaces. Swapping providers or moving to a self-hosted model must not touch business logic. |
| Immutable audit | Every state change, view of a restricted contract, AI generation, and approval is recorded append-only for legal defensibility. |
| Portable infrastructure | No hard dependency on a single cloud vendor's managed services. Postgres, S3-compatible object storage, Kafka, Kubernetes. |
| Regional data residency | Architecture must permit regional deployment of the data plane with a federated control/search plane. |

---

## 3. Technology stack

### Backend
- **Java 21**, **Spring Boot 3.2+** (Spring Web, Data JPA, Security, Batch, Validation)
- **Spring Modulith** — enforce module boundaries in a modular monolith; permits later extraction to services
- **PostgreSQL 16** — primary datastore; `JSONB` for flexible attributes; **pgvector** extension for embeddings
- **OpenSearch** (or Elasticsearch) — full-text and faceted search over contract text and metadata
- **Apache Kafka** — domain event bus (Redpanda acceptable for smaller on-prem footprint)
- **Redis** — caching, distributed locks, rate limiting
- **MinIO / S3-compatible object storage** — documents, with server-side encryption and versioning
- **Flyway** — database migrations
- **Keycloak** — OIDC identity provider, SAML federation to corporate IdP
- **Camunda 8** *or* a custom state machine — see §7 for the decision framework

### Frontend
- **React 18 + TypeScript**, **Vite**
- **TanStack Query** (server state), **Zustand** (client state)
- **React Hook Form + Zod** — dynamic, schema-driven forms
- **shadcn/ui + Tailwind CSS** — component layer, heavily customized to the design language in §16
- **TipTap** — in-browser document viewing/light redlining
- **Recharts** — dashboards
- **Framer Motion** — restrained transitions only (§16.2)
- **Server-sent events / streaming** — required for conversational surfaces; do not build AI interaction on request-response polling
- **Design tokens as the single source of truth** — color, type scale, spacing, radius, and motion defined once and consumed everywhere. This is what prevents an enterprise app from drifting into visual inconsistency across 40 screens.

### AI / ML
- **Spring AI** as the abstraction layer over LLM providers
- **Anthropic Claude** as the primary model (Opus for complex legal reasoning; Sonnet for extraction and classification at volume)
- **pgvector** for vector storage; embeddings generated per clause, per contract section
- **Apache Tika + Tesseract** (or a managed OCR service) for document parsing and scanned-document OCR
- **LangChain4j** optional, only if orchestration complexity warrants it

### Observability & ops
- OpenTelemetry, Prometheus, Grafana, Loki
- Testcontainers for integration testing
- Kubernetes + Helm; GitHub Actions or GitLab CI

---

## 4. Module structure

Build as a **modular monolith** using Spring Modulith. Each module owns its schema and exposes an API; cross-module communication is by published events or explicit service interfaces. This gives service-oriented discipline without early distributed-systems cost.

```
clm-platform/
├── clm-common/              Shared kernel: IDs, money, audit, error model, security context
├── clm-identity/            Users, roles, permissions, entity scoping, delegation
├── clm-organization/        Legal entities, org hierarchy, signing authority, legal teams
├── clm-contract/            Contract aggregate, hierarchy, versions, terms, parties
├── clm-clause/              Clause library, variants, translations, playbook positions
├── clm-template/            Template composition, assembly engine, merge fields
├── clm-intake/              Intake sessions, dynamic form schemas, triage
├── clm-workflow/            Workflow definitions, instances, tasks, SLA, escalation
├── clm-assignment/          Lawyer assignment rules, workload, conflict checks
├── clm-document/            Storage, versioning, rendering, format conversion
├── clm-signature/           E-signature provider abstraction and adapters
├── clm-obligation/          Obligation extraction, tracking, alerts, renewals
├── clm-ai/                  LLM orchestration, prompts, extraction, RAG, guardrails
├── clm-search/              Indexing, structured query, semantic search, NL query
├── clm-migration/           Legacy ingestion pipeline, review queue, reconciliation
├── clm-integration/         Outbound/inbound connectors, event publishing
├── clm-analytics/           Reporting, dashboards, BI export
└── clm-api/                 REST controllers, GraphQL gateway, auth filters
```

---

## 5. Data model

### 5.1 Organization and identity

```
LEGAL_ENTITY
  id, legal_name, short_name, country_code, registration_number,
  default_governing_law, default_language, parent_entity_id (FK self),
  data_residency_region, legal_team_id, status

SIGNING_AUTHORITY
  id, legal_entity_id, user_id, contract_type (nullable = all),
  max_value_amount, currency, valid_from, valid_to, delegated_from_user_id

LEGAL_TEAM
  id, name, region, default_queue_sla_hours

APP_USER
  id, external_idp_id, email, display_name, default_entity_id,
  department, cost_center, manager_user_id, hr_system_id, status

USER_DELEGATION
  id, from_user_id, to_user_id, scope, valid_from, valid_to
```

Notes: `APP_USER` fields sourced from HR sync are read-only in the app. `SIGNING_AUTHORITY` is enforced as a hard guard before signature dispatch, not as an advisory check.

### 5.2 Contract core

```
CONTRACT
  id, contract_number (human-readable, entity-prefixed),
  contract_type_code, title, status,
  contracting_entity_id (FK LEGAL_ENTITY),
  parent_contract_id (FK self, nullable),
  relationship_type (CHILD_OF | AMENDS | RENEWS | SUPERSEDES | REPLACES),
  template_id (FK, nullable),
  governing_law_code, dispute_resolution_forum,
  primary_language, prevailing_language,
  effective_date, expiry_date, notice_period_days,
  auto_renew, renewal_term_months,
  value_amount, currency, value_basis,
  risk_score, risk_tier,
  source (NATIVE | MIGRATED | THIRD_PARTY_PAPER | IMPORTED),
  confidentiality_level, owner_user_id, assigned_lawyer_id,
  created_at, created_by, updated_at, updated_by

CONTRACT_TYPE_DEFINITION
  code, display_name, category, field_schema (JSONB),
  default_template_id, default_workflow_id,
  requires_legal_review_default, retention_years, is_active

CONTRACT_TERM
  id, contract_id, term_key, term_value (JSONB), term_type,
  is_inherited, source_contract_id, effective_from, effective_to,
  extraction_confidence, verified_by, verified_at

CONTRACT_VERSION
  id, contract_id, version_no, version_label,
  change_summary, is_executed, created_by, created_at

PARTY
  id, legal_name, trading_name, country_code, registration_number,
  party_type (CUSTOMER | VENDOR | PARTNER | EMPLOYEE | OTHER),
  mdm_external_id, mdm_system, mdm_last_synced_at,
  sanctions_check_status, sanctions_checked_at

CONTRACT_PARTY
  contract_id, party_id, role (COUNTERPARTY | GUARANTOR | AFFILIATE),
  signatory_name, signatory_email, signatory_title, notice_address (JSONB)
```

**Critical design points:**

- `type_attributes` — contract-type-specific fields live in a JSONB column on `CONTRACT`, validated at write time against the JSON Schema in `CONTRACT_TYPE_DEFINITION.field_schema`. Index frequently-queried keys with GIN indexes. This is what makes new contract types a configuration action.
- `CONTRACT_TERM` as discrete rows is what makes term inheritance queryable. A liability cap is a row, not a sentence buried in a PDF.
- `PARTY.mdm_external_id` is a **soft reference**, not a foreign key. Contracts must be creatable before MDM onboarding completes, and must survive MDM downtime.
- `relationship_type` + `parent_contract_id` forms the contract hierarchy tree. Enforce max depth (suggest 4) and prevent cycles at the service layer.

### 5.3 Term inheritance resolution

Implement `EffectiveTermsResolver` in `clm-contract`:

```
resolveEffectiveTerms(contractId, asOfDate) -> Map<termKey, ResolvedTerm>
```

Algorithm:
1. Walk up `parent_contract_id` to the root, collecting the ancestor chain.
2. Start with the root's terms where `effective_from <= asOfDate < effective_to`.
3. Descend the chain; a child's own term for the same `term_key` overrides the inherited value.
4. Apply all contracts with `relationship_type = AMENDS` pointing at any node in the chain, ordered by `effective_from`; later amendments override earlier ones.
5. Each `ResolvedTerm` carries `value`, `sourceContractId`, `isInherited`, and `isOverridden` so the UI can show provenance.

Cache results keyed on `(contractId, asOfDate, contractTreeVersion)`; invalidate on any write to the tree.

### 5.4 Clause and template library

```
CLAUSE_CONCEPT
  id, concept_code, name, category, description,
  risk_category, is_core, owning_legal_team_id

CLAUSE_VARIANT
  id, clause_concept_id, version_no,
  jurisdiction_code, language_code,
  body_text, body_rich (structured),
  position_tier (PREFERRED | ACCEPTABLE | FALLBACK | UNACCEPTABLE),
  risk_tier, guidance_notes,
  canonical_source_variant_id (FK self, nullable),
  translation_status (CURRENT | STALE | PENDING_REVIEW | MACHINE_DRAFT),
  approved_by, approved_at, status (DRAFT | ACTIVE | DEPRECATED),
  embedding vector(1536)

CLAUSE_VARIANT_USAGE
  clause_variant_id, contract_id, contract_version_id,
  was_modified, modification_diff

TEMPLATE
  id, name, contract_type_code, legal_entity_id (nullable = global),
  jurisdiction_code, language_code, version_no, status,
  owning_legal_team_id, approved_by, approved_at

TEMPLATE_SECTION
  id, template_id, sort_order, heading, is_optional,
  inclusion_condition (expression), clause_concept_id (nullable),
  default_clause_variant_id, static_body (nullable)

MERGE_FIELD
  id, template_id, field_key, source_path, format_mask, is_required
```

**Design points:**

- Separating `CLAUSE_CONCEPT` from `CLAUSE_VARIANT` is what makes multi-jurisdiction and multi-language work. "Limitation of liability" is one concept with a German-law German-language variant, an English-law English variant, and so on.
- `canonical_source_variant_id` + `translation_status`: when the canonical (usually English) variant is updated, a domain event marks all descendant translations `STALE` and creates review tasks for the owning legal team. Machine translation may pre-draft, but a `MACHINE_DRAFT` variant can never be used in an executed contract without human approval.
- `position_tier` is the negotiation playbook. It drives deviation detection: if a counterparty's proposed clause maps to a concept and the closest match is `FALLBACK` or worse, flag it.
- `CLAUSE_VARIANT_USAGE` answers "which live contracts use the pre-2024 indemnity clause?" — a question that is otherwise near-impossible and is a top driver of legal risk.
- Never hard-delete a clause variant. Deprecate.

### 5.5 Workflow, assignment, obligations

```
WORKFLOW_DEFINITION
  id, name, version_no, status, scope_expression, definition (JSONB), published_at

WORKFLOW_INSTANCE
  id, contract_id, workflow_definition_id, workflow_version_no,
  current_state, started_at, completed_at, sla_due_at, is_escalated

WORKFLOW_TASK
  id, workflow_instance_id, state_key, task_type,
  assigned_role_expression, assigned_user_id, assigned_team_id,
  status, due_at, completed_at, outcome, comments, delegated_from_user_id

ASSIGNMENT_RULE
  id, name, priority, condition_expression (JSONB),
  target_type (USER | TEAM | ROUND_ROBIN | LOAD_BALANCED),
  target_id, fallback_rule_id, is_active

OBLIGATION
  id, contract_id, obligation_type, description,
  due_date, recurrence_rule, owner_user_id, owning_department,
  status, source_clause_variant_id, source_text_location (JSONB),
  extraction_confidence, verified_by, alert_lead_days

AUDIT_EVENT
  id, entity_type, entity_id, action, actor_user_id, actor_type (USER | SYSTEM | AI),
  occurred_at, before_state (JSONB), after_state (JSONB),
  ai_model_id, ai_prompt_hash, correlation_id
```

Notes: `WORKFLOW_INSTANCE` pins `workflow_version_no` — publishing a new workflow definition must never alter in-flight instances. `AUDIT_EVENT` is append-only; enforce with a DB trigger blocking UPDATE and DELETE.

---

## 6. AI intake

### 6.1 Intake session model

```
INTAKE_SESSION
  id, requester_user_id, channel (CHAT | FORM | EMAIL | UPLOAD | API),
  status, contract_type_code (inferred, revisable),
  captured_fields (JSONB), field_provenance (JSONB),
  confidence_scores (JSONB), conversation_history (JSONB),
  triage_result (JSONB), resulting_contract_id
```

`field_provenance` records, per field, whether the value came from `USER_INPUT`, `INFERRED_FROM_CONTEXT`, `PREFILLED_FROM_HR`, `PREFILLED_FROM_MDM`, `EXTRACTED_FROM_DOCUMENT`, or `AI_INFERRED`. This drives the UI's "auto-filled — tap to change" affordance and is required for audit.

### 6.2 Behavioural requirements

1. **Chat and form are one artifact.** The chat is a conversational renderer over the same field schema the form uses. Both write to `captured_fields`. The structured panel is always visible and directly editable.
2. **Infer before asking.** Before the first question, resolve from SSO/HR: requester identity, entity, department, cost center, manager. If a document or email thread was supplied, extract counterparty, type, term, and value first. Target: a routine NDA completes in ≤3 questions.
3. **Business language in, legal language out.** Present questions in operational terms ("Are you sharing customer data?"); store normalized legal attributes (`data_processing = true`, triggering the DPA sub-flow and GDPR review flag).
4. **Counterparty resolution inline.** Type-ahead against `PARTY` + MDM. Existing counterparty prefills legal name, address, and surfaces prior contracts and previously negotiated positions. New counterparty triggers onboarding flag and sanctions screening before drafting proceeds.
5. **Transparent triage.** Show the routing decision and its reason ("Value above €500k and non-standard governing law → legal review, est. 3 business days") before submission.
6. **Third-party paper is a distinct flow.** Detect from the first message or an upload. That path is: parse → map clauses to concepts → score deviations against playbook → propose fallback language. Do not force it through the "new contract" question tree.
7. **Guardrails on autonomy.** Fully automated generate-and-send is permitted only when: contract type is on the allowlist, value below a configured threshold, zero clause deviations, all extraction confidences above threshold, and counterparty screening is clear. Every auto-issued contract is visible in a legal review queue.

### 6.3 Triage scoring

Compute a composite score from: contract value band, governing law (standard vs. non-standard for the entity), counterparty risk (new, sanctioned-adjacent, prior disputes), data processing involvement, clause deviation count and severity, contract type base risk, and term length. Score maps to one of three paths — self-service, standard approval, legal review — with thresholds configurable per entity.

---

## 6A. AI-first interaction model

### 6A.1 The principle

The system is not a form application with an AI helper attached. It is an AI system that renders forms when structure helps. Users should be able to complete any task by describing what they want, and the traditional controls exist so that people who prefer them — or who need precision on a specific field — are never blocked.

Build both paths against the same domain services. The conversational path and the form path must be functionally equivalent: anything achievable in one is achievable in the other, operating on identical state.

### 6A.2 AI presence on every surface

| Surface | AI-first behaviour | Traditional fallback retained |
|---|---|---|
| Intake | Conversation captures the request; the form fills itself as the conversation proceeds | Full form, directly editable at any moment |
| Field level | Every field offers inline suggestion with a source ("From your Nov 2025 Meridian MSA") | Manual entry, dropdown, date picker |
| Drafting | Document assembled from precedent and approved clauses, with each choice explained | Manual template selection and clause insertion |
| Review | Deviations surfaced and ranked, with proposed fallback language | Manual document comparison |
| Approval | Briefing generated: what changed, what's non-standard, what's being asked | Full document and redline access |
| Search / inquiry | Natural language question answered with cited contracts | Faceted filters and saved queries |
| Reporting | "Show me expiring EMEA vendor contracts by value" builds the report | Report builder and dashboard library |
| Admin | Describe a workflow or rule in prose; AI drafts the configuration for review | Visual workflow and rule editors |
| Obligations | Extracted, classified, and owner-suggested automatically | Manual obligation entry |

**Implementation requirement:** a shared `AiAffordance` component contract on the frontend. Any field, section, or view can declare AI capability, and it renders consistently: a suggestion chip, a source attribution, an accept/edit/dismiss control, and an explanation on demand. Do not implement AI presentation ad hoc per screen — inconsistency here destroys trust faster than inaccuracy does.

### 6A.3 The transition posture

The traditional UI is not deprecated and is not a second-class citizen. It is deliberately maintained because:

- Legal professionals reviewing a €50m agreement need direct, deterministic control.
- Regulatory and audit review may require demonstrating the system works without AI.
- User trust is earned incrementally; forcing conversational-only interaction on day one drives users back to email.

Track adoption per surface (conversational vs. traditional completion rate) as a product metric. Move defaults toward AI as measured accuracy and user trust justify it, per surface, rather than in a single switchover.

### 6A.4 Accuracy, transparency, traceability — enforced, not aspirational

**Accuracy**
- Every extraction and suggestion returns a confidence score; below-threshold values are never silently applied.
- Structured output validated against JSON schema; a parse failure is an error, never a best-effort guess.
- Evaluation harness (see §9.2) gates every prompt and model change. Accuracy regressions block release.
- Numeric, date, and monetary fields are re-validated by deterministic parsers after AI extraction. Never trust an LLM's arithmetic or date normalization without verification.
- Confidence thresholds are configurable per field and per contract type. A liability cap warrants a higher bar than a document title.

**Transparency**
- Every AI-supplied value displays its source inline. "Governing law: Germany" carries "inferred from Acme GmbH entity default" — always, not on hover only.
- Show the reasoning where the decision is consequential: routing decisions, risk scores, and deviation flags state their basis in plain language.
- Confidence is rendered visually, not just stored. Verified, high-confidence, and unverified values are visually distinct everywhere they appear — including in exports and alerts.
- Natural language queries display the interpreted structured query before executing. Silent misinterpretation is worse than no answer.

**Traceability**
- `AI_INTERACTION` table records every AI call: contract, user, surface, model ID, prompt ID and version, input hash, output, confidence, latency, cost, and whether the output was accepted, edited, or rejected.
- Accept/edit/reject telemetry is the primary quality signal. An edited suggestion tells you more than any offline benchmark.
- Field-level provenance persists for the life of the record. Three years later, "why does this contract say 24 months?" must be answerable.
- Every AI-applied change is individually reversible with one action, and the reversal is itself audited.

```
AI_INTERACTION
  id, contract_id, intake_session_id, user_id, surface, capability,
  model_id, prompt_id, prompt_version, input_hash, input_summary (JSONB),
  output (JSONB), confidence_score, source_references (JSONB),
  outcome (ACCEPTED | EDITED | REJECTED | AUTO_APPLIED | PENDING),
  edited_delta (JSONB), latency_ms, token_cost, occurred_at,
  correlation_id, reverted_at, reverted_by
```

---

## 6B. Precedent-driven prefill and drafting

The highest-value AI capability in the system: users should rarely start from a blank contract. Most contracts a company signs resemble one it has signed before.

### 6B.1 Behaviour

When a user begins an intake, the system searches the existing corpus for relevant precedents and offers them proactively:

> *"You've signed 4 NDAs with logistics vendors in Germany. Your Meridian NDA from Nov 2025 is the closest match — mutual, 3-year term, German law, with the standard DPA annex. Use it as the basis?"*
>
> `Use this one` · `Compare all 4` · `Start from standard template`

On acceptance, the system prefills every field the precedent can supply, marks each as `PREFILLED_FROM_PRECEDENT` with a link to the source contract, and — critically — **carries the precedent through to drafting**. The generated document uses the precedent's clause variants and negotiated positions, not the generic template default. Differences are shown as a reviewable diff, not silently applied.

### 6B.2 Precedent matching

Candidates are ranked on a weighted composite:

| Signal | Weight consideration |
|---|---|
| Same counterparty | Highest — prior negotiated position with this party is the strongest precedent |
| Same counterparty group / parent | High |
| Same contract type | High |
| Same contracting entity and jurisdiction | High |
| Similar counterparty industry and size | Medium |
| Semantic similarity of business purpose | Medium |
| Recency | Medium — decay older precedents |
| Executed without dispute or amendment | Medium — prefer clean precedents |
| Same requesting department | Low |

Retrieve via a hybrid of structured filtering and vector similarity over contract-level embeddings, then rerank. Always present at least the reason for the top match; the user must understand why this precedent was chosen.

### 6B.3 Precedent-aware drafting

Rather than assembling from template defaults, the drafting service:

1. Loads the precedent's clause variant set and any recorded negotiated deviations.
2. Loads the current approved template for the contract type, entity, jurisdiction, and language.
3. Diffs them, and for each difference decides: carry forward the precedent position, or apply the current standard.
4. **Flags every carried-forward deviation for explicit user or legal acknowledgement.** A precedent may contain a concession that was acceptable in 2025 but is no longer within playbook — carrying it forward silently is a serious risk.
5. Produces the draft with a change summary: what came from precedent, what came from the current template, and what needs a decision.

**Guardrails.** Deprecated clause variants are never carried forward — they are replaced with the current approved variant and the substitution is highlighted. Deviations at `UNACCEPTABLE` tier block automated drafting and route to legal. Precedents older than a configurable age trigger a staleness warning. Precedents from a different governing law are never used for clause text, only for commercial terms.

### 6B.4 Data model additions

```
PRECEDENT_LINK
  id, contract_id, precedent_contract_id, match_score,
  match_reasons (JSONB), used_for (PREFILL | DRAFTING | BOTH),
  fields_inherited (JSONB), clauses_inherited (JSONB),
  deviations_carried (JSONB), acknowledged_by, acknowledged_at

CONTRACT_EMBEDDING
  contract_id, embedding_type (SUMMARY | COMMERCIAL_TERMS | FULL_TEXT),
  embedding vector(1536), generated_at, model_id
```

`PRECEDENT_LINK` is not merely provenance — it enables portfolio-level questions such as "which contracts inherited the liability position we no longer accept?"

---

## 7. Workflow engine

### 7.1 Build vs. embed decision

Evaluate Camunda 8 against a custom state machine early. **Recommendation: custom state machine using Spring StateMachine or a hand-rolled interpreter over a JSON definition.** Rationale: CLM workflows are approval routing, not complex BPMN orchestration; the operational weight and licensing of Camunda 8 is disproportionate; and workflow definitions need to be authored by legal ops in a domain-specific UI, not BPMN modeler. Revisit if requirements grow to include long-running compensating transactions or cross-system sagas.

### 7.2 Definition schema (JSONB)

```json
{
  "key": "vendor-msa-emea",
  "version": 3,
  "scope": { "contractTypes": ["MSA"], "entities": ["ACME_GMBH", "ACME_SAS"] },
  "states": [
    {
      "key": "legal_review",
      "type": "task",
      "assignment": { "ruleSet": "legal-assignment-emea" },
      "slaHours": 48,
      "escalation": { "afterHours": 48, "to": "role:legal_manager" },
      "transitions": [
        { "on": "approve", "to": "finance_review" },
        { "on": "request_changes", "to": "drafting" },
        { "on": "reject", "to": "closed_rejected" }
      ]
    },
    {
      "key": "finance_review",
      "type": "parallel",
      "branches": ["finance_approval", "security_review"],
      "joinPolicy": { "type": "quorum", "required": 2 }
    },
    {
      "key": "signature",
      "type": "task",
      "guards": ["signing_authority_valid", "no_open_deviations"],
      "action": "dispatch_esignature"
    }
  ]
}
```

### 7.3 Engine requirements

- **Assignment resolves to roles, not people.** A step targets "Finance approver for {contractingEntity} above {value}"; `AssignmentResolver` maps it to a user at runtime via `SIGNING_AUTHORITY`, org hierarchy from HR, and active `USER_DELEGATION`. Reorgs must not break running workflows or stored definitions.
- **Parallel by default.** Legal, finance, and security review concurrently. Support quorum joins and conditional steps that auto-skip when their condition is unmet.
- **Guards block transitions.** `signing_authority_valid` is evaluated before signature dispatch and blocks the transition on failure. Never surface an authority problem after signature.
- **SLA timers with escalation.** Scheduled job scans overdue tasks; escalates per definition; supports reassignment on OOO from HR calendar.
- **Version pinning.** Instances execute against the definition version active at start.
- **Every transition emits a domain event and an audit record.**

### 7.4 AI assistance in workflow

- Approver briefing: a generated summary listing deviations from playbook, changes since the last version reviewed, and the specific decisions being asked for.
- Cycle time prediction from historical instances of the same definition.
- Bottleneck detection surfaced in the legal ops dashboard.
- Reviewer suggestion based on similarity to past contracts that reviewer handled.

---

## 8. Legacy data migration

The hardest part of the programme and the most common cause of CLM project failure. Treat it as a first-class product surface, not a one-off script.

### 8.1 Pipeline

```
Discovery → Ingest → Parse/OCR → Classify → Extract → Score → Review queue → Promote → Reconcile
```

| Stage | Detail |
|---|---|
| Discovery | Inventory sources: shared drives, SharePoint, email archives, filing cabinets. Deduplicate by content hash. Record source path for traceability. |
| Ingest | Load into object storage with immutable original preserved. Never modify the source artifact. |
| Parse/OCR | Tika for digital text; Tesseract or managed OCR for scans. Record OCR confidence per page; flag pages below threshold for manual handling. |
| Classify | LLM classifies contract type, identifies contracting entity and counterparty, detects language and governing law. |
| Extract | Structured extraction of the field set defined by the contract type, plus obligations and key dates. Every field returns a value **and** a confidence score **and** a source text span. |
| Score | Composite record confidence. Route: high → auto-promote with spot check; medium → review queue; low → full manual entry with AI pre-fill. |
| Review queue | A purpose-built UI: extracted field beside the highlighted source text span in the document, accept/correct per field, keyboard-driven. Target under 3 minutes per medium-confidence contract. |
| Promote | Create `CONTRACT` with `source = MIGRATED`, terms with `extraction_confidence` and `verified_by` populated, link document versions. |
| Reconcile | Compare counts and total contract value against source-system reports. Produce a signed-off reconciliation record. |

### 8.2 Requirements

- **Migrated records are structurally identical to native ones.** Same tables, same model. Only `source`, `extraction_confidence`, and `verified_by` differ. No second-class shadow schema.
- **Confidence is visible in the UI.** An unverified extracted expiry date must be visually distinct from a verified one, everywhere it appears — especially in renewal alerts.
- **Idempotent and resumable.** Content-hash keyed; re-running the pipeline must not create duplicates.
- **Batch size assumption:** thousands of records. Design for throughput but do not over-engineer for millions.
- **Prioritize by value.** Migrate active, high-value, and near-renewal contracts first. Expired low-value contracts can be bulk-loaded with minimal extraction.
- Build the review UI before running bulk extraction. Extraction without a good review tool produces an unusable corpus.

---

## 9. AI services layer

### 9.1 Capabilities

| Capability | Description | Model tier |
|---|---|---|
| Intake conversation | Conversational field capture, clarification, business-to-legal translation | Mid |
| Classification | Contract type, jurisdiction, language, counterparty identification | Fast |
| Metadata extraction | Structured fields with confidence + source spans | Mid |
| Obligation extraction | Deliverables, dates, payment terms, notice requirements | Mid |
| Clause matching | Map arbitrary text to `CLAUSE_CONCEPT` via embeddings + reranking | Embedding + fast |
| Deviation analysis | Compare proposed clause to playbook tiers, explain the delta and its risk | High |
| Drafting assistance | Generate clause language grounded in approved variants | High |
| Redline suggestion | Propose counter-language from the fallback ladder | High |
| Summarization | Contract summary, approver briefing, change-since-last-version | Mid |
| Natural language search | Translate a question to structured query + semantic retrieval | Mid |
| Translation drafting | Pre-draft clause translations for human legal review | High |

### 9.2 Architecture requirements

- All AI access through `clm-ai` interfaces (`LlmClient`, `EmbeddingClient`, `DocumentParser`). No module calls a provider SDK directly.
- **Prompts are versioned resources** in the repository, not inline strings. Each prompt has an ID and version; `AUDIT_EVENT.ai_prompt_hash` records which was used.
- **Structured output enforced.** Use JSON schema constraints and validate; never regex-parse prose.
- **Grounding over generation.** Drafting and redlining retrieve approved clause variants via RAG and are instructed to prefer them. Generating novel legal language unprompted is a failure mode, not a feature.
- **Confidence and provenance are mandatory outputs.** Any extracted field without a source span is treated as low confidence.
- **Cost and latency controls:** cache embeddings, batch extraction jobs, route by task complexity, enforce per-tenant token budgets.
- **PII and confidentiality:** classify contracts by confidentiality level; restricted contracts may be excluded from external model calls entirely, with a configurable policy per legal entity and region. Log every AI call against a contract in the audit trail.
- **Evaluation harness from day one.** A labelled corpus of ~200 contracts with human-verified extractions; run extraction accuracy, clause-matching precision/recall, and deviation detection F1 on every prompt or model change. Without this, prompt changes are unmeasurable.

---

## 10. Search, reporting, and BI

### 10.1 Search

Three complementary paths, unified in one interface:

1. **Structured filter** — faceted query over `CONTRACT` and `CONTRACT_TERM` (entity, type, status, counterparty, value band, expiry window, governing law, assigned lawyer). Backed by OpenSearch.
2. **Full-text** — over contract body text, with highlighted snippets.
3. **Semantic / natural language** — "which EMEA vendor contracts have liability caps below 1x fees and expire this year?" An LLM translates to a structured query plus optional semantic retrieval, shows the interpreted query to the user for confirmation, and executes. Always display the translated query — silent misinterpretation is worse than no answer.

### 10.2 Reporting and dashboards

Standard dashboards: contract portfolio by entity/type/status, expiry and renewal pipeline (30/60/90/180-day), obligation compliance, cycle time by workflow stage and by lawyer, deviation frequency by clause concept, legal team workload, value at risk by counterparty, migration progress and confidence distribution.

**Ad-hoc inquiry:** a saved-query builder over a curated semantic layer, exportable to Excel and CSV, with scheduled email delivery.

**BI export:** publish a star schema to a warehouse (fact tables: contract lifecycle events, obligations, workflow tasks; dimensions: entity, party, contract type, clause, user, time) refreshed from the event stream. This keeps heavy analytical queries off the transactional database and lets the enterprise BI tool consume CLM data natively.

---

## 10A. Conversational inquiry and reporting

Inquiry is where non-legal users most often give up on CLM systems. Faceted filters require knowing the schema; report builders require knowing what's possible. The AI-first answer is that users ask questions.

### 10A.1 Interaction pattern

1. User asks in plain language: *"What are we spending with logistics vendors in Europe, and what's up for renewal before year end?"*
2. System shows its interpretation — the structured query, the filters applied, the scope — in readable form, before executing.
3. Result renders in the most appropriate form automatically: a number, a table, a chart, a map, or a list of contracts. The system chooses; the user can override.
4. Every figure is traceable: click any number to see the contributing contracts.
5. Follow-ups maintain context: *"Now just the ones with auto-renewal"* refines rather than restarts.
6. Any result can be saved as a live report, scheduled for delivery, or exported.

### 10A.2 Requirements

- **Never answer from generation.** Every figure is computed from a database query. The LLM's role is translating intent to query and result to prose — never producing numbers itself. This is the single most important safeguard in the reporting layer.
- **Show the query.** Always. In readable form for business users, with a "view technical query" affordance for analysts.
- **Refuse gracefully.** If the question can't be mapped to available data, say so and suggest what can be answered. Never approximate silently.
- **Respect authorization in the query, not the response.** Filters are applied at the data layer against the user's entity and confidentiality scope. Never retrieve then redact.
- **Suggest proactively.** Surface what the user probably wants to know before they ask: expiries approaching, obligations overdue, unusual deviations, concentration risk.

### 10A.3 Semantic layer

Build a curated semantic model between natural language and the database: named business metrics (contract value, annualized value, spend to date, days to expiry), named dimensions with synonyms ("counterparty", "vendor", "supplier", "the other side" → `PARTY`), and defined relationships. The LLM generates against this layer, not raw SQL. This bounds the failure surface, makes queries verifiable, and lets non-technical users read the interpreted query.

---

## 11. Integrations

### 11.1 Pattern

An anti-corruption layer per external system. Inbound data is mapped to the internal model at the boundary; no external schema leaks into the core. Outbound calls go through a `ConnectorRegistry` with retry, circuit breaker, and dead-letter queue. All integrations are configurable per legal entity — different regions frequently use different HR or ERP instances.

### 11.2 E-signature

`ESignatureProvider` interface with a DocuSign adapter first; Adobe Sign and a regional provider adapter to follow. Interface covers: create envelope, add signers with routing order, place signature/date/initial tabs, send, poll or receive webhook status, retrieve executed document with certificate of completion, void envelope.

Requirements: store `esign_envelope_id` on `DOCUMENT_FILE`; verify webhook signatures; reconcile daily against provider status for missed webhooks; store the completion certificate as an immutable artifact; validate signing authority as a workflow guard before dispatch.

### 11.3 HR system

Inbound sync (SCIM where available, otherwise scheduled batch): user identity, department, cost center, manager hierarchy, employment status, out-of-office periods. Drives approval routing, delegation, and automatic deactivation. Outbound event: employment contract executed → notify HR.

### 11.4 Customer / vendor master

Inbound sync of party records keyed on `mdm_external_id`. On counterparty creation in CLM without an MDM match, publish a `PartyOnboardingRequested` event. Handle the case where MDM merges two records: `PARTY` needs a `merged_into_party_id` field and a reconciliation job.

### 11.5 ERP / finance

Outbound: executed contract with commercial terms → create purchase agreement or revenue contract. Inbound: actual spend or invoice data against a contract, to power spend-versus-commitment reporting.

### 11.6 Collaboration

Microsoft Teams / Slack: approval notifications with inline approve/reject actions, intake initiation, expiry alerts. Email: intake by forwarding to a monitored mailbox; approval by reply is explicitly out of scope for v1 for auditability reasons.

### 11.7 Outbound event catalogue

`ContractCreated`, `ContractSubmitted`, `ContractApproved`, `ContractExecuted`, `ContractAmended`, `ContractExpiring`, `ContractRenewed`, `ContractTerminated`, `ObligationDue`, `ObligationBreached`, `WorkflowTaskAssigned`, `WorkflowEscalated`, `ClauseVariantDeprecated`, `PartyOnboardingRequested`.

---

## 12. Security, compliance, data residency

- **AuthN:** OIDC via corporate IdP, SAML federation, MFA enforced for legal and signing roles.
- **AuthZ:** RBAC plus attribute-based scoping. Permissions evaluated against legal entity, department, confidentiality level, and explicit contract ACL. A user in the German entity must not see Brazilian HR contracts by default.
- **Confidentiality tiers:** Public / Internal / Confidential / Restricted. Restricted contracts require explicit named access, log every view, and may be excluded from AI processing by policy.
- **Encryption:** TLS in transit; AES-256 at rest; encrypted object storage; consider field-level encryption for compensation data in employment contracts.
- **Audit:** append-only `AUDIT_EVENT`, DB-trigger-enforced. Retained per the longest applicable retention rule.
- **Data residency:** design the data plane (Postgres, object storage, search index) to be deployable per region, with a control plane and federated search layer above. Even if v1 deploys a single global instance, do not make assumptions that block regional splitting — particularly, avoid cross-region foreign keys and design search as federatable. **This is the single most expensive thing to retrofit.**
- **Retention and disposal:** per contract type and jurisdiction, from `CONTRACT_TYPE_DEFINITION.retention_years`. Legal hold flag overrides all disposal.
- **GDPR:** data subject access and erasure workflows for personal data in employment and individual contracts, bounded by legal retention obligations.

---

## 13. Implementation phases

### Phase 0 — Design system and AI foundation (weeks 1–6, parallel with Phase 1)
Design tokens, component library, the `AiAffordance` contract, streaming infrastructure, `AI_INTERACTION` logging, prompt versioning, and the evaluation harness skeleton. **Exit criterion:** any team can build a screen that presents AI suggestions consistently, and every AI call is logged and reversible by construction. Doing this first is what prevents forty screens of inconsistent AI treatment.

### Phase 1 — Foundation (months 1–3)
Core domain model, legal entity and identity, contract CRUD, document storage and versioning, basic search, audit infrastructure, authentication and authorization, admin UI shell. **Exit criterion:** a contract can be created, stored, versioned, retrieved, and every action is audited.

### Phase 2 — Library and templates (months 3–5)
Clause concepts and variants, jurisdiction and language variants, translation sync workflow, template composition and assembly, document generation to Word/PDF, merge fields. **Exit criterion:** legal can author a template and generate a correct contract document from structured data.

### Phase 3 — Intake, precedent, and workflow (months 5–8)
Dynamic form schemas, AI conversational intake, **precedent matching and prefill (§6B)**, **precedent-aware drafting**, triage engine, workflow definition model and engine, assignment rules, task UI, SLA and escalation, notifications. **Exit criterion:** a non-legal user completes an NDA request conversationally in under 90 seconds, the draft is generated from an identified precedent with deviations flagged, and it flows through approval without manual intervention.

Note: precedent capability depends on having a corpus. Sequence a first migration slice (Phase 5) ahead of this, or seed with a representative sample, so precedent matching can be built and evaluated against real data.

### Phase 4 — Signature and integration (months 7–9)
E-signature abstraction and DocuSign adapter, signing authority guards, HR sync, MDM sync, event publishing, Teams/Slack notifications. **Exit criterion:** a contract is executed and countersigned entirely within the platform.

### Phase 5 — Migration (months 8–11, overlaps)
Ingestion pipeline, OCR, extraction models, confidence scoring, review queue UI, bulk promotion, reconciliation reporting. **Exit criterion:** the legacy corpus is loaded, reconciled, and signed off.

### Phase 6 — AI depth (months 10–13)
Clause matching and deviation detection, playbook enforcement, drafting and redline assistance, obligation extraction, natural language search, approver briefings, evaluation harness. **Exit criterion:** measured extraction and deviation-detection accuracy meets agreed thresholds on the eval corpus.

### Phase 7 — Conversational inquiry, analytics, and scale (months 12–15)
Semantic layer (§10A.3), conversational inquiry, proactive insight surfacing, warehouse star schema, BI export, executive dashboards, ad-hoc query builder, performance tuning, regional deployment if required.

Note: phases overlap. Build the migration review UI (Phase 5) early enough to validate the extraction approach before committing to bulk processing.

---

## 14. Development guidance for implementation

1. **Start with the domain model and the audit trail.** Everything else depends on them and both are painful to change later.
2. **Build the configuration UI alongside each configurable feature.** A workflow engine without a workflow authoring UI will be configured by developers forever, which defeats its purpose.
3. **Test with real contract documents from the outset.** Synthetic contracts hide the parsing, formatting, and language problems that dominate real effort.
4. **Testcontainers for integration tests** against real Postgres, Kafka, and OpenSearch. Mocked infrastructure hides the failures that matter.
5. **Contract tests for every integration adapter,** so provider changes are caught in CI.
6. **Feature-flag AI capabilities individually.** Ship the deterministic path first; enable AI assistance on top of a working manual flow.
7. **Instrument cycle time from day one.** It is the primary success metric and cannot be measured retrospectively.
8. **Seed data matters.** Provide a realistic seed set: several legal entities across jurisdictions, a populated clause library with variants and translations, templates, workflow definitions, and sample contracts including a hierarchy with amendments. Development against an empty database produces a system that only works when empty.
9. **Build the design system and `AiAffordance` contract before the second screen.** Retrofitting consistent AI presentation across an existing app is far more expensive than establishing it once.
10. **Build the deterministic path first, then lead with AI on top of it.** The AI-first posture does not mean AI-only implementation. Every conversational capability must have a working traditional path underneath it — that path is both the fallback and the correctness reference.
11. **Instrument accept/edit/reject on every AI suggestion from the first one shipped.** This telemetry is the primary quality signal and cannot be reconstructed later.
12. **Never let an LLM produce a number that reaches a user.** Figures come from queries; the model translates intent and phrases results. Violating this in the reporting layer will eventually produce a confidently wrong figure in front of an executive, and the credibility loss is not recoverable.

---

## 16. Design language and UX standards

The interface must be simple enough that a sales manager completes an NDA request unaided, and capable enough that a general counsel manages a global portfolio worth billions. These are not in tension if the complexity is progressive rather than upfront.

### 16.1 Design principles

**Restraint.** One primary action per screen. Generous whitespace. A limited type scale. Most enterprise software fails aesthetically by showing everything at once; the discipline is deciding what not to show.

**Progressive disclosure.** The default view answers the common case. Depth is one interaction away, never zero and never five. A contract page opens on a summary a non-lawyer understands; the full clause-level detail, version history, and audit trail are present but not competing for attention.

**Content over chrome.** Contract text, values, and dates are the subject. Navigation, toolbars, and panels recede. Avoid dense toolbars, nested tabs, and permanent sidebars full of icons.

**Confidence through clarity.** A user handling a nine-figure agreement must feel the system is precise. That comes from consistent alignment, correct typographic hierarchy, exact number formatting, and no visual noise — not from decoration.

**Speed as a design feature.** Sub-100ms interaction feedback. Optimistic updates. Skeleton states that match final layout. Perceived performance is a large share of perceived quality.

### 16.2 Visual specification

| Element | Standard |
|---|---|
| Type scale | One family, 5–6 sizes maximum. A refined sans (Inter, SF Pro, or similar) for UI; consider a serif for contract body text to signal document context. |
| Weights | Two weights: regular and medium. Avoid bold and light. |
| Case | Sentence case throughout. Never all-caps labels. |
| Color | Near-monochrome base. One accent color used sparingly for primary action and active state. Semantic color reserved exclusively for status — risk, deviation, overdue. |
| Density | Comfortable default; a compact mode for legal power users working portfolio grids. |
| Borders | Hairline (0.5–1px), low contrast. Prefer whitespace and alignment over rules and boxes. |
| Elevation | Maximum two levels. Subtle shadows only where floating is meaningful. |
| Corner radius | Consistent and modest. One value for controls, one for cards. |
| Motion | 150–250ms, ease-out. Motion clarifies state change and spatial relationship only. No decorative animation. |
| Numbers | Tabular figures for all monetary and numeric columns. Locale-correct currency and date formatting per user, not per server. |
| Dark mode | First-class, not an inversion. Lawyers read long documents at night. |
| Accessibility | WCAG 2.1 AA minimum. Full keyboard operability — legal reviewers work at speed and will not use a mouse for repetitive review. |

### 16.3 AI presentation standards

Consistency in how AI appears matters more than any individual feature:

- **A single visual language for AI-supplied content** — one consistent treatment for suggestion, source attribution, and confidence, used identically everywhere.
- **AI suggestions are visually distinct from user-entered values** until accepted, then become normal content with retained provenance.
- **Never a spinner without context.** State what the system is doing: "Reviewing 4 similar contracts" rather than a loading indicator.
- **Streaming responses** for anything over one second.
- **Accept, edit, dismiss — always all three,** always in the same position, always one interaction each.
- **Explanation on demand, never forced.** A quiet "why?" affordance beside each suggestion.
- **Errors are honest.** "I couldn't find a governing law clause in this document" is better than a confident wrong answer or a generic failure.

### 16.4 Surface-specific requirements

**Intake** — feels like a conversation, not a form. Opens with a single input, no field grid. The structured summary builds alongside and is always editable. Quick-reply options wherever answers are enumerable. Progress and expected routing visible before submission. Target: routine NDA in under 90 seconds, three questions or fewer.

**Contract view** — opens on a plain-language summary: parties, what it's for, key dates, key numbers, current status, what's needed next. Clause detail, versions, obligations, and audit trail are tabs or sections below, not a competing wall of panels. A persistent "ask about this contract" affordance.

**Portfolio / inquiry** — a single question input as the primary interface, with saved views and filters available beside it. Results render in the appropriate visualization automatically. Every figure drills through to source contracts.

**Approval** — the briefing is the screen: what changed, what's non-standard, what decision is required. Full document one interaction away. Approve, reject, and request-changes are unambiguous and equally weighted. Must be fully usable on mobile — approvers are frequently travelling.

**Legal workbench** — the exception to the restraint rule. Legal professionals reviewing complex agreements benefit from density: side-by-side comparison, clause-level navigation, deviation list, playbook reference, and precedent lookup simultaneously. Build this as a distinct compact-density workspace rather than compromising the general UI toward density.

---

## 15. Open decisions

| Decision | Why it matters | Recommended default |
|---|---|---|
| Data residency: single global instance vs. regional | Most expensive retrofit in the system | Single instance, regional-ready architecture |
| Workflow engine: Camunda vs. custom | Operational weight and authoring UX | Custom state machine over JSON definitions |
| LLM hosting: API vs. self-hosted | Confidentiality policy, cost, latency | API with per-entity policy to exclude Restricted contracts |
| Negotiation surface: Word round-trip vs. browser editing | Lawyer adoption depends heavily on this | Word round-trip primary, browser viewing/light editing secondary |
| Bilingual contracts: parallel columns vs. separate documents | Affects document generation architecture | Configurable per entity; support both |
| Migration depth: full extraction vs. metadata-only for inactive contracts | Cost and timeline | Tiered by contract value and activity status |
| Default surface: conversational vs. traditional per screen | Adoption risk vs. transition speed | Traditional default at launch, per-surface switch as measured accuracy and trust justify |
| Precedent staleness threshold | Risk of carrying forward outdated concessions | 18 months, configurable per contract type |
| Auto-apply confidence threshold per field | Balance of speed against error rate | Start conservative; tune from accept/edit/reject telemetry, never from intuition |
| Design system: build vs. extend shadcn | Time to a distinctive, coherent interface | Extend shadcn with a custom token layer; do not adopt defaults unmodified |
