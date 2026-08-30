import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api";
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

export function CommentThreads({ entityType, entityId }: { entityType: string; entityId: string }) {
  const qc = useQueryClient();
  const key = ["comments", entityType, entityId];
  const q = useQuery({ queryKey: key, queryFn: () => api(`/comments/${entityType}/${entityId}`) });

  const [newBody, setNewBody] = useState("");
  const [newTitle, setNewTitle] = useState("");
  const [showNew, setShowNew] = useState(false);
  const [composerKey, setComposerKey] = useState(0);
  const [replyFor, setReplyFor] = useState<string | null>(null);
  const [replyBody, setReplyBody] = useState("");
  const [replyKey, setReplyKey] = useState(0);

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
          {threads.map((t: any) => (
            <div key={t.id} className="card p-3.5 lift">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-sm">{t.title}</span>
                    {t.status === "RESOLVED" ? (
                      <Badge tone="ok">resolved</Badge>
                    ) : (
                      <Badge tone="warn">open</Badge>
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
                  {t.status === "RESOLVED" ? "Reopen" : "Resolve"}
                </button>
              </div>

              <div className="mt-3 space-y-3">
                {t.messages.map((m: any) => (
                  <div key={m.id} className="flex gap-2.5">
                    <div
                      className="w-6 h-6 rounded-full grid place-items-center text-[10px] font-medium text-white shrink-0 mt-0.5"
                      style={{ background: m.mine ? "var(--accent)" : "var(--ink-faint)" }}
                    >
                      {(m.author || "?").slice(0, 1)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-xs text-ink-faint">
                        <span className="text-ink-soft font-medium">{m.author}</span> · {timeAgo(m.createdAt)}
                        {m.editedAt && " · edited"}
                      </div>
                      <div
                        className="rte-content text-sm mt-0.5"
                        dangerouslySetInnerHTML={{ __html: m.bodyHtml }}
                      />
                    </div>
                  </div>
                ))}
              </div>

              {replyFor === t.id ? (
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
                <button
                  className="mt-2 ml-8 text-xs link"
                  onClick={() => {
                    setReplyFor(t.id);
                    setReplyBody("");
                    setReplyKey((k) => k + 1);
                  }}
                >
                  Reply
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
