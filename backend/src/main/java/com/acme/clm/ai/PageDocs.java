package com.acme.clm.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-route functional guides for the help assistant, compiled from each page's
 * actual implementation: sections, visible controls, what they do, AI features
 * and permission gating. {@code forPage} picks the guide whose route pattern
 * matches the user's current path (longest prefix wins).
 */
public final class PageDocs {

    private final Map<String, String> pages = new LinkedHashMap<>();

    private void add(String pattern, String guide) {
        pages.put(pattern, guide);
    }

    /** Matches by exact path or prefix, longest first. Query strings are ignored (but see tab notes). */
    public String forPage(String page) {
        String path = page == null ? "" : page.trim().split("\\?", 2)[0];
        if (!path.startsWith("/")) path = "/" + path;
        String best = null;
        String bestKey = null;
        for (Map.Entry<String, String> e : pages.entrySet()) {
            String p = e.getKey();
            boolean hit = p.equals("/") ? path.equals("/") : path.equals(p) || path.startsWith(p.endsWith("/") ? p : p + "/");
            if (hit && (bestKey == null || p.length() > bestKey.length())) {
                best = e.getValue();
                bestKey = p;
            }
        }
        if (best != null) return best;
        // known deep paths that should not fall through to generic guidance
        if (path.startsWith("/intake-sessions/")) return pages.get("/intake-sessions/:id");
        return "";
    }

    private static final PageDocs INSTANCE = new PageDocs();

    public static PageDocs instance() {
        return INSTANCE;
    }

