import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty } from "../components/ui";
import { AiAffordance } from "../components/AiAffordance";

function tierTone(t: string): any {
  return t === "PREFERRED" ? "ok" : t === "ACCEPTABLE" ? "neutral" : t === "FALLBACK" ? "warn" : "risk";
}

export default function Clauses() {
  const [sel, setSel] = useState<string | null>(null);
  const [usageFor, setUsageFor] = useState<string | null>(null);
  const [testText, setTestText] = useState("");
  const [deviation, setDeviation] = useState<any>(null);
  const [checking, setChecking] = useState(false);

  const concepts = useQuery({ queryKey: ["concepts"], queryFn: () => api("/clauses/concepts") });
  const variants = useQuery({
    queryKey: ["variants", sel],
    queryFn: () => api(`/clauses/concepts/${sel}/variants`),
    enabled: !!sel,
  });
  const usage = useQuery({
    queryKey: ["usage", usageFor],
    queryFn: () => api(`/clauses/variants/${usageFor}/usage`),
    enabled: !!usageFor,
  });

  const selConcept = (concepts.data || []).find((c: any) => c.id === sel);

  async function check() {
    if (!testText.trim() || !selConcept) return;
    setChecking(true);
    setDeviation(null);
    try {
      const r = await api("/ai/deviation", {
        method: "POST",
        json: { proposedClause: testText, conceptCode: selConcept.conceptCode },
      });
      setDeviation(r);
    } finally {
      setChecking(false);
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-medium">Clause library</h1>
      <div className="grid lg:grid-cols-[280px_1fr] gap-4">
        <Card className="!p-0">
          {concepts.isLoading ? (
            <Spinner />
          ) : (
            <ul>
              {(concepts.data || []).map((c: any) => (
                <li key={c.id}>
                  <button
                    className={`w-full text-left px-3 py-2.5 text-sm border-b border-border ${
                      sel === c.id ? "bg-surface-2" : "hover:bg-surface-2"
                    }`}
                    onClick={() => { setSel(c.id); setUsageFor(null); setDeviation(null); }}
                  >
                    <div className="flex items-center justify-between">
                      <span>{c.name}</span>
                      {c.isCore && <Badge tone="accent">core</Badge>}
                    </div>
                    <div className="text-xs text-ink-faint">
                      {c.variantCount} variant{c.variantCount === 1 ? "" : "s"} · {c.category}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <div className="space-y-4">
          {!sel && <Empty>Select a clause concept.</Empty>}
          {sel && (
            <>
              <Card>
                <SectionTitle>{selConcept?.name} — playbook positions</SectionTitle>
                {variants.isLoading ? (
                  <Spinner />
                ) : (
                  <div className="space-y-3">
                    {(variants.data || []).map((v: any) => (
                      <div key={v.id} className="border-b border-border pb-3 last:border-0">
                        <div className="flex items-center justify-between">
                          <Badge tone={tierTone(v.positionTier)}>{v.positionTier}</Badge>
                          <button className="link text-xs" onClick={() => setUsageFor(v.id)}>
                            which contracts use this?
                          </button>
                        </div>
                        <p className="text-sm mt-1 font-serif">{v.bodyText}</p>
                        {v.guidanceNotes && <p className="text-xs text-ink-faint mt-1">{v.guidanceNotes}</p>}
                        {usageFor === v.id && (
                          <div className="mt-2 bg-surface-2 rounded-[8px] p-2">
                            {usage.isLoading ? (
                              <Spinner />
                            ) : (usage.data?.usedByContracts || []).length === 0 ? (
                              <div className="text-xs text-ink-faint">Not used by any live contract.</div>
                            ) : (
                              <ul className="text-xs space-y-1">
                                {usage.data.usedByContracts.map((u: any) => (
                                  <li key={u.contractId}>
                                    <Link to={`/contracts/${u.contractId}`} className="link">{u.contractNumber}</Link>
                                    <span className="text-ink-faint"> — {u.title} ({u.status})</span>
                                  </li>
                                ))}
                              </ul>
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </Card>

              <Card>
                <SectionTitle>Check proposed language against playbook</SectionTitle>
                <textarea
                  className="input"
                  rows={4}
                  placeholder="Paste a counterparty's proposed clause…"
                  value={testText}
                  onChange={(e) => setTestText(e.target.value)}
                />
                <button className="btn btn-ai mt-2" onClick={check} disabled={checking || !testText.trim()}>
                  {checking ? "Analysing…" : "✦ Analyse deviation"}
                </button>
                {deviation && (
                  <div className="mt-3 space-y-2">
                    <div className="text-sm">
                      Overall risk: <Badge tone={tierTone(deviation.overall_risk === "HIGH" ? "UNACCEPTABLE" : deviation.overall_risk === "MEDIUM" ? "FALLBACK" : "PREFERRED")}>{deviation.overall_risk}</Badge>
                    </div>
                    {(deviation.deviations || []).map((dv: any, i: number) => (
                      <AiAffordance
                        key={i}
                        suggestion={<><b>{dv.clause_concept}</b> — closest tier {dv.closest_tier} ({dv.severity})</>}
                        explanation={dv.explanation}
                        source="AI deviation analysis vs. playbook"
                      />
                    ))}
                  </div>
                )}
              </Card>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
