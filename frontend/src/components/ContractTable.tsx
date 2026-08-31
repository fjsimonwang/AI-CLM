import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { money, date, dateTime } from "../api";
import { Badge, Card, statusTone, riskTone, Empty } from "./ui";
import { Icon } from "./icons";

/**
 * The contract register table with client-side table controls:
 * add/remove columns, reorder them, per-column filter menus
 * (is / contains / larger / smaller / before / after / is empty) and
 * per-column sort toggles. Everything persists in localStorage.
 */

type ColKind = "contract" | "text" | "select" | "number" | "date";

type ColDef = {
  key: string;
  label: string;
  kind: ColKind;
  align?: "left" | "right";
  pinned?: boolean; // cannot be hidden (table needs a link column)
  minWidth: number;
  raw: (c: any) => any;
  render: (c: any) => React.ReactNode;
  options?: string[];
};

type Filter = { op: string; value: string };
type Sort = { key: string; dir: "asc" | "desc" } | null;
type View = { order: string[]; hidden: string[] };

const OPS: Record<ColKind, string[]> = {
  contract: ["is", "contains", "starts with", "is empty"],
  text: ["is", "contains", "starts with", "is empty"],
  select: ["is", "is not", "is empty"],
  number: ["is", "larger than", "smaller than", "is empty"],
  date: ["is", "before", "after", "is empty"],
};

const COLUMNS: ColDef[] = [
  {
    key: "contract", label: "Contract", kind: "contract", pinned: true, minWidth: 210,
    raw: (c) => c.contractNumber,
    render: (c) => (
      <>
        <Link to={`/contracts/${c.id}`} className="link font-medium">{c.contractNumber}</Link>
        <div className="text-xs text-ink-faint">{c.title}</div>
      </>
    ),
  },
  { key: "type", label: "Type", kind: "text", minWidth: 110, raw: (c) => c.type, render: (c) => c.type },
  {
    key: "counterparties", label: "Counterparty", kind: "text", minWidth: 160,
    raw: (c) => (c.counterparties || []).join(", "),
    render: (c) => (c.counterparties || []).join(", ") || "—",
  },
  { key: "entity", label: "Entity", kind: "text", minWidth: 110, raw: (c) => c.entity, render: (c) => c.entity },
  {
    key: "status", label: "Status", kind: "select", minWidth: 130,
    options: ["DRAFT", "IN_REVIEW", "EXECUTED", "CLOSED_REJECTED"],
    raw: (c) => c.status,
    render: (c) => <Badge tone={statusTone(c.status)}>{c.status}</Badge>,
  },
  {
    key: "valueAmount", label: "Value", kind: "number", align: "right", minWidth: 130,
    raw: (c) => (c.valueAmount == null ? null : Number(c.valueAmount)),
    render: (c) => <span className="tabular">{money(c.valueAmount, c.currency)}</span>,
  },
  { key: "currency", label: "Currency", kind: "select", minWidth: 100, options: ["USD", "EUR", "GBP", "CHF"], raw: (c) => c.currency, render: (c) => c.currency },
  {
    key: "expiryDate", label: "Expiry", kind: "date", minWidth: 150,
    raw: (c) => c.expiryDate,
    render: (c) => (
      <span className="tabular">
        {date(c.expiryDate)}
        {c.source === "MIGRATED" && <span title="migrated, some terms unverified"> ·<Badge tone="warn">migrated</Badge></span>}
      </span>
    ),
  },
  { key: "effectiveDate", label: "Effective", kind: "date", minWidth: 120, raw: (c) => c.effectiveDate, render: (c) => <span className="tabular">{date(c.effectiveDate)}</span> },
  { key: "governingLaw", label: "Governing law", kind: "text", minWidth: 120, raw: (c) => c.governingLaw, render: (c) => c.governingLaw || "—" },
  {
    key: "autoRenew", label: "Auto-renew", kind: "select", minWidth: 110, options: ["Yes", "No"],
    raw: (c) => (c.autoRenew == null ? null : c.autoRenew ? "Yes" : "No"),
    render: (c) => (c.autoRenew == null ? "—" : c.autoRenew ? "Yes" : "No"),
  },
  {
    key: "riskTier", label: "Risk", kind: "select", minWidth: 110, options: ["LOW", "MEDIUM", "HIGH"],
    raw: (c) => c.riskTier,
    render: (c) => <Badge tone={riskTone(c.riskTier)}>{c.riskTier || "—"}</Badge>,
  },
  {
    key: "updatedAt", label: "Last updated", kind: "date", minWidth: 170,
    raw: (c) => c.updatedAt,
    render: (c) => <span className="tabular whitespace-nowrap">{dateTime(c.updatedAt)}</span>,
  },
  {
    key: "updatedBy", label: "Last updated by", kind: "text", minWidth: 150,
    raw: (c) => c.updatedBy,
    render: (c) => c.updatedBy || "—",
  },
];

