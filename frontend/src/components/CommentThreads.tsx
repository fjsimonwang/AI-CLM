import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, useAuth } from "../api";
import { Spinner, Empty, Badge } from "./ui";
import { Icon } from "./icons";
import { RichText } from "./RichText";

function timeAgo(iso: string) {
  const d = new Date(iso).getTime();
  const mins = Math.round((Date.now() - d) / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function CommentThreads({ entityType, entityId, agentEnabled }: {
  entityType: string;
  entityId: string;
  agentEnabled?: boolean;
}) {
  const qc = useQueryClient();
  const key = ["comments", entityType, entityId];
  // when set (just-posted thread + agents on): poll for the agents' autonomous replies
  const [agentPollUntil, setAgentPollUntil] = useState(0);
  const q = useQuery({
    queryKey: key,
    queryFn: () => api(`/comments/${entityType}/${entityId}`),
    // while agents may still be drafting replies to a just-posted thread, keep polling
    refetchInterval: agentPollUntil > Date.now() ? 5000 : false,
  });

  const [newBody, setNewBody] = useState("");
  const [newTitle, setNewTitle] = useState("");
  const [showNew, setShowNew] = useState(false);
  const [composerKey, setComposerKey] = useState(0);
  const [replyFor, setReplyFor] = useState<string | null>(null);
  const [replyBody, setReplyBody] = useState("");
  const [replyKey, setReplyKey] = useState(0);
  const [agentFor, setAgentFor] = useState<string | null>(null);
  const [agentQuestion, setAgentQuestion] = useState("");
  const [agentTarget, setAgentTarget] = useState("");
  const [agentKey, setAgentKey] = useState(0);
  const [foldOpen, setFoldOpen] = useState<Record<string, boolean>>({});

  // when the caller doesn't pass agentEnabled, decide from the contract's live agent-channel status
  const statusQuery = useQuery({
    queryKey: ["agent-status", entityId],
    queryFn: () => api(`/agent-channel/contracts/${entityId}/status`) as Promise<{ active: boolean }>,
    enabled: entityType === "CONTRACT" && agentEnabled === undefined,
  });
  const agentEnabledResolved = agentEnabled === undefined ? !!statusQuery.data?.active : agentEnabled;
  const agentAsk = agentEnabledResolved && entityType === "CONTRACT";

  const agentsQuery = useQuery({
    queryKey: ["agent-agents", entityId],
    queryFn: () => api(`/agent-channel/contracts/${entityId}/agents`) as Promise<Array<{ userId: string; name: string; roleHint: string }>>,
    enabled: agentAsk,
  });
  const me = useAuth((s) => s.user);
  const otherAgents = (agentsQuery.data || []).filter((a) => a.userId !== me?.id);

  const invalidate = () => qc.invalidateQueries({ queryKey: key });

  const createThread = useMutation({
    mutationFn: () =>
      api("/comments/threads", {
        method: "POST",
        json: { entityType, entityId, title: newTitle, bodyHtml: newBody },
      }),
    onSuccess: () => {
      setNewBody("");
      setNewTitle("");
      setShowNew(false);
      setComposerKey((k) => k + 1);
      invalidate();
      // with agent collaboration on, participants' agents answer the new thread on their own —
      // poll until roughly the longest an agent exchange can take to land
      if (agentAsk) setAgentPollUntil(Date.now() + 100_000);
    },
  });
  const reply = useMutation({
    mutationFn: (threadId: string) =>
      api(`/comments/threads/${threadId}/reply`, { method: "POST", json: { bodyHtml: replyBody } }),
    onSuccess: () => {
      setReplyBody("");
      setReplyFor(null);
      setReplyKey((k) => k + 1);
      invalidate();
    },
  });
  const askAgent = useMutation({
    mutationFn: ({ threadId, question, targetUserId }: { threadId: string; question: string; targetUserId?: string }) =>
      api(`/agent-channel/contracts/${entityId}/ask`, { method: "POST", json: { threadId, question, targetUserId: targetUserId || null } }),
    onSuccess: () => {
      setAgentQuestion("");
      setAgentFor(null);
      setAgentKey((k) => k + 1);
      invalidate();
    },
  });
  const resolve = useMutation({
    mutationFn: ({ id, resolved }: { id: string; resolved: boolean }) =>
      api(`/comments/threads/${id}/resolve?resolved=${resolved}`, { method: "POST" }),
    onSuccess: invalidate,
  });

  const threads = q.data || [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-ink-soft uppercase tracking-wide">Discussion</h3>
        {!showNew && (
          <button className="btn" onClick={() => setShowNew(true)}>
            <Icon.plus width={14} height={14} /> New thread
          </button>
        )}
      </div>

      {showNew && (
        <div className="card p-3 space-y-2 pop-in">
          <input
            className="input"
            placeholder="Thread title (e.g. “Liability cap”)"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
          />
          <RichText key={composerKey} placeholder="Start the discussion…" onChange={setNewBody} />
          {entityType === "CONTRACT" && agentAsk && (
            <div className="text-xs text-violet-600 dark:text-violet-400 flex items-center gap-1">
              <Icon.bot width={13} height={13} /> Participants' agents may answer automatically once you post.
            </div>
          )}
          <div className="flex gap-2 justify-end">
            <button className="btn" onClick={() => setShowNew(false)}>
              Cancel
            </button>
            <button
              className="btn btn-primary"
              disabled={createThread.isPending || !newBody.trim()}
              onClick={() => createThread.mutate()}
            >
              Post
            </button>
          </div>
        </div>
      )}

      {q.isLoading ? (
        <Spinner />
      ) : threads.length === 0 ? (
        <Empty>No discussion yet.</Empty>
      ) : (
        <div className="space-y-3 stagger">
          {threads.map((t: any) => {
            const resolved = t.status === "RESOLVED";
            const folded = resolved && !foldOpen[t.id];
            return (
            <div key={t.id} className="card p-3.5 lift">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    {resolved && (
                      <button
                        className="w-5 h-5 grid place-items-center rounded text-ink-faint hover:bg-surface-2 hover:text-ink shrink-0"
                        style={{ transition: "transform 0.2s", transform: foldOpen[t.id] ? "none" : "rotate(-90deg)" }}
                        aria-label={foldOpen[t.id] ? "Collapse resolved thread" : "Expand resolved thread"}
                        onClick={() => setFoldOpen((s) => ({ ...s, [t.id]: !s[t.id] }))}
                      >
                        <Icon.chevronDown width={14} height={14} />
                      </button>
                    )}
                    <span className={"font-medium text-sm" + (resolved ? " text-ink-soft" : "")}>{t.title}</span>
                    {resolved ? (
                      <Badge tone="ok">resolved</Badge>
                    ) : (
                      <Badge tone="warn">open</Badge>
                    )}
                    {folded && (
                      <button
                        className="text-xs text-ink-faint hover:text-ink-soft hover:underline"
                        onClick={() => setFoldOpen((s) => ({ ...s, [t.id]: true }))}
                      >
                        {t.messages.length} {t.messages.length === 1 ? "message" : "messages"}
                      </button>
                    )}
                  </div>
                  <div className="text-xs text-ink-faint mt-0.5">
                    {t.participants.join(", ")} · opened {timeAgo(t.createdAt)}
                  </div>
                </div>
                <button
                  className="btn"
                  style={{ padding: "0.25rem 0.55rem" }}
                  onClick={() => resolve.mutate({ id: t.id, resolved: t.status !== "RESOLVED" })}
                >
                  {resolved ? "Reopen" : "Resolve"}
                </button>
              </div>

              {folded ? null : (
              <>
              <div className="mt-3 space-y-3">
                {t.messages.map((m: any) => (
                  <div key={m.id} className="flex gap-2.5">
                    <div
                      className={"w-6 h-6 rounded-full grid place-items-center text-[10px] font-medium text-white shrink-0 mt-0.5" + (m.channel === "AGENT" ? " ring-2 ring-violet-300" : "")}
                      style={{ background: m.channel === "AGENT" ? "#7c3aed" : m.mine ? "var(--accent)" : "var(--ink-faint)" }}
                    >
                      {m.channel === "AGENT" ? "✦" : (m.author || "?").slice(0, 1)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-xs text-ink-faint">
                        <span className={"font-medium " + (m.channel === "AGENT" ? "text-violet-600 dark:text-violet-400" : "text-ink-soft")}>
                          {m.author}
                        </span>
                        {m.channel === "AGENT" && (
                          <span
                            className="ml-1.5 inline-flex items-center rounded px-1 py-px text-[10px] font-medium bg-violet-100 text-violet-700 dark:bg-violet-500/20 dark:text-violet-300"
                            title="This reply was generated by the agent based on the contract record"
                          >
                            agent
                          </span>
                        )} · {timeAgo(m.createdAt)}
                        {m.editedAt && " · edited"}
                      </div>
                      <div
                        className={"rte-content text-sm mt-0.5" + (m.channel === "AGENT" ? " pl-2 border-l-2 border-violet-200 dark:border-violet-500/40" : "")}
                        dangerouslySetInnerHTML={{ __html: m.bodyHtml }}
                      />
                    </div>
                  </div>
                ))}
              </div>

              {agentFor === t.id ? (
                <div className="mt-3 ml-8 rounded-lg border border-violet-200 dark:border-violet-500/40 bg-violet-50 dark:bg-violet-500/10 p-2.5 space-y-2">
                  <div className="text-xs font-medium text-violet-700 dark:text-violet-300 flex items-center gap-1.5">
                    ✦ Ask a participant's agent
                    <span className="font-normal text-violet-500 dark:text-violet-400">
                      — they speak as that person; the two agents can go back and forth if needed
                    </span>
                  </div>
                  <select
                    className="input text-sm"
                    value={agentTarget}
                    onChange={(e) => setAgentTarget(e.target.value)}
                    aria-label="Who should answer"
                  >
                    <option value="">Auto — best placed to answer</option>
                    {otherAgents.map((a) => (
                      <option key={a.userId} value={a.userId}>
                        {a.name} ({a.roleHint})
                      </option>
                    ))}
                  </select>
                  <textarea
                    className="input text-sm"
                    rows={2}
                    placeholder="e.g. What is the notice period before expiry?"
                    value={agentQuestion}
                    onChange={(e) => setAgentQuestion(e.target.value)}
                  />
                  {askAgent.isError && (
                    <div className="text-xs text-red-600 dark:text-red-400">
                      {(askAgent.error as Error)?.message || "The agent could not answer."}
                    </div>
                  )}
                  <div className="flex gap-2 justify-end">
                    <button className="btn" onClick={() => setAgentFor(null)}>
                      Cancel
                    </button>
                    <button
                      className="btn btn-primary"
                      disabled={askAgent.isPending || !agentQuestion.trim() || t.status === "RESOLVED"}
                      onClick={() => askAgent.mutate({ threadId: t.id, question: agentQuestion, targetUserId: agentTarget || undefined })}
                    >
                      {askAgent.isPending ? "Agents are talking…" : "Ask"}
                    </button>
                  </div>
                </div>
              ) : replyFor === t.id ? (
                <div className="mt-3 pl-8 space-y-2">
                  <RichText key={replyKey} placeholder="Reply…" onChange={setReplyBody} minHeight={56} />
                  <div className="flex gap-2 justify-end">
                    <button className="btn" onClick={() => setReplyFor(null)}>
                      Cancel
                    </button>
                    <button
                      className="btn btn-primary"
                      disabled={reply.isPending || !replyBody.trim()}
                      onClick={() => reply.mutate(t.id)}
                    >
                      Reply
                    </button>
                  </div>
                </div>
              ) : (
                <div className="mt-2 ml-8 flex items-center gap-3">
                  <button
                    className="text-xs link"
                    onClick={() => {
                      setReplyFor(t.id);
                      setReplyBody("");
                      setReplyKey((k) => k + 1);
                    }}
                  >
                    Reply
                  </button>
                  {agentAsk && t.status !== "RESOLVED" && (
                    <button
                      className="text-xs font-medium text-violet-600 dark:text-violet-400 hover:underline"
                      onClick={() => {
                        askAgent.reset();
                        setAgentFor(t.id);
                        setAgentQuestion("");
                        setAgentTarget("");
                        setAgentKey((k) => k + 1);
                      }}
                    >
                      ✦ Ask agent
                    </button>
                  )}
                </div>
              )}
              </>
              )}
            </div>
          );
          })}
        </div>
      )}
    </div>
  );
}