    private PageDocs() {
        add("/", """
            DASHBOARD (/) — titled "Portfolio", the personal landing page.
            The top header shows a small "AI-driven platform / AI agents work together with you"
            banner here only.
            Header: "Portfolio · N contracts · total value" and a "Customize"/"Done" toggle button.
            Sections (user-arrangeable, drag to reorder in Customize mode): "AI insight", "Needs your
            attention", "Key numbers" (stat cards: "Contracts you can access", "Expiring ≤ 90 days",
            "Obligations overdue", "Open workflow tasks"), "Contracts by type", "Expiry & renewal
            pipeline", "Risk distribution", "Migration" (migrated contracts + unverified low-confidence
            terms), "Ask the portfolio" (clickable suggested questions that run the AI inquiry),
            "My open items" (rejected contracts to revise + open workflow tasks; each has a "Review"
            button — for an approver it goes to the Approvals page, otherwise to the contract's
            Workflow tab; the "All approvals" link shows only for users with APPROVE), plus any saved
            inquiry chart sections (live bar/pie charts, with a "refine" link back into the Contracts
            inquiry). "Needs your attention" lists drafts to review/submit, rejected contracts, open
            tasks, discussions awaiting reply, access requests — each links to the right page.
            Customize mode: drag to reorder, half/full width per section, hide (eye) sections;
            a "Hidden sections" card can re-show them; edits auto-save (PUT /me/dashboard-config).
            AI: the "AI insight" section (collapsible, subtle styling — no flashing) shows a fast
            triage built ONLY from your open tasks and attention items: a short summary plus a few
            ranked items. A "Deeper analysis" button runs the heavier review of your audit trail,
            AI activity, risks and obligations ("What matters now", "Suspicious & worth checking",
            "Suggested next actions") on demand.
            No permission gates; data is access-filtered server-side.
            """);
        add("/intake", """
            NEW REQUEST / INTAKE (/intake) — conversational contract intake. Two-column split pane.
            Arriving here via "Revise & resubmit" on a rejected request loads that request's saved
            session instead of a fresh one, with a red "Revising a returned request" banner showing
            why it was sent back; edits + regenerate + resubmit update the same contract. On a
            revision the document is NOT re-assembled from the template — the last edited version is
            kept as-is; the review page says so, and the requester can rebuild it from the template
            with "Re-assemble" on the contract's Document tab if they want to.
            LEFT: chat "New request — describe what you need in plain language". AI replies stream
            token by token. Under the latest AI question there are quick-answer chips ("Send N answers"
            composes them). Header buttons: "Save draft"/"Saved", "Drafts (N)" (opens a drafts drawer —
            click to resume, 'X' deletes after confirm), "New chat". On a fresh chat there are shortcut
            cards: start a contract type directly, start from one of your recent contracts ("New one
            like this" / "Same party, new type" / "Amend it"), or upload a 3rd-party paper contract
            (AI reads the document and fills the fields; that document becomes read-only after submit).
            A separate paper mode replaces the chat with a drag-and-drop upload zone ("Drop the contract
            file here", .docx/.html/.txt, max 10 MB) and "Back to chat".
            RIGHT: "Request details" card — all intake fields grouped under colored headers (BASICS,
            SCOPE, TERM & RENEWAL, DATA & PRIVACY, GOVERNANCE, EXPECTED ROUTING), each editable,
            required fields marked *; AI-populated fields show provenance chips and "% confidence";
            low-trust values are highlighted with "looks right — confirm". Fields save on blur; a
            "Save N edit(s)" button flushes pending edits. Also: "Third-party paper" card (with
            sandboxed "Preview"), green "All required info is captured" notice when ready, "Expected
            routing" card (predicted triage path + score + explanation), "Precedents" card (AI-scored,
            toggle to select one), "Supporting documents" card. Bottom: a status chip ("Still need: …"
            or "Confirm the highlighted values first") and the "Generate Document Draft" button —
            enabled only when nothing required is missing and AI guesses are confirmed.
            After generating: "Draft ready for your review" card listing carried clause chips and AI
            deviation callouts, with "Submit for approval" (confirm modal → starts the workflow),
            "Save draft", "Cancel"; below it a full document editor + "AI document review" panel.
            """);
        add("/intake-sessions/:id", """
            INTAKE HISTORY (/intake-sessions/{id}) — read-only archive of a completed conversational
            intake: the "Conversation" transcript (user bubbles labeled "Requester", AI bubbles labeled
            "AI intake") and a "Captured details" card listing the fields captured in that session.
            No editing. Linked from a contract's Overview summary.
            """);
        add("/contracts/:id", """
            CONTRACT DETAIL (/contracts/{id}) — the full contract record. Keep in mind the ids are
            contract UUIDs, not numbers.
            Header: a "Back" link that returns to the previous page you were on (falls back to the
            Contracts list if you opened the contract directly), title, contract number, status
            badge (DRAFT / IN_REVIEW / EXECUTED / CLOSED_REJECTED / CANCELLED), risk badge,
            "migrated" badge, parent-contract link.
            Requestor actions: on a plain DRAFT — "Submit for approval" and "Cancel request"
            (→ CANCELLED); on a DRAFT that an approver rejected (shows a red rejection banner with
            the reason — "returned to you" for the requestor, "returned to the requestor" for
            anyone else viewing it) — "Revise & resubmit" and "Close request"
            (→ CLOSED_REJECTED); on IN_REVIEW — "Recall to draft". "Revise & resubmit" reopens the
            request's intake session and takes you to the New request page with the conversation
            and form loaded, so you can edit the details and resubmit — it updates the SAME
            contract (keeps its number, bumps a draft version), it does not create a new one.
            "Cancel request", "Close request" and "Recall to draft" each ask for confirmation.
            Cancelled and closed contracts are terminal — they can't be resubmitted and stop
            appearing in dashboard open items.
            When the current workflow task is
            assigned to the viewer, the header also shows "Approve" / "Reject" — each opens a
            confirmation dialog (reject requires a reason); the note lands in the Workflow tab.
            FLOW PROGRESS BAR: directly under the header, above the "AI summary & briefing" card, a
            horizontal step bar shows where the contract is. When an approval workflow is running it
            shows that workflow's own steps (e.g. Manager approval → Signature → Executed, or the
            Legal review chain) with the current step marked "In progress" and, under it,
            "Pending: <who>" — the individual it is assigned to, or the team/role name (e.g. "Legal
            team") when it is not yet with a named person. Finished steps are ticked, and the live
            contract status badge is shown. With no workflow yet it shows the generic lifecycle
            (Draft → In review → Executed); a returned/rejected, cancelled or closed request is
            called out on the bar too.
            An "AI summary & briefing" card sits under it (collapsible; can regenerate the
            approver briefing).
            DISCUSSION PANEL: comment threads live in a DRAGGABLE panel (not a tab). By default it
            is docked as a full-height left column right beside the tab content (sticky, so it
            stays in view as you scroll). Drag its header to detach it into a free-floating box
            anywhere on screen; a "Dock" button then snaps it back. Open/closed and any custom
            position are remembered. "New thread" (just a message, no title), reply, resolve, and a
            "✦ Ask agent" action per thread when you have Agent Crew on (see CROSS-CUTTING FEATURES
            below). An "×" hides the panel (a "Show discussion" button brings it back); a link with
            ?tab=discussion opens it. While participants' agents are working, an animated status
            shows INSIDE the affected thread — "Checking with all agents", changing to "An agent is
            replying" once an agent message actually lands.
            TABS: "Overview" (key-value terms: contracting
            entity, counterparties, governing law, value, dates, owner, liability; grouped "Deal
            terms"; "Clauses used" with tier badges; "People with access" — add/remove participants
            via "add person"; "Hierarchy" child links);
            "Key terms" (table of extracted terms with source quote + provenance inherited/overrides +
            confidence); "Document" (document editor with Read/Edit toggle when EDIT_CONTRACT, autosave,
            "Re-assemble" — rebuilds the document from the current template + clauses, discarding manual
            edits; this is the ONLY thing that regenerates the document, resubmitting a revised request
            never does — "Open full", supporting documents with "Download", plus the "AI document
            review" panel — see below);
            "Workflow" (workflow name, current state badge, "Start workflow", task list showing each
            task's outcome, completion date and the approve/reject note — a rejection reason is
            highlighted); "Obligations" (table with Verify/Close and confidence);
            "Relations" ("Detect with AI" button finds amendments/master/SOW links; AI suggestions await
            "Confirm"/"Dismiss" with confidence % and reasons; shows confirmed relations and the
            precedent basis); "Risks" (risk register: "Add risk", severity badges, "Dismiss"/"Resolve"/
            "Reopen", quoted locations); "Audit trail" (who did what, with AI actors marked).
            AI review & submit gate: "Submit for approval" first runs/syncs an "AI quick check" (modal
            listing findings by severity; still allows "Submit anyway"). In the Document tab the review
            panel lets you pick checked rules and optional playbooks, re-runs, links findings' quotes
            into the document, and offers Dismiss/Resolve.
            """);
        add("/contracts", """
            CONTRACTS LIST (/contracts) — the contract register with an AI inquiry pane.
            Header: title "Contracts" and a "New request" button (goes to /intake).
            Split pane: LEFT "AI inquiry — ask about your portfolio": an "Ask" input + "Ask" button
            (POST /inquiry/ask) and suggestion chips; results show "Interpreted as" (the AI's
            restatement with filter badges), a refusal as a warning card; "edit & re-run" exposes an
            editable interpretation + raw filter fields and "Re-run"; "Add as dashboard section" saves
            a bar/pie chart (choose "Group by" dimension) to the Dashboard; "New chat" resets. When an
            inquiry is active the list pane widens and an accent banner shows "Filtered by your AI
            inquiry" with "Show all" to clear.
            RIGHT: filter card ("All types", status, region, "Counterparty…" text, "Expiring ≤ 30/90/180
            days") above the contract table. The table has add/remove columns ("Columns (N)" button),
            per-column sort (arrows in each header) and per-column filter menus (is / contains / before /
            after / larger / smaller / is empty). Columns include Contract number + title, Type,
            Counterparty, Entity, Status badge, Value, Expiry (with migrated badge), Risk badge,
            "Last updated" (date + time of the most recent change to the contract — sortable and
            filterable by before/after) and "Last updated by" (who made that change). Numbers link
            to the detail page. Column choice, order and sort persist in the browser.
            """);
        add("/approvals", """
            APPROVALS (/approvals) — the approver workbench; needs the APPROVE permission.
            Header: "Approvals" with a scope toggle "My tasks" / "All open" (top-right).
            A stack of task cards; each shows contract number (link to the contract), state and type
            badges, "overdue" badge when past due, title, and a meta line "type · value · assigned ·
            due date". "Discuss" expands a comment thread per contract. Only the assignee sees
            the action buttons (labels from the workflow, e.g. "approve", "reject"); other viewers
            see "Actions are handled by the assigned user (name)". Clicking "approve" or "reject"
            opens a confirmation dialog with a note field — OPTIONAL for approve, REQUIRED for
            reject (confirm stays disabled until a reason is typed). The note is saved on the
            workflow task, appears in the contract's Workflow tab, and a rejection reason is shown
            to the requestor.
            Each card has an "AI approver briefing" card (hover or click to expand; "Re-run" regenerates):
            summary, "What changed" vs the precedent/playbook, "Deviations from standard", "Decision
            requested".
            """);
        add("/auto-reject", """
            AUTO-REJECTION (/auto-reject) — the approver's automatic-rejection rule builder; needs
            the APPROVE permission. An explainer card ("How auto-rejection works") summarises the
            mechanics: a rule rejects a contract automatically when it reaches YOUR approval step
            with the rule's requirements unmet. "New rule" opens a create form (name; scope
            sections — "Applies to contract types", "Applies in countries", "Applies to our
            contracting entities" as toggle chips, empty meaning no restriction; and "Describe the
            conditions in your own words" free-text area). Saving does NOT execute yet: press
            "Structure with AI" — the AI restates the rule as a regulated summary plus a numbered
            list of executable requirements (attachment/field/text checks) with an ANY_OF/ALL_OF
            combinator badge; without a structured interpretation the rule stays "needs
            structuring" and never rejects. Each rule card: enabled toggle, scope chips, the
            instructions, the interpretation (summary + requirements), "fired N times", Edit/
            Delete, "Re-interpret" (when instructions changed), and "Dry-run" — pick one of your
            open approval tasks and see met/unmet requirements with a "Would reject" verdict.
            "Recent auto-rejections" lists the fired events (contract, rule, reason, when). Rules
            belong to the approver who created them; admins/legal counsel with global access see all.
            The "When should auto-rejection kick in?" card sets ONE global trigger timing for all
            your rules: reject as soon as the request reaches your queue, or wait N hours before
            auto-rejection starts to evaluate (applies only to requests that arrive after the
            setting is changed; delayed rules are checked at most every 5 minutes).
            """);
        add("/obligations", """
            OBLIGATIONS (/obligations) — track contractual obligations and deadlines extracted from
            contracts; needs VIEW_OBLIGATIONS.
            Four stat tiles: "Overdue" (red), "Due next 30 days" (amber), "Due next 90 days",
            "Unverified (low conf.)". Status filter buttons: "All", "OPEN", "OVERDUE", "CLOSED".
            Table: Obligation (description + type), Contract link, Due date (+overdue badge), Owner,
            Status badge, Confidence, actions. "Verify" marks an AI-extracted obligation as human-
            verified; "Close" closes it out. The "Unverified (low confidence)" tile highlights what
            needs your review.
            """);
        add("/clauses", """
            CLAUSE LIBRARY (/clauses) — browse playbook positions and check proposed language; needs
            VIEW_CLAUSES. Two columns: a concept list (with "core" badges and variant counts) on the
            left; on the right, after selecting a concept: the concept's playbook positions card (tier
            badges PREFERRED / ACCEPTABLE / FALLBACK / UNACCEPTABLE, clause text, guidance) and a
            "Check proposed language against playbook" card. Each tier can expand "which contracts use
            this?". In the check card: paste a counterparty clause, then "✦ Analyse deviation" — the AI
            returns "Overall risk" (badge) and per-concept deviations ("closest tier X (severity)") with
            a "why?" toggle. The library is read-only; editing happens in Administration.
            """);
        add("/templates", """
            TEMPLATE LIBRARY (/templates) — read-only library of approved contract templates; needs
            VIEW_TEMPLATES. Search box ("Search name, tag or type…") above a card grid (name, type
            badge, description, tag chips, "jurisdiction/language · v N sections"). Clicking a card
            opens a modal: "Sections" list (order, heading, optional marker, concept badge colored by
            its preferred tier) and a "Preview" panel rendering the template. "Manage templates" (top
            right, only for MANAGE_TEMPLATES holders) links to /admin?tab=templates.
            """);
        add("/access", """
            ACCESS (/access) — data-scope self service; available to every signed-in user.
            Sections: "Waiting on your decision (N)" (requests routed to you, with Approve/Reject and
            an optional decision note); "Your access" (your grants: scope, source, expiry); "Your
            requests" (status badges); "Apply for access": toggle dimension-value chips (multi-select),
            a live "You are requesting" preview via /access/preview that also shows "Approvable by:
            names" or warns when no approver owns that scope, plus "Why do you need this access?" and
            optional "Expires (days)" before "Submit request".
            """);
        add("/ai-log", """
            AI ACTIVITY (/ai-log) — governance log of every AI interaction; needs VIEW_AI_LOG.
            A single table: When, Surface, Capability, Prompt (id@version), Conf. (%), Latency
            (ms), Outcome badge (ACCEPTED/AUTO_APPLIED, EDITED, REJECTED, PENDING, plus "reverted").
            Each row has "Revert" (until reverted) to undo what the AI wrote — e.g. an applied
            extraction. There are no other filters; this is the observability/undo surface for AI.
            """);
        add("/admin", """
            ADMINISTRATION (/admin?tab=…) — master data management; the sidebar group needs
            MANAGE_MASTERDATA, and it shows the admin tab links. Common affordances: searchable table,
            "New" button, "Edit" modal per row. Tabs:
            "entities" legal entities (country, governing law, region, owning team, active flag);
            "teams" legal teams (region, SLA hours); "users" (MANAGE_USERS only; roles, default entity,
            active); "parties" counterparties (type, sanctions status); "types" contract types (base
            risk, retention, auto-issue flags, default template/workflow, JSON fieldSchema that drives
            the intake form); "concepts" clause concepts; "variants" clause variants per concept
            (tier + text, never deleted, deprecated instead); "templates" (merge fields like
            {{counterparty_name}}, docx/HTML upload); "playbooks"; "signing" signing authority (entity,
            user, type, max value); "rules" assignment rules (priority, USER/TEAM/ROUND_ROBIN target);
            "workflows" (MANAGE_WORKFLOWS only; JSON state machines, Publishing; in-flight instances
            unaffected); "dimensions" access dimensions; "scopes" approver scopes (constraints JSON per
            dimension); "grants" direct access grants; "review-rules" the AI review checklist used by
            document review (code, instruction, severity, active);
            "policies" Policies & procedures — upload a Word/HTML/txt policy document (or paste its
            text); tick the roles / countries / regions it applies to (untick all = everyone on
            that dimension) and a "Confidential" toggle. The floating help assistant reads these
            documents and answers policy/procedure questions from them, but only the ones matching
            the asking user's role, country (from their default entity), region and — for
            confidential documents — broad access (ADMIN / general counsel). Deactivate a row to
            take it out of the assistant's knowledge without deleting it.
            """);
        add("/login", """
            LOGIN (/login) — email/password sign-in. The demo environment also lists demo accounts
            grouped by role; clicking one signs in as that user after a quick arithmetic
            "not a robot" check. Not part of the signed-in experience.
            """);
    }

