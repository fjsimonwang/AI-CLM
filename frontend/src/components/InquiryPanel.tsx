import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, money } from "../api";
import { Card, SectionTitle, Badge, Spinner } from "./ui";
import { Icon } from "./icons";

const GROUP_LABEL: Record<string, string> = {
  status: "Status", type: "Contract type", entity: "Entity", currency: "Currency", riskTier: "Risk tier",
};

export function InquiryPanel({ onFilters }: { onFilters: (filters: Record<string, any> | null) => void }) {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [chartTitle, setChartTitle] = useState("");
  const [chartType, setChartType] = useState("bar");
  const [groupBy, setGroupBy] = useState("status");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [draftInterpreted, setDraftInterpreted] = useState("");
  const [draftFilters, setDraftFilters] = useState<Record<string, string>>({});

  useEffect(() => {
    api<string[]>("/inquiry/suggestions").then(setSuggestions).catch(() => {});
  }, []);

  useEffect(() => {
    onFilters(answer?.refusal || !answer?.filters ? null : answer.filters);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [answer]);

  async function ask(q: string) {
    if (!q.trim()) return;
    setBusy(true);
    setAnswer(null);
    setEditing(false);
    try {
      const r = await api("/inquiry/ask", { method: "POST", json: { question: q } });
      setAnswer(r);
      setAdded(false);
      setAdding(false);
      setChartTitle(r.interpreted || q);
      setChartType("bar");
      setGroupBy("status");
    } catch (e: any) {
      setAnswer({ refusal: e.message });
    } finally {
      setBusy(false);
    }
  }

  async function addToDashboard() {
    setSaveError(null);
    try {
      const cfg = await api("/me/dashboard-config");
      const charts = [...(cfg.charts || []), {
        id: crypto.randomUUID(),
        title: chartTitle.trim() || (answer.interpreted || question),
        question,
        interpreted: answer.interpreted,
        filters: answer.filters || {},
        chart: chartType,
        groupBy,
      }];
      await api("/me/dashboard-config", { method: "PUT", json: { layout: cfg.layout, charts } });
      setAdded(true);
      setAdding(false);
    } catch (e: any) {
      setSaveError(e.message || "Could not add to dashboard");
    }
  }

  function startEdit() {
    setDraftInterpreted(answer.interpreted || "");
    setDraftFilters(
      Object.fromEntries(Object.entries(answer.filters || {}).map(([k, v]) => [k, String(v)]))
    );
    setEditing(true);
  }

  async function rerun() {
    setBusy(true);
    setEditing(false);
    try {
      const filters: Record<string, any> = {};
      for (const [k, v] of Object.entries(draftFilters)) {
        if (v === "" || v == null) continue;
        filters[k] = /^-?\d+(\.\d+)?$/.test(v) ? Number(v) : v;
      }
      const r = await api("/inquiry/rerun", {
        method: "POST",
        json: { question, interpreted: draftInterpreted, filters },
      });
      setAnswer(r);
      setAdded(false);
      setAdding(false);
      setChartTitle(r.interpreted || question);
    } catch (e: any) {
      setAnswer({ refusal: e.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col h-full min-h-0 bg-surface">
      <div className="px-3 py-2.5 border-b border-border flex items-center justify-between gap-2 shrink-0">
        <span className="text-sm font-medium truncate">
          Ask about your portfolio
        </span>
        {(answer || question) && !busy && (
          <button
            className="btn shrink-0"
            style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
            title="Clear this inquiry and start over"
            onClick={() => {
              setQuestion("");
              setAnswer(null);
              setEditing(false);
              setAdding(false);
              setAdded(false);
              setSaveError(null);
            }}
          >
            <Icon.plus width={12} height={12} /> New chat
          </button>
        )}
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto p-3 space-y-3">
        <div className="flex gap-2">
          <input
            className="input"
            placeholder="Ask a question…"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && ask(question)}
          />
          <button className="btn btn-primary" onClick={() => ask(question)} disabled={busy}>
            Ask
          </button>
        </div>
        {suggestions.length > 0 && !answer && !busy && (
          <div className="flex flex-wrap gap-1.5">
            {suggestions.map((s) => (
              <button key={s} className="chip" onClick={() => { setQuestion(s); ask(s); }}>
                {s}
              </button>
            ))}
          </div>
        )}

        {busy && <Spinner label="Interpreting your question…" />}

        {answer && !busy && (
          <>
            <Card className="!p-3">
              <SectionTitle
                right={
                  !answer.refusal && !editing ? (
                    <button className="link text-xs" onClick={startEdit}>edit &amp; re-run</button>
                  ) : undefined
                }
              >
                Interpreted as
              </SectionTitle>
              {!editing ? (
                <>
                  <p className="text-sm">{answer.interpreted}</p>
                  {answer.filters && Object.keys(answer.filters).length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {Object.entries(answer.filters).map(([k, v]) => (
                        <Badge key={k} tone="accent">{k}: {String(v)}</Badge>
                      ))}
                    </div>
                  )}
                  {answer.refusal && (
                    <p className="text-sm mt-2" style={{ color: "var(--warn)" }}>{answer.refusal}</p>
                  )}
                </>
              ) : (
                <div className="space-y-2">
                  <input
                    className="input"
                    value={draftInterpreted}
                    onChange={(e) => setDraftInterpreted(e.target.value)}
                    placeholder="Plain-English restatement"
                  />
                  <div className="text-xs text-ink-faint">Filters (blank a value to remove it)</div>
                  {["entity_region", "contract_type_code", "status", "counterparty_name", "expiring_within_days", "min_value", "max_value"].map((k) => (
                    <div key={k} className="flex items-center gap-1.5">
                      <span className="text-xs text-ink-faint w-28 shrink-0">{k}</span>
                      <input
                        className="input"
                        value={draftFilters[k] ?? ""}
                        onChange={(e) => setDraftFilters((f) => ({ ...f, [k]: e.target.value }))}
                      />
                    </div>
                  ))}
                  <div className="flex gap-2 pt-1">
                    <button className="btn btn-primary" onClick={rerun}>Re-run</button>
                    <button className="btn" onClick={() => setEditing(false)}>Cancel</button>
                  </div>
                </div>
              )}
            </Card>

            {answer.answerable && (
              <>
                <div className="text-xs text-ink-faint tabular">
                  {answer.count} matching contracts
                  {answer.totalValue != null && <> · total {money(answer.totalValue, answer.primaryCurrency)}</>}
                </div>

                <button
                  className="btn text-xs"
                  style={{ padding: "0.25rem 0.6rem" }}
                  onClick={() => setAdding((v) => !v)}
                >
                  {added ? (
                    <span className="inline-flex items-center gap-1.5" style={{ color: "var(--ok)" }}>
                      <Icon.checkCircle width={13} height={13} /> Added to dashboard
                    </span>
                  ) : (
                    <><Icon.plus width={12} height={12} /> Add as dashboard section</>
                  )}
                </button>

                {adding && !added && (
                  <Card className="!p-3 space-y-2">
                    <input
                      className="input"
                      placeholder="Section title"
                      value={chartTitle}
                      onChange={(e) => setChartTitle(e.target.value)}
                    />
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <select className="input" value={chartType} onChange={(e) => setChartType(e.target.value)}>
                        <option value="bar">Bar chart</option>
                        <option value="pie">Pie chart</option>
                      </select>
                      <select className="input" value={groupBy} onChange={(e) => setGroupBy(e.target.value)}>
                        <option value="status">Group by: status</option>
                        <option value="type">Group by: type</option>
                        <option value="entity">Group by: entity</option>
                        <option value="riskTier">Group by: risk tier</option>
                        <option value="currency">Group by: currency</option>
                      </select>
                    </div>
                    {saveError && <div className="text-xs" style={{ color: "var(--risk)" }}>{saveError}</div>}
                    <div className="flex gap-2 justify-end">
                      <button className="btn" onClick={() => setAdding(false)}>Cancel</button>
                      <button className="btn btn-primary" disabled={!chartTitle.trim()} onClick={addToDashboard}>
                        Add
                      </button>
                    </div>
                  </Card>
                )}

                {(answer.stats || []).length > 0 && answer.groupBy && (
                  <Card className="!p-3">
                    <SectionTitle>Group summary — {GROUP_LABEL[answer.groupBy] || answer.groupBy}</SectionTitle>
                    <div style={{ height: 150 }}>
                      <ResponsiveContainer>
                        <BarChart data={(answer.stats || []).map((s: any) => ({ name: s.key, count: s.count }))} margin={{ left: 0, right: 8 }}>
                          <CartesianGrid vertical={false} stroke="var(--border)" />
                          <XAxis dataKey="name" tick={{ fontSize: 10, fill: "var(--ink-faint)" }} />
                          <YAxis tick={{ fontSize: 10, fill: "var(--ink-faint)" }} allowDecimals={false} width={36} />
                          <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 12 }} />
                          <Bar dataKey="count" fill="var(--accent)" radius={[3, 3, 0, 0]} barSize={22} />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                    <div className="overflow-x-auto mt-2">
                      <table className="w-full text-xs">
                      <thead>
                        <tr className="text-left text-ink-faint border-b border-border">
                          <th className="py-1.5 font-medium">{GROUP_LABEL[answer.groupBy] || answer.groupBy}</th>
                          <th className="py-1.5 font-medium text-right">Count</th>
                          <th className="py-1.5 font-medium text-right">Total</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(answer.stats || []).map((s: any) => (
                          <tr key={s.key} className="border-b border-border last:border-0">
                            <td className="py-1.5">{s.key}</td>
                            <td className="py-1.5 text-right tabular">{s.count}</td>
                            <td className="py-1.5 text-right tabular">{money(s.totalValue, s.currency)}</td>
                          </tr>
                        ))}
                      </tbody>
                      </table>
                    </div>
                  </Card>
                )}

                {(answer.stats || []).length === 1 && !answer.groupBy && (answer.metric === "sum_value" || answer.metric === "avg_value" || answer.metric === "min_value" || answer.metric === "max_value") && (
                  <Card className="!p-3">
                    <SectionTitle>Summary</SectionTitle>
                    <div className="text-xs space-y-1 tabular">
                      <div>Count: {answer.stats[0].count}</div>
                      <div>Total: {money(answer.stats[0].totalValue, answer.stats[0].currency)}</div>
                      <div>Average: {money(answer.stats[0].avgValue, answer.stats[0].currency)}</div>
                      <div>Range: {money(answer.stats[0].minValue, answer.stats[0].currency)} – {money(answer.stats[0].maxValue, answer.stats[0].currency)}</div>
                    </div>
                  </Card>
                )}
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}