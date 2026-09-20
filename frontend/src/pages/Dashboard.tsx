import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api, money, date, usePending, usePerms } from "../api";
import { Card, SectionTitle, Stat, Badge, Spinner, Empty } from "../components/ui";
import { Icon } from "../components/icons";
import { AiInsight } from "../components/AiInsight";

const AXIS = { fontSize: 11, fill: "var(--ink-faint)" };
const PALETTE = ["var(--accent)", "var(--warn)", "var(--ok)", "var(--risk)", "var(--ai)", "#59a5d5", "#c583b2", "#8fbf6a", "#d9a441", "#8b93a7"];

const SECTION_LABELS: Record<string, string> = {
  "ai-insight": "AI insight",
  "attention": "Needs your attention",
  "stats": "Key numbers",
  "by-type": "Contracts by type",
  "expiry": "Expiry & renewal pipeline",
  "risk": "Risk distribution",
  "migration": "Migration health",
  "inquiry": "Ask the portfolio",
  "tasks": "My open items",
};

export default function Dashboard() {
  const nav = useNavigate();
  const qc = useQueryClient();
  const dash = useQuery({ queryKey: ["analytics"], queryFn: () => api("/analytics/dashboard") });
  const obl = useQuery({ queryKey: ["obl-dash"], queryFn: () => api("/obligations/dashboard") });
  const suggestions = useQuery({ queryKey: ["suggestions"], queryFn: () => api<string[]>("/inquiry/suggestions") });
  const me = usePending();
  const cfg = useQuery({ queryKey: ["dash-config"], queryFn: () => api("/me/dashboard-config") });
  const mine: any = me.data || {};
  const can = usePerms();
  const isApprover = can("APPROVE");

  const [layout, setLayout] = useState<{ key: string; visible: boolean; size?: "full" | "half" }[] | null>(null);
  const [editing, setEditing] = useState(false);
  const [tasksOpen, setTasksOpen] = useState(false);
  const [dragKey, setDragKey] = useState<string | null>(null);

  // merge stored layout with newly-added default sections and custom charts
  useEffect(() => {
    if (!cfg.data || layout) return;
    const stored = (cfg.data.layout || []) as { key: string; visible: boolean; size?: "full" | "half" }[];
    const charts: any[] = cfg.data.charts || [];
    const known = new Set(stored.map((s) => s.key));
    const merged = [...stored];
    for (const k of Object.keys(SECTION_LABELS)) if (!known.has(k)) merged.push({ key: k, visible: true, size: "full" } as any);
    for (const c of charts) {
      const k = `chart:${c.id}`;
      if (!known.has(k)) merged.push({ key: k, visible: true, size: "full" } as any);
    }
    setLayout(merged);
  }, [cfg.data, layout]);

  const charts: any[] = layout ? (cfg.data?.charts || []).filter((c: any) => layout.some((l) => l.key === `chart:${c.id}`)) : [];

  const save = useMutation({
    mutationFn: (next: any) => api("/me/dashboard-config", { method: "PUT", json: next }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["dash-config"] }),
  });

  function updateLayout(next: { key: string; visible: boolean; size?: "full" | "half" }[]) {
    setLayout(next);
    save.mutate({ layout: next, charts });
  }

  function setSize(key: string, size: "full" | "half") {
    if (!layout) return;
    updateLayout(layout.map((l) => (l.key === key ? { ...l, size } : l)));
  }

  function removeChart(id: string) {
    const nextCharts = charts.filter((c) => c.id !== id);
    const nextLayout = (layout || []).filter((l) => l.key !== `chart:${id}`);
    setLayout(nextLayout);
    save.mutate({ layout: nextLayout, charts: nextCharts });
  }

  /** Live reorder while dragging: move the dragged section to the hovered slot. */
  function dropTo(fromKey: string, toKey: string) {
    if (!layout) return;
    const from = idx(fromKey);
    const to = idx(toKey);
    if (from < 0 || to < 0 || from === to) return;
    const next = [...layout];
    const [m] = next.splice(from, 1);
    next.splice(to, 0, m);
    setLayout(next);
  }

  function toggle(key: string) {
    if (!layout) return;
    updateLayout(layout.map((l) => (l.key === key ? { ...l, visible: !l.visible } : l)));
  }

  const d: any = dash.data || {};
  const val: Record<string, number> = d.portfolioValueByCurrency || {};
  const expiry = Object.entries(d.expiryPipeline || {}).map(([k, v]) => ({ name: `${k}d`, value: v as number }));
  const byType = (d.byType || []).map((x: any) => ({ name: x.key, value: x.value }));
  const risk = Object.entries(d.riskTiers || {}).map(([k, v]) => ({ name: k, value: v as number }));

  const renders: Record<string, () => any> = {
    "ai-insight": () => <AiInsight />,
    "attention": () => (
      <AttentionCard mine={mine} editing={editing} isApprover={isApprover} />
    ),
    "stats": () => (
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Stat label="Contracts you can access" value={d.totalContracts ?? "—"} />
        <Stat label="Expiring ≤ 90 days" value={d.expiryPipeline?.["90"] ?? 0} tone="text-[color:var(--warn)]" />
        <Stat label="Obligations overdue" value={obl.data?.overdue ?? d.obligationsOverdue ?? 0} tone="text-[color:var(--risk)]" />
        <Stat label="Open workflow tasks" value={d.openWorkflowTaskTotal ?? 0} />
      </div>
    ),
    "by-type": () => <ChartCard title="Contracts by type" data={byType} accent="var(--accent)" horizontal />,
    "expiry": () => <ChartCard title="Expiry & renewal pipeline" data={expiry} accent="var(--warn)" />,
    "risk": () => (
      <ChartCard
        title="Risk distribution"
        data={risk}
        accent="var(--risk)"
        cellColor={(name: string) => (name === "CRITICAL" ? "var(--risk)" : name === "HIGH" ? "var(--orange)" : name === "MEDIUM" ? "var(--warn)" : "var(--ok)")}
      />
    ),
    "migration": () => (
      <Card>
        <SectionTitle>Migration</SectionTitle>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-ink-soft">Migrated contracts</span>
            <span className="tabular">{d.migration?.migratedContracts ?? 0}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-ink-soft">Unverified low-confidence terms</span>
            <Badge tone="warn">{d.migration?.unverifiedLowConfidenceTerms ?? 0}</Badge>
          </div>
          <p className="text-xs text-ink-faint pt-1">
            Unverified extracted values are visually distinct everywhere they appear, including renewal alerts.
          </p>
        </div>
      </Card>
    ),
    "inquiry": () => (
      <Card>
        <SectionTitle right={<Link className="link text-xs" to="/inquiry">Open inquiry</Link>}>Ask the portfolio</SectionTitle>
        <div className="space-y-1.5">
          {(suggestions.data || []).map((s) => (
            <button
              key={s}
              className="w-full text-left text-sm px-2 py-1.5 rounded-[8px] hover:bg-surface-2"
              onClick={() => nav(`/inquiry?q=${encodeURIComponent(s)}`)}
            >
              {s}
            </button>
          ))}
        </div>
      </Card>
    ),
    "tasks": () => {
      const drafts = (mine.myDrafts || []) as any[];
      const rejected = drafts.filter((c) => c.rejectionReason);
      const freshDrafts = drafts.filter((c) => !c.rejectionReason);
      const openTasks = (mine.openTasks || []) as any[];
      const nothing = openTasks.length === 0 && drafts.length === 0;
      const total = rejected.length + freshDrafts.length + openTasks.length;
      let budget = tasksOpen ? Infinity : LIMIT;
      const take = <T,>(xs: T[]) => { const r = xs.slice(0, budget); budget -= r.length; return r; };
      return (
        <Card>
          <SectionTitle
            right={isApprover ? <Link className="link text-xs" to="/approvals">All approvals</Link> : undefined}
          >
            My open items
          </SectionTitle>
          {me.isLoading ? (
            <Spinner />
          ) : nothing ? (
            <Empty>Nothing waiting on you.</Empty>
          ) : (
            <div className="divide-y divide-border">
              {take(rejected).map((c: any) => (
                <div key={c.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <Link to={`/contracts/${c.id}`} className="link text-sm font-medium">
                      {c.contractNumber}
                    </Link>
                    <span className="text-sm text-ink-soft"> · {c.title}</span>
                    <div className="text-xs text-ink-faint truncate">
                      <Badge tone="risk">rejected</Badge> {c.rejectionReason}
                    </div>
                  </div>
                  <Link to={`/contracts/${c.id}`} className="btn">Review</Link>
                </div>
              ))}
              {take(freshDrafts).map((c: any) => (
                <div key={c.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <Link to={`/contracts/${c.id}`} className="link text-sm font-medium">{c.contractNumber}</Link>
                    <span className="text-sm text-ink-soft"> · {c.title}</span>
                    <div className="text-xs text-ink-faint">Draft — review &amp; submit for approval</div>
                  </div>
                  <Link to={`/contracts/${c.id}`} className="btn">Open</Link>
                </div>
              ))}
              {take(openTasks).map((t: any) => (
                <div key={t.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <Link to={`/contracts/${t.contractId}`} className="link text-sm font-medium">
                      {t.contractNumber}
                    </Link>
                    {t.discussionUpdate && (
                      <span className="inline-flex align-[-2px] ml-1.5" title="New discussion activity on this contract">
                        <Icon.message width={13} height={13} style={{ color: "var(--accent)" }} />
                      </span>
                    )}
                    <span className="text-sm text-ink-soft"> · {t.contractTitle}</span>
                    <div className="text-xs text-ink-faint">
                      {t.state} · {t.type} · due {date(t.dueAt)} {t.overdue && <Badge tone="risk">overdue</Badge>}
                    </div>
                  </div>
                  <Link to={isApprover ? "/approvals" : `/contracts/${t.contractId}?tab=workflow`} className="btn">
                    Review
                  </Link>
                </div>
              ))}
            </div>
          )}
          <ExpandToggle open={tasksOpen} total={total} limit={LIMIT} onToggle={() => setTasksOpen((o) => !o)} />
        </Card>
      );
    },
  };

  function renderSection(key: string, editMode: boolean) {
    const sz = (layout || []).find((l) => l.key === key)?.size === "half" ? "half" : "full";
    const sizeProps = { size: sz as "full" | "half", onSize: (s: "full" | "half") => setSize(key, s) };
    if (key.startsWith("chart:")) {
      const id = key.slice(6);
      const chart = charts.find((c) => c.id === id);
      if (!chart) return null;
      const body = <ChartSection chart={chart} />;
      return editMode ? (
        <EditShell label={chart.title || "Inquiry chart"} {...sizeProps} onDelete={() => removeChart(id)}>{body}</EditShell>
      ) : (
        body
      );
    }
    const r = renders[key];
    if (!r) return null;
    const body = r();
    return editMode ? (
      <EditShell label={SECTION_LABELS[key] || key} {...sizeProps} onHide={() => toggle(key)}>
        {body == null ? <Empty>Nothing to show right now.</Empty> : body}
      </EditShell>
    ) : (
      body
    );
  }

  function idx(key: string) {
    return (layout || []).findIndex((l) => l.key === key);
  }

  if (dash.isLoading || cfg.isLoading || !layout) return <Spinner label="Loading dashboard…" />;

  const visible = layout.filter((l) => l.visible);
  const hidden = layout.filter((l) => !l.visible);
  const labelOf = (k: string) => (k.startsWith("chart:") ? charts.find((c) => `chart:${c.id}` === k)?.title || "Inquiry chart" : SECTION_LABELS[k] || k);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between -mb-2">
        <div>
          <h1 className="text-xl font-medium">Portfolio</h1>
          <p className="text-sm text-ink-faint">
            {d.totalContracts} contracts · {Object.entries(val).map(([c, a]) => money(a, c)).join(" · ") || "no value recorded"}
          </p>
        </div>
        <button className="btn" onClick={() => setEditing((e) => !e)}>
          {editing ? null : <Icon.settings width={14} height={14} />}
          {editing ? "Done" : "Customize"}
        </button>
      </div>

      {editing && (
        <div
          className="rounded-[10px] border border-dashed border-[color:var(--accent)] px-3 py-2 text-xs text-ink-soft flex items-center gap-2"
          style={{ background: "color-mix(in srgb, var(--accent-soft) 60%, transparent)" }}
        >
          <Icon.settings width={13} height={13} />
          Drag sections to reorder, switch half or full width — you see the real layout while you edit. Hide with the eye, or remove inquiry charts. Your layout is saved automatically.
        </div>
      )}

      {!editing && (
        <div className="grid gap-6 md:grid-cols-2 items-start">
          {visible.map((l) => (
            <div key={l.key} className={l.size === "half" ? "" : "md:col-span-2"}>{renderSection(l.key, false)}</div>
          ))}
        </div>
      )}

      {editing && (
        <>
          <div className="grid gap-6 md:grid-cols-2 items-start" onDragOver={(e) => e.preventDefault()}>
            {visible.map((l) => (
              <div
                key={l.key}
                className={`cursor-grab active:cursor-grabbing ${l.size === "half" ? "" : "md:col-span-2"}`}
                draggable
                onDragStart={(e) => {
                  e.dataTransfer.setData("text/plain", l.key);
                  e.dataTransfer.effectAllowed = "move";
                  setDragKey(l.key);
                }}
                onDragEnter={(e) => {
                  e.preventDefault();
                  if (!dragKey || dragKey === l.key) return;
                  dropTo(dragKey, l.key);
                }}
                onDragEnd={() => {
                  if (dragKey && layout) save.mutate({ layout, charts });
                  setDragKey(null);
                }}
                style={{ opacity: dragKey === l.key ? 0.45 : undefined }}
              >
                {renderSection(l.key, true)}
              </div>
            ))}
          </div>
          {visible.length === 0 && <Empty>All sections hidden — add some back below.</Empty>}
          {hidden.length > 0 && (
            <Card>
              <SectionTitle>Hidden sections</SectionTitle>
              <div className="flex flex-wrap gap-2">
                {hidden.map((l) => (
                  <button key={l.key} className="btn" onClick={() => toggle(l.key)}>
                    <Icon.eye width={13} height={13} /> {labelOf(l.key)}
                  </button>
                ))}
              </div>
            </Card>
          )}
        </>
      )}
    </div>
  );
}

/* ---------- edit-mode wrappers ---------- */

function EditShell({ label, size, onSize, children, onHide, onDelete }: {
  label?: string; size?: "full" | "half"; onSize?: (s: "full" | "half") => void; children: any;
  onHide?: () => void; onDelete?: () => void;
}) {
  return (
    <div className="relative rounded-[12px] border border-dashed border-[color:var(--accent)] p-2.5">
      <div className="absolute -top-2.5 right-2 flex items-center gap-1 bg-surface border border-border rounded-[8px] px-1 py-0.5 shadow-sm z-10">
        <span className="text-ink-faint cursor-move" title="Drag to reorder"><Icon.menu width={13} height={13} /></span>
        {label && <span className="text-[11px] text-ink-faint px-1">{label}</span>}
        {onSize && (
          <>
            <button
              className="btn !p-1 !border-0"
              title="Half width"
              onClick={() => onSize("half")}
              style={size === "half" ? { background: "var(--accent-soft)", color: "var(--accent)" } : undefined}
            >
              <Icon.halfWidth width={13} height={13} />
            </button>
            <button
              className="btn !p-1 !border-0"
              title="Full width"
              onClick={() => onSize("full")}
              style={size !== "half" ? { background: "var(--accent-soft)", color: "var(--accent)" } : undefined}
            >
              <Icon.fullWidth width={13} height={13} />
            </button>
          </>
        )}
        {onHide && <button className="btn !p-1 !border-0" title="Hide section" onClick={onHide}><Icon.eyeOff width={13} height={13} /></button>}
        {onDelete && <button className="btn !p-1 !border-0" title="Remove chart" onClick={onDelete}><Icon.trash width={13} height={13} /></button>}
      </div>
      {children}
    </div>
  );
}

const LIMIT = 6;

function ExpandToggle({ open, total, limit, onToggle }: { open: boolean; total: number; limit: number; onToggle: () => void }) {
  if (total <= limit) return null;
  return (
    <button className="link text-xs mt-2.5" onClick={onToggle}>
      {open ? "Show less" : `Show all ${total}`}
    </button>
  );
}

function AttentionCard({ mine, editing, isApprover }: { mine: any; editing: boolean; isApprover: boolean }) {
  const [open, setOpen] = useState(false);
  const attention =
    (mine.openTasks || []).length + (mine.discussionsAwaiting || 0) + (mine.accessToDecide || []).length
    + (mine.myDrafts || []).length;
  if (attention === 0 && !editing) return null;
  const items = [
    ...(mine.myDrafts || []).map((c: any) => (
            <Link key={c.id} to={`/contracts/${c.id}`} className="flex items-center gap-2.5 rounded-[8px] border border-border p-2.5 lift hover:border-[color:var(--accent)]">
              {c.rejectionReason ? (
                <>
                  <span className="w-8 h-8 rounded-[8px] grid place-items-center shrink-0" style={{ background: "color-mix(in srgb, var(--risk) 15%, transparent)", color: "var(--risk)" }}><Icon.x width={16} height={16} /></span>
                  <div className="min-w-0"><div className="text-sm font-medium truncate">{c.contractNumber}</div><div className="text-xs text-ink-faint truncate">Rejected — {c.rejectionReason}</div></div>
                </>
              ) : (
                <>
                  <span className="w-8 h-8 rounded-[8px] grid place-items-center shrink-0" style={{ background: "var(--accent-soft)", color: "var(--accent)" }}><Icon.file width={16} height={16} /></span>
                  <div className="min-w-0"><div className="text-sm font-medium truncate">{c.contractNumber}</div><div className="text-xs text-ink-faint">Draft — review & submit for approval</div></div>
                </>
              )}
            </Link>
          )),
    ...(mine.openTasks || []).map((t: any) => (
            <Link key={t.id} to={isApprover ? "/approvals" : `/contracts/${t.contractId}?tab=workflow`} className="flex items-center gap-2.5 rounded-[8px] border border-border p-2.5 lift hover:border-[color:var(--accent)]">
              <span className="w-8 h-8 rounded-[8px] grid place-items-center shrink-0" style={{ background: "color-mix(in srgb, var(--ok) 15%, transparent)", color: "var(--ok)" }}><Icon.checkCircle width={16} height={16} /></span>
              <div className="min-w-0"><div className="text-sm font-medium truncate">{t.contractNumber}</div><div className="text-xs text-ink-faint">{t.state} · {t.type} · due {date(t.dueAt)}{t.overdue ? " · overdue" : ""}</div></div>
            </Link>
          )),
    ...(mine.discussions || []).filter((x: any) => x.awaitingMe).map((x: any) => (
            <Link key={x.threadId} to={`/contracts/${x.contractId}`} className="flex items-center gap-2.5 rounded-[8px] border border-border p-2.5 lift hover:border-[color:var(--accent)]">
              <span className="w-8 h-8 rounded-[8px] grid place-items-center shrink-0" style={{ background: "color-mix(in srgb, var(--warn) 15%, transparent)", color: "var(--warn)" }}><Icon.message width={16} height={16} /></span>
              <div className="min-w-0"><div className="text-sm font-medium truncate">{x.title}</div><div className="text-xs text-ink-faint">{x.contractNumber} · {x.mentioned ? "you were mentioned" : x.askedAgent ? "your agent was asked" : "reply awaited"}</div></div>
            </Link>
          )),
    ...(mine.accessToDecide || []).map((r: any) => (
            <Link key={r.id} to="/access" className="flex items-center gap-2.5 rounded-[8px] border border-border p-2.5 lift hover:border-[color:var(--accent)]">
              <span className="w-8 h-8 rounded-[8px] grid place-items-center shrink-0" style={{ background: "color-mix(in srgb, var(--ai) 15%, transparent)", color: "var(--ai)" }}><Icon.shieldCheck width={16} height={16} /></span>
              <div className="min-w-0"><div className="text-sm font-medium truncate">Access request</div><div className="text-xs text-ink-faint">{r.scope}</div></div>
            </Link>
          )),
  ];

  return (
    <Card className="border-[color:var(--accent)]">
      <SectionTitle>Needs your attention</SectionTitle>
      {attention === 0 ? (
        <Empty>Nothing needs your attention right now.</Empty>
      ) : (
        <>
          <div className="grid sm:grid-cols-2 gap-2">
            {open ? items : items.slice(0, LIMIT)}
          </div>
          <ExpandToggle open={open} total={items.length} limit={LIMIT} onToggle={() => setOpen((o) => !o)} />
        </>
      )}
    </Card>
  );
}

/* ---------- chart cards ---------- */

function ChartCard({ title, data, accent, horizontal, cellColor }: {
  title: string; data: { name: string; value: number }[]; accent: string; horizontal?: boolean; cellColor?: (name: string) => string;
}) {
  const empty = !data || data.length === 0;
  return (
    <Card>
      <SectionTitle>{title}</SectionTitle>
      {empty ? (
        <Empty>No data yet.</Empty>
      ) : (
        <div style={{ height: 200 }}>
          <ResponsiveContainer>
            {horizontal ? (
              <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
                <CartesianGrid horizontal={false} stroke="var(--border)" />
                <XAxis type="number" tick={AXIS} allowDecimals={false} />
                <YAxis type="category" dataKey="name" tick={AXIS} width={110} />
                <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 12 }} />
                <Bar dataKey="value" fill={accent} radius={[0, 3, 3, 0]} barSize={16} />
              </BarChart>
            ) : (
              <BarChart data={data} margin={{ left: 8, right: 16 }}>
                <CartesianGrid vertical={false} stroke="var(--border)" />
                <XAxis dataKey="name" tick={AXIS} />
                <YAxis tick={AXIS} allowDecimals={false} />
                <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 12 }} />
                <Bar dataKey="value" radius={[3, 3, 0, 0]} barSize={34}>
                  {data.map((r) => (
                    <Cell key={r.name} fill={cellColor ? cellColor(r.name) : accent} />
                  ))}
                </Bar>
              </BarChart>
            )}
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}

