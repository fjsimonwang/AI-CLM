import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, date, fromNow } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty } from "../components/ui";
import { Icon } from "../components/icons";

type Scope = { contractTypes: string[]; countries: string[]; entityIds: string[] };
type Requirement = { kind: string; label?: string; keywords?: string[]; field?: string; op?: string; value?: any };
type Structured = { combinator?: string; requirements?: Requirement[]; summary?: string; notes?: string[] };
type Rule = {
  id: string; name: string; enabled: boolean; instructions: string;
  scope: Scope; structured: Structured | null;
  interpretedAt?: string | null;
  firedCount: number; ownerName?: string | null; mine?: boolean;
};

const emptyScope: Scope = { contractTypes: [], countries: [], entityIds: [] };

function Chip({ on, onClick, children }: { on: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      className={`text-xs rounded-full px-2.5 py-1 border transition-colors ${
        on ? "border-transparent text-white" : "border-border text-ink-soft hover:bg-surface-2 hover:text-ink"
      }`}
      style={on ? { background: "var(--accent)" } : {}}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function Toggle({ on, onChange, disabled }: { on: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled}
      className="relative inline-flex w-9 h-5 rounded-full transition-colors duration-200 shrink-0 disabled:opacity-50"
      style={{ background: on ? "var(--accent)" : "var(--ink-faint)" }}
      aria-label={on ? "On" : "Off"}
      onClick={() => onChange(!on)}
    >
      <span
        className="absolute top-[2px] left-[2px] w-4 h-4 rounded-full bg-white shadow"
        style={{ transition: "transform 0.2s", transform: on ? "translateX(16px)" : "none" }}
      />
    </button>
  );
}

function reqText(r: Requirement): string {
  const kw = (r.keywords || []).map((k) => `"${k}"`).join(", ");
  if (r.kind === "ATTACHMENT") return `Attachment required: a file whose name contains ${kw}`;
  if (r.kind === "TEXT") return `"${r.field}" must mention ${kw}`;
  if (r.kind === "FIELD") {
    const op = r.op === "exists" ? "is present" : r.op === "eq" ? `= ${r.value}` : r.op === "gte" ? `≥ ${r.value}` : `≤ ${r.value}`;
    return `Field "${r.field}" ${op}`;
  }
  return r.label || JSON.stringify(r);
}

/** Global per-approver trigger timing — applies to every rule the approver owns. */
function TriggerSettings() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["auto-reject-settings"], queryFn: () => api("/auto-reject/settings") });
  const s = (q.data || { mode: "IMMEDIATE", delayHours: 0 }) as { mode: string; delayHours: number };
  const [hours, setHours] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const shown = hours === "" ? String(s.delayHours || 1) : hours;

  const save = useMutation({
    mutationFn: (p: { mode: string; delayHours?: number }) =>
      api("/auto-reject/settings", { method: "PUT", json: p }),
    onSuccess: () => { setErr(null); setHours(""); qc.invalidateQueries({ queryKey: ["auto-reject-settings"] }); },
    onError: (e: any) => setErr(e?.message || "Could not save the trigger setting"),
  });

  const pick = (mode: string) => {
    if (mode === s.mode) return;
    save.mutate({ mode, delayHours: mode === "DELAYED" ? Math.max(1, parseInt(shown) || 1) : 0 });
  };
  const commitHours = () => {
    const h = parseInt(shown);
    if (isNaN(h) || h < 1) { setErr("Delay must be at least 1 hour"); return; }
    if (h === s.delayHours) return;
    save.mutate({ mode: "DELAYED", delayHours: h });
  };

  return (
    <Card>
      <SectionTitle>When should auto-rejection kick in?</SectionTitle>
      <p className="text-xs text-ink-faint">This timing applies to all your rules.</p>
      <div className="mt-2 space-y-2 text-sm">
        <label className="flex items-center gap-2 cursor-pointer">
          <input type="radio" name="trigger-mode" checked={s.mode === "IMMEDIATE"} onChange={() => pick("IMMEDIATE")} />
          <span>As soon as the request reaches my queue</span>
        </label>
        <label className="flex items-center gap-2 cursor-pointer">
          <input type="radio" name="trigger-mode" checked={s.mode === "DELAYED"} onChange={() => pick("DELAYED")} />
          <span className="flex items-center gap-1.5 flex-wrap">
            Wait
            <input
              type="number" min={1} max={720} disabled={s.mode !== "DELAYED"}
              value={shown}
              onChange={(e) => setHours(e.target.value)}
              onBlur={commitHours}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); commitHours(); } }}
              className="border border-border rounded px-1.5 py-0.5 w-16 text-sm bg-surface disabled:opacity-50"
            />
            hours before auto-rejection starts to evaluate
          </span>
        </label>
      </div>
      {s.mode === "DELAYED" && (
        <p className="text-xs text-ink-faint mt-2">
          Requests sit in your queue for at least {s.delayHours} hour{s.delayHours === 1 ? "" : "s"}; rules are evaluated
          afterward (checked continuously, at most every 5 minutes).
        </p>
      )}
      {err && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{err}</div>}
      {save.isPending && <span className="inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin text-ink-faint align-middle mt-2" />}
    </Card>
  );
}