    /** Convenience wrapper. */
    public static String guideFor(String page) {
        return instance().forPage(page);
    }

    /**
     * Cross-cutting features that don't live on a single route (they appear on many pages or in
     * shared components). Always included in the help prompt, regardless of the current page.
     * KEEP THIS CURRENT: every PR that adds or changes a user-facing feature must update this
     * block and/or the matching {@code add(...)} page guide above.
     */
    public static String features() {
        return """
            CROSS-CUTTING FEATURES (available across pages, not tied to one route):

            HELP ASSISTANT NUDGES — beside the collapsed help icon a small "Ask me: …" bubble
            fades in with a question relevant to the current page; it fades out after ~10s and
            re-appears after a stretch of inactivity. Clicking it opens the chat and asks that
            question. It is deliberately low-key.

            HELP ASSISTANT KNOWLEDGE — besides how-to guidance, the floating help chat can answer
            questions about the organisation's own CLM policies and procedures. Admins upload those
            documents under Administration → "Policies & procedures" and tag each with the roles /
            countries / regions it applies to and whether it is confidential. Each user's help chat
            only sees the documents that match their role, their country/region (from their default
            legal entity) and — for confidential ones — broad access. If no policy covers a
            question the assistant says so rather than guessing.

            AGENT COLLABORATION / "AGENT CREW" — each participant on a contract can have a personal
            AI agent that takes part in that contract's Discussion threads, speaking AS that person
            (first person), grounded in the contract record.
            - The "Agent Crew" toggle in the top header (previously labelled "Agent talk") is the
              MASTER SWITCH for the user's advanced agent functionality: it controls agent
              discussion AND the dashboard AI insight AND the AI approver briefings. Toggling it
              opens a confirmation dialog that names it as the advanced agent functions and lists
              all three. While off, none of those run for the user and the dashboard/contract show
              an "AI off — enable Agent Crew" hint instead. There is no per-contract switch
              (PATCH /me/agent-setting).
            - Using it: open a Discussion thread on a contract and click "✦ Ask agent". Pick "Auto —
              best placed to answer" (the system routes the question to the participant whose role
              best fits — financial / legal / approval / general) or target a specific person's
              agent from the dropdown. While this runs the thread shows "Checking with all agents",
              switching to "An agent is replying" once an agent message lands; the reply is tagged
              "<Name> (agent)" with violet styling. Posting a new thread with Agent Crew on triggers
              the same auto-answer without the "✦ Ask agent" click.
            - Runs in the background: the agents work server-side, so the replies still land in the
              thread even if you navigate away — reopen the contract and they will be there.
            - Back-and-forth: for a targeted question, if the asker also has their agent enabled,
              the asker's agent may auto-review the answer and post one follow-up question; the
              target agent answers once more (bounded — it stops after a couple of exchanges).
            - Agents are advisory: their messages are contributions to the discussion, not
              approvals or contract changes. A represented user is nudged when their agent has
              posted something awaiting their attention.
            - Eligibility: the asker and the represented participant must both be able to view the
              contract, and the represented participant must have opted in globally.

            AUTO-REJECTION — approvers can define rules that automatically reject a contract when it
            reaches their approval step with the rule's requirements unmet. Managed on the
            Auto-rejection page (Work section, needs APPROVE). See that page's guide for details.
            """;
    }
}