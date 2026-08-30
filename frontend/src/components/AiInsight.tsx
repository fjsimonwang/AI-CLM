import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import { Badge, Spinner } from "./ui";
import { Icon } from "./icons";

/**
 * The dashboard AI insight. Folded by default; the header shows a flashing indicator whenever
 * the backend reports attention signals (overdue items, high risks, failing AI runs…).
 * The insight itself is generated in the background from the user's whole activity trace.
 */
export function AiInsight() {
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: ["ai-insight"],
    queryFn: () => api("/ai/insight"),
    refetchInterval: (query: any) => (query?.state?.data?.pending ? 5000 : false),
  });

  const data: any = q.data || {};
  const sig = data.signals || null;
  const signalCount = sig
    ? ["overdueTasks", "highRisks", "overdueObligations", "failingAi", "expiringSoon"]
        .reduce((a: number, k) => a + (Number(sig[k]) || 0), 0)
    : 0;
  const openTasks = sig ? Number(sig.openTasks) || 0 : 0;
  const flashing = signalCount + openTasks > 0;
  const insight = data.insight;
  const sections = insight
    ? [
        { key: "highlights", label: "What matters now", items: insight.highlights || [], sev: true },
        { key: "suspicious", label: "Suspicious & worth checking", items: insight.suspicious || [], sev: false },
        { key: "suggestions", label: "Suggested next actions", items: insight.suggestions || [], sev: false, steps: true },
      ]
    : [];

  return (
    <div
      className="card overflow-hidden"
      style={flashing ? { borderColor: "var(--risk)" } : { borderColor: "color-mix(in srgb, var(--ai) 45%, transparent)" }}
    >
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flash-head w-full flex items-center gap-2.5 px-4 py-3 text-left hover:bg-surface-2 transition-colors"
      >
        {flashing ? (
          <span
            className="flash-dot w-2.5 h-2.5 rounded-full shrink-0"
            style={{ background: "var(--risk)" }}
          />
        ) : (
          <Icon.sparkle width={16} height={16} className="shrink-0" style={{ color: "var(--ai)" }} />
        )}
        <span className="text-sm font-medium text-ink-soft uppercase tracking-wide flex-1">AI insight</span>
        {open === false && (signalCount > 0 || openTasks > 0) && (
          <Badge tone={flashing ? "risk" : "neutral"}>
            {signalCount > 0
              ? `${signalCount + openTasks} signal${signalCount + openTasks > 1 ? "s" : ""}`
              : `${openTasks} task${openTasks > 1 ? "s" : ""}`}
          </Badge>
        )}
        {!flashing && insight && <Badge tone="ai">ready</Badge>}
        <Icon.chevronDown
          width={15}
          height={15}
          style={{ transition: "transform 0.2s", transform: open ? "none" : "rotate(-90deg)" }}
        />
      </button>

      {open && (
        <div className="px-4 pb-4 pt-1 fade-in">
          {!insight ? (
            <div className="py-2">
              <Spinner label="The AI is gathering your recent work, AI activity and open items…" />
            </div>
          ) : (
            <div className="space-y-4">
              {sections.map((s) => (
                <div key={s.key}>
                  <div className="text-[11px] uppercase tracking-wide text-ink-faint mb-1.5">{s.label}</div>
                  {s.items.length === 0 ? (
                    <div className="text-xs text-ink-faint">Nothing here.</div>
                  ) : (
                    <div className="space-y-2">
                      {s.items.map((it: any, i: number) => (
                        <div key={i} className="rounded-[8px] border border-border p-2.5">
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-sm font-medium">{it.title}</span>
                            {s.sev && (
                              <Badge tone={it.severity === "HIGH" ? "risk" : it.severity === "LOW" ? "ok" : "warn"}>
                                {it.severity}
                              </Badge>
                            )}
                          </div>
                          {it.detail && <div className="text-xs text-ink-soft mt-1">{it.detail}</div>}
                          {s.steps && Array.isArray(it.steps) && it.steps.length > 0 && (
                            <ol className="text-xs text-ink-soft mt-1.5 list-decimal pl-4 space-y-0.5">
                              {it.steps.map((step: string, j: number) => (
                                <li key={j}>{step}</li>
                              ))}
                            </ol>
                          )}
                          {s.steps && (
                            <div className="mt-1.5">
                              {it.in_system !== false ? (
                                <Badge tone="accent">do it in this system</Badge>
                              ) : (
                                <Badge tone="neutral">outside the system</Badge>
                              )}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
              <div className="text-[11px] text-ink-faint">
                Generated from your audit trail, AI activity log, tasks, risks and obligations
                {data.generatedAt ? ` · generated ${new Date(data.generatedAt).toLocaleString()}` : ""}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}