export default function AutoReject() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<Rule | "new" | null>(null);
  const [testing, setTesting] = useState<Rule | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const rules = useQuery({ queryKey: ["auto-reject-rules"], queryFn: () => api("/auto-reject/rules") });
  const events = useQuery({ queryKey: ["auto-reject-events"], queryFn: () => api("/auto-reject/events") });
  const typesQ = useQuery({ queryKey: ["refdata-contract-types"], queryFn: () => api("/refdata/contract-types") });
  const refQ = useQuery({ queryKey: ["auto-reject-refdata"], queryFn: () => api("/auto-reject/refdata") });
  const tasksQ = useQuery({ queryKey: ["tasks", "mine"], queryFn: () => api("/workflow/my-tasks") });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["auto-reject-rules"] });
    qc.invalidateQueries({ queryKey: ["auto-reject-events"] });
  };

  const save = useMutation({
    mutationFn: (r: Partial<Rule>) =>
      r.id
        ? api(`/auto-reject/rules/${r.id}`, { method: "PUT", json: r })
        : api("/auto-reject/rules", { method: "POST", json: r }),
    onSuccess: () => { setEditing(null); invalidate(); },
  });
  const toggle = useMutation({
    mutationFn: (r: Rule) => api(`/auto-reject/rules/${r.id}`, { method: "PUT", json: { enabled: !r.enabled } }),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: (id: string) => api(`/auto-reject/rules/${id}`, { method: "DELETE" }),
    onSuccess: () => { setConfirmDelete(null); invalidate(); },
  });
  const interpret = useMutation({
    mutationFn: (id: string) => api(`/auto-reject/rules/${id}/interpret`, { method: "POST" }),
    onSuccess: invalidate,
  });
  const test = useMutation({
    mutationFn: (p: { id: string; contractId: string }) =>
      api(`/auto-reject/rules/${p.id}/test`, { method: "POST", json: { contractId: p.contractId } }),
  });

  const rulesList = (rules.data || []) as Rule[];
  const eventsList = (events.data || []) as any[];

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-medium">Auto-rejection</h1>
          <p className="text-sm text-ink-soft mt-1 max-w-2xl">
            Describe rejection conditions in plain language; the AI structures them into executable
            checks. When a contract reaches <em>your</em> approval step without meeting a rule's
            requirements, it is rejected automatically on your behalf.
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing("new")}>
          <Icon.plus width={14} height={14} /> New rule
        </button>
      </div>

      <TriggerSettings />

      {editing && (
        <RuleEditor
          rule={editing === "new" ? null : editing}
          types={(typesQ.data || []) as any[]}
          entities={((refQ.data as any)?.entities || []) as any[]}
          countries={((refQ.data as any)?.countries || []) as string[]}
          saving={save.isPending}
          error={(save.error as any)?.message}
          onSave={(r) => save.mutate(r as any)}
          onCancel={() => setEditing(null)}
        />
      )}

      {rules.isLoading ? (
        <Spinner />
      ) : rulesList.length === 0 ? (
        <Card>
          <Empty>
            No auto-rejection rules yet. Create one, describe the conditions in your own words, then press
            "Structure with AI" to make it executable.
          </Empty>
        </Card>
      ) : (
        <div className="space-y-3">
          {rulesList.map((r) => (
            <RuleCard
              key={r.id}
              rule={r}
              busy={interpret.isPending && interpret.variables === r.id}
              onToggle={() => toggle.mutate(r)}
              onEdit={() => setEditing(r)}
              onInterpret={() => interpret.mutate(r.id)}
              onTest={() => { setTesting(r); test.reset(); }}
              onDelete={() => setConfirmDelete(r.id)}
              interpretError={interpret.error && interpret.variables === r.id ? (interpret.error as any).message : null}
              deleteArmed={confirmDelete === r.id}
              onConfirmDelete={() => remove.mutate(r.id)}
              onCancelDelete={() => setConfirmDelete(null)}
              deletePending={remove.isPending}
            />
          ))}
        </div>
      )}

      <Card>
        <SectionTitle>Recent auto-rejections</SectionTitle>
        {eventsList.length === 0 ? (
          <Empty>Nothing has been rejected automatically yet.</Empty>
        ) : (
          <div className="space-y-2">
            {eventsList.map((e, i) => (
              <div key={i} className="flex items-start gap-3 text-sm py-1.5 border-b border-border last:border-0">
                <span className="shrink-0" style={{ color: "var(--risk)" }}>
                  <Icon.shieldX width={15} height={15} />
                </span>
                <div className="min-w-0">
                  <div>
                    <span className="font-medium">{e.payload?.contractNumber || "Contract"}</span>
                    {" "}rejected by rule <span className="font-medium">{e.ruleName}</span>
                  </div>
                  <div className="text-xs text-ink-faint mt-0.5">{e.payload?.reason}</div>
                </div>
                <span className="ml-auto shrink-0 text-xs text-ink-faint">{fromNow(e.occurredAt)}</span>
              </div>
            ))}
          </div>
        )}
      </Card>

      {testing && (
        <TestModal
          rule={testing}
          tasks={(tasksQ.data || []) as any[]}
          result={test.data as any}
          running={test.isPending}
          error={(test.error as any)?.message}
          onRun={(contractId) => test.mutate({ id: testing.id, contractId })}
          onClose={() => setTesting(null)}
        />
      )}
    </div>
  );
}

