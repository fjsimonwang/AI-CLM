import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty } from "../components/ui";
import { Icon } from "../components/icons";

type Dim = { code: string; name: string; description: string; values: string[] };

function scopeText(c: Record<string, string[]>) {
  const keys = Object.keys(c);
  if (!keys.length) return "everything";
  return keys.map((k) => `${k}: ${(c[k] || []).join(" / ") || "any"}`).join("  ·  ");
}

export default function Access() {
  const qc = useQueryClient();
  const dims = useQuery({ queryKey: ["access-dims"], queryFn: () => api<Dim[]>("/access/dimensions") });
  const myGrants = useQuery({ queryKey: ["my-grants"], queryFn: () => api("/access/my-grants") });
  const myReqs = useQuery({ queryKey: ["my-reqs"], queryFn: () => api("/access/my-requests") });
  const pending = useQuery({ queryKey: ["access-pending"], queryFn: () => api("/access/requests/pending") });

  const [scope, setScope] = useState<Record<string, string[]>>({});
  const [justification, setJustification] = useState("");
  const [expiresDays, setExpiresDays] = useState<string>("");
  const [applyError, setApplyError] = useState("");

  const constraints = useMemo(
    () => Object.fromEntries(Object.entries(scope).filter(([, v]) => v.length > 0)),
    [scope]
  );
  const hasScope = Object.keys(constraints).length > 0;

  const previewQ = useQuery({
    queryKey: ["access-preview", JSON.stringify(constraints)],
    queryFn: () => api("/access/preview", { method: "POST", json: { constraints } }),
    enabled: hasScope,
  });

  const toggle = (dim: string, val: string) =>
    setScope((s) => {
      const cur = new Set(s[dim] || []);
      cur.has(val) ? cur.delete(val) : cur.add(val);
      return { ...s, [dim]: [...cur] };
    });

  const apply = useMutation({
    mutationFn: () =>
      api("/access/requests", {
        method: "POST",
        json: { constraints, justification, expiresDays: expiresDays ? Number(expiresDays) : null },
      }),
    onSuccess: () => {
      setScope({});
      setJustification("");
      setExpiresDays("");
      setApplyError("");
      qc.invalidateQueries({ queryKey: ["my-reqs"] });
    },
    onError: (e: any) => setApplyError(e.message),
  });

  const decide = useMutation({
    mutationFn: ({ id, approve, note }: { id: string; approve: boolean; note: string }) =>
      api(`/access/requests/${id}/decide`, { method: "POST", json: { approve, note } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["access-pending"] });
      qc.invalidateQueries({ queryKey: ["me-summary"] });
    },
  });
  const [notes, setNotes] = useState<Record<string, string>>({});

  return (
    <div className="space-y-5 max-w-4xl">
      <div>
        <h1 className="text-xl font-medium flex items-center gap-2">
          <Icon.shieldCheck /> Access
        </h1>
        <p className="text-sm text-ink-faint">
          You can always see contracts you're involved in. Apply for a broader scope to see more —
          requests are routed to whoever owns that scope.
        </p>
      </div>

      {(pending.data || []).length > 0 && (
        <Card className="border-[color:var(--warn)]">
          <SectionTitle>Waiting on your decision ({pending.data.length})</SectionTitle>
          <div className="space-y-3">
            {pending.data.map((r: any) => (
              <div key={r.id} className="border-b border-border pb-3 last:border-0">
                <div className="text-sm">
                  <b>{r.user}</b> requests <Badge tone="accent">{r.scope}</Badge>
                </div>
                {r.justification && <p className="text-xs text-ink-soft mt-1">"{r.justification}"</p>}
                <div className="flex items-center gap-2 mt-2">
                  <input
                    className="input"
                    style={{ maxWidth: 260 }}
                    placeholder="Decision note (optional)"
                    value={notes[r.id] || ""}
                    onChange={(e) => setNotes((n) => ({ ...n, [r.id]: e.target.value }))}
                  />
                  <button
                    className="btn btn-primary"
                    disabled={decide.isPending}
                    onClick={() => decide.mutate({ id: r.id, approve: true, note: notes[r.id] || "" })}
                  >
                    Approve
                  </button>
                  <button
                    className="btn"
                    style={{ borderColor: "var(--risk)", color: "var(--risk)" }}
                    disabled={decide.isPending}
                    onClick={() => decide.mutate({ id: r.id, approve: false, note: notes[r.id] || "" })}
                  >
                    Reject
                  </button>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      <div className="grid md:grid-cols-2 gap-4">
        <Card>
          <SectionTitle>Your access</SectionTitle>
          {myGrants.isLoading ? (
            <Spinner />
          ) : (myGrants.data || []).length === 0 ? (
            <Empty>Only contracts you're directly involved in.</Empty>
          ) : (
            <ul className="space-y-2">
              {myGrants.data.map((g: any) => (
                <li key={g.id} className="text-sm">
                  <div className="flex items-center gap-2">
                    <Icon.shield width={14} height={14} className="text-ink-faint" />
                    <span>{scopeText(g.constraints)}</span>
                  </div>
                  <div className="text-[11px] text-ink-faint ml-6">
                    {g.source.toLowerCase()} · {g.expiresAt ? `expires ${new Date(g.expiresAt).toLocaleDateString()}` : "no expiry"}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <SectionTitle>Your requests</SectionTitle>
          {myReqs.isLoading ? (
            <Spinner />
          ) : (myReqs.data || []).length === 0 ? (
            <Empty>No access requests yet.</Empty>
          ) : (
            <ul className="space-y-2">
              {myReqs.data.map((r: any) => (
                <li key={r.id} className="text-sm flex items-center justify-between">
                  <span>{r.scope}</span>
                  <Badge tone={r.status === "APPROVED" ? "ok" : r.status === "REJECTED" ? "risk" : "warn"}>
                    {r.status.toLowerCase()}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card>
        <SectionTitle>Apply for access</SectionTitle>
        {dims.isLoading ? (
          <Spinner />
        ) : (
          <div className="space-y-4">
            {(dims.data || []).map((d) => (
              <div key={d.code}>
                <div className="text-xs text-ink-faint mb-1" title={d.description}>
                  {d.name}
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {d.values.map((v) => {
                    const on = (scope[d.code] || []).includes(v);
                    return (
                      <button
                        key={v}
                        onClick={() => toggle(d.code, v)}
                        className={`chip transition-all ${on ? "!bg-[color:var(--accent)] !text-white !border-[color:var(--accent)]" : "hover:border-[color:var(--accent)]"}`}
                      >
                        {v}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}

            <div className="rounded-[8px] p-3 text-sm" style={{ background: "var(--surface-2)" }}>
              <div className="text-xs text-ink-faint">You are requesting</div>
              <div className="font-medium">{hasScope ? scopeText(constraints) : "— pick at least one value —"}</div>
              {hasScope && previewQ.data && (
                <div className="text-xs mt-1">
                  {previewQ.data.approvable ? (
                    <span className="text-ink-soft">
                      Approvable by: {previewQ.data.approvers.map((a: any) => a.name).join(", ")}
                    </span>
                  ) : (
                    <span style={{ color: "var(--risk)" }}>
                      No approver owns a scope covering this — narrow it or ask an admin.
                    </span>
                  )}
                </div>
              )}
            </div>

            <div className="grid sm:grid-cols-[1fr_140px] gap-2">
              <input
                className="input"
                placeholder="Why do you need this access?"
                value={justification}
                onChange={(e) => setJustification(e.target.value)}
              />
              <input
                className="input"
                type="number"
                placeholder="Expires (days)"
                value={expiresDays}
                onChange={(e) => setExpiresDays(e.target.value)}
              />
            </div>
            {applyError && <div className="text-xs" style={{ color: "var(--risk)" }}>{applyError}</div>}
            <button
              className="btn btn-primary"
              disabled={!hasScope || apply.isPending || (previewQ.data && !previewQ.data.approvable)}
              onClick={() => apply.mutate()}
            >
              {apply.isPending ? "Submitting…" : "Submit request"}
            </button>
          </div>
        )}
      </Card>
    </div>
  );
}
