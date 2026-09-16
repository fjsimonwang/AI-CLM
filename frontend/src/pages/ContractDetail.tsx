import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams, useLocation, useNavigate } from "react-router-dom";
import { api, apiBlob, money, date, usePerms, useAuth } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty, Tabs, statusTone, riskTone, Confidence, DecisionDialog } from "../components/ui";
import { BriefingCard } from "../components/BriefingCard";
import { Icon } from "../components/icons";
import { DocumentPanel } from "../components/DocumentPanel";
import { CommentThreads } from "../components/CommentThreads";
import { AiReviewPanel } from "../components/AiReviewPanel";
import { DockablePanel, useDockablePanel } from "../components/DockablePanel";
import { RiskRegister } from "../components/RiskRegister";
import { useHighlight } from "../components/highlight";
import { useDocEdited } from "../components/docEdited";

const enumLabel = (v: any) =>
  typeof v === "string" && v === v.toUpperCase() && v.includes("_")
    ? v.toLowerCase().replace(/_/g, " ")
    : String(v);

const RELATION_LABELS: Record<string, string> = {
  AMENDS: "Amends",
  AMENDED_BY: "Amended by",
  SUPERSEDES: "Supersedes",
  SUPERSEDED_BY: "Superseded by",
  PRECEDES: "Preceded by",
  FOLLOWS: "Follows",
  MASTER: "Sits under",
  UNDER: "Subcontract of",
  SIMILAR_FAMILY: "Same family",
};
const relationLabel = (t: string) => RELATION_LABELS[t] || enumLabel(t);
const sevRank = (s?: string) => (s === "CRITICAL" ? 4 : s === "HIGH" ? 3 : s === "MEDIUM" ? 2 : 1);

const renderSummaryWithSession = (summary: string, sessionId: string) => {
  const m = summary.match(/(.*?conversational intake session\s+)([0-9a-f-]+)(.*)/i);
  if (!m) return summary;
  return (
    <>
      {m[1]}
      <Link to={`/intake-sessions/${sessionId}`} className="link">{m[2]}</Link>
      {m[3]}
    </>
  );
};

const WF_STATE_LABELS: Record<string, string> = {
  owner_approval: "Manager approval",
  legal_review: "Legal review",
  drafting: "Requestor revision",
  finance_review: "Finance approval",
  finance_approval: "Finance approval",
  signature: "Signature",
  executed: "Executed",
  closed_rejected: "Rejected",
};
const wfLabel = (key: string) =>
  WF_STATE_LABELS[key] ||
  key.replace(/_/g, " ").replace(/\b\w/g, (c: string) => c.toUpperCase());

type StepState = "done" | "current" | "todo" | "rejected";
type ProgressStep = { key: string; label: string; state: StepState; pending?: string };

/**
 * Horizontal flow bar showing where the contract sits in its approval workflow.
 * When a workflow instance is running (or completed) it renders that workflow's
 * own states; otherwise it falls back to the generic Draft → In review → Executed
 * lifecycle keyed off the contract status.
 */
function WorkflowProgress({
  wf,
  contractStatus,
  rejectionReason,
}: {
  wf: any;
  contractStatus: string;
  rejectionReason?: string | null;
}) {
  const started = !!wf?.started && Array.isArray(wf?.states) && wf.states.length > 0;

  let title = "Contract lifecycle";
  let steps: ProgressStep[] = [];
  let note: { text: string; tone: "risk" | "muted" } | null = null;

  if (started) {
    title = wf.workflowName || "Approval workflow";
    const order = wf.states.filter((s: any) => s.key !== "closed_rejected");
    const curKey: string = wf.currentState;
    const curIdx = order.findIndex((s: any) => s.key === curKey);
    const completed = wf.status === "COMPLETED";
    const executed = completed && curKey === "executed";
    const doneStates = new Set(
      (wf.tasks || [])
        .filter((t: any) => t.status !== "OPEN" && t.outcome && t.outcome !== "reject")
        .map((t: any) => t.state),
    );
    const openTasks = (wf.tasks || []).filter((t: any) => t.status === "OPEN");
    const pendingTask =
      openTasks.find((t: any) => t.state === curKey) || openTasks[0] || null;
    // who the current step is waiting on: the named individual, else the team/role
    const pendingWho = pendingTask
      ? pendingTask.assignee || pendingTask.roleLabel || null
      : null;
    // the requestor's submission is always the first step of the flow (a running or completed
    // workflow means the request was submitted)
    steps = [
      { key: "submitted", label: "Submitted", state: "done" as StepState },
      ...order.map((s: any, i: number) => {
        let state: StepState = "todo";
        if (executed) state = "done";
        else if (s.key === curKey && wf.status === "RUNNING") state = "current";
        else if (doneStates.has(s.key) || (curIdx >= 0 && i < curIdx)) state = "done";
        return {
          key: s.key,
          label: wfLabel(s.key),
          state,
          pending: state === "current" && pendingWho ? pendingWho : undefined,
        };
      }),
    ];
  } else {
    const returned = contractStatus === "DRAFT" && !!rejectionReason;
    if (contractStatus === "CANCELLED") {
      steps = [
        { key: "draft", label: "Draft", state: "done" },
        { key: "cancelled", label: "Cancelled", state: "rejected" },
      ];
      note = { text: "This request was cancelled by the requestor.", tone: "muted" };
    } else if (contractStatus === "CLOSED_REJECTED") {
      steps = [
        { key: "draft", label: "Draft", state: "done" },
        { key: "review", label: "In review", state: "rejected" },
        { key: "closed", label: "Closed", state: "todo" },
      ];
      note = { text: "Rejected in approval and closed by the requestor.", tone: "risk" };
    } else {
      // Draft -> Submitted -> In review -> Executed
      const idx = contractStatus === "EXECUTED" ? 3 : contractStatus === "IN_REVIEW" ? 2 : 0;
      steps = [
        { key: "draft", label: "Draft" },
        { key: "submitted", label: "Submitted" },
        { key: "review", label: "In review" },
        { key: "executed", label: "Executed" },
      ].map((s, i) => ({
        ...s,
        state: (idx === 3 ? "done" : i < idx ? "done" : i === idx ? "current" : "todo") as StepState,
      }));
      if (returned)
        note = {
          text: "Returned to the requestor after rejection — revise & resubmit or close it out.",
          tone: "risk",
        };
    }
  }

  const statusLabel =
    contractStatus === "IN_REVIEW"
      ? "In review"
      : contractStatus.charAt(0) + contractStatus.slice(1).toLowerCase().replace(/_/g, " ");

  return (
    <Card className="fade-in">
      <div className="flex items-center justify-between gap-3 mb-3">
        <span className="text-sm font-medium text-ink-soft uppercase tracking-wide">{title}</span>
        <Badge tone={statusTone(contractStatus)}>{statusLabel}</Badge>
      </div>
      <ol className="flex items-start">
        {steps.map((s, i) => {
          const last = i === steps.length - 1;
          const prevDone = i > 0 && steps[i - 1].state === "done";
          // the segment INTO this step is green once the previous step is done — so the green
          // track reaches the active step, not just the last completed one
          const leadInGreen = prevDone;
          const connectorDone = s.state === "done";
          return (
            <li key={s.key} className="flex-1 flex flex-col items-center text-center min-w-0">
              <div className="flex items-center w-full">
                <span
                  className="h-[2px] flex-1 rounded"
                  style={{
                    background: i === 0 ? "transparent" : leadInGreen ? "var(--ok)" : "var(--border)",
                  }}
                />
                <span
                  className="shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-[11px]"
                  style={
                    s.state === "done"
                      ? { background: "var(--ok)", color: "#fff" }
                      : s.state === "current"
                        ? { border: "2px solid var(--accent)", background: "var(--surface)" }
                        : s.state === "rejected"
                          ? { background: "var(--risk)", color: "#fff" }
                          : { border: "2px solid var(--border)", background: "var(--surface)" }
                  }
                >
                  {s.state === "done" ? (
                    <svg viewBox="0 0 12 12" width={11} height={11}>
                      <path
                        d="M2.5 6.5 L5 9 L9.5 3.5"
                        fill="none"
                        stroke="#fff"
                        strokeWidth="1.8"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  ) : s.state === "rejected" ? (
                    "✕"
                  ) : s.state === "current" ? (
                    <span className="w-1.5 h-1.5 rounded-full" style={{ background: "var(--accent)" }} />
                  ) : (
                    <span className="text-ink-faint">{i + 1}</span>
                  )}
                </span>
                <span
                  className="h-[2px] flex-1 rounded"
                  style={{
                    background: last ? "transparent" : connectorDone ? "var(--ok)" : "var(--border)",
                  }}
                />
              </div>
              <span
                className={`mt-1.5 text-xs leading-tight px-1 ${
                  s.state === "todo" ? "text-ink-faint" : "text-ink"
                }`}
              >
                {s.label}
              </span>
              {s.state === "current" && (
                <span className="text-[11px] mt-0.5" style={{ color: "var(--accent)" }}>
                  In progress
                </span>
              )}
              {s.pending && (
                <span className="text-[11px] mt-0.5 text-ink-soft leading-tight">
                  Pending: {s.pending}
                </span>
              )}
            </li>
          );
        })}
      </ol>
      {note && (
        <p
          className="text-xs mt-3"
          style={{ color: note.tone === "risk" ? "var(--risk)" : "var(--ink-faint)" }}
        >
          {note.text}
        </p>
      )}
    </Card>
  );
}

