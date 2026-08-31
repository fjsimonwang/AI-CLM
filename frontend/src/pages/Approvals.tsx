import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, money, date, useAuth } from "../api";
import { Card, SectionTitle, Badge, Spinner, Empty } from "../components/ui";
import { CommentThreads } from "../components/CommentThreads";
import { BriefingCard } from "../components/BriefingCard";
import { Icon } from "../components/icons";

export default function Approvals() {
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const [scope, setScope] = useState<"mine" | "all">("mine");
  const [brief, setBrief] = useState<Record<string, any>>({});
  const [comment, setComment] = useState<Record<string, string>>({});
  const [discuss, setDiscuss] = useState<Record<string, boolean>>({});
  const [briefingFresh, setBriefingFresh] = useState<Record<string, boolean>>({});

  const tasks = useQuery({
    queryKey: ["tasks", scope],
    queryFn: () => api(scope === "mine" ? "/workflow/my-tasks" : "/workflow/tasks"),
  });

  const contractIds = ((tasks.data || []) as any[]).map((t) => t.contractId).join(",");
  const stored = useQuery({
    queryKey: ["briefings", contractIds],
    queryFn: () => api(`/ai/briefings?contractIds=${contractIds}`),
    enabled: !!contractIds,
  });

  const briefingFor = (t: any) => brief[t.id] || (stored.data || {})[t.contractId]?.briefing;

  const act = useMutation({
    mutationFn: ({ taskId, event, comment }: { taskId: string; event: string; comment?: string }) =>
      api(`/workflow/tasks/${taskId}/act`, { method: "POST", json: { event, comment } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tasks"] }),
  });

  const briefing = useMutation({
    mutationFn: (p: { contractId: string; force?: boolean }) =>
      api("/ai/approver-briefing", { method: "POST", json: { contractId: p.contractId, force: !!p.force } }),
    onSuccess: (r: any, p) =>
      qc.invalidateQueries({ queryKey: ["briefings"] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-medium">Approvals</h1>
        <div className="flex gap-1">
          <button className={`btn ${scope === "mine" ? "btn-primary" : ""}`} onClick={() => setScope("mine")}>
            My tasks
          </button>
          <button className={`btn ${scope === "all" ? "btn-primary" : ""}`} onClick={() => setScope("all")}>
            All open
          </button>
        </div>
      </div>

      {tasks.isLoading ? (
        <Spinner />
      ) : (tasks.data || []).length === 0 ? (
        <Empty>No open tasks.</Empty>
      ) : (
        <div className="space-y-3">
          {(tasks.data || []).map((t: any) => (
            <Card key={t.id}>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <Link to={`/contracts/${t.contractId}`} state={{ from: "approvals" }} className="link font-medium">{t.contractNumber}</Link>
                    <Badge tone="accent">{t.currentState}</Badge>
                    <Badge tone="neutral">{t.type}</Badge>
                    {t.overdue && <Badge tone="risk">overdue</Badge>}
                  </div>
                  <div className="text-sm text-ink-soft mt-0.5">{t.contractTitle}</div>
                  <div className="text-xs text-ink-faint mt-0.5">
                    {t.contractType} · {money(t.contractValue, t.currency)} · assigned {t.assignee} · due {date(t.dueAt)}
                  </div>
                </div>
                <div className="flex gap-2 shrink-0">
                  <button
                    className="btn"
                    onClick={() => setDiscuss((s) => ({ ...s, [t.id]: !s[t.id] }))}
                  >
                    <Icon.message width={14} height={14} /> Discuss
                  </button>
                </div>
              </div>

              <BriefingCard
                briefing={briefingFor(t)}
                generatedAt={(stored.data || {})[t.contractId]?.generatedAt || (brief[t.id] ? new Date().toISOString() : undefined)}
                cached
                defaultFolded
                rerunning={briefing.isPending && briefing.variables?.contractId === t.contractId}
                autoOpen={!!briefingFresh[t.id]}
                onRerun={() =>
                  briefing
                    .mutateAsync({ contractId: t.contractId })
                    .then((r: any) => {
                      setBrief((b) => ({ ...b, [t.id]: r.briefing }));
                      setBriefingFresh((f) => ({ ...f, [t.id]: true }));
                      qc.invalidateQueries({ queryKey: ["briefings", contractIds] });
                    })
                }
              />

              <div className="mt-3 flex flex-wrap items-center gap-2">
                {t.assignedUserId && t.assignedUserId === user?.id ? (
                  <>
                    <input
                      className="input"
                      style={{ maxWidth: 320 }}
                      placeholder="Comment (optional)"
                      value={comment[t.id] || ""}
                      onChange={(e) => setComment((c) => ({ ...c, [t.id]: e.target.value }))}
                    />
                    {(t.availableEvents || []).map((ev: string) => (
                      <button
                        key={ev}
                        className={`btn ${ev === "approve" ? "btn-primary" : ""}`}
                        style={ev === "reject" ? { borderColor: "var(--risk)", color: "var(--risk)" } : {}}
                        disabled={act.isPending}
                        onClick={() => act.mutate({ taskId: t.id, event: ev, comment: comment[t.id] })}
                      >
                        {ev.replace(/_/g, " ")}
                      </button>
                    ))}
                  </>
                ) : (
                  <span className="text-xs text-ink-faint">
                    Actions are handled by the assigned user{t.assignee ? ` (${t.assignee})` : ""}.
                  </span>
                )}
              </div>
              {act.isError && <div className="text-xs mt-2" style={{ color: "var(--risk)" }}>{(act.error as any)?.message}</div>}

              {discuss[t.id] && (
                <div className="mt-4 pt-4 border-t border-border">
                  <CommentThreads entityType="CONTRACT" entityId={t.contractId} />
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
