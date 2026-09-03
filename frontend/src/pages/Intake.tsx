import React, { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiForm, apiBlob, apiStream, apiText } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty } from "../components/ui";
import { AiAffordance } from "../components/AiAffordance";
import { Icon } from "../components/icons";
import { DocumentPanel } from "../components/DocumentPanel";
import { AiReviewPanel } from "../components/AiReviewPanel";
import { DockablePanel, useDockablePanel } from "../components/DockablePanel";

type Msg = { role: string; content: string };

/** Small rounded-rectangle action link used under the chat greeting (neutral, warms to accent on hover). */
const actionLinkCls =
  "inline-flex items-center gap-1.5 rounded-[8px] border border-border bg-surface px-2.5 py-1 text-xs font-medium text-ink-soft transition-colors hover:border-[color:var(--accent)] hover:text-[color:var(--accent)]";
type Field = {
  key: string;
  label: string;
  type: "enum" | "text" | "textarea" | "boolean" | "number" | "date" | "users";
  required: boolean;
  options?: { value: string; label: string }[] | null;
  help?: string | null;
  group?: string | null;
  money?: boolean;
};

const provLabel = (p?: string) =>
  (p || "").replace(/_/g, " ").toLowerCase().replace("unconfirmed", "").trim();

/** Field-group → brand color, so each section of the request form reads as its own zone. */
const GROUP_COLORS: Record<string, string> = {
  BASICS: "var(--accent)",
  SCOPE: "var(--teal)",
  "TERM & RENEWAL": "var(--orange)",
  "DATA & PRIVACY": "var(--info)",
  GOVERNANCE: "var(--ai)",
  "EXPECTED ROUTING": "var(--warn)",
};
const GROUP_PALETTE = GROUP_COLORS;
const GROUP_RAINBOW = ["var(--accent)", "var(--teal)", "var(--info)", "var(--ai)", "var(--orange)", "var(--warn)"];
function groupColor(groupName: string): string {
  const k = groupName.trim().toUpperCase();
  if (GROUP_PALETTE[k]) return GROUP_PALETTE[k];
  let h = 0;
  for (const ch of k) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return GROUP_RAINBOW[h % GROUP_RAINBOW.length];
}

function groupFields(spec: Field[]): [string, Field[]][] {
  const groups: Record<string, Field[]> = {};
  const order: string[] = [];
  for (const f of spec) {
    const g = (f as any).group || "Basics";
    if (!groups[g]) { groups[g] = []; order.push(g); }
    groups[g].push(f);
  }
  return order.map((g) => [g, groups[g]]);
}

