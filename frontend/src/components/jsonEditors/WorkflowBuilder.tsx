import React from "react";
import { Icon } from "../icons";
import { TagListEditor } from "./TagListEditor";
import {
  parseJsonish, WORKFLOW_ROLES, WORKFLOW_TASK_TYPES, WORKFLOW_GUARDS, roleLabel,
} from "./shared";

type Transition = { on: string; to: string };
type WFState = {
  key: string;
  kind: "task" | "end";
  outcome: "executed" | "rejected";
  taskType: string;
  role: string;
  slaHours: string;
  guards: string[];
  otherGuards: string[];
  transitions: Transition[];
  extra: Record<string, any>;
};
type WFDef = { initial: string; states: WFState[]; extraTop: Record<string, any> };

const KNOWN_GUARDS = new Set(WORKFLOW_GUARDS.map((g) => g.value));

function parseDefinition(raw: any): WFDef {
  const d = parseJsonish<any>(raw, {});
  const { key: _k, version: _v, initial, states, ...extraTop } = d || {};
  const list: WFState[] = Array.isArray(states)
    ? states.map((s: any) => {
        const { key, type, taskType, assignment, slaHours, guards, transitions, ...extra } = s || {};
        const guardArr: string[] = Array.isArray(guards) ? guards.map(String) : [];
        const role = assignment?.role ?? "";
        const k = key || "";
        return {
          key: k,
          kind: type === "end" ? "end" : "task",
          outcome: k.includes("reject") ? "rejected" : "executed",
          taskType: taskType || "APPROVAL",
          role,
          slaHours: slaHours != null && slaHours !== "" ? String(slaHours) : "",
          guards: guardArr.filter((g) => KNOWN_GUARDS.has(g)),
          otherGuards: guardArr.filter((g) => !KNOWN_GUARDS.has(g)),
          transitions: Array.isArray(transitions)
            ? transitions.map((t: any) => ({ on: t?.on || "", to: t?.to || "" }))
            : [],
          extra,
        };
      })
    : [];
  return { initial: initial || list[0]?.key || "", states: list, extraTop };
}

function buildDefinition(wf: WFDef, entityKey: string, versionNo: number): any {
  return {
    ...wf.extraTop,
    key: entityKey || wf.extraTop.key || "workflow",
    version: versionNo || 1,
    initial: wf.initial,
    states: wf.states.map((s) => {
      const out: any = { ...s.extra, key: s.key, type: s.kind === "end" ? "end" : "task" };
      if (s.kind === "task") {
        out.taskType = s.taskType;
        out.assignment = { role: s.role };
        if (s.slaHours !== "") out.slaHours = Number(s.slaHours);
        const guards = [...s.guards, ...s.otherGuards];
        if (guards.length) out.guards = guards;
        out.transitions = s.transitions.filter((t) => t.on && t.to);
      }
      return out;
    }),
  };
}

function computeRanks(wf: WFDef): Record<string, number> {
  const ranks: Record<string, number> = {};
  if (!wf.initial || !wf.states.some((s) => s.key === wf.initial)) return ranks;
  const queue = [wf.initial];
  ranks[wf.initial] = 0;
  while (queue.length) {
    const cur = queue.shift()!;
    const state = wf.states.find((s) => s.key === cur);
    if (!state) continue;
    for (const t of state.transitions) {
      if (t.to && ranks[t.to] === undefined) {
        ranks[t.to] = ranks[cur] + 1;
        queue.push(t.to);
      }
    }
  }
  return ranks;
}

const BOX_W = 180;
const BOX_H = 60;
const COL_GAP = 90;
const ROW_GAP = 16;