// ------------------------------------------------------------------ rule card

function RuleCard({ rule: r, busy, onToggle, onEdit, onInterpret, onTest, onDelete, interpretError, deleteArmed, onConfirmDelete, onCancelDelete, deletePending }: {
  rule: Rule; busy: boolean; onToggle: () => void; onEdit: () => void; onInterpret: () => void;
  onTest: () => void; onDelete: () => void; interpretError: string | null; deleteArmed: boolean;
  onConfirmDelete: () => void; onCancelDelete: () => void; deletePending: boolean;
}) {
  const s = r.structured;
  const executable = !!s && (s.requirements || []).length > 0;
  const scopeAllEmpty = !r.scope?.contractTypes?.length && !r.scope?.countries?.length && !r.scope?.entityIds?.length;
  return (
    <Card>
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-medium">{r.name}</span>
            {executable ? (
              <Badge tone={r.enabled ? "ok" : "neutral"}>{r.enabled ? "active" : "paused"}</Badge>
            ) : (
              <Badge tone="warn">needs structuring</Badge>
            )}
            {s?.combinator && <Badge tone="neutral">{s.combinator === "ALL_OF" ? "all of" : "any of"}</Badge>}
          </div>
          <div className="flex flex-wrap items-center gap-1.5 mt-1.5 text-xs text-ink-faint">
            {r.ownerName && !r.mine ? <span className="mr-1">by {r.ownerName}</span> : null}
            <span>scope:</span>
            {scopeAllEmpty ? <Badge tone="neutral">every contract at my step</Badge> : null}
            {(r.scope?.contractTypes || []).map((t) => <Badge key={`t${t}`} tone="accent">{t}</Badge>)}
            {(r.scope?.countries || []).map((c) => <Badge key={`c${c}`} tone="accent">{c}</Badge>)}
            {(r.scope?.entityIds || []).map((e) => <EntityChip key={`e${e}`} id={e} />)}
          </div>
        </div>
        <div className="flex items-center gap-3 shrink-0">
          <span className="text-xs text-ink-faint">
            {r.firedCount > 0 ? `fired ${r.firedCount}×` : "not fired yet"}
          </span>
          <Toggle on={r.enabled} onChange={onToggle} />
        </div>
      </div>

      <div className="mt-3 text-sm text-ink-soft whitespace-pre-wrap">{r.instructions}</div>

      {executable ? (
        <div className="mt-3 pt-3 border-t border-border">
          <div className="text-xs font-medium text-ink-soft mb-1">
            Structured by AI{r.interpretedAt ? ` · ${date(r.interpretedAt)}` : ""}
          </div>
          {s?.summary && <div className="text-sm mb-2">{s.summary}</div>}
          <ol className="text-sm space-y-1 list-decimal list-inside text-ink-soft">
            {(s?.requirements || []).map((q, i) => (
              <li key={i}>
                {q.label || reqText(q)}
                {q.kind === "FIELD" || q.kind === "TEXT" ? <span className="text-xs text-ink-faint"> — {reqText(q)}</span> : null}
              </li>
            ))}
          </ol>
          {(s?.notes || []).length > 0 && (
            <div className="text-xs text-ink-faint mt-2">
              {(s?.notes || []).map((n, i) => <div key={i}>Note: {n}</div>)}
            </div>
          )}
        </div>
      ) : (
        <div className="mt-3 pt-3 border-t border-border text-sm text-ink-faint">
          Not executable yet — the AI hasn't structured these instructions, so this rule can never reject.
        </div>
      )}

      {interpretError && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{interpretError}</div>}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button className="btn btn-sm" onClick={onTest}>
          <Icon.eye width={13} height={13} /> Dry-run
        </button>
        <button className="btn btn-sm" onClick={onInterpret} disabled={busy}>
          {busy
            ? <span className="inline-block w-3 h-3 rounded-full border-2 shrink-0 animate-spin" style={{ borderColor: "var(--ink-faint)", borderTopColor: "transparent" }} />
            : <Icon.sparkle width={13} height={13} />}
          {executable ? "Re-interpret" : "Structure with AI"}
        </button>
        <button className="btn btn-sm" onClick={onEdit}>
          <Icon.edit width={13} height={13} /> Edit
        </button>
        {deleteArmed ? (
          <span className="flex items-center gap-2 text-xs">
            <span style={{ color: "var(--risk)" }}>Delete this rule permanently?</span>
            <button className="btn btn-sm" style={{ borderColor: "var(--risk)", color: "var(--risk)" }} disabled={deletePending} onClick={onConfirmDelete}>
              Yes, delete
            </button>
            <button className="btn btn-sm" onClick={onCancelDelete}>Cancel</button>
          </span>
        ) : (
          <button className="btn btn-sm" onClick={onDelete}>
            <Icon.trash width={13} height={13} /> Delete
          </button>
        )}
      </div>
    </Card>
  );
}