export default function Intake() {
  const nav = useNavigate();
  const location = useLocation();
  const qc = useQueryClient();
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [session, setSession] = useState<any>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [status, setStatus] = useState("");
  const [streamText, setStreamText] = useState("");
  const [chosenPrecedent, setChosenPrecedent] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [result, setResult] = useState<any>(null);
  const [reviseReason, setReviseReason] = useState(""); // set when resuming a rejected request to revise
  const [draft, setDraft] = useState<Record<string, any>>({}); // local edits pending save
  const draftRef = useRef<Record<string, any>>({});            // always-fresh copy for async saves
  const [saving, setSaving] = useState(false);
  const [startPicked, setStartPicked] = useState<any>(null); // a recent contract the user tapped
  const [showRecent, setShowRecent] = useState(false);
  const [showAllRecent, setShowAllRecent] = useState(false);
  const RECENT_PREVIEW = 5;
  const [otherFor, setOtherFor] = useState<string | null>(null); // field key with the inline free-text input open
  const [otherText, setOtherText] = useState("");
  const [picked, setPicked] = useState<Record<string, string>>({}); // field key → chosen quick answer
  const [submittingApproval, setSubmittingApproval] = useState(false);
  const [confirmAction, setConfirmAction] = useState<"submit" | "cancel" | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [paperMode, setPaperMode] = useState(false); // 3rd-party paper upload flow

  // the AI assistant chat is a movable / dockable / resizable panel
  const [chatOpen, setChatOpen] = useState(() => {
    try { return localStorage.getItem("clm-intake-chat.open") !== "0"; } catch { return true; }
  });
  const toggleChat = (v: boolean) => {
    setChatOpen(v);
    try { localStorage.setItem("clm-intake-chat.open", v ? "1" : "0"); } catch { /* ignore */ }
  };
  const chat = useDockablePanel("clm-intake-chat", "[data-intake-chat-card]", { defaultW: 460, defaultDockW: 440 });
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [attachments, setAttachments] = useState<any[]>([]);
  const [attachUploading, setAttachUploading] = useState(false);
  const [attachError, setAttachError] = useState("");
  const supportInputRef = useRef<HTMLInputElement>(null);

  async function loadAttachments() {
    if (!sessionId) return;
    try {
      setAttachments(await api(`/intake/sessions/${sessionId}/attachments`));
    } catch {
      /* non-critical */
    }
  }

  useEffect(() => {
    loadAttachments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  async function openPaperPreview() {
    if (!sessionId || previewLoading) return;
    setPreviewLoading(true);
    try {
      setPreviewHtml(await apiText(`/intake/sessions/${sessionId}/paper`));
    } catch (e: any) {
      setUploadError(e.message || String(e));
    } finally {
      setPreviewLoading(false);
    }
  }

  async function addSupporting(files: FileList | null) {
    if (!sessionId || !files?.length || attachUploading) return;
    setAttachUploading(true);
    setAttachError("");
    try {
      for (const file of Array.from(files)) {
        const fd = new FormData();
        fd.append("file", file);
        await apiForm(`/intake/sessions/${sessionId}/attachments`, fd);
      }
      await loadAttachments();
    } catch (e: any) {
      setAttachError(e.message || String(e));
    } finally {
      setAttachUploading(false);
    }
  }

  async function removeSupporting(id: string) {
    if (!sessionId) return;
    try {
      await api(`/intake/sessions/${sessionId}/attachments/${id}`, { method: "DELETE" });
      await loadAttachments();
    } catch (e: any) {
      setAttachError(e.message || String(e));
    }
  }

  async function downloadAttachment(attachmentId: string, filename: string) {
    const blob = await apiBlob(`/intake/sessions/${sessionId}/attachments/${attachmentId}/file`);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function uploadPaper(file: File) {
    if (!sessionId || uploading) return;
    setUploading(true);
    setUploadError("");
    try {
      const fd = new FormData();
      fd.append("file", file);
      const s: any = await apiForm(`/intake/sessions/${sessionId}/upload-paper`, fd);
      const conv: Msg[] = s.conversation || [];
      const lastAssistant = conv.length && conv[conv.length - 1]?.role === "assistant" ? conv.length - 1 : conv.length;
      setMessages([
        ...conv.slice(0, lastAssistant),
        { role: "user", content: `Uploaded third-party paper: ${file.name}` },
        ...conv.slice(lastAssistant),
      ]);
      setSession(s);
      setDraft({});
      draftRef.current = {};
      setPicked({});
      setPaperMode(false);
    } catch (e: any) {
      setUploadError(e.message || String(e));
    } finally {
      setUploading(false);
    }
  }

  const recent = useQuery({ queryKey: ["recent-contracts"], queryFn: () => api("/me/recent-contracts") });
  const topTypes = useQuery({ queryKey: ["top-contract-types"], queryFn: () => api("/me/top-contract-types") });
  const [showDrafts, setShowDrafts] = useState(false);
  const sessionsQ = useQuery({ queryKey: ["intake-sessions"], queryFn: () => api("/intake/sessions") });

  const resetWorkspace = () => {
    setDraft({});
    draftRef.current = {};
    setPicked({});
    setChosenPrecedent(null);
    setStartPicked(null);
    setOtherFor(null);
    setOtherText("");
    setPreviewHtml(null);
    setInput("");
    setStreamText("");
    setStatus("");
    setSubmitError("");
    setReviseReason("");
  };

  // flush unsaved field edits before switching sessions so nothing typed is lost
  async function flushEdits() {
    if (sessionId && Object.keys(draftRef.current).length) await saveFields();
  }

  async function resumeSession(id: string) {
    if (streaming || id === sessionId) { setShowDrafts(false); return; }
    await flushEdits();
    setShowDrafts(false);
    try {
      const s = await api(`/intake/sessions/${id}`);
      setResult(null);
      setSessionId(s.id);
      setSession(s);
      setMessages(s.conversation || []);
      resetWorkspace();
      toggleChat(true);
    } catch (e: any) {
      setSubmitError(e.message || String(e));
    }
  }

  async function newChat() {
    if (streaming) return;
    await flushEdits();
    try {
      const s = await api("/intake/sessions", { method: "POST" });
      setResult(null);
      setPaperMode(false);
      setSessionId(s.id);
      setSession(s);
      setMessages(s.conversation || []);
      resetWorkspace();
      sessionsQ.refetch();
    } catch (e: any) {
      setSubmitError(e.message || String(e));
    }
  }

  const [deleteArm, setDeleteArm] = useState<string | null>(null);

  // explicit save: flush field edits, then mark the session as a saved draft
  async function saveDraft() {
    if (!sessionId || streaming) return;
    await saveFields();
    try {
      const s = await api(`/intake/sessions/${sessionId}/save`, { method: "POST" });
      setSession(s);
      sessionsQ.refetch();
    } catch (e: any) {
      setSubmitError(e.message || String(e));
    }
  }

  async function deleteSession(id: string) {
    try {
      await api(`/intake/sessions/${id}`, { method: "DELETE" });
      setDeleteArm(null);
      if (id === sessionId) {
        if (!streaming) await newChat();
        else sessionsQ.refetch();
      } else {
        sessionsQ.refetch();
      }
      setShowDrafts(true);
    } catch (e: any) {
      setSubmitError(e.message || String(e));
    }
  }

  async function startType(type: string) {
    if (!sessionId || streaming) return;
    setStreaming(true);
    setStatus("Setting things up…");
    try {
      const s = await api(`/intake/sessions/${sessionId}/start-type`, { method: "POST", json: { type } });
      setSession(s);
      setMessages(s.conversation || []);
      setDraft({});
      draftRef.current = {};
    } finally {
      setStreaming(false);
      setStatus("");
    }
  }

  const bootedRef = useRef(false);
  useEffect(() => {
    if (bootedRef.current) return; // StrictMode double-invoke would create a stray session
    bootedRef.current = true;
    // arriving from "Revise & resubmit": reopen that request's session instead of a fresh one
    const reviseId = (location.state as any)?.reviseSessionId;
    if (reviseId) {
      setReviseReason((location.state as any)?.reviseReason || "");
      nav(location.pathname, { replace: true, state: {} }); // don't re-trigger on back/refresh
      api(`/intake/sessions/${reviseId}`).then((s) => {
        setSessionId(s.id);
        setSession(s);
        setMessages(s.conversation || []);
        sessionsQ.refetch();
        toggleChat(true);
      }).catch((e: any) => setSubmitError(e.message || String(e)));
      return;
    }
    api("/intake/sessions", { method: "POST" }).then((s) => {
      setSessionId(s.id);
      setSession(s);
      setMessages(s.conversation || []);
      sessionsQ.refetch();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function startFrom(contractId: string, mode: string) {
    if (!sessionId) return;
    setStartPicked(null);
    setStreaming(true);
    setStatus("Setting things up…");
    try {
      const s = await api(`/intake/sessions/${sessionId}/start-from`, {
        method: "POST",
        json: { contractId, mode },
      });
      setSession(s);
      setMessages(s.conversation || []);
      setDraft({});
      draftRef.current = {};
    } finally {
      setStreaming(false);
      setStatus("");
    }
  }

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, streamText]);

  const spec: Field[] = session?.fieldSpec || [];
  const captured: Record<string, any> = session?.capturedFields || {};
  const provenance: Record<string, string> = session?.fieldProvenance || {};
  const confidence: Record<string, number> = session?.confidenceScores || {};
  const needs: string[] = session?.needsConfirmation || [];
  const triage = session?.triage;

  const value = (k: string) => (k in draft ? draft[k] : captured[k]);

  // update local state only — persisted on blur / selection / submit, never mid-keystroke
  function pushDraft(k: string, v: any) {
    setDraft((d) => {
      const next = { ...d, [k]: v };
      draftRef.current = next;
      return next;
    });
  }

  async function saveFields(confirmKeys?: string[]) {
    if (!sessionId) return;
    const sent = { ...draftRef.current };
    if (!Object.keys(sent).length && !confirmKeys?.length) return;
    setSaving(true);
    setSubmitError("");
    try {
      const s = await api(`/intake/sessions/${sessionId}/fields`, {
        method: "PUT",
        json: { fields: sent, confirm: confirmKeys || [] },
      });
      setSession(s);
      // drop only the keys we just persisted; keep anything typed since the request started
      setDraft((d) => {
        const next = { ...d };
        for (const k of Object.keys(sent)) if (next[k] === sent[k]) delete next[k];
        draftRef.current = next;
        return next;
      });
    } catch (e: any) {
      setSubmitError(e.message);
    } finally {
      setSaving(false);
    }
  }

  function sendText(text: string) {
    if (!text.trim() || !sessionId || streaming) return;
    setMessages((m) => [...m, { role: "user", content: text.trim() }]);
    setStreaming(true);
    setStreamText("");
    setStatus("Reading your request…");
    apiStream(
      `/intake/sessions/${sessionId}/message/stream`,
      { message: text.trim() },
      {
        onStatus: setStatus,
        onToken: (chunk) => setStreamText((t) => t + chunk),
        onComplete: (data) => {
          setStreaming(false);
          setStatus("");
          setStreamText("");
          setPicked({});
          setOtherFor(null);
          setOtherText("");
          if (!data) return;
          setSession(data);
          setMessages(data.conversation || []);
          setDraft({});
          draftRef.current = {};
        },
        onError: (e) => {
          setStreaming(false);
          setStatus("");
          setPicked({});
          setOtherFor(null);
          setOtherText("");
          setMessages((m) => [...m, { role: "assistant", content: `⚠ ${e}` }]);
        },
      }
    );
  }

  function send() {
    if (!input.trim()) return;
    const text = input;
    setInput("");
    sendText(text);
  }

  async function submit() {
    if (!sessionId) return;
    setSubmitting(true);
    setSubmitError("");
    try {
      // flush any pending edits as part of submit
      const r = await api(`/intake/sessions/${sessionId}/submit`, {
        method: "POST",
        json: { precedentContractId: chosenPrecedent, fields: { ...draftRef.current, ...draft } },
      });
      qc.invalidateQueries({ queryKey: ["contracts"] });
      qc.invalidateQueries({ queryKey: ["analytics"] });
      sessionsQ.refetch(); // the saved draft now has a generated contract
      setResult(r);
    } catch (e: any) {
      setSubmitError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  const missingRequired = useMemo(
    () => spec.filter((f) => f.required && !String(value(f.key) ?? "").trim()).map((f) => f.label),
    [spec, draft, session]
  );
  const ready = session?.readyToSubmit && !Object.keys(draft).length;
  const canSubmit = missingRequired.length === 0 && needs.length === 0;

  // tappable answers for the questions in the latest assistant message
  const quickQuestions = useMemo(() => {
    if (result || streaming || !messages.length) return [];
    const last = messages[messages.length - 1];
    if (!last || last.role !== "assistant") return [];
    const qs: any[] = Array.isArray((last as any).questions) ? (last as any).questions : [];
    return qs
      .filter((q) => {
        const f = spec.find((s) => s.key === q.field);
        return !f || (!(f.key in draft) && !String(value(f.key) ?? "").trim());
      })
      .slice(0, 3);
  }, [messages, result, streaming, spec, draft, session]);

  const chipOptionsFor = (q: any): string[] => {
    const f = spec.find((s) => s.key === q.field);
    if (f?.options?.length) return f.options.map((o) => o.label);
    if (f?.type === "boolean") return ["Yes", "No"];
    return (q.quickOptions || []).map(String);
  };

  const quickLabel = (q: any) =>
    spec.find((s) => s.key === q.field)?.label || String(q.field).replace(/_/g, " ");

  // free-text questions (e.g. a counterparty name) get a direct input instead of chips
  const isFreeTextQuestion = (q: any) => {
    const f = spec.find((s) => s.key === q.field);
    return (!f || f.type === "text" || f.type === "textarea") && !(q.quickOptions || []).length;
  };

  const saveOther = () => {
    if (!otherText.trim() || !otherFor) return;
    const t = otherText.trim();
    setPicked((p) => ({ ...p, [otherFor]: t }));
    setOtherFor(null);
    setOtherText("");
  };

  const sendPicked = () => {
    const parts = quickQuestions
      .map((q) => (picked[q.field] ? `${quickLabel(q)}: ${picked[q.field]}` : null))
      .filter(Boolean) as string[];
    if (!parts.length) return;
    sendText(parts.join(". "));
  };

  const pick = (field: string, v: string) =>
    setPicked((p) => (p[field] === v ? { ...p, [field]: "" } : { ...p, [field]: v }));

  async function submitForApproval() {
    setSubmittingApproval(true);
    try {
      await api(`/workflow/start/${result.contractId}`, { method: "POST" });
      qc.invalidateQueries({ queryKey: ["contracts"] });
      qc.invalidateQueries({ queryKey: ["me-summary"] });
      nav(`/contracts/${result.contractId}`, { state: { justSubmitted: true } });
    } catch (e: any) {
      setSubmitError(e.message);
    } finally {
      setSubmittingApproval(false);
    }
  }

  // Cancel on the review page: keep the draft (so nothing is lost), then return
  // to a fresh New request chat.
  async function cancelReview() {
    setConfirmAction(null);
    try {
      await saveReviewDraft();
    } catch { /* saveReviewDraft surfaces its own error state */ }
    await newChat();
  }

  // Save draft on the review page: persist the current document text and keep the
  // intake draft (with its request number) in the Drafts list until approval.
  const [savingReviewDraft, setSavingReviewDraft] = useState(false);
  const [draftSaved, setDraftSaved] = useState(false);
  async function saveReviewDraft() {
    if (!result || savingReviewDraft) return;
    setSavingReviewDraft(true);
    setSubmitError("");
    try {
      if (!result.paperMode) {
        await api(`/contracts/${result.contractId}/document/sync`, { method: "POST" });
      }
      if (sessionId) {
        await api(`/intake/sessions/${sessionId}/save`, { method: "POST" });
      }
      sessionsQ.refetch();
      setDraftSaved(true);
    } catch (e: any) {
      setSubmitError(e.message || String(e));
    } finally {
      setSavingReviewDraft(false);
    }
  }

  if (result) {
    return (
      <>
      <div className="max-w-7xl mx-auto space-y-4 flex flex-col max-lg:h-auto lg:h-[calc(100vh-120px)]">
        <div className="grid gap-4 items-stretch flex-1 lg:min-h-0 lg:grid-cols-[2fr_1fr]">
        <div className="min-w-0 flex flex-col gap-4 lg:h-full lg:min-h-0 lg:overflow-y-auto">
        <Card>
          <SectionTitle
            right={
              <button
                className="btn"
                style={{ padding: "0.3rem 0.6rem" }}
                onClick={() => { setResult(null); setDraftSaved(false); }}
              >
                <Icon.chevronLeft width={14} height={14} /> Back to conversation
              </button>
            }
          >
            Draft ready for your review
          </SectionTitle>
          <p className="text-sm">
            {result.documentKept ? <>Updated <b>{result.contractNumber}</b>. </> : <>Created <b>{result.contractNumber}</b> as a draft. </>}
            Review the document below, then submit it for approval. You can go{" "}
            <button className="link" onClick={() => { setResult(null); setDraftSaved(false); }}>
              back to the conversation
            </button>{" "}
            to change anything — re-submitting updates this same draft.
          </p>
          {result.documentKept && !result.paperMode && (
            <div
              className="rounded-[8px] p-3 text-xs leading-relaxed mt-2"
              style={{ border: "1px solid var(--border)", background: "var(--surface-2)" }}
            >
              Your last edited version of the document has been kept — it was <b>not</b> re-assembled from the template.
              Open the Document tab and use <b>Re-assemble</b> if you want to rebuild it from the current template and clauses.
            </div>
          )}
          {(result.clausesFromPrecedent || []).length > 0 && (
            <p className="text-xs text-ink-faint mt-1">
              Clauses carried from precedent: {result.clausesFromPrecedent.join(", ")}
            </p>
          )}
          {result.paperMode && (
            <div
              className="rounded-[8px] p-3 text-xs leading-relaxed mt-2"
              style={{ border: "1px solid var(--ai)", background: "var(--ai-soft)" }}
            >
              <b>This contract was created directly from your uploaded paper{result.paperFilename ? ` (${result.paperFilename})` : ""}.</b>{" "}
              The document is the original third-party text and is read-only — it cannot be edited on the review page.
            </div>
          )}
          {(result.draftDeviations || []).length > 0 && (
            <div className="space-y-2 mt-3">
              {result.draftDeviations.map((d: any, i: number) => (
                <AiAffordance key={i} compact suggestion={<><b>{d.concept}</b> — {d.tier}</>} explanation={d.reason} />
              ))}
            </div>
          )}
          <div className="flex flex-wrap gap-2 mt-4">
            <button className="btn btn-primary" disabled={submittingApproval} onClick={() => setConfirmAction("submit")}>
              <Icon.checkCircle width={15} height={15} />
              {submittingApproval ? "Submitting…" : "Submit for approval"}
            </button>
            <button className="btn" disabled={savingReviewDraft} onClick={saveReviewDraft}>
              <Icon.file width={15} height={15} />
              {savingReviewDraft ? "Saving…" : draftSaved ? "Draft saved ✓" : "Save draft"}
            </button>
            <button className="btn" disabled={submittingApproval} onClick={() => setConfirmAction("cancel")}>
              <Icon.x width={15} height={15} />
              Cancel
            </button>
          </div>
          {draftSaved && (
            <div className="text-[11px] text-ink-faint mt-1.5">
              Draft saved — it stays in your Drafts list until you submit it for approval.
            </div>
          )}
          {submitError && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{submitError}</div>}
        </Card>
        {attachments.length > 0 && (
          <Card>
            <SectionTitle>Supporting documents</SectionTitle>
            <div className="space-y-1.5">
              {attachments.map((a: any) => (
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
                </div>
              ))}
            </div>
          </Card>
        )}
        <DocumentPanel contractId={result.contractId} fullPage />
        </div>
        <div className="min-w-0 lg:h-full lg:min-h-0 lg:overflow-y-auto">
          <AiReviewPanel contractId={result.contractId} />
        </div>
        </div>
      </div>

      {confirmAction && (
        <div
          className="modal-backdrop fixed inset-0 z-50 flex items-center justify-center p-6"
          style={{ background: "rgba(15, 17, 21, 0.5)", backdropFilter: "blur(3px)" }}
          onClick={() => setConfirmAction(null)}
        >
          <div className="modal-card card w-full max-w-sm p-6" onClick={(e) => e.stopPropagation()}>
            {confirmAction === "submit" ? (
              <>
                <h2 className="text-base font-medium">Submit for approval?</h2>
                <p className="text-sm text-ink-soft mt-2 leading-relaxed">
                  <b>{result.contractNumber}</b> will move into the approval workflow and the assigned
                  approver will be notified. You can recall it to draft while it's still in review.
                </p>
                <div className="flex justify-end gap-2 mt-5">
                  <button className="btn" onClick={() => setConfirmAction(null)}>Go back</button>
                  <button
                    className="btn btn-primary"
                    disabled={submittingApproval}
                    onClick={() => { setConfirmAction(null); submitForApproval(); }}
                  >
                    <Icon.checkCircle width={14} height={14} />
                    {submittingApproval ? "Submitting…" : "Submit"}
                  </button>
                </div>
              </>
            ) : (
              <>
                <h2 className="text-base font-medium">Cancel and start a new request?</h2>
                <p className="text-sm text-ink-soft mt-2 leading-relaxed">
                  Your draft stays in the Drafts list with its request number — you can resume it any time.
                  This takes you back to the New request page.
                </p>
                <div className="flex justify-end gap-2 mt-5">
                  <button className="btn" onClick={() => setConfirmAction(null)}>Stay here</button>
                  <button className="btn btn-primary" onClick={cancelReview}>Cancel request</button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
      </>
    );
  }

  return (
    <>
    {previewHtml !== null && (
      <div
        className="fixed inset-0 z-50 flex items-center justify-center p-8"
        style={{ background: "rgba(0,0,0,0.45)" }}
        onClick={() => setPreviewHtml(null)}
      >
        <div
          className="bg-surface rounded-[12px] shadow-xl flex flex-col w-[85vw] h-[85vh] overflow-hidden"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="flex items-center justify-between px-4 py-2.5 border-b border-border">
            <div className="text-sm font-medium">
              {session?.paperFilename || "Uploaded paper"}
              <span className="text-ink-faint font-normal"> — preview (untrusted document is sandboxed)</span>
            </div>
            <button className="btn" style={{ padding: "0.25rem 0.5rem" }} onClick={() => setPreviewHtml(null)}>
              <Icon.x width={14} height={14} /> Close
            </button>
          </div>
          <iframe
            title="Paper preview"
            srcDoc={previewHtml}
            sandbox=""
            className="flex-1 w-full border-0 bg-white"
          />
        </div>
      </div>
    )}
    <div className="lg:flex lg:gap-3 lg:items-start">
      {chatOpen && (
        <DockablePanel
          title="AI assistant"
          icon={<Icon.sparkle width={14} height={14} style={{ color: "var(--accent)" }} />}
          cardAttr="data-intake-chat-card"
          pos={chat.pos}
          dockW={chat.dockW}
          onGrab={chat.startDrag}
          onDock={chat.pos ? chat.dock : undefined}
          onClose={() => toggleChat(false)}
          onResize={chat.startResize}
          onDockResize={chat.startDockResize}
        >
      <div className="flex flex-col h-full min-h-0 bg-surface">
        <div
          className="px-4 py-3 border-b border-border flex flex-wrap items-center gap-2 shrink-0"
          style={{ background: "linear-gradient(90deg, var(--accent-soft) 0%, transparent 70%)" }}
        >
          <span className="w-6 h-6 rounded-[8px] bg-accent flex items-center justify-center shrink-0" style={{ background: "var(--accent)" }}>
            <Icon.sparkle width={13} height={13} style={{ color: "#fff" }} />
          </span>
          <span className="text-sm font-medium truncate flex-1">
            New request
            <span className="text-ink-faint font-normal">
              {paperMode ? " — upload the counterparty's paper contract" : " — describe what you need in plain language"}
            </span>
          </span>
          <div className="flex flex-wrap items-center justify-end gap-2 min-w-0 max-lg:order-3 max-lg:w-full">
          <button
            className="btn shrink-0"
            style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
            onClick={saveDraft}
            disabled={streaming || spec.length === 0 || missingRequired.length > 0}
            title={
              spec.length === 0
                ? "Describe your request first so the form can be filled in"
                : missingRequired.length > 0
                ? `Fill all required fields first — still needed: ${missingRequired.join(", ")}`
                : session?.saved
                ? "This draft is saved"
                : "Save this request and its chat so you can resume it later"
            }
          >
            {session?.saved ? (
              <span className="inline-flex items-center gap-1" style={{ color: "var(--ok)" }}>
                <Icon.check width={13} height={13} /> Saved
              </span>
            ) : (
              <>
                <Icon.checkCircle width={13} height={13} /> Save draft
              </>
            )}
          </button>
          <button
            className="btn shrink-0"
            style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
            onClick={() => setShowDrafts((v) => !v)}
            title="Your saved drafts from previous chats"
          >
            <Icon.file width={13} height={13} />
            Drafts{(sessionsQ.data || []).length > 0 ? ` (${(sessionsQ.data || []).length})` : ""}
          </button>
          <button
            className="btn shrink-0"
            style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
            onClick={newChat}
            disabled={streaming}
            title="Start a new request (the current one stays saved)"
          >
            <Icon.plus width={13} height={13} /> New chat
          </button>
          </div>
        </div>
        {session?.resultingContractId && (
          <div
            className="px-4 py-2 border-b text-xs"
            style={{ borderColor: "var(--risk)", background: "color-mix(in srgb, var(--risk) 8%, transparent)" }}
          >
            <span className="font-medium" style={{ color: "var(--risk)" }}>
              {reviseReason ? "Revising a returned request." : "Editing a recalled request."}
            </span>{" "}
            {reviseReason ? <span className="text-ink-soft">Reason it was sent back: {reviseReason}</span> : null}
            <span className="text-ink-faint"> Update the details on the right, then generate and resubmit — it updates the same request.</span>
          </div>
        )}
        {showDrafts && (
          <div className="px-4 py-2 border-b border-border bg-surface-2 max-h-56 overflow-y-auto space-y-1">
            {(() => {
              // backend lists saved drafts until they are submitted for approval
              const drafts = sessionsQ.data || [];
              if (!drafts.length)
                return <div className="text-xs text-ink-faint py-1">No saved drafts yet — click "Save draft" to keep one here.</div>;
              return drafts.map((s: any) => (
                <div
                  key={s.id}
                  className={`w-full text-left rounded-[8px] border p-2 text-sm transition-colors group ${
                    s.id === sessionId
                      ? "border-[color:var(--accent)] bg-accent-soft"
                      : "border-border bg-surface hover:border-[color:var(--accent)]"
                  }`}
                >
                  {deleteArm === s.id ? (
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs" style={{ color: "var(--risk)" }}>Delete this draft and its chat?</span>
                      <span className="flex gap-1.5 shrink-0">
                        <button
                          type="button"
                          className="btn btn-primary"
                          style={{ padding: "0.2rem 0.55rem", fontSize: "0.75rem" }}
                          onClick={() => deleteSession(s.id)}
                          disabled={streaming}
                        >
                          Delete
                        </button>
                        <button
                          type="button"
                          className="btn"
                          style={{ padding: "0.2rem 0.55rem", fontSize: "0.75rem" }}
                          onClick={() => setDeleteArm(null)}
                        >
                          Cancel
                        </button>
                      </span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          setShowDrafts(false);
                          if (s.resultingContractId) nav(`/contracts/${s.resultingContractId}`);
                          else resumeSession(s.id);
                        }}
                        disabled={streaming}
                        className="flex-1 min-w-0 text-left"
                      >
                        <span className="flex items-center justify-between gap-2">
                          <span className="font-medium truncate tabular">
                            {s.requestNumber || "Untitled request"}
                            {s.id === sessionId && <span className="text-[11px] text-ink-faint font-normal"> · current</span>}
                          </span>
                          <span className="text-[11px] text-ink-faint shrink-0">
                            {new Date(s.updatedAt).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
                          </span>
                        </span>
                        <span className="block text-[11px] text-ink-faint mt-0.5 truncate">
                          {s.contractNumber
                            ? `${s.contractType || "Request"} — document drafted (${s.contractNumber})`
                            : s.contractType || "Untitled request"}
                        </span>
                      </button>
                      {!s.resultingContractId && (
                        <button
                          type="button"
                          className="shrink-0 text-ink-faint hover:text-ink opacity-0 group-hover:opacity-100 transition-opacity"
                          title="Remove this draft"
                          onClick={() => setDeleteArm(s.id)}
                        >
                          <Icon.x width={14} height={14} />
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ));
            })()}
          </div>
        )}
        {paperMode ? (
          <div className="flex-1 flex flex-col p-4">
            <div
              className={`flex-1 rounded-[10px] border-2 border-dashed flex flex-col items-center justify-center gap-2 text-center p-6 transition-colors ${
                dragOver ? "!border-[color:var(--accent)] bg-accent-soft" : "border-border"
              } ${uploading ? "pointer-events-none" : "cursor-pointer hover:!border-[color:var(--accent)]"}`}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const f = e.dataTransfer.files?.[0];
                if (f) uploadPaper(f);
              }}
              onClick={() => fileInputRef.current?.click()}
            >
              {uploading ? (
                <Spinner label="AI is reading the contract…" />
              ) : (
                <>
                  <Icon.upload width={32} height={32} style={{ color: "var(--accent)" }} />
                  <div className="text-sm font-medium">Drop the contract file here</div>
                  <div className="text-xs text-ink-faint max-w-xs">
                    …or click to browse. AI will identify the content and fill in the request fields for you.
                  </div>
                  <div className="text-[11px] text-ink-faint mt-1">.docx, .html or .txt · max 10 MB</div>
                </>
              )}
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".docx,.html,.htm,.txt,.md"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) uploadPaper(f);
                e.target.value = "";
              }}
            />
            {uploadError && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{uploadError}</div>}
            <button className="btn mt-3 self-start" onClick={() => { setPaperMode(false); setUploadError(""); }}>
              Back to chat
            </button>
          </div>
        ) : (
        <>
        <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
          {messages.length === 0 && <Spinner label="Starting session…" />}
          {messages.map((m, i) => (
            <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
              <div
                className={`max-w-[80%] text-sm rounded-[10px] px-3 py-2 fade-in ${
                  m.role === "user" ? "bg-[color:var(--accent)] text-white" : "bg-surface-2 text-ink"
                }`}
              >
                {m.role === "assistant" ? <RichText content={m.content} /> : m.content}
              </div>
            </div>
          ))}
          {streaming && (
            <div className="flex justify-start">
              <div className="max-w-[80%] text-sm rounded-[10px] px-3 py-2 bg-surface-2 text-ink">
                {streamText ? <RichText content={streamText} /> : <span className="text-ink-faint">{status}</span>}
              </div>
            </div>
          )}

          {!streaming && quickQuestions.length > 0 && (
            <div className="space-y-2 pt-1 fade-in">
              {quickQuestions.map((q, i) => {
                const open = otherFor === q.field;
                const answered = picked[q.field];
                return (
                  <div key={q.field || i}>
                    <div className="text-[11px] text-ink-faint mb-1">{quickLabel(q)}:</div>
                    {open ? (
                      <div className="flex gap-1.5">
                        <input
                          autoFocus
                          className="input"
                          style={{ padding: "0.3rem 0.6rem", fontSize: "0.8125rem" }}
                          placeholder={quickLabel(q)}
                          value={otherText}
                          onChange={(e) => setOtherText(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") saveOther();
                            if (e.key === "Escape") { setOtherFor(null); setOtherText(""); }
                          }}
                        />
                        <button
                          className="btn btn-primary"
                          style={{ padding: "0.3rem 0.7rem", fontSize: "0.8125rem" }}
                          disabled={!otherText.trim()}
                          onClick={saveOther}
                        >
                          <Icon.check width={13} height={13} />
                        </button>
                      </div>
                    ) : isFreeTextQuestion(q) && !answered ? (
                      <button
                        type="button"
                        onClick={() => { setOtherFor(q.field); setOtherText(""); }}
                        className="text-xs"
                        style={{ color: "var(--accent)" }}
                      >
                        Type your answer…
                      </button>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {isFreeTextQuestion(q) ? (
                          <button
                            type="button"
                            onClick={() => { setOtherFor(q.field); setOtherText(picked[q.field] || ""); }}
                            className="btn"
                            style={{
                              padding: "0.25rem 0.65rem", fontSize: "0.8125rem",
                              borderColor: "var(--accent)", background: "var(--accent-soft)",
                            }}
                          >
                            {answered}
                          </button>
                        ) : (
                          <>
                            {chipOptionsFor(q).map((o) => (
                              <button
                                key={o}
                                type="button"
                                onClick={() => pick(q.field, o)}
                                className="btn"
                                style={{
                                  padding: "0.25rem 0.65rem", fontSize: "0.8125rem",
                                  ...(answered === o
                                    ? { borderColor: "var(--accent)", background: "var(--accent-soft)" }
                                    : {}),
                                }}
                              >
                                {o}
                              </button>
                            ))}
                            {answered && !chipOptionsFor(q).includes(answered) && (
                              <button
                                type="button"
                                onClick={() => { setOtherFor(q.field); setOtherText(answered); }}
                                className="btn"
                                style={{
                                  padding: "0.25rem 0.65rem", fontSize: "0.8125rem",
                                  borderColor: "var(--accent)", background: "var(--accent-soft)",
                                }}
                              >
                                {answered}
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => { setOtherFor(q.field); setOtherText(""); }}
                              className="btn"
                              style={{ padding: "0.25rem 0.65rem", fontSize: "0.8125rem", borderStyle: "dashed" }}
                            >
                              Other…
                            </button>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
              {Object.values(picked).some(Boolean) && (
                <button
                  type="button"
                  onClick={sendPicked}
                  className="btn btn-primary"
                  style={{ padding: "0.35rem 0.85rem", fontSize: "0.8125rem" }}
                  title={quickQuestions
                    .map((q) => (picked[q.field] ? `${quickLabel(q)}: ${picked[q.field]}` : null))
                    .filter(Boolean)
                    .join(". ")}
                >
                  <Icon.check width={13} height={13} />
                  Send {Object.values(picked).filter(Boolean).length} answer{Object.values(picked).filter(Boolean).length > 1 ? "s" : ""}
                </button>
              )}
            </div>
          )}

          {messages.length <= 1 && !streaming && (topTypes.data || []).length > 0 && (
            <div className="pt-1 fade-in">
              <div className="text-xs text-ink-faint mb-1.5">You often create — start one directly:</div>
              <div className="flex flex-wrap gap-1.5">
                {(topTypes.data || []).map((t: any) => (
                  <button
                    key={t.type}
                    type="button"
                    onClick={() => startType(t.type)}
                    className={actionLinkCls}
                    title={`Start a new ${t.type} (you've created ${t.count} recently)`}
                  >
                    <Icon.sparkle width={12} height={12} />
                    {(t.type || "").replaceAll("_", " ")}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.length <= 1 && !streaming && (recent.data || []).length > 0 && (
            <div className="pt-1 slide-in">
              <button
                type="button"
                onClick={() => setShowRecent((v) => !v)}
                className={actionLinkCls}
              >
                <Icon.chevronRight
                  width={12}
                  height={12}
                  style={{
                    transition: "transform 0.15s",
                    transform: showRecent ? "rotate(90deg)" : "none",
                  }}
                />
                or start from one of your recent contracts
              </button>
              <div className={`unfold ${showRecent ? "unfold-open" : ""}`}>
                <div>
              <div
                className={`space-y-1.5 mt-1.5 ${showAllRecent ? "overflow-y-auto pr-1" : ""}`}
                style={showAllRecent ? { maxHeight: 332 } : undefined}
              >
                {(showAllRecent ? recent.data : recent.data.slice(0, RECENT_PREVIEW)).map((c: any) => (
                  <div key={c.id}>
                    <button
                      onClick={() => setStartPicked(startPicked?.id === c.id ? null : c)}
                      className={`w-full text-left rounded-[8px] border p-2 text-sm transition-colors ${
                        startPicked?.id === c.id
                          ? "border-[color:var(--accent)] bg-accent-soft"
                          : "border-border hover:border-[color:var(--accent)]"
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium truncate">{c.contractNumber}</span>
                        <Badge tone="neutral">{c.type}</Badge>
                      </div>
                      <div className="text-xs text-ink-faint truncate">{c.title}</div>
                    </button>
                    {startPicked?.id === c.id && (
                      <div className="flex flex-wrap gap-1.5 mt-1.5 pl-1 pop-in">
                        <button className="btn" style={{ padding: "0.3rem 0.55rem" }} onClick={() => startFrom(c.id, "SIMILAR")}>
                          <Icon.plus width={13} height={13} /> New one like this
                        </button>
                        <button className="btn" style={{ padding: "0.3rem 0.55rem" }} onClick={() => startFrom(c.id, "SAME_PARTY")}>
                          <Icon.handshake width={13} height={13} /> Same party, new type
                        </button>
                        <button className="btn" style={{ padding: "0.3rem 0.55rem" }} onClick={() => startFrom(c.id, "AMEND")}>
                          <Icon.edit width={13} height={13} /> Amend it
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
                </div>
              </div>
              {showRecent && recent.data.length > RECENT_PREVIEW && (
                <button
                  type="button"
                  onClick={() => setShowAllRecent((v) => !v)}
                  className="text-xs inline-flex items-center gap-1 mt-1 transition-colors hover:opacity-80"
                  style={{ color: "var(--accent)" }}
                >
                  <Icon.chevronDown
                    width={12}
                    height={12}
                    style={{
                      transition: "transform 0.15s",
                      transform: showAllRecent ? "rotate(180deg)" : "none",
                    }}
                  />
                  {showAllRecent ? "Show less" : `Show more (${recent.data.length - RECENT_PREVIEW})`}
                </button>
              )}
            </div>
          )}

          {!streaming && messages.length <= 1 && !session?.paperFilename && (
            <div className="pt-1">
              <button
                type="button"
                onClick={() => { setPaperMode(true); setUploadError(""); }}
                className={actionLinkCls}
              >
                <Icon.upload width={12} height={12} />
                or, upload a 3rd-party paper contract
              </button>
            </div>
          )}
        </div>
        <div className="p-3 border-t border-border flex gap-2">
          <input
            className="input"
            placeholder={'e.g. "Mutual NDA with Meridian, a logistics vendor in Germany, 24 months"'}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            disabled={streaming}
          />
          <button className="btn btn-primary" onClick={send} disabled={streaming || !input.trim()}>
            Send
          </button>
        </div>
        </>
        )}
      </div>
        </DockablePanel>
      )}
      <div className="min-w-0 lg:flex-1">
      {!chatOpen && (
        <button
          className="btn mb-3"
          style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
          onClick={() => toggleChat(true)}
        >
          <Icon.sparkle width={13} height={13} /> AI assistant
        </button>
      )}
      <div className="relative max-lg:h-auto lg:h-[calc(100vh-160px)]">
        <div className="space-y-4 max-lg:h-auto max-lg:overflow-visible lg:h-full lg:overflow-y-auto pr-1 pb-20">
        {session?.paperFilename && (
          <Card>
            <SectionTitle>
              <span className="inline-flex items-center gap-1.5" style={{ color: "var(--ai)" }}>
                <Icon.file width={13} height={13} />
                Third-party paper
              </span>
            </SectionTitle>
            <div className="flex items-center gap-2">
              <Icon.file width={18} height={18} style={{ color: "var(--accent)", flexShrink: 0 }} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium truncate">{session.paperFilename}</div>
                <div className="text-[11px] text-ink-faint">
                  Will be used as the contract document, as-is (read-only after submit)
                </div>
              </div>
              <button className="btn shrink-0" style={{ padding: "0.35rem 0.7rem" }} onClick={openPaperPreview} disabled={previewLoading}>
                {previewLoading ? "Loading…" : (
                  <>
                    <Icon.eye width={13} height={13} /> Preview
                  </>
                )}
              </button>
            </div>
          </Card>
        )}

        {session?.readyToSubmit && (
          <div
            className="rounded-[8px] p-3 text-xs leading-relaxed"
            style={{ border: "1px solid var(--ok)", background: "color-mix(in srgb, var(--ok) 8%, transparent)" }}
          >
            <b>All required info is captured.</b> Before moving to the next step, please review
            every field on this panel and confirm the contents are correct — especially values
            marked <span style={{ color: "var(--warn)" }}>"AI guess — confirm"</span>.
          </div>
        )}
        <Card>
          <SectionTitle
            right={
              session?.contractType ? <Badge tone="neutral">{session.contractType}</Badge> : undefined
            }
          >
            <span className="inline-flex items-center gap-1.5">
              <Icon.checkCircle width={13} height={13} style={{ color: "var(--accent)" }} />
              Request details
            </span>
          </SectionTitle>
          <p className="text-xs text-ink-faint mb-3">
            These are the fields this contract type needs. They fill in as you talk; edit any of them directly.
            Required fields are marked <span style={{ color: "var(--risk)" }}>*</span>.
          </p>

          {spec.length === 0 ? (
            <div className="space-y-2">
              {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton h-9" />)}
            </div>
          ) : (
            <div className="space-y-4">
              {groupFields(spec).map(([groupName, groupFieldsList]) => (
                <div key={groupName}>
                  {groupName !== "Basics" ? (
                    <div className="flex items-center gap-1.5 mb-2">
                      <span
                        className="inline-block w-[3px] h-3 rounded-full"
                        style={{ background: groupColor(groupName) }}
                      />
                      <span
                        className="text-[10px] font-semibold uppercase tracking-wider"
                        style={{ color: "color-mix(in srgb, " + groupColor(groupName) + " 80%, var(--ink))" }}
                      >
                        {groupName}
                      </span>
                    </div>
                  ) : null}
                  <div
                    className="space-y-2.5"
                    style={groupName !== "Basics" ? {
                      borderLeft: "2px solid color-mix(in srgb, " + groupColor(groupName) + " 22%, transparent)",
                      paddingLeft: 10,
                      marginLeft: 1,
                    } : undefined}
                  >
                    {groupFieldsList.map((f) => {
                      const needsConfirm = needs.includes(f.key);
                      const prov = provenance[f.key];
                      const conf = confidence[f.key];
                      return (
                        <div
                          key={f.key}
                          className={`rounded-[8px] transition-colors ${needsConfirm ? "p-2 -m-2" : ""}`}
                          style={needsConfirm ? { border: "1px solid var(--warn)", background: "color-mix(in srgb, var(--warn) 8%, transparent)" } : {}}
                        >
                          <div className="flex items-center justify-between mb-0.5 gap-2">
                            <label className="text-xs text-ink-faint truncate">
                              {f.label}
                              {f.required && <span style={{ color: "var(--risk)" }}> *</span>}
                            </label>
                            {prov && (
                              <span className="chip shrink-0" title={f.help || ""}>
                                {needsConfirm
                                  ? prov.startsWith("USER")
                                    ? "check this value"
                                    : "AI guess — confirm"
                                  : provLabel(prov)}
                              </span>
                            )}
                          </div>
                          <FieldInput
                            field={f}
                            value={value(f.key)}
                            onChange={(v) => pushDraft(f.key, v)}
                            onCommit={() => saveFields()}
                          />
                          <div className="flex items-center gap-2 mt-1">
                            {conf != null && (
                              <span className="text-[10px] text-ink-faint">{Math.round(Number(conf) * 100)}% confidence</span>
                            )}
                            {needsConfirm && (
                              <button className="text-[11px] link" onClick={() => saveFields([f.key])}>
                                looks right — confirm
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
              {Object.keys(draft).length > 0 && (
                <button
                  className="btn btn-primary w-full justify-center pop-in"
                  disabled={saving}
                  onClick={saveDraft}
                >
                  {saving ? "Saving…" : `Save ${Object.keys(draft).length} edit${Object.keys(draft).length > 1 ? "s" : ""}`}
                </button>
              )}
            </div>
          )}
        </Card>

        {triage && (
          <Card>
            <SectionTitle>
              <span className="inline-flex items-center gap-1.5">
                <Icon.sparkle width={13} height={13} style={{ color: "var(--orange)" }} />
                Expected routing
              </span>
            </SectionTitle>
            <div className="text-sm">
              <Badge tone={triage.path === "LEGAL_REVIEW" ? "risk" : triage.path === "STANDARD_APPROVAL" ? "warn" : "ok"}>
                {triage.pathLabel}
              </Badge>
              <span className="text-ink-faint text-xs"> · score {triage.score}</span>
            </div>
            <p className="text-xs text-ink-soft mt-1">{triage.explanation}</p>
            <ul className="text-[11px] text-ink-faint mt-1 list-disc pl-4">
              {(triage.factors || []).map((x: string, i: number) => (
                <li key={i}>{x}</li>
              ))}
            </ul>
          </Card>
        )}

        {(session?.precedents || []).length > 0 && (
          <Card>
            <SectionTitle>
              <span className="inline-flex items-center gap-1.5">
                <Icon.handshake width={13} height={13} style={{ color: "var(--ai)" }} />
                Precedents
              </span>
            </SectionTitle>
            <p className="text-xs text-ink-faint mb-2">Start from one of these rather than a blank contract.</p>
            <div className="space-y-2">
              {session.precedents.map((p: any) => (
                <button
                  key={p.contractId}
                  onClick={() => setChosenPrecedent(chosenPrecedent === p.contractId ? null : p.contractId)}
                  className={`w-full text-left rounded-[8px] border p-2 text-sm ${
                    chosenPrecedent === p.contractId ? "border-[color:var(--ai)] bg-[color:var(--ai-soft)]" : "border-border"
                  }`}
                >
                  <div className="flex justify-between">
                    <span className="font-medium">{p.contractNumber}</span>
                    <Badge tone="ai">{Math.round(p.score * 100)}%</Badge>
                  </div>
                  <div className="text-xs text-ink-faint">{p.title}</div>
                  <div className="text-[11px] text-ink-faint mt-0.5">{(p.reasons || [])[0]}</div>
                </button>
              ))}
            </div>
          </Card>
        )}

        <Card>
          <SectionTitle>
            <span className="inline-flex items-center gap-1.5">
              <Icon.file width={13} height={13} style={{ color: "var(--teal)" }} />
              Supporting documents
            </span>
          </SectionTitle>
          <p className="text-xs text-ink-faint mb-2">
            Optional — attach extra files (emails, schedules, drafts) to this request. They are carried to the contract.
          </p>
          {attachments.length > 0 && (
            <div className="space-y-1.5 mb-2">
              {attachments.map((a: any) => (
                <div key={a.id} className="flex items-center gap-2 rounded-[8px] border border-border p-1.5">
                  <Icon.file width={14} height={14} className="shrink-0 text-ink-faint" />
                  <span className="text-xs truncate flex-1" title={a.filename}>{a.filename}</span>
                  <span className="text-[10px] text-ink-faint shrink-0">{(a.size / 1024).toFixed(0)} KB</span>
                  <button
                    type="button"
                    className="text-ink-faint hover:text-ink shrink-0"
                    title="Remove"
                    onClick={() => removeSupporting(a.id)}
                  >
                    <Icon.x width={13} height={13} />
                  </button>
                </div>
              ))}
            </div>
          )}
          {attachError && <div className="text-xs mb-2" style={{ color: "var(--risk)" }}>{attachError}</div>}
          <input
            ref={supportInputRef}
            type="file"
            multiple
            className="hidden"
            onChange={(e) => {
              addSupporting(e.target.files);
              e.target.value = "";
            }}
          />
          <button className="btn w-full justify-center" onClick={() => supportInputRef.current?.click()} disabled={attachUploading}>
            {attachUploading ? <Spinner label="Uploading…" /> : (
              <>
                <Icon.plus width={13} height={13} /> Add supporting documents
              </>
            )}
          </button>
        </Card>

        </div>

        <div className="absolute bottom-4 right-4 z-20 flex flex-col items-end gap-1.5">
          {!canSubmit && !submitting && (
            <div
              className="chip max-w-[420px] truncate"
              style={{ borderColor: "var(--warn)", color: "var(--warn)" }}
              title={missingRequired.length
                ? `Still need: ${missingRequired.join(", ")}`
                : "Confirm the highlighted values first"}
            >
              {missingRequired.length
                ? `Still need: ${missingRequired.join(", ")}`
                : "Confirm the highlighted values first"}
            </div>
          )}
          {submitError && (
            <div className="text-xs max-w-[420px] truncate" style={{ color: "var(--risk)" }} title={submitError}>
              {submitError}
            </div>
          )}
          <button
            className="btn btn-ai shadow-lg"
            disabled={!canSubmit || submitting}
            onClick={submit}
          >
            <Icon.sparkle width={14} height={14} />
            {submitting ? "Generating…" : "Generate Document Draft"}
          </button>
        </div>
      </div>
      </div>
    </div>
    </>
  );
}

/** Renders chat replies with light structure: **bold**, bullets, paragraphs. */
function RichText({ content }: { content: string }) {
  const lines = useMemo(() => content.split("\n").map((l) => l.replace(/\s+$/, "")), [content]);
  const blocks: { type: "p" | "ul"; items: string[] }[] = [];
  for (const line of lines) {
    const t = line.trim();
    if (!t) continue;
    const bullet = /^([-*•]|\d+[.)])\s+(.*)$/.exec(t);
    if (bullet) {
      const last = blocks[blocks.length - 1];
      if (last?.type === "ul") last.items.push(bullet[2]);
      else blocks.push({ type: "ul", items: [bullet[2]] });
    } else {
      blocks.push({ type: "p", items: [t] });
    }
  }
  return (
    <div className="space-y-1.5">
      {blocks.map((b, i) =>
        b.type === "p" ? (
          <p key={i}><Inline text={b.items[0]} /></p>
        ) : (
          <ul key={i} className="list-disc pl-4 space-y-0.5">
            {b.items.map((it, j) => (
              <li key={j}><Inline text={it} /></li>
            ))}
          </ul>
        )
      )}
    </div>
  );
}

function Inline({ text }: { text: string }) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return (
    <>
      {parts.map((p, i) =>
        p.startsWith("**") && p.endsWith("**") && p.length > 4
          ? <b key={i}>{p.slice(2, -2)}</b>
          : <span key={i}>{p}</span>
      )}
    </>
  );
}

function FieldInput({
  field,
  value,
  onChange,
  onCommit,
}: {
  field: Field;
  value: any;
  onChange: (v: any) => void;
  /** persist to the server — fired on blur for free text, immediately for discrete choices */
  onCommit: () => void;
}) {
  if (field.type === "enum") {
    return (
      <select
        className="input"
        value={value ?? ""}
        onChange={(e) => { onChange(e.target.value); setTimeout(onCommit, 0); }}
      >
        <option value="">— select —</option>
        {(field.options || []).map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    );
  }
  if (field.type === "users") {
    const selected: string[] = Array.isArray(value) ? value : value ? [String(value)] : [];
    const toggle = (id: string) => {
      const next = selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id];
      onChange(next);
      setTimeout(onCommit, 0);
    };
    return (
      <div className="rounded-[8px] border border-border p-1.5 max-h-36 overflow-y-auto flex flex-wrap gap-1">
        {(field.options || []).map((o) => {
          const on = selected.includes(o.value);
          return (
            <button
              key={o.value}
              type="button"
              onClick={() => toggle(o.value)}
              className={`chip transition-all ${on ? "!bg-[color:var(--accent)] !text-white !border-[color:var(--accent)]" : "hover:border-[color:var(--accent)]"}`}
            >
              {o.label.split(" (")[0]}
            </button>
          );
        })}
      </div>
    );
  }
  if (field.type === "boolean") {
    const v = value === true || value === "true";
    return (
      <div className="flex gap-1">
        {[
          { l: "Yes", val: true },
          { l: "No", val: false },
        ].map((o) => (
          <button
            key={o.l}
            type="button"
            className={`btn flex-1 justify-center ${
              (value != null && v === o.val) ? "btn-primary" : ""
            }`}
            style={{ padding: "0.35rem" }}
            onClick={() => { onChange(o.val); setTimeout(onCommit, 0); }}
          >
            {o.l}
          </button>
        ))}
      </div>
    );
  }
  if (field.type === "number") {
    return (
      <input
        className="input"
        type="number"
        placeholder={field.money ? "amount" : undefined}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value === "" ? "" : Number(e.target.value))}
        onBlur={() => setTimeout(onCommit, 0)}
        onKeyDown={(e) => e.key === "Enter" && (e.currentTarget.blur())}
      />
    );
  }
  if (field.type === "date") {
    return (
      <input
        className="input"
        type="date"
        value={value ?? ""}
        onChange={(e) => { onChange(e.target.value); setTimeout(onCommit, 0); }}
      />
    );
  }
  if (field.type === "textarea") {
    return (
      <textarea
        className="input"
        rows={2}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        onBlur={() => setTimeout(onCommit, 0)}
      />
    );
  }
  return (
    <input
      className="input"
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value)}
      onBlur={onCommit}
      onKeyDown={(e) => e.key === "Enter" && e.currentTarget.blur()}
    />
  );
}
