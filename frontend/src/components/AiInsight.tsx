import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import { Badge, Spinner } from "./ui";
import { Icon } from "./icons";

/**
 * The dashboard AI insight. Folded by default. The default view is a fast triage built only from
 * the user's open tasks and attention items; "Deeper analysis" runs the heavier trace analysis
 * (audit trail, AI activity, risks, obligations) on demand.
 */
export function AiInsight() {
  const [open, setOpen] = useState(false);
  const [deep, setDeep] = useState(false);
  const [deepForce, setDeepForce] = useState(0);

  const q = useQuery({
    queryKey: ["ai-insight"],
    queryFn: () => api("/ai/insight"),
    refetchInterval: (query: any) => (query?.state?.data?.pending ? 4000 : false),
  });

  const deepQ = useQuery({
    queryKey: ["ai-insight-deep", deepForce],
    queryFn: () => api(`/ai/insight/deep${deepForce ? "?force=true" : ""}`),
    enabled: deep,
    refetchInterval: (query: any) => (query?.state?.data?.pending ? 4000 : false),
  });
  const deepFailed = deepQ.data?.failed === true;

  const data: any = q.data || {};
  const aiDisabled = data.aiDisabled === true;
  const sig = data.signals || null;
  const signalCount = sig
    ? ["openTasks", "overdueTasks", "highRisks", "overdueObligations", "failingAi", "expiringSoon"]
        .reduce((a: number, k) => a + (Number(sig[k]) || 0), 0)
    : 0;
  const quick = data.quick;
  const deepInsight = deepQ.data?.insight;
  const deepSections = deepInsight
    ? [
        { key: "highlights", label: "What matters now", items: deepInsight.highlights || [], sev: true },
        { key: "suspicious", label: "Suspicious & worth checking", items: deepInsight.suspicious || [], sev: false },
        { key: "suggestions", label: "Suggested next actions", items: deepInsight.suggestions || [], sev: false, steps: true },
      ]
    : [];

  if (aiDisabled) {
    return (
      <div className="card px-4 py-3 flex items-center gap-2.5 text-sm text-ink-faint" style={{ borderColor: "var(--border)" }}>
        <Icon.sparkle width={15} height={15} className="shrink-0" />
        AI insight is off. Turn on <span className="font-medium text-ink-soft">Agent&nbsp;Crew</span> in the header to re-enable the advanced agent functions.
      </div>
    );
  }

  return (
    <div
      className="card overflow-hidden"
      style={{ borderColor: "color-mix(in srgb, var(--ai) 22%, var(--border))" }}
    >
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center gap-2.5 px-4 py-3 text-left hover:bg-surface-2 transition-colors"
      >
        <Icon.sparkle width={16} height={16} className="shrink-0" style={{ color: "var(--ai)" }} />
        <span className="text-sm font-medium text-ink-soft uppercase tracking-wide flex-1">AI insight</span>
        {!open && signalCount > 0 && (
          <Badge tone="neutral">
            {signalCount} item{signalCount > 1 ? "s" : ""} to review
          </Badge>
        )}
        {!open && quick && <Badge tone="neutral">ready</Badge>}
        <Icon.chevronDown
          width={15}
          height={15}
          style={{ transition: "transform 0.2s", transform: open ? "none" : "rotate(-90deg)" }}
        />
      </button>

      {open && (
        <div className="px-4 pb-4 pt-1 fade-in">
          {!quick ? (
            <div className="py-2">
              <Spinner label="Summarising your open tasks and attention items…" />
            </div>
          ) : (
            <div className="space-y-4">
              {quick.summary && <p className="text-sm text-ink-soft leading-relaxed">{quick.summary}</p>}

              {(quick.items || []).length > 0 && (
                <div className="space-y-2">
                  {quick.items.map((it: any, i: number) => (
                    <div key={i} className="rounded-[8px] border border-border p-2.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-medium">{it.title}</span>
                        {it.severity && (
                          <Badge tone={it.severity === "HIGH" ? "warn" : it.severity === "LOW" ? "ok" : "neutral"}>
                            {it.severity}
                          </Badge>
                        )}
                      </div>
                      {it.detail && <div className="text-xs text-ink-soft mt-1">{it.detail}</div>}
                    </div>
                  ))}
                </div>
              )}

              <div className="border-t border-border pt-3">
                {!deep ? (
                  <button className="btn" onClick={() => setDeep(true)}>
                    <Icon.sparkle width={13} height={13} /> Deeper analysis
                  </button>
                ) : deepFailed ? (
                  <div className="flex items-center gap-3">
                    <span className="text-xs text-ink-faint">Deeper analysis could not be generated.</span>
                    <button className="btn" onClick={() => setDeepForce((n) => n + 1)}>Try again</button>
                  </div>
                ) : !deepInsight ? (
                  <Spinner label="Analysing your audit trail, AI activity, risks and obligations…" />
                ) : (
                  <div className="space-y-4">
                    {deepSections.map((s) => (
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
                                    <Badge tone={it.severity === "HIGH" ? "warn" : it.severity === "LOW" ? "ok" : "neutral"}>
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
                    {deepQ.data?.generatedAt && (
                      <div className="text-[11px] text-ink-faint">
                        Deeper analysis · generated {new Date(deepQ.data.generatedAt).toLocaleString()}
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div className="text-[11px] text-ink-faint">
                Triage from your open tasks and attention items
                {data.generatedAt ? ` · updated ${new Date(data.generatedAt).toLocaleString()}` : ""}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
