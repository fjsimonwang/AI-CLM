import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, date } from "../api";
import { Card, Badge, Spinner, Empty, Stat, Confidence, statusTone } from "../components/ui";

export default function Obligations() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState("");
  const dash = useQuery({ queryKey: ["obl-dash"], queryFn: () => api("/obligations/dashboard") });
  const list = useQuery({
    queryKey: ["obligations", filter],
    queryFn: () => api(`/obligations${filter ? `?status=${filter}` : ""}`),
  });

  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      api(`/obligations/${id}/status`, { method: "POST", json: { status } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["obligations"] }),
  });
  const verify = useMutation({
    mutationFn: (id: string) => api(`/obligations/${id}/verify`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["obligations"] }),
  });

  const d: any = dash.data || {};

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-medium">Obligations</h1>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Stat label="Overdue" value={d.overdue ?? 0} tone="text-[color:var(--risk)]" />
        <Stat label="Due next 30 days" value={d.dueNext30 ?? 0} tone="text-[color:var(--warn)]" />
        <Stat label="Due next 90 days" value={d.dueNext90 ?? 0} />
        <Stat label="Unverified (low conf.)" value={d.unverifiedLowConfidence ?? 0} />
      </div>

      <div className="flex gap-1">
        {["", "OPEN", "OVERDUE", "CLOSED"].map((s) => (
          <button key={s} className={`btn ${filter === s ? "btn-primary" : ""}`} onClick={() => setFilter(s)}>
            {s || "All"}
          </button>
        ))}
      </div>

      {list.isLoading ? (
        <Spinner />
      ) : (list.data || []).length === 0 ? (
        <Empty>No obligations.</Empty>
      ) : (
        <Card className="!p-0 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-ink-faint border-b border-border">
                <th className="px-3 py-2 font-medium">Obligation</th>
                <th className="px-3 py-2 font-medium">Contract</th>
                <th className="px-3 py-2 font-medium">Due</th>
                <th className="px-3 py-2 font-medium">Owner</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Confidence</th>
                <th className="px-3 py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {(list.data || []).map((o: any) => (
                <tr key={o.id} className="border-b border-border last:border-0">
                  <td className="px-3 py-2.5">
                    {o.description}
                    <div className="text-xs text-ink-faint">{o.type}</div>
                  </td>
                  <td className="px-3 py-2.5">
                    <Link to={`/contracts/${o.contractId}`} className="link">{o.contractNumber}</Link>
                  </td>
                  <td className="px-3 py-2.5 tabular">
                    {date(o.dueDate)} {o.overdue && <Badge tone="risk">overdue</Badge>}
                  </td>
                  <td className="px-3 py-2.5">{o.owner || o.owningDepartment || "—"}</td>
                  <td className="px-3 py-2.5"><Badge tone={statusTone(o.status)}>{o.status}</Badge></td>
                  <td className="px-3 py-2.5"><Confidence value={o.extractionConfidence} verified={o.verified} /></td>
                  <td className="px-3 py-2.5 text-right whitespace-nowrap">
                    {!o.verified && (
                      <button className="btn" style={{ padding: "0.25rem 0.5rem" }} onClick={() => verify.mutate(o.id)}>
                        Verify
                      </button>
                    )}{" "}
                    {o.status !== "CLOSED" && (
                      <button
                        className="btn"
                        style={{ padding: "0.25rem 0.5rem" }}
                        onClick={() => setStatus.mutate({ id: o.id, status: "CLOSED" })}
                      >
                        Close
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}
