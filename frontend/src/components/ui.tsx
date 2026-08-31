import React, { useState } from "react";

export function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`card p-4 ${className}`}>{children}</div>;
}

export function SectionTitle({ children, right }: { children: React.ReactNode; right?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between mb-3">
      <h2 className="text-sm font-medium text-ink-soft uppercase tracking-wide">{children}</h2>
      {right}
    </div>
  );
}

export type BadgeTone =
  | "neutral" | "ok" | "warn" | "risk" | "accent" | "ai"
  | "info" | "teal" | "orange";

const TONE_COLORS: Record<BadgeTone, string | null> = {
  neutral: null,
  ok: "var(--ok)",
  warn: "var(--warn)",
  risk: "var(--risk)",
  accent: "var(--accent)",
  ai: "var(--ai)",
  info: "var(--info)",
  teal: "var(--teal)",
  orange: "var(--orange)",
};

export function Badge({
  children,
  tone = "neutral",
}: {
  children: React.ReactNode;
  tone?: BadgeTone;
}) {
  const c = TONE_COLORS[tone] ?? TONE_COLORS.neutral;
  const style =
    c == null
      ? undefined
      : {
          color: c,
          borderColor: `color-mix(in srgb, ${c} 40%, transparent)`,
          background: `color-mix(in srgb, ${c} 11%, transparent)`,
        };
  return <span className="chip" style={style}>{children}</span>;
}

export function statusTone(status?: string): BadgeTone {
  switch ((status || "").toUpperCase()) {
    case "EXECUTED":
    case "DONE":
    case "CLOSED":
    case "ACTIVE":
    case "SIGNED":
      return "ok";
    case "APPROVED":
      return "teal";
    case "IN_REVIEW":
    case "PENDING_APPROVAL":
    case "AWAITING_INPUT":
      return "info";
    case "CLOSED_REJECTED":
    case "REJECTED":
    case "OVERDUE":
    case "BREACHED":
    case "EXPIRED":
    case "CANCELLED":
    case "TERMINATED":
      return "risk";
    case "DRAFT":
      return "neutral";
    case "PENDING":
    case "PENDING_REVIEW":
    case "IN_PROGRESS":
    case "SUSPENDED":
      return "warn";
    default:
      return "warn";
  }
}

/** Severity / risk-tier colors: CRITICAL red, HIGH orange, MEDIUM amber, LOW green. */
export function riskTone(tier?: string): BadgeTone {
  switch ((tier || "").toUpperCase()) {
    case "CRITICAL":
      return "risk";
    case "HIGH":
      return "orange";
    case "MEDIUM":
      return "warn";
    case "LOW":
      return "ok";
    default:
      return "neutral";
  }
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-ink-faint text-sm py-6 justify-center">
      <span className="inline-block w-3 h-3 rounded-full border-2 border-ink-faint border-t-transparent animate-spin" />
      {label || "Loading…"}
    </div>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <div className="text-ink-faint text-sm py-8 text-center">{children}</div>;
}

export function KeyVal({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs text-ink-faint mb-0.5">{label}</div>
      <div className="text-sm tabular">{children}</div>
    </div>
  );
}

export function Stat({ label, value, tone }: { label: string; value: React.ReactNode; tone?: string }) {
  return (
    <div className="card stat-card p-3">
      <div className="text-xs text-ink-faint">{label}</div>
      <div className={`text-xl font-medium tabular mt-1 ${tone || ""}`}>{value}</div>
    </div>
  );
}

export function Confidence({ value, verified }: { value?: number | null; verified?: boolean }) {
  if (verified) return <Badge tone="teal">✓ verified</Badge>;
  if (value == null) return <Badge tone="neutral">—</Badge>;
  const pct = Math.round(value * 100);
  const tone = pct >= 85 ? "ok" : pct >= 65 ? "warn" : "risk";
  return <Badge tone={tone as any}>{pct}% conf.</Badge>;
}

export function Tabs({
  tabs,
  active,
  onChange,
}: {
  tabs: { key: string; label: string; count?: number; extra?: React.ReactNode; icon?: React.ReactNode }[];
  active: string;
  onChange: (k: string) => void;
}) {
  return (
    <div className="flex gap-1 border-b border-border mb-4 overflow-x-auto">
      {tabs.map((t) => (
        <button
          key={t.key}
          onClick={() => onChange(t.key)}
          className={`px-3 py-2 text-sm whitespace-nowrap border-b-2 -mb-px ${
            active === t.key
              ? "border-[color:var(--accent)] text-ink font-medium"
              : "border-transparent text-ink-faint hover:text-ink-soft"
          }`}
        >
          {t.icon}
          {t.label}
          {t.count != null && <span className="ml-1 text-ink-faint">({t.count})</span>}
          {t.extra}
        </button>
      ))}
    </div>
  );
}

/**
 * Confirmation dialog for an approve/reject workflow decision. The reason is optional for
 * "approve" and required for "reject"; the confirm button stays disabled until a reject
 * reason is entered. The note is passed back to onConfirm and is stored on the workflow task.
 */
export function DecisionDialog({
  kind,
  contractNumber,
  busy,
  error,
  onConfirm,
  onCancel,
}: {
  kind: "approve" | "reject";
  contractNumber?: string;
  busy?: boolean;
  error?: string;
  onConfirm: (note: string) => void;
  onCancel: () => void;
}) {
  const [note, setNote] = useState("");
  const isReject = kind === "reject";
  const blocked = isReject && !note.trim();
  return (
    <div
      className="modal-backdrop fixed inset-0 z-50 flex items-center justify-center p-6"
      style={{ background: "rgba(15, 17, 21, 0.5)", backdropFilter: "blur(3px)" }}
      onClick={onCancel}
    >
      <div className="modal-card card w-full max-w-sm p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-base font-medium">
          {isReject ? "Reject" : "Approve"} {contractNumber ? <b>{contractNumber}</b> : "this contract"}?
        </h2>
        <p className="text-sm text-ink-soft mt-2 leading-relaxed">
          {isReject
            ? "This ends your review and returns the request to the requestor as a draft carrying your reason, so they can revise and resubmit or close it. The reason is kept on the workflow record."
            : "This records your approval and advances the workflow to the next step."}
        </p>
        <label className="text-xs text-ink-faint mt-4 block">
          {isReject ? "Reason for rejection" : "Comment (optional)"}
          {isReject && <span style={{ color: "var(--risk)" }}> *</span>}
        </label>
        <textarea
          className="input mt-1"
          rows={3}
          autoFocus
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={isReject ? "e.g. Liability cap exceeds policy; needs re-negotiation." : "Add context for the record…"}
        />
        {error && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{error}</div>}
        <div className="flex justify-end gap-2 mt-5">
          <button className="btn" onClick={onCancel}>Go back</button>
          <button
            className="btn btn-primary"
            style={isReject ? { background: "var(--risk)", borderColor: "var(--risk)" } : {}}
            disabled={busy || blocked}
            onClick={() => onConfirm(note.trim())}
          >
            {busy ? "Working…" : isReject ? "Reject contract" : "Approve"}
          </button>
        </div>
      </div>
    </div>
  );
}
