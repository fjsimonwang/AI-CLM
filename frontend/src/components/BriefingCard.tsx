import { useEffect, useRef, useState } from "react";
import { Icon } from "./icons";

/** Shared AI approver briefing display used by the Approvals page and the contract page.
 *  With defaultFolded, the body starts hidden and expands on click or on ~0.5s hover;
 *  the chevron icon folds it again. onRerun places a Re-run action in the header. */
export function BriefingCard({
  briefing,
  generatedAt,
  cached,
  defaultFolded,
  onRerun,
  rerunning,
  autoOpen,
}: {
  briefing: any;
  generatedAt?: string;
  cached?: boolean;
  defaultFolded?: boolean;
  onRerun?: () => void;
  rerunning?: boolean;
  autoOpen?: boolean;
}) {
  const [open, setOpen] = useState(!defaultFolded);
  const hoverTimer = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (autoOpen) setOpen(true);
  }, [autoOpen]);

  if (!briefing && !onRerun) return null;
  const changes: string[] = briefing?.changes || [];
  const nonStandard: string[] = briefing?.non_standard || briefing?.nonStandard || [];

  function startHover() {
    if (!defaultFolded || open) return;
    if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
    hoverTimer.current = window.setTimeout(() => setOpen(true), 500);
  }
  function cancelHover() {
    if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
  }

  return (
    <div
      className="mt-3 fade-in rounded-[8px] border p-3"
      style={{ borderColor: "color-mix(in srgb, var(--ai) 35%, transparent)" }}
    >
      <div
        className="flex items-center justify-between mb-2"
        role={open ? undefined : "button"}
        onClick={() => !open && setOpen(true)}
        onMouseEnter={startHover}
        onMouseLeave={cancelHover}
      >
        <div className="flex items-center gap-1.5 text-xs font-medium text-ink-soft">
          <Icon.chevronDown
            width={13}
            height={13}
            className="shrink-0"
            style={{ transition: "transform 0.2s", transform: open ? "none" : "rotate(-90deg)" }}
            onClick={(e: any) => {
              e.stopPropagation();
              setOpen((o) => !o);
            }}
          />
          <Icon.sparkle width={13} height={13} /> AI approver briefing
          {!open && (
            <span className="text-[11px] text-ink-faint font-normal">— hover or click to show</span>
          )}
        </div>
        <div className="flex items-center gap-3">
          {generatedAt && open && (
            <span className="text-[11px] text-ink-faint">
              {cached ? "Generated " : "Regenerated "}
              {new Date(generatedAt).toLocaleString()}
            </span>
          )}
          {onRerun && (
            <button
              className="link text-xs"
              disabled={rerunning}
              onClick={(e: any) => {
                e.stopPropagation();
                onRerun();
              }}
            >
              {rerunning ? "Re-running…" : "Re-run"}
            </button>
          )}
        </div>
      </div>
      {open && briefing && (
        <div className="space-y-2.5">
          {briefing.summary && <p className="text-sm leading-relaxed text-ink">{briefing.summary}</p>}
          {changes.length > 0 && (
            <div>
              <div className="text-[10px] font-medium uppercase tracking-wider text-ink-faint mb-1">What changed</div>
              <ul className="list-disc pl-4 space-y-0.5">
                {changes.map((x, i) => (
                  <li key={i} className="text-sm text-ink-soft">{x}</li>
                ))}
              </ul>
            </div>
          )}
          {nonStandard.length > 0 && (
            <div>
              <div className="text-[10px] font-medium uppercase tracking-wider text-ink-faint mb-1">Deviations from standard</div>
              <ul className="list-disc pl-4 space-y-0.5">
                {nonStandard.map((x, i) => (
                  <li key={i} className="text-sm text-ink-soft">{x}</li>
                ))}
              </ul>
            </div>
          )}
          {briefing.decision && (
            <div className="text-sm text-ink-soft">
              <span className="font-medium text-ink">Decision requested: </span>
              {briefing.decision}
            </div>
          )}
        </div>
      )}
    </div>
  );
}