function EntityChip({ id }: { id: string }) {
  const q = useQuery({
    queryKey: ["auto-reject-refdata"],
    queryFn: () => api("/auto-reject/refdata"),
  });
  const e = (((q.data as any)?.entities || []) as any[]).find((x) => x.id === id);
  return <Badge tone="accent">{e?.shortName || id.slice(0, 8)}</Badge>;
}

// ------------------------------------------------------------------ editor

function RuleEditor({ rule, types, entities, countries, onSave, onCancel, saving, error }: {
  rule: Rule | null; types: any[]; entities: any[]; countries: string[];
  onSave: (r: Partial<Rule>) => void; onCancel: () => void; saving: boolean; error: string | null;
}) {
  const [name, setName] = useState(rule?.name || "");
  const [scope, setScope] = useState<Scope>(rule?.scope || emptyScope);
  const [instructions, setInstructions] = useState(rule?.instructions || "");
  const flip = (k: keyof Scope, v: string) =>
    setScope((s) => ({ ...s, [k]: s[k].includes(v) ? s[k].filter((x) => x !== v) : [...s[k], v] }));

  const countryOptions = useMemo(() => {
    const set = new Set<string>(countries);
    entities.forEach((e) => e.countryCode && set.add(e.countryCode));
    return [...set].sort();
  }, [countries, entities]);

  return (
    <Card>
      <SectionTitle>{rule ? "Edit rule" : "New auto-rejection rule"}</SectionTitle>
      <div className="space-y-4">
        <div>
          <label className="text-xs font-medium text-ink-soft">Rule name</label>
          <input className="input mt-1" style={{ maxWidth: 420 }} value={name}
                 onChange={(e) => setName(e.target.value)} placeholder="e.g. Procurement docs complete" />
        </div>

        <div className="space-y-3">
          <div>
            <label className="text-xs font-medium text-ink-soft">Applies to contract types</label>
            <div className="mt-1 text-xs text-ink-faint mb-1.5">Leave empty to apply to every type.</div>
            <div className="flex flex-wrap gap-1.5">
              {types.map((t) => (
                <Chip key={t.code} on={scope.contractTypes.includes(t.code)} onClick={() => flip("contractTypes", t.code)}>
                  {t.displayName || t.code}
                </Chip>
              ))}
            </div>
          </div>
          <div>
            <label className="text-xs font-medium text-ink-soft">Applies in countries</label>
            <div className="mt-1 text-xs text-ink-faint mb-1.5">Matched on the contracting entity's country.</div>
            <div className="flex flex-wrap gap-1.5">
              {countryOptions.map((c) => (
                <Chip key={c} on={scope.countries.includes(c)} onClick={() => flip("countries", c)}>{c}</Chip>
              ))}
            </div>
          </div>
          <div>
            <label className="text-xs font-medium text-ink-soft">Applies to our contracting entities</label>
            <div className="flex flex-wrap gap-1.5 mt-1.5">
              {entities.map((e) => (
                <Chip key={e.id} on={scope.entityIds.includes(e.id)} onClick={() => flip("entityIds", e.id)}>
                  {e.shortName} <span className="opacity-60">({e.countryCode})</span>
                </Chip>
              ))}
            </div>
          </div>
        </div>

        <div>
          <label className="text-xs font-medium text-ink-soft">Describe the conditions in your own words</label>
          <div className="mt-1 text-xs text-ink-faint mb-1.5">
            State what must be present or true for a request to pass your review — e.g. "a procurement
            contract must attach the NDA or the vendor TPDD form named 'TPDD screening', otherwise reject".
          </div>
          <textarea className="input" rows={4} value={instructions}
                    onChange={(e) => setInstructions(e.target.value)}
                    placeholder="Every vendor purchase must come with the NDA or the 'TPDD screening' form attached…" />
        </div>

        {error && <div className="text-xs" style={{ color: "var(--risk)" }}>{error}</div>}
      </div>

      <div className="mt-4 flex gap-2">
        <button className="btn btn-primary" disabled={saving || !name.trim() || !instructions.trim()}
                onClick={() => onSave({ id: rule?.id, name, scope, instructions })}>
          {saving ? "Saving…" : rule ? "Save changes" : "Create rule"}
        </button>
        <button className="btn" onClick={onCancel}>Cancel</button>
      </div>
      {!rule && (
        <div className="text-xs text-ink-faint mt-2">
          After creating, press "Structure with AI" on the rule card to turn these words into executable checks.
        </div>
      )}
    </Card>
  );
}

