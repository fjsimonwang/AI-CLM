import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, date, usePerms } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty, riskTone } from "./ui";
import { Icon } from "./icons";

const CATEGORIES = [
  "LEGAL", "FINANCIAL", "OPERATIONAL", "COMPLIANCE", "DATA_PRIVACY", "COMMERCIAL", "OTHER",
];
const SEVERITIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

export function RiskRegister({ contractId, onLocate }: { contractId: string; onLocate?: (quote: string) => void }) {
  const can = usePerms();
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);

  const risks = useQuery({
    queryKey: ["risks", contractId],
    queryFn: () => api(`/contracts/${contractId}/risks`),
    enabled: !!contractId,
  });

  const add = useMutation({
    mutationFn: (body: any) => api(`/contracts/${contractId}/risks`, { method: "POST", json: body }),
    onSuccess: () => {
      setAdding(false);
      qc.invalidateQueries({ queryKey: ["risks", contractId] });
    },
  });

  const update = useMutation({
    mutationFn: (p: { riskId: string; body: any }) =>
      api(`/contracts/${contractId}/risks/${p.riskId}`, { method: "POST", json: p.body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["risks", contractId] }),
  });

  const list: any[] = risks.data || [];
  const open = list.filter((r) => r.status === "OPEN");
  const closed = list.filter((r) => r.status === "CLOSED");
  const sevRank = (s: string) => (s === "CRITICAL" ? 4 : s === "HIGH" ? 3 : s === "MEDIUM" ? 2 : 1);

  return (
    <div className="space-y-4 fade-in">
      <Card>
        <SectionTitle
          right={
            can("EDIT_CONTRACT") && !adding ? (
              <button className="btn" onClick={() => setAdding(true)}>
                <Icon.plus width={14} height={14} /> Add risk
              </button>
            ) : undefined
          }
        >
          Risk register
        </SectionTitle>
        <p className="text-xs text-ink-faint -mt-1 mb-3">
          Every risk on this contract — manually registered or identified by AI document review.
          AI-identified entries can be closed once resolved.
        </p>

        {adding && (
          <AddRiskForm
            busy={add.isPending}
            error={(add.error as any)?.message}
            onCancel={() => setAdding(false)}
            onSubmit={(body) => add.mutate(body)}
          />
        )}

        {risks.isLoading ? (
          <Spinner />
        ) : list.length === 0 ? (
          <Empty>
            No risks registered. Run an AI review from the Document tab, or add one manually.
          </Empty>
        ) : (
          <div className="divide-y divide-border">
            {[...open].sort((a, b) => sevRank(b.severity) - sevRank(a.severity)).map((r) => (
              <RiskRow key={r.id} risk={r} canClose={can("EDIT_CONTRACT")} onLocate={onLocate} onUpdate={(body) => update.mutate({ riskId: r.id, body })} busy={update.isPending} />
            ))}
          </div>
        )}
      </Card>

      {closed.length > 0 && (
        <Card>
          <SectionTitle>Closed ({closed.length})</SectionTitle>
          <div className="divide-y divide-border">
            {closed.map((r) => (
              <RiskRow key={r.id} risk={r} canClose={can("EDIT_CONTRACT")} onLocate={onLocate} onUpdate={(body) => update.mutate({ riskId: r.id, body })} busy={update.isPending} />
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

function RiskRow({
  risk,
  canClose,
  onLocate,
  onUpdate,
  busy,
}: {
  risk: any;
  canClose: boolean;
  onLocate?: (quote: string) => void;
  onUpdate: (body: any) => void;
  busy: boolean;
}) {
  const open = risk.status === "OPEN";
  return (
    <div className={`py-3 flex items-start justify-between gap-3 ${open ? "" : "opacity-60"}`}>
      <div className="min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`text-sm font-medium ${open ? "" : "line-through"}`}>{risk.title}</span>
          <Badge tone={riskTone(risk.severity)}>{risk.severity}</Badge>
          {risk.category && <Badge tone="neutral">{risk.category}</Badge>}
          {risk.source === "AI_REVIEW" ? (
            <Badge tone="ai">
              <span className="flex items-center gap-1"><Icon.sparkle width={11} height={11} /> AI identified</span>
            </Badge>
          ) : (
            <Badge tone="neutral">manual</Badge>
          )}
          {!open && <Badge tone="ok">{risk.resolution === "DISMISSED" ? "dismissed" : "resolved"}</Badge>}
        </div>
        {risk.detail && <div className="text-xs text-ink-soft mt-1">{risk.detail}</div>}
        {risk.location && (
          <button
            type="button"
            title="Highlight this passage in the document"
            className="text-left text-[11px] font-serif italic text-ink-faint mt-1 pl-2 border-l-2 border-border hover:text-ink-soft hover:border-[color:var(--ai)] transition-colors"
            onClick={() => onLocate?.(risk.location)}
          >
            “{risk.location}”
          </button>
        )}
        <div className="text-[11px] text-ink-faint mt-1">
          Opened {date(risk.createdAt)}
          {risk.closedAt ? ` · closed ${date(risk.closedAt)}` : ""}
          {risk.closedAt && risk.resolution ? ` (${risk.resolution.toLowerCase()})` : ""}
        </div>
      </div>
      {canClose && open && (
        <span className="flex shrink-0 items-center gap-3 text-xs">
          <button className="link" disabled={busy} onClick={() => onUpdate({ status: "CLOSED", resolution: "DISMISSED" })}>
            Dismiss
          </button>
          <button className="link" disabled={busy} onClick={() => onUpdate({ status: "CLOSED", resolution: "RESOLVED" })}>
            Resolve
          </button>
        </span>
      )}
      {canClose && !open && (
        <button className="link text-xs shrink-0" disabled={busy} onClick={() => onUpdate({ status: "OPEN" })}>
          Reopen
        </button>
      )}
    </div>
  );
}

function AddRiskForm({
  onSubmit,
  onCancel,
  busy,
  error,
}: {
  onSubmit: (body: any) => void;
  onCancel: () => void;
  busy: boolean;
  error?: string;
}) {
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("LEGAL");
  const [severity, setSeverity] = useState("MEDIUM");
  const [detail, setDetail] = useState("");
  return (
    <div className="rounded-[8px] border border-border p-3 mb-3 space-y-2 bg-surface-2">
      <input className="input" placeholder="Risk title" value={title} onChange={(e) => setTitle(e.target.value)} />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <select className="input" value={category} onChange={(e) => setCategory(e.target.value)}>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <select className="input" value={severity} onChange={(e) => setSeverity(e.target.value)}>
          {SEVERITIES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>
      <textarea className="input" rows={2} placeholder="What is the risk and why does it matter?" value={detail} onChange={(e) => setDetail(e.target.value)} />
      {error && <div className="text-xs" style={{ color: "var(--risk)" }}>{error}</div>}
      <div className="flex gap-2 justify-end">
        <button className="btn" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary" disabled={!title.trim() || busy} onClick={() => onSubmit({ title, category, severity, detail })}>
          {busy ? "Saving…" : "Add risk"}
        </button>
      </div>
    </div>
  );
}