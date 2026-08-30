import React, { useState } from "react";

/**
 * Shared AI affordance contract (plan §6A.2 / §16.3). One consistent treatment for a
 * suggestion, its source attribution, a confidence read, accept / edit / dismiss, and
 * an explanation on demand — used identically on every surface.
 */
export function AiAffordance({
  suggestion,
  source,
  confidence,
  explanation,
  onAccept,
  onEdit,
  onDismiss,
  busy,
  compact,
}: {
  suggestion: React.ReactNode;
  source?: string;
  confidence?: number | null;
  explanation?: string;
  onAccept?: () => void;
  onEdit?: () => void;
  onDismiss?: () => void;
  busy?: boolean;
  compact?: boolean;
}) {
  const [showWhy, setShowWhy] = useState(false);
  const pct = confidence == null ? null : Math.round(confidence * 100);
  const tone = pct == null ? "" : pct >= 85 ? "var(--ok)" : pct >= 65 ? "var(--warn)" : "var(--risk)";

  return (
    <div
      className="rounded-[10px] border fade-in"
      style={{ borderColor: "var(--ai)", background: "var(--ai-soft)", padding: compact ? "0.5rem 0.65rem" : "0.75rem" }}
    >
      <div className="flex items-start gap-2">
        <span
          className="mt-0.5 inline-flex items-center justify-center rounded-full text-[10px] font-medium shrink-0"
          style={{ width: 18, height: 18, background: "var(--ai)", color: "#fff" }}
          title="AI suggestion"
        >
          ✦
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-sm text-ink">{suggestion}</div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1 text-xs text-ink-faint">
            {source && <span>Source: {source}</span>}
            {pct != null && <span style={{ color: tone }}>{pct}% confidence</span>}
            {explanation && (
              <button className="link" onClick={() => setShowWhy((v) => !v)}>
                {showWhy ? "hide" : "why?"}
              </button>
            )}
          </div>
          {showWhy && explanation && (
            <div className="mt-1.5 text-xs text-ink-soft border-l-2 pl-2" style={{ borderColor: "var(--ai)" }}>
              {explanation}
            </div>
          )}
          {(onAccept || onEdit || onDismiss) && (
            <div className="flex gap-1.5 mt-2">
              {onAccept && (
                <button className="btn btn-ai" style={{ padding: "0.3rem 0.6rem" }} disabled={busy} onClick={onAccept}>
                  Accept
                </button>
              )}
              {onEdit && (
                <button className="btn" style={{ padding: "0.3rem 0.6rem" }} disabled={busy} onClick={onEdit}>
                  Edit
                </button>
              )}
              {onDismiss && (
                <button className="btn" style={{ padding: "0.3rem 0.6rem" }} disabled={busy} onClick={onDismiss}>
                  Dismiss
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