export default function ContractDetail() {
  const { id } = useParams();
  const location = useLocation();
  const nav = useNavigate();
  const qc = useQueryClient();
  const can = usePerms();
  const [justSubmitted, setJustSubmitted] = useState(() => !!(location.state as any)?.justSubmitted);
  // "Back" returns to wherever the user came from (browser history). If they landed here directly
  // (fresh tab, bookmark, deep link) there is no in-app history, so fall back to the register.
  const canGoBack = location.key !== "default";
  const goBack = () => (canGoBack ? nav(-1) : nav("/contracts"));
  const validTabs = ["overview", "terms", "document", "workflow", "obligations", "relations", "risks", "audit"];
  const initialTab = new URLSearchParams(location.search).get("tab");
  const [tab, setTab] = useState(initialTab && validTabs.includes(initialTab) ? initialTab : "overview");
  // Discussion lives in a persistent left side panel, not a tab. `?tab=discussion` opens it.
  const [discussionOpen, setDiscussionOpen] = useState(() => {
    if (initialTab === "discussion") return true;
    try { return localStorage.getItem("clm-contract-discussion") !== "0"; } catch { return true; }
  });
  const toggleDiscussion = (v: boolean) => {
    setDiscussionOpen(v);
    try { localStorage.setItem("clm-contract-discussion", v ? "1" : "0"); } catch { /* ignore */ }
  };
  // The discussion panel is DOCKED (a left column, full viewport-height, sticky) by default.
  // Dragging its header detaches it to a free-floating box (movable + resizable); "Dock" snaps back.
  const disc = useDockablePanel("clm-discussion-pos", "[data-discussion-card]", { defaultW: 360, defaultDockW: 340 });
  // AI document review — a movable/dockable/resizable/closable panel, same as the discussion panel.
  const [reviewOpen, setReviewOpen] = useState(() => {
    try { return localStorage.getItem("clm-contract-airev") === "1"; } catch { return false; }
  });
  const toggleReview = (v: boolean) => {
    setReviewOpen(v);
    try { localStorage.setItem("clm-contract-airev", v ? "1" : "0"); } catch { /* ignore */ }
  };
  const review = useDockablePanel("clm-airev-pos", "[data-airev-card]", { defaultW: 400, defaultDockW: 380 });

  const [decision, setDecision] = useState<null | "approve" | "reject">(null);
  const [lifecycle, setLifecycle] = useState<null | "cancel" | "close" | "recall">(null);
  const [signStep, setSignStep] = useState<null | "confirm" | "sent">(null);
  const [recordOpen, setRecordOpen] = useState(() => {
    try { return localStorage.getItem("clm-contract-summary") !== "0"; } catch { return true; }
  });
  const toggleRecord = (v: boolean) => {
    setRecordOpen(v);
    try { localStorage.setItem("clm-contract-summary", v ? "1" : "0"); } catch { /* ignore */ }
  };
  const [quickCheck, setQuickCheck] = useState<any>(null); // result object or "error"
  const [checking, setChecking] = useState(false);
  const [qcError, setQcError] = useState<string | null>(null);

  const c = useQuery({ queryKey: ["contract", id], queryFn: () => api(`/contracts/${id}`) });
  const wf = useQuery({ queryKey: ["wf", id], queryFn: () => api(`/workflow/status/${id}`) });
  const d: any = c.data;
  const fields = useQuery({
    queryKey: ["typefields", d?.type],
    queryFn: () => api(`/refdata/contract-types/${d.type}/fields`),
    enabled: !!d?.type,
  });
  const audit = useQuery({ queryKey: ["audit", id], queryFn: () => api(`/audit/CONTRACT/${id}`), enabled: tab === "audit" });

  const relations = useQuery({ queryKey: ["relations", id], queryFn: () => api(`/contracts/${id}/relations`) });
  const risks = useQuery({ queryKey: ["risks", id], queryFn: () => api(`/contracts/${id}/risks`), enabled: !!id });
  const attachmentsQuery = useQuery({
    queryKey: ["contract-attachments", id],
    queryFn: () => api(`/contracts/${id}/attachments`),
    enabled: tab === "document",
  });

  async function downloadAttachment(attachmentId: string, filename: string) {
    const blob = await apiBlob(`/contracts/${id}/attachments/${attachmentId}/file`);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
  const [removeArm, setRemoveArm] = useState<string | null>(null);
  const removeAttachment = useMutation({
    mutationFn: (attachmentId: string) =>
      api(`/contracts/${id}/attachments/${attachmentId}`, { method: "DELETE" }),
    onSuccess: () => {
      setRemoveArm(null);
      qc.invalidateQueries({ queryKey: ["contract-attachments", id] });
      qc.invalidateQueries({ queryKey: ["contract", id] });
    },
  });
  const detectRelations = useMutation({
    mutationFn: () => api(`/contracts/${id}/relations/detect`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["relations", id] }),
  });
  const decideRelation = useMutation({
    mutationFn: (p: { relId: string; decision: "confirm" | "reject" }) =>
      api(`/contracts/${id}/relations/${p.relId}/${p.decision}`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["relations", id] }),
  });

  const storedBriefings = useQuery({
    queryKey: ["briefing", id],
    queryFn: () => api(`/ai/briefings?contractIds=${id}`),
    enabled: !!id,
  });
  const summarize = useMutation({
    mutationFn: () => api("/ai/approver-briefing", { method: "POST", json: { contractId: id, force: true } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["briefing", id] });
      qc.invalidateQueries({ queryKey: ["briefings"] });
    },
  });
  const startWf = useMutation({
    mutationFn: () => api(`/workflow/start/${id}`, { method: "POST" }),
    onSuccess: () => {
      setJustSubmitted(true);
      qc.invalidateQueries({ queryKey: ["wf", id] });
      qc.invalidateQueries({ queryKey: ["contract", id] });
      qc.invalidateQueries({ queryKey: ["contracts"] });
    },
  });
  const setStatus = useMutation({
    mutationFn: (status: string) => api(`/contracts/${id}/status`, { method: "PATCH", json: { status } }),
    onSuccess: () => {
      setLifecycle(null);
      qc.invalidateQueries({ queryKey: ["contract", id] });
      qc.invalidateQueries({ queryKey: ["contracts"] });
      qc.invalidateQueries({ queryKey: ["me-summary"] });
    },
  });
  const revise = useMutation({
    mutationFn: () => api(`/intake/revise/${id}`, { method: "POST" }),
    onSuccess: (s: any) =>
      nav("/intake", {
        state: { reviseSessionId: s.id, reviseContractId: id, reviseReason: (c.data as any)?.rejectionReason },
      }),
  });
  // recall from review → back to draft AND reopen the intake session so the form is editable again
  const recall = useMutation({
    mutationFn: async () => {
      await api(`/contracts/${id}/status`, { method: "PATCH", json: { status: "DRAFT" } });
      return api(`/intake/revise/${id}`, { method: "POST" });
    },
    onSuccess: (s: any) => {
      setLifecycle(null);
      qc.invalidateQueries({ queryKey: ["contract", id] });
      qc.invalidateQueries({ queryKey: ["contracts"] });
      qc.invalidateQueries({ queryKey: ["me-summary"] });
      nav("/intake", { state: { reviseSessionId: s.id, reviseContractId: id } });
    },
    onError: () => {
      // the status change may have succeeded even if reopening the session failed — refresh
      qc.invalidateQueries({ queryKey: ["contract", id] });
      qc.invalidateQueries({ queryKey: ["wf", id] });
    },
  });
  const actOnTask = useMutation({
    mutationFn: (p: { taskId: string; event: string; comment?: string }) =>
      api(`/workflow/tasks/${p.taskId}/act`, { method: "POST", json: { event: p.event, comment: p.comment } }),
    onSuccess: () => {
      setDecision(null);
      qc.invalidateQueries({ queryKey: ["wf", id] });
      qc.invalidateQueries({ queryKey: ["contract", id] });
      qc.invalidateQueries({ queryKey: ["contracts"] });
      qc.invalidateQueries({ queryKey: ["me-summary"] });
      qc.invalidateQueries({ queryKey: ["tasks"] });
    },
  });

  // mandatory AI quick check before the requestor can submit a draft for approval.
  // Edits are synced first, and if a completed review already covers the edited
  // content it is reused instead of running a second review.
  const beginSubmit = () => {
    if (!id || checking) return;
    setChecking(true);
    setQcError(null);
    setQuickCheck(null);
    const editedAt = useDocEdited.getState().lastAt || 0;
    api(`/contracts/${id}/document/sync`, { method: "POST" })
      .then(() => api(`/ai/review-runs?contractId=${id}`))
      .then((run: any) => {
        if (run && run.status === "DONE" && run.createdAt &&
            new Date(run.createdAt).getTime() > editedAt) {
          return run;
        }
        return api("/ai/review-quick", { method: "POST", json: { contractId: id } });
      })
      .then((res: any) => {
        const findings: any[] = res.finding || res.findings || [];
        setQuickCheck({ ...res, criticalCount: findings.filter((f) => f.severity === "CRITICAL").length });
        // the quick-check run is persisted server-side — keep the review panel in sync
        qc.invalidateQueries({ queryKey: ["review-run", id] });
      })
      .catch((e: any) => setQcError(e.message || "AI quick check failed"))
      .finally(() => setChecking(false));
  };

  const grouped = useMemo(() => {
    const attrs: Record<string, any> = d?.typeAttributes || {};
    const spec: any[] = fields.data || [];
    const specByKey = Object.fromEntries(spec.map((f) => [f.key, f]));
    const groups: Record<string, { key: string; label: string; value: any; money?: boolean }[]> = {};
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null || v === "") continue;
      const f = specByKey[k];
      const g = f?.group || "Other";
      (groups[g] ||= []).push({ key: k, label: f?.label || k.replace(/_/g, " "), value: v, money: f?.money });
    }
    return groups;
  }, [d, fields.data]);

  const user = useAuth((s) => s.user);
  const storedBriefing = (storedBriefings.data || {})[id || ""];

  // `!d && !isError` covers react-query's between-retries gap (isLoading briefly false while
  // data is still undefined) — without this the render below would crash on `d.ownerUserId`.
  if (c.isLoading || (!d && !c.isError)) return <Spinner label="Loading contract…" />;
  if (c.isError || !d) {
    const status = (c.error as any)?.status;
    return (
      <Empty>
        {status === 403
          ? "You don't have access to this contract. Request access from the Access screen."
          : "Contract not found."}
      </Empty>
    );
  }

  const canEdit = can("EDIT_CONTRACT");
  const isRequestor = !!user && (user.id === d.ownerUserId || user.id === (d as any).createdBy);

  const dismissConfirm = () => {
    setJustSubmitted(false);
    try { window.history.replaceState({}, ""); } catch { /* ignore */ }
  };

  const wfData: any = wf.data || {};
  const stages: { key: string; label: string }[] = (wfData.states || [])
    .filter((s: any) => s.key !== "closed_rejected")
    .map((s: any) => ({
      key: s.key,
      label: s.key.replace(/_/g, " ").replace(/\b\w/g, (c: string) => c.toUpperCase()),
    }));
  const currentIdx = stages.findIndex((s) => s.key === wfData.currentState);
  const openTasks: any[] = (wfData.tasks || []).filter((t: any) => t.status === "OPEN");
  const activeTask = openTasks.find((t: any) => t.state === wfData.currentState) || openTasks[0];

  return (
    <div className="space-y-4">
      {decision && activeTask && (
        <DecisionDialog
          kind={decision}
          contractNumber={d.contractNumber}
          busy={actOnTask.isPending}
          error={(actOnTask.error as any)?.message}
          onCancel={() => setDecision(null)}
          onConfirm={(note) => actOnTask.mutate({ taskId: activeTask.id, event: decision, comment: note })}
        />
      )}
      {lifecycle && (
        <div
          className="modal-backdrop fixed inset-0 z-50 flex items-center justify-center p-6"
          style={{ background: "rgba(15, 17, 21, 0.5)", backdropFilter: "blur(3px)" }}
          onClick={() => setLifecycle(null)}
        >
          <div className="modal-card card w-full max-w-sm p-6" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-base font-medium">
              {lifecycle === "close" ? "Close" : lifecycle === "recall" ? "Recall" : "Cancel"} <b>{d.contractNumber}</b>?
            </h2>
            <p className="text-sm text-ink-soft mt-2 leading-relaxed">
              {lifecycle === "close"
                ? "This closes the rejected request for good. It stays on record as closed but no longer needs action and can't be resubmitted."
                : lifecycle === "recall"
                ? "This pulls the request out of approval and cancels the current approval task, then reopens it in the New request form so you can edit and resubmit."
                : "This abandons the draft request. It stays on record as cancelled and can't be resubmitted."}
            </p>
            {((setStatus.error as any)?.message || (recall.error as any)?.message) && (
              <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>
                {(setStatus.error as any)?.message || (recall.error as any)?.message}
              </div>
            )}
            <div className="flex justify-end gap-2 mt-5">
              <button className="btn" onClick={() => setLifecycle(null)}>Go back</button>
              <button
                className="btn btn-primary"
                style={lifecycle === "recall" ? {} : { background: "var(--risk)", borderColor: "var(--risk)" }}
                disabled={setStatus.isPending || recall.isPending}
                onClick={() => {
                  if (lifecycle === "recall") { recall.reset(); recall.mutate(); }
                  else setStatus.mutate(lifecycle === "close" ? "CLOSED_REJECTED" : "CANCELLED");
                }}
              >
                {setStatus.isPending || recall.isPending
                  ? "Working…"
                  : lifecycle === "close"
                  ? "Close request"
                  : lifecycle === "recall"
                  ? "Recall & edit"
                  : "Cancel request"}
              </button>
            </div>
          </div>
        </div>
      )}
      {signStep === "confirm" && activeTask && (
        <div
          className="modal-backdrop fixed inset-0 z-50 flex items-center justify-center p-6"
          style={{ background: "rgba(15, 17, 21, 0.5)", backdropFilter: "blur(3px)" }}
          onClick={() => setSignStep(null)}
        >
          <div className="modal-card card w-full max-w-sm p-6" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-base font-medium">Send <b>{d.contractNumber}</b> for signature?</h2>
            <p className="text-sm text-ink-soft mt-2 leading-relaxed">
              This sends the final document to the counterparty and signatories for electronic
              signature. When all parties have signed, the contract becomes <b>Executed</b>.
            </p>
            {(actOnTask.error as any)?.message && (
              <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{(actOnTask.error as any).message}</div>
            )}
            <div className="flex justify-end gap-2 mt-5">
              <button className="btn" onClick={() => setSignStep(null)}>Cancel</button>
              <button
                className="btn btn-primary"
                disabled={actOnTask.isPending}
                onClick={() =>
                  actOnTask.mutate(
                    { taskId: activeTask.id, event: "complete", comment: "Sent for e-signature completion." },
                    { onSuccess: () => setSignStep("sent") },
                  )
                }
              >
                {actOnTask.isPending ? "Sending…" : "Send for signature"}
              </button>
            </div>
          </div>
        </div>
      )}
      {signStep === "sent" && (
        <div
          className="confirm-backdrop fixed inset-0 z-50 flex items-center justify-center p-6"
          style={{ background: "rgba(15, 17, 21, 0.5)", backdropFilter: "blur(5px)" }}
          onClick={(e) => { if (e.target === e.currentTarget) setSignStep(null); }}
        >
          <div className="confirm-card card w-full max-w-md p-8 text-center" onClick={(e) => e.stopPropagation()}>
            <div className="relative mx-auto mb-4" style={{ width: 72, height: 72 }}>
              <span
                className="confirm-halo absolute inset-0 rounded-full"
                style={{ background: "color-mix(in srgb, var(--ok) 35%, transparent)" }}
              />
              <svg className="confirm-check relative" viewBox="0 0 52 52" width={72} height={72}>
                <circle cx="26" cy="26" r="24" fill="none" stroke="var(--ok)" strokeWidth="2.5" />
                <path
                  d="M15 27 l8 8 l15 -16"
                  fill="none" stroke="var(--ok)" strokeWidth="3.5"
                  strokeLinecap="round" strokeLinejoin="round"
                />
              </svg>
            </div>
            <h2 className="text-lg font-medium">Sent for e-signature</h2>
            <p className="text-sm text-ink-soft mt-1">
              <span className="font-medium tabular text-ink">{d.contractNumber}</span> has been sent to
              the signatories to complete e-signature. All parties have signed and the contract is now{" "}
              <b>Executed</b>.
            </p>
            <button className="btn btn-primary mt-5 w-full justify-center" onClick={() => setSignStep(null)}>
              Done
            </button>
          </div>
        </div>
      )}
      {justSubmitted && (
        <div
          className="confirm-backdrop fixed inset-0 z-50 flex items-center justify-center p-6"
          style={{ background: "rgba(15, 17, 21, 0.5)", backdropFilter: "blur(5px)" }}
          onClick={(e) => { if (e.target === e.currentTarget) dismissConfirm(); }}
        >
          <div className="confirm-card card w-full max-w-md p-8 text-center" onClick={(e) => e.stopPropagation()}>
            <div className="relative mx-auto mb-4" style={{ width: 72, height: 72 }}>
              <span
                className="confirm-halo absolute inset-0 rounded-full"
                style={{ background: "color-mix(in srgb, var(--ok) 35%, transparent)" }}
              />
              <svg className="confirm-check relative" viewBox="0 0 52 52" width={72} height={72}>
                <circle cx="26" cy="26" r="24" fill="none" stroke="var(--ok)" strokeWidth="2.5" />
                <path
                  d="M15 27 l8 8 l15 -16"
                  fill="none" stroke="var(--ok)" strokeWidth="3.5"
                  strokeLinecap="round" strokeLinejoin="round"
                />
              </svg>
            </div>
            <h2 className="text-lg font-medium">Submitted successfully</h2>
            <p className="text-sm text-ink-soft mt-1">
              Your request number{" "}
              <span className="font-medium tabular text-ink">{d.requestNumber || d.contractNumber}</span>{" "}
              has been submitted successfully.
            </p>

            <div className="mt-6 mb-2 text-left">
              <div className="text-xs font-medium text-ink-faint uppercase tracking-wide mb-3 text-left">
                Approval workflow
              </div>
              {stages.length === 0 ? (
                <div className="flex items-center gap-2 text-sm text-ink-faint py-2">
                  <span className="spin inline-block w-3.5 h-3.5 rounded-full border-2 border-border border-t-[color:var(--accent)]" />
                  Preparing approval workflow…
                </div>
              ) : (
                <ol>
                  {stages.map((st, i) => {
                    const done = currentIdx >= 0 && i < currentIdx;
                    const active = i === currentIdx;
                    const last = i === stages.length - 1;
                    return (
                      <li key={st.key} className="confirm-stage flex gap-3" style={{ animationDelay: `${320 + i * 160}ms` }}>
                        <div className="flex flex-col items-center" style={{ width: 22 }}>
                          {done ? (
                            <span className="w-[22px] h-[22px] rounded-full flex items-center justify-center" style={{ background: "var(--ok)" }}>
                              <svg viewBox="0 0 12 12" width={11} height={11}>
                                <path d="M2.5 6.5 L5 9 L9.5 3.5" fill="none" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                              </svg>
                            </span>
                          ) : active ? (
                            <span
                              className="confirm-ring w-[22px] h-[22px] rounded-full flex items-center justify-center"
                              style={{ border: "2px solid var(--accent)", background: "var(--surface)" }}
                            >
                              <span className="w-1.5 h-1.5 rounded-full" style={{ background: "var(--accent)" }} />
                            </span>
                          ) : (
                            <span
                              className="w-[22px] h-[22px] rounded-full"
                              style={{ border: "2px solid var(--border)", background: "var(--surface)" }}
                            />
                          )}
                          {!last && (
                            <span
                              className="w-[2px] flex-1 my-0.5 rounded"
                              style={{ minHeight: 18, background: done ? "var(--ok)" : "var(--border)" }}
                            />
                          )}
                        </div>
                        <div style={{ paddingTop: 1, paddingBottom: last ? 0 : 16 }}>
                          <div className={`text-sm ${done || active ? "text-ink" : "text-ink-faint"}`}>{st.label}</div>
                          {active && (
                            <div className="text-xs mt-0.5" style={{ color: "var(--accent)" }}>
                              In progress
                            </div>
                          )}
                          {done && (
                            <div className="text-xs mt-0.5" style={{ color: "var(--ok)" }}>
                              Completed
                            </div>
                          )}
                          {active && activeTask?.dueAt && (
                            <div className="text-xs text-ink-faint mt-0.5">
                              Due {date(activeTask.dueAt)}
                            </div>
                          )}
                        </div>
                      </li>
                    );
                  })}
                </ol>
              )}
            </div>

            <button className="btn btn-primary mt-4 w-full justify-center" onClick={dismissConfirm}>
              View contract
            </button>
          </div>
        </div>
      )}

      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <button type="button" onClick={goBack} className="link text-sm flex items-center gap-1">
            <Icon.chevronLeft width={14} height={14} /> Back
          </button>
          <h1 className="text-xl font-medium mt-1">{d.title}</h1>
          <div className="text-sm text-ink-faint flex flex-wrap items-center gap-2 mt-1">
            <span className="tabular">{d.contractNumber}</span>
            <Badge tone={statusTone(d.status)}>{d.status}</Badge>
            <Badge tone={riskTone(d.riskTier)}>{d.riskTier || "risk n/a"}</Badge>
            {d.source === "MIGRATED" && <Badge tone="warn">migrated</Badge>}
            {d.parentContractId && (
              <Link to={`/contracts/${d.parentContractId}`} className="link">
                {d.relationshipType} parent
              </Link>
            )}
          </div>
        </div>
        <div className="flex gap-2 shrink-0 flex-wrap justify-end">
        {isRequestor && (
          <>
            {d.status === "DRAFT" && d.rejectionReason && d.intakeSessionId && (
              <button className="btn btn-primary" disabled={revise.isPending} onClick={() => revise.mutate()}>
                {revise.isPending ? "Opening…" : "Revise & resubmit"}
              </button>
            )}
            {d.status === "DRAFT" && !(d.rejectionReason && d.intakeSessionId) && (
              <button className="btn btn-primary" disabled={checking || startWf.isPending} onClick={beginSubmit}>
                {d.rejectionReason ? "Revise & resubmit" : "Submit for approval"}
              </button>
            )}
            {d.status === "DRAFT" && d.rejectionReason && (
              <button
                className="btn"
                style={{ borderColor: "var(--risk)", color: "var(--risk)" }}
                disabled={setStatus.isPending}
                onClick={() => { setStatus.reset(); setLifecycle("close"); }}
              >
                Close request
              </button>
            )}
            {d.status === "DRAFT" && !d.rejectionReason && (
              <button
                className="btn"
                disabled={setStatus.isPending}
                onClick={() => { setStatus.reset(); setLifecycle("cancel"); }}
              >
                Cancel request
              </button>
            )}
            {d.status === "IN_REVIEW" && (
              <button
                className="btn"
                disabled={setStatus.isPending || recall.isPending}
                onClick={() => { setStatus.reset(); recall.reset(); setLifecycle("recall"); }}
              >
                Recall &amp; edit
              </button>
            )}
          </>
        )}
        {activeTask && user?.id && activeTask.assignedUserId === user.id && (
          <>
            {wfData.availableEvents?.includes("approve") && (
              <button
                className="btn btn-primary"
                disabled={actOnTask.isPending}
                onClick={() => { actOnTask.reset(); setDecision("approve"); }}
              >
                Approve
              </button>
            )}
            {wfData.availableEvents?.includes("reject") && (
              <button
                className="btn"
                style={{ borderColor: "var(--risk)", color: "var(--risk)" }}
                disabled={actOnTask.isPending}
                onClick={() => { actOnTask.reset(); setDecision("reject"); }}
              >
                Reject
              </button>
            )}
            {wfData.availableEvents?.includes("complete") && (
              <button
                className="btn btn-primary"
                disabled={actOnTask.isPending}
                onClick={() => { actOnTask.reset(); setSignStep("confirm"); }}
              >
                Send for Signature
              </button>
            )}
          </>
        )}
        </div>
      </div>

      {d.status === "DRAFT" && d.rejectionReason && (
        <div
          className="rounded-lg border p-3 text-sm"
          style={{ borderColor: "var(--risk)", background: "color-mix(in srgb, var(--risk) 8%, transparent)" }}
        >
          <div className="font-medium" style={{ color: "var(--risk)" }}>
            {isRequestor
              ? "This request was rejected and returned to you"
              : "This request was rejected and returned to the requestor"}
          </div>
          <div className="text-ink-soft mt-1">{d.rejectionReason}</div>
          {isRequestor && (
            <div className="text-xs text-ink-faint mt-1.5">
              Edit the contract and use “Revise &amp; resubmit”, or “Close request” to close it out. Full history is in the Workflow tab.
            </div>
          )}
        </div>
      )}

      <WorkflowProgress
        wf={wfData}
        contractStatus={d.status}
        rejectionReason={d.rejectionReason}
      />

      <div className="lg:flex lg:gap-3 lg:items-start">
        {discussionOpen && (
          <DockablePanel
            title="Discussion"
            icon={<Icon.message width={14} height={14} />}
            cardAttr="data-discussion-card"
            pos={disc.pos}
            dockW={disc.dockW}
            headerExtra={
              d.discussionCount ? <span className="text-xs text-ink-faint">({d.discussionCount})</span> : undefined
            }
            onGrab={disc.startDrag}
            onDock={disc.pos ? disc.dock : undefined}
            onClose={() => toggleDiscussion(false)}
            onResize={disc.startResize}
            onDockResize={disc.startDockResize}
          >
            <div className="h-full overflow-y-auto p-3">
              <CommentThreads entityType="CONTRACT" entityId={id!} />
            </div>
          </DockablePanel>
        )}
        <div className="min-w-0 lg:flex-1 space-y-4">
          {(!discussionOpen || !reviewOpen || !recordOpen) && (
            <div className="flex flex-wrap gap-2">
              {!discussionOpen && (
                <button
                  className="btn"
                  style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
                  onClick={() => toggleDiscussion(true)}
                >
                  <Icon.message width={13} height={13} /> Show discussion
                  {d.discussionCount ? ` (${d.discussionCount})` : ""}
                </button>
              )}
              {!reviewOpen && (
                <button
                  className="btn"
                  style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
                  onClick={() => toggleReview(true)}
                >
                  <Icon.sparkle width={13} height={13} /> AI review
                </button>
              )}
              {!recordOpen && (
                <button
                  className="btn"
                  style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
                  onClick={() => toggleRecord(true)}
                >
                  <Icon.sparkle width={13} height={13} /> AI summary &amp; briefing
                </button>
              )}
            </div>
          )}
          {recordOpen && (
            <Card className="!p-0 overflow-hidden">
              <div className="w-full flex items-center justify-between px-4 py-3" style={{ background: "var(--surface-2)" }}>
                <span className="text-sm font-medium text-ink-soft uppercase tracking-wide">AI summary &amp; briefing</span>
                <button className="btn !p-1 !border-0" title="Hide" onClick={() => toggleRecord(false)}>
                  <Icon.x width={14} height={14} />
                </button>
              </div>
              <div className="px-4 pb-4 pt-1 space-y-4 fade-in">
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <h2 className="text-sm font-medium text-ink-soft uppercase tracking-wide">Summary</h2>
                    {(summarize.data as any)?.aiDisabled ? (
                      <span className="text-xs text-ink-faint">AI off — enable Agent Crew in the header</span>
                    ) : (
                      <button className="btn btn-ai" style={{ padding: "0.3rem 0.6rem" }} disabled={summarize.isPending} onClick={() => summarize.mutate()}>
                        <Icon.sparkle width={14} height={14} /> {summarize.isPending ? "Reviewing…" : storedBriefing ? "Regenerate AI briefing" : "Generate AI briefing"}
                      </button>
                    )}
                  </div>
                  <p className="text-[0.95rem] leading-relaxed font-serif">
                    {d.summary && d.intakeSessionId ? (
                      renderSummaryWithSession(d.summary, d.intakeSessionId)
                    ) : (
                      d.summary || "No summary recorded yet."
                    )}
                  </p>
                  {(storedBriefing || summarize.isPending) && (
                    <div className="mt-3">
                      {summarize.isPending && !storedBriefing ? (
                        <p className="text-sm text-ink-faint">Preparing AI briefing…</p>
                      ) : (
                        <BriefingCard
                          briefing={storedBriefing?.briefing}
                          generatedAt={storedBriefing?.generatedAt}
                          cached
                        />
                      )}
                    </div>
                  )}
                </div>
              </div>
            </Card>
          )}
          <Tabs
            tabs={[
          { key: "overview", label: "Overview" },
          { key: "terms", label: "Key terms", count: (d.effectiveTerms || []).length },
          { key: "document", label: "Document", count: d.documentCount ?? (d.versions || []).length },
          { key: "workflow", label: "Workflow" },
          { key: "obligations", label: "Obligations", count: (d.obligations || []).length },
          { key: "relations", label: "Relations", count:
              (relations.data?.relations?.length ?? 0)
              + (d.parentContractId ? 1 : 0)
              + (d.children || []).length },
          { key: "risks", label: "Risks", count: (risks.data || []).filter((r: any) => r.status === "OPEN").length },
          { key: "audit", label: "Audit trail" },
        ]}
        active={tab}
        onChange={(t) => {
          setTab(t);
          toggleRecord(false);
        }}
      />

      {tab === "overview" && (
        <div className="space-y-4 fade-in">
          <div className="grid md:grid-cols-4 gap-3 stagger">
            <KV label="Contracting entity" value={d.entityName} sub={d.entity} />
            <KV label="Counterparties" value={(d.parties || []).map((p: any) => p.legalName).join(", ") || "—"} />
            <KV label="Governing law" value={d.governingLaw || "—"} />
            <KV label="Value" value={money(d.valueAmount, d.currency)} sub={d.valueBasis} />
            <KV label="Annual value" value={money(d.annualValueAmount, d.currency)} />
            <KV label="Payment terms" value={d.paymentTermsDays ? `${d.paymentTermsDays} days` : "—"} />
            <KV label="Effective" value={date(d.effectiveDate)} />
            <KV
              label="Expiry"
              value={date(d.expiryDate)}
              sub={d.renewalType && d.renewalType !== "NONE" ? `${enumLabel(d.renewalType)} renewal` : undefined}
            />
            <KV label="Owner" value={d.owner || "—"} />
            <KV label="Assigned lawyer" value={d.assignedLawyer || "—"} />
            {d.liabilitySummary && <KV label="Liability" value={d.liabilitySummary} />}
          </div>

          <div className="grid md:grid-cols-2 gap-4">
            <Card>
              <SectionTitle>Deal terms</SectionTitle>
              {Object.keys(grouped).length === 0 ? (
                <Empty>No structured attributes captured.</Empty>
              ) : (
                <div className="space-y-3">
                  {Object.entries(grouped).map(([g, items]) => (
                    <div key={g}>
                      <div className="text-[11px] uppercase tracking-wide text-ink-faint mb-1">{g}</div>
                      <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
                        {items.map((it) => (
                          <div key={it.key}>
                            <dt className="text-xs text-ink-faint">{it.label}</dt>
                            <dd className="tabular">
                              {typeof it.value === "boolean" ? (
                                <Badge tone={it.value ? "ok" : "neutral"}>{it.value ? "Yes" : "No"}</Badge>
                              ) : it.money ? (
                                money(Number(it.value), d.currency)
                              ) : (
                                enumLabel(it.value)
                              )}
                            </dd>
                          </div>
                        ))}
                      </dl>
                    </div>
                  ))}
                </div>
              )}
            </Card>
            <Card>
              <SectionTitle>Clauses used</SectionTitle>
              {(d.clausesUsed || []).length === 0 ? (
                <Empty>No clause library links yet — assemble the document.</Empty>
              ) : (
                <ul className="space-y-1.5 text-sm">
                  {(d.clausesUsed || []).map((u: any, i: number) => (
                    <li key={i} className="flex items-center justify-between">
                      <span>{u.concept}</span>
                      <Badge
                        tone={
                          u.positionTier === "PREFERRED"
                            ? "ok"
                            : u.positionTier === "FALLBACK"
                            ? "warn"
                            : u.positionTier === "UNACCEPTABLE"
                            ? "risk"
                            : "neutral"
                        }
                      >
                        {u.positionTier}
                      </Badge>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <ParticipantsCard contractId={id!} participants={d.participants || []} canEdit={canEdit} />
          </div>
          {(d.children || []).length > 0 && (
            <Card>
              <SectionTitle>Hierarchy</SectionTitle>
              <ul className="text-sm space-y-1">
                {(d.children || []).map((ch: any) => (
                  <li key={ch.id}>
                    <Link to={`/contracts/${ch.id}`} className="link">{ch.contractNumber}</Link>
                    <span className="text-ink-faint"> — {ch.relationshipType} · {ch.title} · {ch.status}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}

      {tab === "terms" && (
        <Card className="!p-0 overflow-x-auto fade-in">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-ink-faint border-b border-border">
                <th className="px-3 py-2 font-medium">Term</th>
                <th className="px-3 py-2 font-medium">Effective value</th>
                <th className="px-3 py-2 font-medium">Source</th>
                <th className="px-3 py-2 font-medium">Provenance</th>
                <th className="px-3 py-2 font-medium">Confidence</th>
              </tr>
            </thead>
            <tbody>
              {(d.effectiveTerms || []).map((t: any, i: number) => (
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-3 py-2.5">{t.key}</td>
                  <td className="px-3 py-2.5 tabular font-medium">{String(t.value)}</td>
                  <td className="px-3 py-2.5 text-ink-soft">{t.sourceContractNumber}</td>
                  <td className="px-3 py-2.5">
                    {t.inherited && <Badge tone="neutral">inherited</Badge>}{" "}
                    {t.overridden && <Badge tone="accent">overrides</Badge>}
                    {!t.inherited && !t.overridden && <span className="text-ink-faint">own</span>}
                  </td>
                  <td className="px-3 py-2.5">
                    <Confidence value={t.extractionConfidence} verified={t.verified} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {tab === "document" && (
        <div className="fade-in flex flex-col gap-3 lg:h-[calc(0.9428*min(100vw-276px,1240px)+72px)]">
          <div className="min-w-0 flex-1 min-h-0"><DocumentPanel contractId={id!} /></div>
          {(attachmentsQuery.data || []).length > 0 && (
            <Card className="shrink-0">
              <SectionTitle>Supporting documents</SectionTitle>
              <div className="space-y-1.5">
                {(attachmentsQuery.data || []).map((a: any) => (
                  <div key={a.id} className="flex items-center gap-2 rounded-[8px] border border-border p-2">
                    <Icon.file width={15} height={15} className="shrink-0 text-ink-faint" />
                    <span className="text-sm truncate flex-1" title={a.filename}>{a.filename}</span>
                    <span className="text-xs text-ink-faint shrink-0">{(a.size / 1024).toFixed(0)} KB</span>
                    <button
                      className="btn shrink-0"
                      style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
                      onClick={() => downloadAttachment(a.id, a.filename)}
                    >
                      <Icon.externalLink width={13} height={13} /> Download
                    </button>
                    {d.status === "DRAFT" && isRequestor && (
                      removeArm === a.id ? (
                        <span className="flex items-center gap-1 shrink-0">
                          <button
                            className="btn shrink-0"
                            style={{ padding: "0.25rem 0.5rem", fontSize: "0.8125rem", borderColor: "var(--risk)", color: "var(--risk)" }}
                            disabled={removeAttachment.isPending}
                            onClick={() => removeAttachment.mutate(a.id)}
                          >
                            {removeAttachment.isPending ? "Removing…" : "Remove"}
                          </button>
                          <button className="btn shrink-0" style={{ padding: "0.25rem 0.5rem", fontSize: "0.8125rem" }} onClick={() => setRemoveArm(null)}>
                            Cancel
                          </button>
                        </span>
                      ) : (
                        <button
                          className="shrink-0 text-ink-faint hover:text-[color:var(--risk)] transition-colors"
                          title="Remove this document"
                          onClick={() => setRemoveArm(a.id)}
                        >
                          <Icon.x width={14} height={14} />
                        </button>
                      )
                    )}
                  </div>
                ))}
              </div>
              {removeAttachment.error && (
                <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>
                  {(removeAttachment.error as any).message}
                </div>
              )}
            </Card>
          )}
        </div>
      )}

      {tab === "workflow" && (
        <Card className="fade-in">
          <SectionTitle
            right={
              !wf.data?.started && canEdit && (
                <button className="btn btn-primary" onClick={beginSubmit} disabled={checking || startWf.isPending}>
                  Start workflow
                </button>
              )
            }
          >
            Workflow
          </SectionTitle>
          {wf.isLoading ? (
            <Spinner />
          ) : !wf.data?.started ? (
            <Empty>No workflow running for this contract.</Empty>
          ) : (
            <div className="space-y-3">
              <div className="text-sm">
                <span className="text-ink-faint">{wf.data.workflow}</span> · current state{" "}
                <Badge tone="accent">{wf.data.currentState}</Badge> · {wf.data.status}
                {wf.data.escalated && <Badge tone="risk">escalated</Badge>}
              </div>
              <div className="divide-y divide-border">
                {(wf.data.tasks || []).map((t: any) => (
                  <div key={t.id} className="py-2 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span>{t.state} · {t.type} · {t.assignee || "unassigned"}</span>
                      <span className="text-ink-faint text-xs whitespace-nowrap">
                        {t.status} {t.outcome && `→ ${t.outcome}`}
                        {t.completedAt && ` · ${date(t.completedAt)}`}
                        {t.overdue && <Badge tone="risk">overdue</Badge>}
                      </span>
                    </div>
                    {t.comments && (
                      <div
                        className="mt-1 text-xs text-ink-soft border-l-2 pl-2"
                        style={{ borderColor: t.outcome === "reject" ? "var(--risk)" : "var(--border)" }}
                      >
                        <span className="text-ink-faint">
                          {t.outcome === "reject" ? "Rejection reason" : "Comment"}:
                        </span>{" "}
                        {t.comments}
                      </div>
                    )}
                  </div>
                ))}
              </div>
              <p className="text-xs text-ink-faint">
                Approve or reject from the header actions above (or the Approvals screen). A rejection
                needs a reason; it returns the request to the requestor as a draft to revise and
                resubmit, and the reason is kept here on the workflow record.
              </p>
            </div>
          )}
        </Card>
      )}

      {tab === "obligations" && (
        <Card className="!p-0 overflow-x-auto fade-in">
          {(d.obligations || []).length === 0 ? (
            <Empty>No obligations tracked.</Empty>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-ink-faint border-b border-border">
                  <th className="px-3 py-2 font-medium">Obligation</th>
                  <th className="px-3 py-2 font-medium">Due</th>
                  <th className="px-3 py-2 font-medium">Owner</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                  <th className="px-3 py-2 font-medium">Confidence</th>
                </tr>
              </thead>
              <tbody>
                {(d.obligations || []).map((o: any) => (
                  <tr key={o.id} className="border-b border-border last:border-0">
                    <td className="px-3 py-2.5">{o.description}<div className="text-xs text-ink-faint">{o.type}</div></td>
                    <td className="px-3 py-2.5 tabular">{date(o.dueDate)}</td>
                    <td className="px-3 py-2.5">{o.owner || o.owningDepartment || "—"}</td>
                    <td className="px-3 py-2.5"><Badge tone={statusTone(o.status)}>{o.status}</Badge></td>
                    <td className="px-3 py-2.5"><Confidence value={o.extractionConfidence} verified={o.verified} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}

      {tab === "relations" && (
        <div className="space-y-4 fade-in">
          <Card>
            <div className="flex items-start justify-between gap-3">
              <div>
                <SectionTitle>Contract relations</SectionTitle>
                <p className="text-xs text-ink-faint mt-0.5">
                  Explicit hierarchy, sourced precedents, and AI-identified relationships.
                  Suggestions are matched on party name, signed entity and active period — confirm only what is real.
                </p>
              </div>
              <button
                className="btn btn-ai shrink-0"
                onClick={() => detectRelations.mutate()}
                disabled={detectRelations.isPending}
              >
                {detectRelations.isPending ? "Scanning contracts…" : "Detect with AI"}
              </button>
            </div>
            {relations.isLoading && <Spinner />}
            {detectRelations.error && (
              <p className="text-xs text-[color:var(--risk)] mt-2">{(detectRelations.error as Error).message}</p>
            )}

            {/* hierarchy */}
            {(d.parentContractId || (d.children || []).length > 0) && (
              <div className="mt-4">
                <div className="text-[11px] uppercase tracking-wide text-ink-faint mb-2">Hierarchy</div>
                <div className="space-y-1.5 text-sm">
                  {d.parentContractId && (
                    <div className="flex items-center gap-2">
                      <Badge tone="neutral">{d.relationshipType ? relationLabel(d.relationshipType) : "Parent"}</Badge>
                      <Link to={`/contracts/${d.parentContractId}`} className="link">Parent contract</Link>
                    </div>
                  )}
                  {(d.children || []).map((ch: any) => (
                    <div key={ch.id} className="flex items-center gap-2">
                      <Badge tone="neutral">{ch.relationshipType ? relationLabel(ch.relationshipType) : "Child"}</Badge>
                      <Link to={`/contracts/${ch.id}`} className="link">{ch.contractNumber} — {ch.title}</Link>
                      <Badge tone={statusTone(ch.status)}>{enumLabel(ch.status)}</Badge>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </Card>

          {/* AI suggestions */}
          {(relations.data?.suggested || []).length > 0 && (
            <Card>
              <SectionTitle>AI-suggested — awaiting your confirmation</SectionTitle>
              <div className="space-y-3 mt-2">
                {(relations.data.suggested as any[]).map((r) => (
                  <div key={r.id} className="border border-[color:var(--ai)]/40 rounded-lg p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <Badge tone="ai">{relationLabel(r.relationType)}</Badge>
                          <Link to={`/contracts/${r.otherContractId}`} className="link font-medium">
                            {r.contractNumber} — {r.title}
                          </Link>
                          <Badge tone="neutral">{enumLabel(r.type)}</Badge>
                          <Badge tone={statusTone(r.status)}>{enumLabel(r.status)}</Badge>
                        </div>
                        {typeof r.confidence === "number" && (
                          <div className="text-xs text-ink-faint mt-1">
                            Confidence {Math.round(r.confidence * 100)}%
                          </div>
                        )}
                        <ul className="text-xs text-ink-faint mt-1.5 list-disc pl-4 space-y-0.5">
                          {(Array.isArray(r.reasons) ? r.reasons : []).map((x: string, j: number) => (
                            <li key={j}>{x}</li>
                          ))}
                        </ul>
                      </div>
                      <div className="flex flex-col gap-2 shrink-0">
                        <button
                          className="btn"
                          disabled={decideRelation.isPending}
                          onClick={() => decideRelation.mutate({ relId: r.id, decision: "confirm" })}
                        >
                          Confirm
                        </button>
                        <button
                          className="btn"
                          disabled={decideRelation.isPending}
                          onClick={() => decideRelation.mutate({ relId: r.id, decision: "reject" })}
                        >
                          Dismiss
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* confirmed relations */}
          {(relations.data?.confirmed || []).length > 0 && (
            <Card>
              <SectionTitle>Confirmed relations</SectionTitle>
              <div className="space-y-2 mt-2">
                {(relations.data.confirmed as any[]).map((r) => (
                  <div key={r.id} className="flex items-center gap-2 flex-wrap text-sm border-b border-border pb-2 last:border-0">
                    <Badge tone="ok">{relationLabel(r.relationType)}</Badge>
                    <Link to={`/contracts/${r.otherContractId}`} className="link font-medium">
                      {r.contractNumber} — {r.title}
                    </Link>
                    <Badge tone={statusTone(r.status)}>{enumLabel(r.status)}</Badge>
                    {r.decidedBy && <span className="text-xs text-ink-faint">confirmed by {r.decidedBy}</span>}
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* precedent basis */}
          <Card>
            <SectionTitle>Precedent basis</SectionTitle>
            {(d.precedents || []).length === 0 ? (
              <Empty>No precedent links.</Empty>
            ) : (
              <div className="space-y-3">
                {(d.precedents || []).map((p: any, i: number) => (
                  <div key={i} className="border-b border-border pb-3 last:border-0">
                    <div className="flex items-center justify-between">
                      <Link to={`/contracts/${p.precedentContractId}`} className="link font-medium">
                        {p.precedentNumber} — {p.precedentTitle}
                      </Link>
                      <Badge tone="ai">match {Math.round(p.matchScore * 100)}%</Badge>
                    </div>
                    <ul className="text-xs text-ink-faint mt-1 list-disc pl-4">
                      {(Array.isArray(p.matchReasons) ? p.matchReasons : []).map((r: string, j: number) => (
                        <li key={j}>{r}</li>
                      ))}
                    </ul>
                    <div className="text-xs mt-1">Used for {p.usedFor} · {p.acknowledged ? "acknowledged" : "not acknowledged"}</div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      )}

      {tab === "risks" && (
        <RiskRegister
          contractId={id!}
          onLocate={(quote) => {
            useHighlight.getState().locate(quote);
            setTab("document");
          }}
        />
      )}

      {tab === "audit" && (
        <Card className="!p-0 overflow-x-auto fade-in">
          {audit.isLoading ? (
            <Spinner />
          ) : (audit.data || []).length === 0 ? (
            <Empty>No audit events.</Empty>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-ink-faint border-b border-border">
                  <th className="px-3 py-2 font-medium">When</th>
                  <th className="px-3 py-2 font-medium">Action</th>
                  <th className="px-3 py-2 font-medium">Actor</th>
                  <th className="px-3 py-2 font-medium">Detail</th>
                </tr>
              </thead>
              <tbody>
                {(audit.data || []).map((e: any) => (
                  <tr key={e.id} className="border-b border-border last:border-0">
                    <td className="px-3 py-2 tabular text-xs">{new Date(e.occurredAt).toLocaleString()}</td>
                    <td className="px-3 py-2">{e.action}</td>
                    <td className="px-3 py-2">{e.actor} {e.actorType === "AI" && <Badge tone="ai">AI</Badge>}</td>
                    <td className="px-3 py-2 text-xs text-ink-faint">{e.after ? JSON.stringify(e.after) : ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}
        </div>
        {reviewOpen && (
          <DockablePanel
            title="AI review"
            icon={<Icon.sparkle width={14} height={14} style={{ color: "var(--ai)" }} />}
            cardAttr="data-airev-card"
            pos={review.pos}
            dockW={review.dockW}
            onGrab={review.startDrag}
            onDock={review.pos ? review.dock : undefined}
            onClose={() => toggleReview(false)}
            onResize={review.startResize}
            onDockResize={review.startDockResize}
          >
            <div className="h-full overflow-y-auto p-3">
              <AiReviewPanel contractId={id!} bare />
            </div>
          </DockablePanel>
        )}
      </div>

      {(checking || qcError || quickCheck) && (
        <div className="fixed inset-0 z-50 grid place-items-center p-4" style={{ background: "rgba(0,0,0,0.45)" }}>
          <div className="card w-full max-w-lg max-h-[85vh] overflow-y-auto p-5 space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-medium flex items-center gap-2">
                <Icon.sparkle width={16} height={16} /> AI quick check
              </h2>
              <Badge tone="ai">required before submit</Badge>
            </div>

            {checking && <Spinner label="The AI is reviewing the draft against all check points…" />}

            {!checking && qcError && (
              <>
                <p className="text-sm" style={{ color: "var(--risk)" }}>{qcError}</p>
                <div className="flex gap-2 justify-end">
                  <button className="btn" onClick={() => { setQcError(null); setQuickCheck(null); }}>Cancel</button>
                  <button
                    className="btn btn-primary"
                    disabled={startWf.isPending}
                    onClick={() => { setQcError(null); setQuickCheck(null); startWf.mutate(); }}
                  >
                    Submit without check
                  </button>
                </div>
              </>
            )}

            {!checking && quickCheck && (
              <>
                {quickCheck.criticalCount > 0 ? (
                  <div className="rounded-[8px] px-3 py-2 text-sm" style={{ background: "color-mix(in srgb, var(--risk) 12%, transparent)", color: "var(--risk)" }}>
                    Warning: {quickCheck.criticalCount} critical issue{quickCheck.criticalCount > 1 ? "s" : ""} found. You can still submit, but reviewers will see these findings.
                  </div>
                ) : (
                  <div className="rounded-[8px] px-3 py-2 text-sm" style={{ background: "color-mix(in srgb, var(--ok) 12%, transparent)" }}>
                    {(quickCheck.findings || []).length} issue{(quickCheck.findings || []).length === 1 ? "" : "s"} found — no critical issues.
                  </div>
                )}
                {(quickCheck.findings || []).length > 0 && (
                  <div className="space-y-2 max-h-72 overflow-y-auto">
                    {[...quickCheck.findings]
                      .sort((a: any, b: any) => sevRank(b.severity) - sevRank(a.severity))
                      .map((f: any, i: number) => (
                        <div key={i} className="rounded-[8px] border p-2.5">
                          <div className="flex items-center justify-between gap-2 mb-1">
                            <span className="text-sm font-medium">{f.title}</span>
                            <Badge tone={riskTone(f.severity) as any}>{f.severity || "MEDIUM"}</Badge>
                          </div>
                          <div className="text-xs text-ink-soft">{f.detail}</div>
                        </div>
                      ))}
                  </div>
                )}
                <div className="text-[11px] text-ink-faint">
                  Findings are also recorded in the risk register and visible to reviewers.
                </div>
                <div className="flex gap-2 justify-end">
                  <button className="btn" onClick={() => setQuickCheck(null)}>
                    Go back
                  </button>
                  <button className="btn btn-primary" disabled={startWf.isPending} onClick={() => { setQuickCheck(null); startWf.mutate(); }}>
                    {quickCheck.criticalCount > 0 ? "Submit anyway" : "Submit for approval"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ParticipantsCard({
  contractId,
  participants,
  canEdit,
}: {
  contractId: string;
  participants: any[];
  canEdit: boolean;
}) {
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [userId, setUserId] = useState("");
  const users = useQuery({ queryKey: ["users-ref"], queryFn: () => api("/refdata/users"), enabled: adding });
  const add = useMutation({
    mutationFn: () => api(`/contracts/${contractId}/participants`, { method: "POST", json: { userId, role: "VIEWER" } }),
    onSuccess: () => { setAdding(false); setUserId(""); qc.invalidateQueries({ queryKey: ["contract", contractId] }); },
  });
  const remove = useMutation({
    mutationFn: (uid: string) => api(`/contracts/${contractId}/participants/${uid}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["contract", contractId] }),
  });

  return (
    <Card>
      <SectionTitle right={canEdit && !adding ? <button className="link text-xs" onClick={() => setAdding(true)}>add person</button> : undefined}>
        People with access
      </SectionTitle>
      {participants.length === 0 && !adding ? (
        <Empty>Only the owner and assigned lawyer.</Empty>
      ) : (
        <ul className="space-y-1.5 text-sm">
          {participants.map((p: any) => (
            <li key={p.userId} className="flex items-center justify-between">
              <span className="flex items-center gap-2">
                <span className="w-6 h-6 rounded-full grid place-items-center text-[10px] font-medium text-white shrink-0" style={{ background: "var(--ink-faint)" }}>
                  {(p.name || "?").slice(0, 1)}
                </span>
                {p.name} <Badge tone="neutral">{p.role.toLowerCase()}</Badge>
              </span>
              {canEdit && (
                <button className="link text-xs" onClick={() => remove.mutate(p.userId)}>remove</button>
              )}
            </li>
          ))}
        </ul>
      )}
      {adding && (
        <div className="flex gap-2 mt-2">
          <select className="input" value={userId} onChange={(e) => setUserId(e.target.value)}>
            <option value="">— choose —</option>
            {(users.data || []).map((u: any) => (
              <option key={u.id} value={u.id}>{u.displayName}</option>
            ))}
          </select>
          <button className="btn btn-primary" disabled={!userId || add.isPending} onClick={() => add.mutate()}>Add</button>
          <button className="btn" onClick={() => setAdding(false)}>Cancel</button>
        </div>
      )}
    </Card>
  );
}

function KV({ label, value, sub }: { label: string; value: React.ReactNode; sub?: string }) {
  return (
    <div className="card p-3 lift">
      <div className="text-xs text-ink-faint mb-0.5">{label}</div>
      <div className="text-sm tabular">{value}</div>
      {sub && <div className="text-xs text-ink-faint mt-0.5 capitalize">{sub}</div>}
    </div>
  );
}
