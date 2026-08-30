import { useParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import { Card, Badge, Spinner, Empty } from "../components/ui";
import { Icon } from "../components/icons";

type Msg = { role: string; content: string };

const label = (k: string) => k.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

export default function IntakeSessionView() {
  const { id } = useParams<{ id: string }>();
  const s = useQuery({
    queryKey: ["intake-session", id],
    queryFn: () => api(`/intake/sessions/${id}`),
  });

  if (s.isLoading) return <Spinner label="Loading intake session…" />;
  if (s.isError) return <Empty>Intake session not found.</Empty>;

  const d: any = s.data;
  const conversation: Msg[] = d.conversation || [];
  const fields: Record<string, any> = d.capturedFields || {};

  return (
    <div className="space-y-4 max-w-[860px] mx-auto">
      <div className="flex items-center gap-2 text-sm">
        <Link to={d.resultingContractId ? `/contracts/${d.resultingContractId}` : "/contracts"} className="link">
          ← Back
        </Link>
      </div>

      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-medium">Intake chat history</h1>
          <div className="text-sm text-ink-faint mt-0.5">
            Original conversational intake session {String(d.id).slice(0, 8)}
            {d.resultingContractId && <> that generated this contract request</>}
          </div>
        </div>
        <div className="flex gap-2">
          <Badge tone="accent">{String(d.status)}</Badge>
          {d.contractType && <Badge tone="neutral">{String(d.contractType).replace(/_/g, " ")}</Badge>}
        </div>
      </div>

      <Card className="!p-0">
        <div className="px-4 py-3 border-b border-border text-sm font-medium flex items-center gap-1.5">
          <Icon.message width={14} height={14} /> Conversation
        </div>
        <div className="p-4 space-y-3">
          {conversation.length === 0 ? (
            <Empty>No messages recorded in this session.</Empty>
          ) : (
            conversation.map((m, i) => (
              <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
                <div className={`text-xs uppercase tracking-wider font-medium shrink mt-3 mr-2 self-start ${
                  m.role === "user" ? "order-2 text-ink-faint" : "text-[color:var(--ai)]"
                }`}>
                  {m.role === "user" ? "Requester" : "AI intake"}
                </div>
                <div
                  className={`max-w-[80%] text-sm rounded-[10px] px-3 py-2 whitespace-pre-wrap ${
                    m.role === "user" ? "bg-[color:var(--accent)] text-white" : "bg-surface-2 text-ink"
                  }`}
                >
                  {m.content}
                </div>
              </div>
            ))
          )}
        </div>
      </Card>

      {Object.keys(fields).length > 0 && (
        <Card>
          <div className="text-sm font-medium mb-2">Captured details</div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2">
            {Object.entries(fields)
              .filter(([k]) => !k.startsWith("_"))
              .map(([k, v]) => (
                <div key={k} className="flex gap-2 text-sm min-w-0">
                  <span className="text-ink-faint shrink-0">{label(k)}:</span>
                  <span className="text-ink break-words">
                    {Array.isArray(v) ? v.join(", ") : String(v)}
                  </span>
                </div>
              ))}
          </div>
        </Card>
      )}
    </div>
  );
}