const DEFAULT_VIEW: View = {
  order: COLUMNS.map((d) => d.key),
  hidden: ["currency", "effectiveDate", "governingLaw", "autoRenew"],
};

const LS_KEY = "clm-contracts-view";

function loadView(): View {
  // keep the view valid even as the column set evolves: every known column stays
  // in `order` (appended at the end if new), hidden only lists hideable columns
  const known = COLUMNS.map((d) => d.key);
  let order = [...known];
  let hidden = DEFAULT_VIEW.hidden;
  try {
    const v = JSON.parse(localStorage.getItem(LS_KEY) || "");
    if (Array.isArray(v.order) && Array.isArray(v.hidden)) {
      order = v.order.filter((k: string) => known.includes(k));
      known.forEach((k) => { if (!order.includes(k)) order.push(k); });
      hidden = v.hidden.filter((k: string) => COLUMNS.some((d) => d.key === k && !d.pinned));
    }
  } catch { /* first visit */ }
  return { order, hidden };
}

function isEmptyVal(v: any) {
  return v == null || v === "" || (Array.isArray(v) && v.length === 0);
}

function match(def: ColDef, f: Filter, c: any): boolean {
  const v = def.raw(c);
  const input = (f.value || "").trim();
  switch (f.op) {
    case "is empty": return isEmptyVal(v);
    case "is": {
      if (def.kind === "number") return v != null && v === Number(input);
      if (def.kind === "select") return String(v ?? "").toUpperCase() === input.toUpperCase();
      return String(v ?? "").toLowerCase() === input.toLowerCase();
    }
    case "is not": return String(v ?? "").toUpperCase() !== input.toUpperCase();
    case "contains": return String(v ?? "").toLowerCase().includes(input.toLowerCase());
    case "starts with": return String(v ?? "").toLowerCase().startsWith(input.toLowerCase());
    case "larger than": return v != null && v > Number(input);
    case "smaller than": return v != null && v < Number(input);
    case "before": return v != null && v !== "" && String(v) < input;
    case "after": return v != null && v !== "" && String(v) > input;
    default: return true;
  }
}

function compare(def: ColDef, a: any, b: any): number {
  if (def.kind === "number") return Number(def.raw(a)) - Number(def.raw(b));
  const sa = String(def.raw(a));
  const sb = String(def.raw(b));
  return sa.localeCompare(sb, undefined, { numeric: true, sensitivity: "base" });
}