// ------------------------------------------------------------------ dry-run modal

function TestModal({ rule, tasks, result, running, error, onRun, onClose }: {
  rule: Rule; tasks: any[]; result: any; running: boolean; error: string | null;
  onRun: (contractId: string) => void; onClose: () => void;
}) {
  const [choice, setChoice] = useState<string>(tasks[0]?.contractId || "");
  return (
    <div className="fixed inset-0 z-50 grid place-items-center p-4" style={{ background: "rgba(0,0,0,0.45)" }} onClick={onClose}>
      <Card className="w-full max-w-lg" >
        <div onClick={(e) => e.stopPropagation()}>
          <SectionTitle right={<button className="btn btn-sm" onClick={onClose}><Icon.x width={13} height={13} /></button>}>
            Dry-run: {rule.name}
          </SectionTitle>
          {tasks.length === 0 ? (
            <Empty>You have no open approval tasks to test against.</Empty>
          ) : (
            <>
              <label className="text-xs font-medium text-ink-soft">Pick one of your open tasks</label>
              <select className="input mt-1" value={choice} onChange={(e) => setChoice(e.target.value)}>
                {tasks.filter((t) => t.contractId).map((t) => (
                  <option key={t.id} value={t.contractId}>
                    {t.contractNumber} — {(t.contractTitle || "").slice(0, 60)}
                  </option>
                ))}
              </select>
              <button className="btn btn-primary mt-3" disabled={!choice || running} onClick={() => onRun(choice)}>
                {running ? "Testing…" : "Run dry-run"}
              </button>
              {error && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{error}</div>}
              {result && (
                <div className="mt-4 pt-3 border-t border-border">
                  <div className="text-sm flex items-center gap-2">
                    {result.wouldReject
                      ? <Badge tone="risk">would reject</Badge>
                      : <Badge tone="ok">would pass</Badge>}
                    <span className="text-xs text-ink-faint">
                      scope {result.scopeMatched ? "matched" : "not matched"} · rule {result.executable ? "executable" : "not structured"}
                    </span>
                  </div>
                  {(result.unmet || []).length > 0 && (
                    <div className="mt-2 text-sm">
                      <span className="text-xs font-medium" style={{ color: "var(--risk)" }}>Unmet:</span>
                      <ul className="list-disc list-inside text-ink-soft">
                        {result.unmet.map((u: string, i: number) => <li key={i}>{u}</li>)}
                      </ul>
                    </div>
                  )}
                  {(result.met || []).length > 0 && (
                    <div className="mt-2 text-sm">
                      <span className="text-xs font-medium" style={{ color: "var(--ok, #16a34a)" }}>Met:</span>
                      <ul className="list-disc list-inside text-ink-soft">
                        {result.met.map((u: string, i: number) => <li key={i}>{u}</li>)}
                      </ul>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </Card>
    </div>
  );
}