function FlowPreview({ wf }: { wf: WFDef }) {
  if (wf.states.length === 0) return null;
  const ranks = computeRanks(wf);
  const maxRank = Math.max(0, ...Object.values(ranks));
  const unreachedRank = maxRank + 1;
  const columns: WFState[][] = [];
  for (const s of wf.states) {
    const r = ranks[s.key] ?? unreachedRank;
    (columns[r] ||= []).push(s);
  }
  const pos: Record<string, { x: number; y: number }> = {};
  columns.forEach((col, r) => {
    (col || []).forEach((s, i) => {
      pos[s.key] = { x: r * (BOX_W + COL_GAP), y: i * (BOX_H + ROW_GAP) };
    });
  });
  const width = columns.length * (BOX_W + COL_GAP) - COL_GAP;
  const height = Math.max(...columns.map((c) => (c ? c.length : 0))) * (BOX_H + ROW_GAP) - ROW_GAP;

  const edges: { from: string; to: string; label: string }[] = [];
  for (const s of wf.states) {
    for (const t of s.transitions) {
      if (t.to && pos[t.to]) edges.push({ from: s.key, to: t.to, label: t.on });
    }
  }

  return (
    <div className="overflow-auto rounded-lg border border-border bg-surface-2" style={{ padding: 20 }}>
      <div style={{ position: "relative", width: Math.max(width, BOX_W), height: Math.max(height, BOX_H) }}>
        <svg
          width={Math.max(width, BOX_W)}
          height={Math.max(height, BOX_H)}
          style={{ position: "absolute", inset: 0, overflow: "visible", pointerEvents: "none" }}
        >
          <defs>
            <marker id="wf-arrow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 z" fill="var(--ink-faint)" />
            </marker>
          </defs>
          {edges.map((e, i) => {
            const a = pos[e.from], b = pos[e.to];
            const forward = b.x >= a.x;
            const x1 = a.x + BOX_W, y1 = a.y + BOX_H / 2;
            const x2 = forward ? b.x : b.x + BOX_W, y2 = b.y + BOX_H / 2;
            const midX = (x1 + x2) / 2, midY = (y1 + y2) / 2;
            const dip = forward ? 0 : 34; // bow backward edges outward so they're visible
            const cy = midY - dip;
            const path = forward
              ? `M${x1},${y1} C${midX},${y1} ${midX},${y2} ${x2},${y2}`
              : `M${x1},${y1} C${x1 + 30},${y1 - dip} ${x2 - 30},${y2 - dip} ${x2},${y2}`;
            return (
              <g key={i}>
                <path d={path} fill="none" stroke="var(--ink-faint)" strokeWidth={1.4} markerEnd="url(#wf-arrow)" opacity={0.75} />
                <text
                  x={forward ? midX : (x1 + x2) / 2}
                  y={forward ? midY - 5 : cy - 5}
                  textAnchor="middle"
                  fontSize={10.5}
                  fill="var(--ink-soft)"
                  stroke="var(--surface-2)"
                  strokeWidth={3}
                  paintOrder="stroke"
                >
                  {e.label}
                </text>
              </g>
            );
          })}
        </svg>
        {wf.states.map((s) => {
          const p = pos[s.key];
          if (!p) return null;
          const tone = s.kind === "end" ? (s.outcome === "rejected" ? "var(--risk)" : "var(--ok)") : "var(--accent)";
          const isUnreached = ranks[s.key] === undefined;
          return (
            <div
              key={s.key}
              style={{
                position: "absolute", left: p.x, top: p.y, width: BOX_W, height: BOX_H,
                borderRadius: 10, border: `1.5px solid ${tone}`,
                background: `color-mix(in srgb, ${tone} 9%, var(--surface))`,
                padding: "6px 10px", fontSize: 12, display: "flex", flexDirection: "column",
                justifyContent: "center", opacity: isUnreached ? 0.55 : 1,
              }}
              title={isUnreached ? "Not reachable from the start state" : undefined}
            >
              <div style={{ fontWeight: 600, color: tone, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {s.key || "(no key)"} {isUnreached && "⚠"}
              </div>
              <div style={{ color: "var(--ink-faint)", fontSize: 10.5 }}>
                {s.kind === "end" ? (s.outcome === "rejected" ? "Rejected — back to draft" : "Completes the contract") : `${s.taskType} · ${roleLabel(s.role)}`}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function WorkflowBuilder({
  definition,
  entityKey,
  versionNo,
  onChange,
}: {
  definition: any;
  entityKey: string;
  versionNo: number;
  onChange: (next: any) => void;
}) {
  const wf = parseDefinition(definition);
  const emit = (next: WFDef) => onChange(buildDefinition(next, entityKey, versionNo));

  const stateKeys = wf.states.map((s) => s.key);

  const updateState = (idx: number, patch: Partial<WFState>) =>
    emit({ ...wf, states: wf.states.map((s, i) => (i === idx ? { ...s, ...patch } : s)) });

  const renameStateKey = (idx: number, newKey: string) => {
    const oldKey = wf.states[idx].key;
    const states = wf.states.map((s, i) => {
      const renamed = s.transitions.map((t) => (t.to === oldKey ? { ...t, to: newKey } : t));
      return i === idx ? { ...s, key: newKey, transitions: renamed } : { ...s, transitions: renamed };
    });
    emit({ ...wf, states, initial: wf.initial === oldKey ? newKey : wf.initial });
  };

  const addState = () => {
    let key = "new_state", n = 1;
    while (stateKeys.includes(key)) key = `new_state_${n++}`;
    const s: WFState = {
      key, kind: "task", outcome: "executed", taskType: "APPROVAL", role: "owner",
      slaHours: "48", guards: [], otherGuards: [], transitions: [], extra: {},
    };
    emit({ ...wf, states: [...wf.states, s], initial: wf.initial || key });
  };

  const removeState = (idx: number) => {
    const removedKey = wf.states[idx].key;
    const states = wf.states.filter((_, i) => i !== idx)
      .map((s) => ({ ...s, transitions: s.transitions.filter((t) => t.to !== removedKey) }));
    emit({ ...wf, states, initial: wf.initial === removedKey ? (states[0]?.key || "") : wf.initial });
  };

  const addTransition = (idx: number) => {
    const other = stateKeys.find((k) => k !== wf.states[idx].key) || "";
    updateState(idx, { transitions: [...wf.states[idx].transitions, { on: "approve", to: other }] });
  };
  const updateTransition = (idx: number, tIdx: number, patch: Partial<Transition>) => {
    const transitions = wf.states[idx].transitions.map((t, i) => (i === tIdx ? { ...t, ...patch } : t));
    updateState(idx, { transitions });
  };
  const removeTransition = (idx: number, tIdx: number) => {
    updateState(idx, { transitions: wf.states[idx].transitions.filter((_, i) => i !== tIdx) });
  };

  const toggleGuard = (idx: number, guard: string) => {
    const s = wf.states[idx];
    const guards = s.guards.includes(guard) ? s.guards.filter((g) => g !== guard) : [...s.guards, guard];
    updateState(idx, { guards });
  };

  return (
    <div className="mt-1 space-y-3">
      {wf.states.length > 0 && (
        <div>
          <label className="text-xs text-ink-faint">Start state</label>
          <select className="input mt-1" value={wf.initial} onChange={(e) => emit({ ...wf, initial: e.target.value })}>
            {stateKeys.map((k) => <option key={k} value={k}>{k}</option>)}
          </select>
        </div>
      )}

      <FlowPreview wf={wf} />

      <div className="space-y-2">
        {wf.states.map((s, idx) => (
          <div key={idx} className="rounded-lg border border-border p-3 space-y-2.5">
            <div className="flex items-center gap-2">
              <input
                className="input font-medium"
                style={{ flex: 1 }}
                value={s.key}
                onChange={(e) => renameStateKey(idx, e.target.value)}
                placeholder="state_key"
              />
              {wf.initial === s.key && <span className="chip text-[11px]" style={{ color: "var(--accent)", borderColor: "var(--accent)" }}>Start</span>}
              <button type="button" className="btn" style={{ padding: "0.3rem 0.5rem", borderColor: "var(--risk)", color: "var(--risk)" }} onClick={() => removeState(idx)}>
                <Icon.trash width={13} height={13} />
              </button>
            </div>

            <div className="flex gap-1.5">
              {(["task", "end"] as const).map((k) => (
                <button
                  key={k}
                  type="button"
                  className={`btn flex-1 justify-center ${s.kind === k ? "btn-primary" : ""}`}
                  style={{ padding: "0.35rem" }}
                  onClick={() => updateState(idx, { kind: k })}
                >
                  {k === "task" ? "Task (has an assignee)" : "Terminal (ends the workflow)"}
                </button>
              ))}
            </div>

            {s.kind === "end" ? (
              <div>
                <div className="flex gap-1.5">
                  {([
                    ["executed", "Completed — contract becomes executed"],
                    ["rejected", "Rejected — back to requestor as a draft"],
                  ] as const).map(([k, label]) => (
                    <button
                      key={k}
                      type="button"
                      className={`btn flex-1 justify-center text-xs ${s.outcome === k ? "btn-primary" : ""}`}
                      style={{ padding: "0.35rem" }}
                      onClick={() => updateState(idx, { outcome: k })}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                {s.outcome === "rejected" && !s.key.includes("reject") && (
                  <div className="text-[11px] mt-1" style={{ color: "var(--warn)" }}>
                    The state key should contain "reject" (e.g. <code>closed_rejected</code>) — the workflow
                    engine detects a rejection outcome from the key.
                  </div>
                )}
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-xs text-ink-faint">Task type</label>
                    <select className="input mt-1" value={s.taskType} onChange={(e) => updateState(idx, { taskType: e.target.value })}>
                      {WORKFLOW_TASK_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="text-xs text-ink-faint">SLA (hours)</label>
                    <input
                      className="input mt-1" type="number" value={s.slaHours}
                      onChange={(e) => updateState(idx, { slaHours: e.target.value })}
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs text-ink-faint">Assigned to</label>
                  <select
                    className="input mt-1"
                    value={WORKFLOW_ROLES.some((r) => r.value === s.role) ? s.role : "__custom"}
                    onChange={(e) => updateState(idx, { role: e.target.value === "__custom" ? "" : e.target.value })}
                  >
                    {WORKFLOW_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                    <option value="__custom">Custom role code…</option>
                  </select>
                  {!WORKFLOW_ROLES.some((r) => r.value === s.role) && (
                    <input
                      className="input mt-1.5" value={s.role} placeholder="custom_role_code"
                      onChange={(e) => updateState(idx, { role: e.target.value })}
                    />
                  )}
                </div>

                <div>
                  <label className="text-xs text-ink-faint">Guards (block this step until true)</label>
                  <div className="mt-1 space-y-1">
                    {WORKFLOW_GUARDS.map((g) => (
                      <label key={g.value} className="flex items-center gap-2 text-xs">
                        <input type="checkbox" checked={s.guards.includes(g.value)} onChange={() => toggleGuard(idx, g.value)} />
                        {g.label}
                      </label>
                    ))}
                  </div>
                  {s.otherGuards.length > 0 && (
                    <div className="mt-1">
                      <TagListEditor value={s.otherGuards} onChange={(v) => updateState(idx, { otherGuards: v })} placeholder="other guard…" />
                    </div>
                  )}
                </div>

                <div>
                  <label className="text-xs text-ink-faint">Transitions</label>
                  <div className="mt-1 space-y-1.5">
                    {s.transitions.map((t, tIdx) => (
                      <div key={tIdx} className="flex items-center gap-1.5">
                        <input
                          className="input text-xs" style={{ flex: 1 }} value={t.on} placeholder="event (e.g. approve)"
                          onChange={(e) => updateTransition(idx, tIdx, { on: e.target.value })}
                        />
                        <Icon.arrowRight width={13} height={13} className="text-ink-faint shrink-0" />
                        <select
                          className="input text-xs" style={{ flex: 1 }} value={t.to}
                          onChange={(e) => updateTransition(idx, tIdx, { to: e.target.value })}
                        >
                          <option value="">— target state —</option>
                          {stateKeys.map((k) => <option key={k} value={k}>{k}</option>)}
                        </select>
                        <button type="button" className="btn" style={{ padding: "0.3rem 0.4rem" }} onClick={() => removeTransition(idx, tIdx)}>
                          <Icon.trash width={12} height={12} />
                        </button>
                      </div>
                    ))}
                    <button type="button" className="btn" style={{ padding: "0.3rem 0.6rem" }} onClick={() => addTransition(idx)}>
                      <Icon.plus width={12} height={12} /> Add transition
                    </button>
                  </div>
                </div>
              </>
            )}
          </div>
        ))}
      </div>

      <button type="button" className="btn" onClick={addState}>
        <Icon.plus width={14} height={14} /> Add state
      </button>
    </div>
  );
}