function filterInput(def: ColDef, filter: Filter, onOp: (op: string) => void, onValue: (v: string) => void) {
  const valueEditor =
    def.kind === "select" ? (
      <select className="input" value={filter.value} onChange={(e) => onValue(e.target.value)}>
        <option value="">…</option>
        {(def.options || []).map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    ) : def.kind === "date" ? (
      <input type="date" className="input" value={filter.value} onChange={(e) => onValue(e.target.value)} />
    ) : def.kind === "number" ? (
      <input type="number" className="input" placeholder="0" value={filter.value} onChange={(e) => onValue(e.target.value)} />
    ) : (
      <input className="input" placeholder="Value…" value={filter.value} onChange={(e) => onValue(e.target.value)} />
    );
  return (
    <>
      <select className="input" value={filter.op} onChange={(e) => onOp(e.target.value)}>
        {OPS[def.kind].map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
      {filter.op !== "is empty" && valueEditor}
    </>
  );
}

export function ContractTable({ rows }: { rows: any[] }) {
  const [view, setView] = useState<View>(loadView);
  const [filters, setFilters] = useState<Record<string, Filter>>({});
  const [sort, setSort] = useState<Sort>(null);
  const [menu, setMenu] = useState<{ key: string; mode: "filter" | "filter-mgr"; anchor: { left: number; bottom: number } } | null>(null);

  // Popovers are rendered position:fixed (anchored to the opening button's screen rect) so
  // they can't be clipped by the table's horizontal scroll container. Any scroll/resize
  // invalidates the anchor, so close rather than show a stale menu.
  useEffect(() => {
    if (!menu) return;
    const close = () => setMenu(null);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => { window.removeEventListener("scroll", close, true); window.removeEventListener("resize", close); };
  }, [menu]);

  function anchorOf(e: React.MouseEvent) {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    return { left: r.left, bottom: r.bottom };
  }

  const byKey = useMemo(() => Object.fromEntries(COLUMNS.map((d) => [d.key, d])), []);
  const visible = view.order.map((k) => byKey[k]).filter((d) => d && !view.hidden.includes(d.key));
  const minWidth = visible.reduce((n, d) => n + d.minWidth, 0);
  const activeFilters = Object.entries(filters).filter(([, f]) => f.op === "is empty" || (f.value || "").trim() !== "");

  const shown = useMemo(() => {
    let out = rows;
    if (activeFilters.length) {
      out = out.filter((c) => activeFilters.every(([key, f]) => match(byKey[key], f, c)));
    }
    if (sort) {
      const def = byKey[sort.key];
      out = [...out].sort((a, b) => {
        const ea = isEmptyVal(def.raw(a));
        const eb = isEmptyVal(def.raw(b));
        if (ea && !eb) return 1; // rows without a value always sink, both directions
        if (!ea && eb) return -1;
        const c = compare(def, a, b);
        return sort.dir === "asc" ? c : -c;
      });
    }
    return out;
  }, [rows, filters, sort, byKey, activeFilters]);

  function persist(next: View) {
    setView(next);
    localStorage.setItem(LS_KEY, JSON.stringify(next));
  }

  function setColFilter(key: string, patch: Partial<Filter>) {
    setFilters((s) => {
      const f: Filter = { ...(s[key] || { op: "is", value: "" }), ...patch };
      return { ...s, [key]: f }; // kept even when empty; only applied when it has a value (or op is "is empty")
    });
  }

  function isFilterActive(f?: Filter) {
    return !!f && (f.op === "is empty" || (f.value || "").trim() !== "");
  }

  function clearColFilter(key: string) {
    setFilters((s) => { const n = { ...s }; delete n[key]; return n; });
    setMenu(null);
  }

  function cycleSort(key: string) {
    setSort((s) => (s?.key !== key ? { key, dir: "asc" } : s.dir === "asc" ? { key, dir: "desc" } : null));
  }

  function move(key: string, dir: -1 | 1) {
    const order = [...view.order];
    const i = order.indexOf(key);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= order.length) return;
    [order[i], order[j]] = [order[j], order[i]];
    persist({ ...view, order });
  }

  function closeMenu() { setMenu(null); }

  return (
    <Card className="!p-0 relative">
      <div className="flex items-center justify-between px-3 py-2 border-b border-border text-xs text-ink-faint">
        <span>
          {activeFilters.length || sort
            ? `${shown.length} of ${rows.length} contracts${sort ? ` · sorted by ${byKey[sort.key].label.toLowerCase()} (${sort.dir === "asc" ? "ascending" : "descending"})` : ""}`
            : `${rows.length} contracts`}
        </span>
        <div className="flex items-center gap-2 relative">
          {activeFilters.length > 0 && (
            <button className="link" onClick={() => setFilters({})}>Clear filters ({activeFilters.length})</button>
          )}
          <button
            className="btn inline-flex items-center gap-1"
            style={{ padding: "0.25rem 0.5rem" }}
            onClick={(e) => setMenu((m) => (m?.mode === "filter-mgr" ? null : { key: "", mode: "filter-mgr", anchor: anchorOf(e) }))}
          >
            <Icon.list width={13} height={13} /> Columns ({visible.length})
          </button>
          {menu?.mode === "filter-mgr" && (
            <>
              <div className="fixed inset-0 z-20" onClick={closeMenu} />
              <div
                className="fixed z-30 w-64 card !p-1 shadow-lg"
                style={{
                  top: menu.anchor.bottom + 6,
                  left: Math.max(8, Math.min(menu.anchor.left, window.innerWidth - 272)),
                }}
              >
                <div className="px-2 py-1.5 text-[11px] uppercase tracking-wide text-ink-faint">Show / order columns</div>
                {view.order.map((key, i) => {
                  const def = byKey[key];
                  const hidden = view.hidden.includes(key);
                  return (
                    <div key={key} className="flex items-center gap-1.5 px-2 py-1 rounded-[8px] hover:bg-surface-2">
                      <label className="flex items-center gap-2 flex-1 text-sm text-ink cursor-pointer">
                        <input
                          type="checkbox"
                          checked={!hidden}
                          disabled={def.pinned}
                          onChange={() =>
                            persist({
                              ...view,
                              hidden: hidden ? view.hidden.filter((k) => k !== key) : [...view.hidden, key],
                            })
                          }
                        />
                        {def.label}
                      </label>
                      <button
                        className="w-6 h-6 grid place-items-center rounded-[6px] text-ink-faint hover:bg-surface hover:text-ink disabled:opacity-30"
                        disabled={i === 0}
                        onClick={() => move(key, -1)}
                        title="Move up"
                      >
                        <Icon.arrowUp width={12} height={12} />
                      </button>
                      <button
                        className="w-6 h-6 grid place-items-center rounded-[6px] text-ink-faint hover:bg-surface hover:text-ink disabled:opacity-30"
                        disabled={i === view.order.length - 1}
                        onClick={() => move(key, 1)}
                        title="Move down"
                      >
                        <Icon.arrowDown width={12} height={12} />
                      </button>
                    </div>
                  );
                })}
                <div className="border-t border-border mt-1 pt-1 px-2 py-1">
                  <button className="link text-xs" onClick={() => { persist(DEFAULT_VIEW); setSort(null); }}>
                    Reset to default
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Vertical scrolling happens inside the card (max-height) so the horizontal
          scrollbar stays at the bottom of the view instead of below the last row,
          and the header stays visible while scrolling. */}
      <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 300px)", borderRadius: "0 0 12px 12px" }}>
        <table className="w-full text-sm" style={{ minWidth }}>
          <thead>
            <tr className="text-left text-xs text-ink-faint border-b border-border">
              {visible.map((def) => {
                const col = filters[def.key];
                const filterActive = isFilterActive(col);
                const sorted = sort?.key === def.key ? sort.dir : null;
                return (
                  <th
                    key={def.key}
                    className={`sticky top-0 z-10 px-3 py-2 font-medium align-bottom ${def.align === "right" ? "text-right" : ""}`}
                    style={{ minWidth: def.minWidth, background: "var(--surface)", boxShadow: "inset 0 -1px 0 var(--border)" }}
                  >
                    <div className={`relative flex items-center gap-0.5 ${def.align === "right" ? "justify-end" : ""}`}>
                      <button
                        className="inline-flex items-center gap-1 hover:text-ink transition-colors"
                        onClick={(e) => setMenu((m) => (m?.key === def.key && m.mode === "filter" ? null : { key: def.key, mode: "filter", anchor: anchorOf(e) }))}
                        title="Filter this column"
                      >
                        {def.label}
                        {filterActive && <span className="inline-block w-1.5 h-1.5 rounded-full" style={{ background: "var(--accent)" }} />}
                      </button>
                      <button
                        className="w-5 h-5 grid place-items-center rounded-[5px] transition-colors hover:bg-surface-2 hover:text-ink"
                        style={{ color: sorted ? "var(--accent)" : undefined }}
                        onClick={() => cycleSort(def.key)}
                        title={sorted ? `Sorted ${sorted === "asc" ? "ascending" : "descending"} — click to clear` : "Sort by this column"}
                      >
                        {sorted ? (
                          sorted === "asc" ? <Icon.arrowUp width={11} height={11} /> : <Icon.arrowDown width={11} height={11} />
                        ) : (
                          <span className="inline-flex flex-col items-center leading-none opacity-60">
                            <Icon.arrowUp width={9} height={9} />
                            <Icon.arrowDown width={9} height={9} style={{ marginTop: -2 }} />
                          </span>
                        )}
                      </button>

                      {menu?.key === def.key && menu.mode === "filter" && (
                        <>
                          <div className="fixed inset-0 z-20" onClick={closeMenu} />
                          <div
                            className="fixed z-30 w-56 card !p-3 space-y-2 shadow-lg"
                            style={{
                              top: menu.anchor.bottom + 6,
                              left: Math.max(8, Math.min(menu.anchor.left, window.innerWidth - 232)),
                            }}
                          >
                            <div className="text-[11px] uppercase tracking-wide text-ink-faint">
                              Filter · {def.label}
                            </div>
                            {filterInput(
                              def,
                              col || { op: "is", value: "" },
                              (op) => setColFilter(def.key, { op }),
                              (value) => setColFilter(def.key, { value }),
                            )}
                            <div className="flex justify-between pt-1">
                              <button className="link text-xs" onClick={() => clearColFilter(def.key)}>Clear</button>
                              <button className="btn btn-primary text-xs" style={{ padding: "0.25rem 0.6rem" }} onClick={closeMenu}>
                                Done
                              </button>
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 ? (
              <tr>
                <td className="px-3 py-6 text-center text-ink-faint" colSpan={visible.length}>
                  {rows.length === 0 ? "No contracts match these filters." : "No contracts match the column filters."}
                </td>
              </tr>
            ) : (
              shown.map((c: any) => (
                <tr key={c.id} className="border-b border-border last:border-0 hover:bg-surface-2">
                  {visible.map((def) => (
                    <td key={def.key} className={`px-3 py-2.5 align-top ${def.align === "right" ? "text-right" : ""}`}>
                      {def.render(c)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
}