/* ---------- custom inquiry chart sections ---------- */

export const CHART_GROUPS: Record<string, { label: string; rowKey: string }> = {
  status: { label: "Status", rowKey: "status" },
  type: { label: "Contract type", rowKey: "type" },
  entity: { label: "Entity", rowKey: "entity" },
  riskTier: { label: "Risk tier", rowKey: "riskTier" },
  currency: { label: "Currency", rowKey: "currency" },
};

function aggregate(rows: any[], groupBy: string) {
  const rk = CHART_GROUPS[groupBy]?.rowKey || "status";
  const m = new Map<string, number>();
  for (const r of rows) {
    const k = String(r[rk] ?? "—") || "—";
    m.set(k, (m.get(k) || 0) + 1);
  }
  return [...m.entries()].sort((a, b) => b[1] - a[1]).map(([name, value]) => ({ name, value }));
}

/** A dashboard section rendering a saved inquiry as a bar or pie chart. */
export function ChartSection({ chart, editing }: { chart: any; editing?: boolean }) {
  const q = useQuery({
    queryKey: ["dash-chart", chart.id],
    queryFn: () =>
      api("/inquiry/rerun", { method: "POST", json: { question: chart.question, interpreted: chart.interpreted, filters: chart.filters || {} } }),
  });

  const rows: any[] = q.data?.contracts || q.data?.rows || [];
  const data = useMemo(() => aggregate(rows, chart.groupBy), [rows, chart.groupBy]);

  return (
    <Card className={editing ? "" : "card-hover"}>
      <SectionTitle
        right={
          <div className="flex items-center gap-2">
            <Badge tone="accent">{chart.chart === "pie" ? "pie" : "bar"} · by {CHART_GROUPS[chart.groupBy]?.label || "status"}</Badge>
            <Link to={`/inquiry?q=${encodeURIComponent(chart.question)}`} className="link text-xs">refine</Link>
          </div>
        }
      >
        {chart.title}
      </SectionTitle>
      {q.isLoading ? (
        <div style={{ height: 200 }} className="grid place-items-center"><Spinner /></div>
      ) : q.isError ? (
        <Empty>Could not refresh this inquiry chart.</Empty>
      ) : data.length === 0 ? (
        <Empty>No contracts match this inquiry right now.</Empty>
      ) : chart.chart === "pie" ? (
        <div style={{ height: 220 }}>
          <ResponsiveContainer>
            <PieChart>
              <Pie data={data} dataKey="value" nameKey="name" innerRadius={48} outerRadius={78} paddingAngle={2} stroke="none">
                {data.map((entry, i) => (
                  <Cell key={entry.name} fill={PALETTE[i % PALETTE.length]} />
                ))}
              </Pie>
              <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 12 }} />
              <Legend wrapperStyle={{ fontSize: 11 }} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div style={{ height: 220 }}>
          <ResponsiveContainer>
            <BarChart data={data} margin={{ left: 8, right: 16 }}>
              <CartesianGrid vertical={false} stroke="var(--border)" />
              <XAxis dataKey="name" tick={AXIS} interval={0} angle={data.length > 5 ? -20 : 0} textAnchor={data.length > 5 ? "end" : "middle"} height={data.length > 5 ? 46 : 30} />
              <YAxis tick={AXIS} allowDecimals={false} />
              <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 12 }} />
              <Bar dataKey="value" radius={[3, 3, 0, 0]} barSize={36}>
                {data.map((entry, i) => (
                  <Cell key={entry.name} fill={PALETTE[i % PALETTE.length]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
      <div className="text-[11px] text-ink-faint mt-1.5">
        Live result of your saved inquiry — {rows.length} matching contract{rows.length === 1 ? "" : "s"}, recomputed each time you open the dashboard.
      </div>
    </Card>
  );
}