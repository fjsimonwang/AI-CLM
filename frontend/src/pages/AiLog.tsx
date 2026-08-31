import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api";
import { Card, Badge, Spinner, Empty } from "../components/ui";

const outcomeTone: Record<string, any> = {
  ACCEPTED: "ok",
  AUTO_APPLIED: "ok",
  EDITED: "warn",
  REJECTED: "risk",
  PENDING: "neutral",
};

export default function AiLog() {
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ["ai-interactions"], queryFn: () => api("/ai/interactions") });

  const revert = useMutation({
    mutationFn: (id: string) => api(`/ai/interactions/${id}/revert`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ai-interactions"] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-medium">AI activity</h1>
          <p className="text-sm text-ink-faint">
            Every AI call is logged with prompt version, confidence and outcome — and is individually reversible.
          </p>
        </div>
      </div>

      {list.isLoading ? (
        <Spinner />
      ) : (list.data || []).length === 0 ? (
        <Empty>No AI interactions yet. Try the New request or Inquiry screens.</Empty>
      ) : (
        <Card className="!p-0 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-ink-faint border-b border-border">
                <th className="px-3 py-2 font-medium">When</th>
                <th className="px-3 py-2 font-medium">Surface</th>
                <th className="px-3 py-2 font-medium">Capability</th>
                <th className="px-3 py-2 font-medium">Prompt</th>
                <th className="px-3 py-2 font-medium">Conf.</th>
                <th className="px-3 py-2 font-medium">Latency</th>
                <th className="px-3 py-2 font-medium">Outcome</th>
                <th className="px-3 py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {(list.data || []).map((a: any) => (
                <tr key={a.id} className="border-b border-border last:border-0">
                  <td className="px-3 py-2 tabular text-xs">{new Date(a.occurredAt).toLocaleString()}</td>
                  <td className="px-3 py-2">{a.surface}</td>
                  <td className="px-3 py-2">{a.capability}</td>
                  <td className="px-3 py-2 text-xs">{a.promptId}@{a.promptVersion}</td>
                  <td className="px-3 py-2 tabular">{a.confidence != null ? `${Math.round(a.confidence * 100)}%` : "—"}</td>
                  <td className="px-3 py-2 tabular">{a.latencyMs != null ? `${a.latencyMs}ms` : "—"}</td>
                  <td className="px-3 py-2">
                    <Badge tone={outcomeTone[a.outcome] || "neutral"}>{a.outcome}</Badge>
                    {a.revertedAt && <Badge tone="risk">reverted</Badge>}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {!a.revertedAt && a.outcome !== "REJECTED" && (
                      <button
                        className="btn"
                        style={{ padding: "0.25rem 0.5rem" }}
                        disabled={revert.isPending}
                        onClick={() => revert.mutate(a.id)}
                      >
                        Revert
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
