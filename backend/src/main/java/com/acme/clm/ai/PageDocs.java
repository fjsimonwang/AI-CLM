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
            Header: "Portfolio · N contracts · total value" and a "Customize"/"Done" toggle button.
            Sections (user-arrangeable, drag to reorder in Customize mode): "AI insight", "Needs your
            attention", "Key numbers" (stat cards: "Contracts you can access", "Expiring ≤ 90 days",
            "Obligations overdue", "Open workflow tasks"), "Contracts by type", "Expiry & renewal
            pipeline", "Risk distribution", "Migration" (migrated contracts + unverified low-confidence
            terms), "Ask the portfolio" (clickable suggested questions that run the AI inquiry),
            "My open tasks" (links to contracts, "Review" button to Approvals), plus any saved
            inquiry chart sections (live bar/pie charts, with a "refine" link back into the Contracts
            inquiry). "Needs your attention" lists drafts to review/submit, open tasks, discussions
            awaiting reply, access requests — each links to the right page.
            Customize mode: drag to reorder, half/full width per section, hide (eye) sections;
            a "Hidden sections" card can re-show them; edits auto-save (PUT /me/dashboard-config).
            AI: the "AI insight" section polls /ai/insight while generating (flashing red dot +
            signal-count badge when there is overdue/risky work), then shows "What matters now",
            "Suspicious & worth checking", "Suggested next actions".
            No permission gates; data is access-filtered server-side.
            """);
        add("/intake", """
            NEW REQUEST / INTAKE (/intake) — conversational contract intake. Two-column split pane.
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
            Header: back link "Contracts", title, contract number, status badge, risk badge, "migrated"
            badge, parent-contract link; "Submit for approval" (DRAFT) or "Recall to draft" (IN_REVIEW)
            buttons for users with EDIT_CONTRACT or the requestor.
            An "AI summary & briefing" card sits under the header (collapsible; can regenerate the
            approver briefing).
            TABS: "Overview" (key-value terms: contracting entity, counterparties, governing law, value,
            dates, owner, liability; grouped "Deal terms"; "Clauses used" with tier badges; "People with
            access" — add/remove participants via "add person"; "Hierarchy" child links);
            "Key terms" (table of extracted terms with source quote + provenance inherited/overrides +
            confidence); "Document" (document editor with Read/Edit toggle when EDIT_CONTRACT, autosave,
            "Re-assemble", "Open full", supporting documents with "Download", plus the "AI document
            review" panel — see below); "Discussion" (comment threads: "New thread", reply, resolve);
            "Workflow" (workflow name, current state badge, "Start workflow", task list — actions happen
            on the Approvals page); "Obligations" (table with Verify/Close and confidence);
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
            days") above the contract table (Contract number + title, Type, Counterparty, Entity,
            Status badge, Value, Expiry with migrated badge, Risk badge). Numbers link to the detail
            page.
            """);
        add("/approvals", """
            APPROVALS (/approvals) — the approver workbench; needs the APPROVE permission.
            Header: "Approvals" with a scope toggle "My tasks" / "All open" (top-right).
            A stack of task cards; each shows contract number (link to the contract), state and type
            badges, "overdue" badge when past due, title, and a meta line "type · value · assigned ·
            due date". "Discuss" expands a comment thread per contract. Only the assignee sees
            "Comment (optional)" and the action buttons (labels from the workflow, e.g. "approve",
            "reject"); other viewers see "Actions are handled by the assigned user (name)".
            Each card has an "AI approver briefing" card (hover or click to expand; "Re-run" regenerates):
            summary, "What changed" vs the precedent/playbook, "Deviations from standard", "Decision
            requested".
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
            document review (code, instruction, severity, active).
            """);
        add("/login", """
            LOGIN (/login) — email/password sign-in plus a clickable demo-account list for the demo
            environment. Not part of the signed-in experience.
            """);
    }

    /** Convenience wrapper. */
    public static String guideFor(String page) {
        return instance().forPage(page);
    }
}