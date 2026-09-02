import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, usePerms } from "../api";
import { Card, Badge, Spinner, Empty, riskTone } from "./ui";
import { Icon } from "./icons";
import { useHighlight } from "./highlight";
import { useDocEdited } from "./docEdited";

const sevTone = (s?: string) => riskTone(s);
const sevRank = (s?: string) => (s === "CRITICAL" ? 4 : s === "HIGH" ? 3 : s === "MEDIUM" ? 2 : 1);
const dedupe = (title: string) => (title || "").toLowerCase().replace(/\s+/g, " ").trim();

export function AiReviewPanel({
  contractId,
  collapsed: collapsedProp,
  onCollapsedChange,
}: {
  contractId: string;
  collapsed?: boolean;
  onCollapsedChange?: (v: boolean) => void;
}) {
  const can = usePerms();
  const qc = useQueryClient();
  const canPlaybook = can("EDIT_CONTRACT");
  const canClose = canPlaybook;
  const [selected, setSelected] = useState<string[]>([]);
  const [selectedRules, setSelectedRules] = useState<string[]>([]);
  const [collapsedInternal, setCollapsedInternal] = useState(false);
  const controlled = onCollapsedChange !== undefined;
  const collapsed = controlled ? !!collapsedProp : collapsedInternal;
  const setCollapsed = (v: boolean | ((p: boolean) => boolean)) => {
    const next = typeof v === "function" ? v(collapsed) : v;
    if (controlled) onCollapsedChange!(next);
    else setCollapsedInternal(next);
  };

  const rules = useQuery({ queryKey: ["review-rules"], queryFn: () => api("/ai/review-rules") });

  // check points are selectable and default to all checked
  useEffect(() => {
    if (!rules.data) return;
    const ids = (rules.data as any[]).filter((r) => r.isActive).map((r) => r.code);
    setSelectedRules((prev) => (prev.length === 0 ? ids : ids.filter((c) => prev.includes(c))));
  }, [rules.data]);
  const playbooks = useQuery({
    queryKey: ["playbooks"],
    queryFn: () => api("/playbooks"),
    enabled: canPlaybook,
  });
  const runQuery = useQuery({
    queryKey: ["review-run", contractId],
    queryFn: () => api(`/ai/review-runs?contractId=${contractId}`),
    enabled: !!contractId,
    refetchInterval: (q: any) => (q?.state?.data?.status === "RUNNING" ? 2500 : false),
  });

  const start = useMutation({
    mutationFn: () =>
      api("/ai/review-runs", { method: "POST", json: { contractId, playbookIds: selected, ruleCodes: selectedRules } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["review-run", contractId] }),
  });

  const risksQuery = useQuery({
    queryKey: ["risks", contractId],
    queryFn: () => api(`/contracts/${contractId}/risks`),
    enabled: !!contractId,
  });
  // Findings map 1:1 to register entries by title (the backend dedupes on the same key)
  const riskFor = (title: string) => {
    const k = dedupe(title);
    return ((risksQuery.data || []) as any[]).find((r) => dedupe(r.title) === k);
  };
  const updateRisk = useMutation({
    mutationFn: (p: { riskId: string; body: any }) =>
      api(`/contracts/${contractId}/risks/${p.riskId}`, { method: "POST", json: p.body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["risks", contractId] }),
  });
  const locate = useHighlight((s) => s.locate);

  const activeRules = (rules.data || []).filter((r: any) => r.isActive);
  const toggle = (id: string) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const toggleRule = (code: string) =>
    setSelectedRules((s) => (s.includes(code) ? s.filter((x) => x !== code) : [...s, code]));

  const run: any = runQuery.data;
  const running = run?.status === "RUNNING";
  const failed = run?.status === "FAILED";
  const hasResult = run?.status === "DONE";
  const findings: any[] = run?.findings || [];
  const critical = findings.filter((f: any) => f.severity === "CRITICAL").length;
  const everRan = !!run;

  // After a document edit, keep the review current: auto-run once per edit burst unless
  // a completed run already covers the edited content — findings ready before submit.
  const editedSeq = useDocEdited((s) => s.seq);
  const editedLastAt = useDocEdited((s) => s.lastAt);
  const autoAttempted = useRef(-1);
  useEffect(() => {
    if (!contractId || editedSeq === 0 || autoAttempted.current === editedSeq) return;
    if (running || start.isPending) return;
    // a run started after the last edit reviewed that content (body text is captured at start)
    const covers = hasResult && run.createdAt &&
      new Date(run.createdAt).getTime() > (editedLastAt || 0);
    if (covers) {
      autoAttempted.current = editedSeq;
      return;
    }
    autoAttempted.current = editedSeq;
    start.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editedSeq, run?.status, run?.createdAt]);

  return (
    <Card>
      <div className="flex items-center justify-between gap-2 mb-3">
        <button
          type="button"
          onClick={() => setCollapsed((c) => !c)}
          className="flex items-center gap-1.5 min-w-0"
          aria-expanded={!collapsed}
        >
          <Icon.chevronDown
            width={15}
            height={15}
            style={{ transition: "transform 0.2s", transform: collapsed ? "rotate(-90deg)" : "none" }}
          />
          <h2 className="text-sm font-medium text-ink-soft uppercase tracking-wide">AI document review</h2>
          {collapsed && hasResult && !running && (
            <Badge tone={critical > 0 ? "risk" : findings.length ? "warn" : "ok"}>
              {findings.length
                ? `${findings.length} finding${findings.length > 1 ? "s" : ""}${critical > 0 ? ` · ${critical} critical` : ""}`
                : "no issues"}
            </Badge>
          )}
          {collapsed && running && <Badge tone="ai">reviewing…</Badge>}
        </button>
        <button
          className="btn btn-ai shrink-0"
          style={{ padding: "0.35rem 0.7rem" }}
          disabled={running || start.isPending || selectedRules.length === 0}
          onClick={() => { setCollapsed(false); start.mutate(); }}
        >
          <Icon.sparkle width={14} height={14} />
          {running
            ? "Reviewing in background…"
            : start.isPending
              ? "Starting…"
              : everRan && hasResult
                ? "Re-run AI review"
                : "Run AI review"}
        </button>
      </div>

      {collapsed ? null : (
      <>
      <p className="text-xs text-ink-faint mb-2">
        A critical review of the current draft against the admin-defined rule checklist and playbooks.
        Runs in the background — you can leave this page and come back; every AI finding is also
        recorded in the Risks tab.
      </p>

      {rules.isLoading ? (
        <Spinner />
      ) : (
        <div className="mt-2 rounded-[8px] border border-border p-2.5 bg-surface-2">
          <div className="flex items-center justify-between mb-1.5">
            <div className="text-xs font-medium">Check points</div>
            <button
              className="link text-[11px]"
              onClick={() =>
                setSelectedRules(selectedRules.length === activeRules.length ? [] : activeRules.map((r: any) => r.code))
              }
            >
              {selectedRules.length === activeRules.length ? "Uncheck all" : "Check all"}
            </button>
          </div>
          <div className="text-[11px] text-ink-faint mb-2">
            Pick which check points the AI evaluates — all are checked by default.
          </div>
          <div className="flex flex-wrap gap-1">
            {activeRules.map((r: any) => {
              const on = selectedRules.includes(r.code);
              return (
                <button
                  key={r.code}
                  type="button"
                  title={`${r.instruction} (severity: ${r.severity})`}
                  onClick={() => toggleRule(r.code)}
                  className={`chip inline-flex items-center gap-1.5 transition-all ${on ? "!border-[color:var(--ai)]" : "opacity-50 hover:opacity-100"}`}
                >
                  {on ? (
                    <Icon.checkCircle width={12} height={12} style={{ color: "var(--ai)" }} />
                  ) : (
                    <span className="inline-block w-3 h-3 rounded-full border border-border" />
                  )}
                  {r.label}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {canPlaybook && (
        <div className="mt-3 rounded-[8px] border border-border p-2.5 bg-surface-2">
          <div className="text-xs font-medium mb-1.5">Playbook review (optional)</div>
          <div className="text-[11px] text-ink-faint mb-2">
            Pick one or more playbooks maintained in Admin → Playbooks; the AI reviews the draft
            against the positions in those documents.
          </div>
          {playbooks.isLoading ? (
            <Spinner />
          ) : (playbooks.data || []).filter((p: any) => p.isActive).length === 0 ? (
            <div className="text-xs text-ink-faint">
              No playbooks maintained yet — add them under Admin → Playbooks.
            </div>
          ) : (
            <div className="flex flex-wrap gap-1">
              {(playbooks.data || [])
                .filter((p: any) => p.isActive)
                .map((p: any) => {
                  const on = selected.includes(p.id);
                  const meta = [p.entity, p.contractType, p.jurisdiction].filter(Boolean).join(" · ");
                  return (
                    <button
                      key={p.id}
                      type="button"
                      title={meta || p.name}
                      onClick={() => toggle(p.id)}
                      className={`chip transition-all ${on ? "!bg-[color:var(--ai)] !text-white !border-[color:var(--ai)]" : "hover:border-[color:var(--ai)]"}`}
                    >
                      {p.name}
                    </button>
                  );
                })}
            </div>
          )}
        </div>
      )}

      {running && <Spinner label="The AI is reviewing the document in the background — safe to leave this page." />}

      {failed && (
        <div className="mt-3 text-xs" style={{ color: "var(--risk)" }}>
          Review failed: {run.error || "unknown error"}
        </div>
      )}

      {hasResult && !running && (
        <div className="mt-3">
          <div className="flex items-center gap-2 mb-2">
            {findings.length === 0 ? (
              <Badge tone="ok">No issues found</Badge>
            ) : (
              <>
                <Badge tone={critical > 0 ? "risk" : "warn"}>
                  {findings.length} finding{findings.length > 1 ? "s" : ""}
                  {critical > 0 ? ` · ${critical} critical` : ""}
                </Badge>
                <span className="text-xs text-ink-faint">
                  recorded in the risk register
                  {run.completedAt ? ` · ${new Date(run.completedAt).toLocaleString()}` : ""}
                </span>
              </>
            )}
          </div>
          {findings.length === 0 ? (
            <Empty>The AI review found no issues against the checklist.</Empty>
          ) : (
            <div className="space-y-2">
              {[...findings]
                .sort((a, b) => sevRank(b.severity) - sevRank(a.severity))
                .map((f: any, i: number) => {
                  const rel = riskFor(f.title);
                  const closed = rel?.status === "CLOSED";
                  return (
                    <div key={i} className={`rounded-[8px] border p-2.5 ${closed ? "opacity-70" : ""}`}>
                      <div className="flex items-center justify-between gap-2 mb-1">
                        <span className="text-sm font-medium">{f.title}</span>
                        <span className="flex items-center gap-1 shrink-0">
                          <Badge tone={sevTone(f.severity) as any}>{f.severity || "MEDIUM"}</Badge>
                          {f.rule && <Badge tone="neutral">rule</Badge>}
                          {f.playbook && <Badge tone="ai">playbook</Badge>}
                          {closed ? (
                            <Badge tone="ok">{rel.resolution === "DISMISSED" ? "dismissed" : "resolved"}</Badge>
                          ) : rel ? (
                            <Badge tone="accent">in risk register</Badge>
                          ) : null}
                        </span>
                      </div>
                      <div className="text-xs text-ink-soft">{f.detail}</div>
                      {f.location && (
                        <button
                          type="button"
                          title="Highlight this passage in the document"
                          className="text-left w-full text-[11px] font-serif italic text-ink-faint mt-1 pl-2 border-l-2 border-border hover:!text-ink-soft hover:border-[color:var(--ai)] transition-colors"
                          onClick={() => locate(f.location)}
                        >
                          “{f.location}”
                        </button>
                      )}
                      {canClose && closed ? (
                        <div className="text-[11px] text-ink-faint mt-1.5">closed in the risk register</div>
                      ) : canClose && rel ? (
                        <div className="flex items-center gap-3 text-xs mt-1.5">
                          <button
                            className="link"
                            disabled={updateRisk.isPending}
                            onClick={() => updateRisk.mutate({ riskId: rel.id, body: { status: "CLOSED", resolution: "DISMISSED" } })}
                          >
                            Dismiss
                          </button>
                          <button
                            className="link"
                            disabled={updateRisk.isPending}
                            onClick={() => updateRisk.mutate({ riskId: rel.id, body: { status: "CLOSED", resolution: "RESOLVED" } })}
                          >
                            Resolve
                          </button>
                        </div>
                      ) : null}
                    </div>
                  );
                })}
            </div>
          )}
        </div>
      )}

      {start.error && !running && (
        <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>
          {(start.error as any).message}
        </div>
      )}
      </>
      )}
    </Card>
  );
}