import { useCallback, useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, useAuth } from "../api";
import { Spinner, Empty, Badge } from "./ui";
import { Icon } from "./icons";
import { quoteCandidates, useHighlight } from "./highlight";
import { useDocEdited } from "./docEdited";

/** Single-call postMessage RPC into the embedded word editor. */
function editorCall(iframe: HTMLIFrameElement, cmd: string, args?: any): Promise<any> {
  return new Promise((resolve) => {
    const id = Math.random().toString(36).slice(2);
    const onMsg = (ev: MessageEvent) => {
      const d: any = ev.data;
      if (d && d.we === 1 && d.re === id) {
        window.removeEventListener("message", onMsg);
        resolve(d.result);
      }
    };
    window.addEventListener("message", onMsg);
    try {
      iframe.contentWindow?.postMessage({ we: 1, id, cmd, args }, "*");
    } catch {}
    setTimeout(() => {
      window.removeEventListener("message", onMsg);
      resolve(null);
    }, 1000);
  });
}

export function DocumentPanel({ contractId, fullPage }: { contractId: string; fullPage?: boolean }) {
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const [mode, setMode] = useState<"view" | "edit">("view");
  const key = ["document", contractId];
  const q = useQuery({ queryKey: key, queryFn: () => api(`/contracts/${contractId}/document`) });

  const assemble = useMutation({
    mutationFn: () => api(`/contracts/${contractId}/document/assemble`, { method: "POST" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: key });
      qc.invalidateQueries({ queryKey: ["contract", contractId] });
    },
  });

  const iframeRef = useRef<HTMLIFrameElement>(null);
  const target = useHighlight((s) => s.target);
  const clearTarget = useHighlight((s) => s.clear);
  const [matchCount, setMatchCount] = useState(0);
  const [instance, setInstance] = useState(0);
  const [notFound, setNotFound] = useState(false);

  const applyHighlight = useCallback(async (quote: string, index: number) => {
    const iframe = iframeRef.current;
    if (!iframe) return false;
    for (const cand of quoteCandidates(quote)) {
      const res = await editorCall(iframe, "highlight", { query: cand });
      const n = res && Number(res.matches) > 0 ? Number(res.matches) : 0;
      if (n > 0) {
        setMatchCount(n);
        setInstance(Math.min(index, n - 1));
        await editorCall(iframe, "gotoHighlight", { index: Math.min(index, n - 1) });
        return true;
      }
    }
    setMatchCount(0);
    return false;
  }, []);

  const targetQuote = target?.quote;
  useEffect(() => {
    if (!targetQuote) {
      setMatchCount(0);
      setInstance(0);
      setNotFound(false);
      return;
    }
    let cancelled = false;
    // retries cover the editor loading its document after a (re)mount
    const tryApply = (attempt: number): void => {
      setTimeout(() => {
        if (cancelled) return;
        applyHighlight(targetQuote, 0).then((ok) => {
          if (cancelled) return;
          if (ok) setNotFound(false);
          else if (attempt < 2) tryApply(attempt + 1);
          else setNotFound(true);
        });
      }, attempt === 0 ? 250 : 900);
    };
    tryApply(0);
    return () => {
      cancelled = true;
    };
  }, [targetQuote, mode, q.data, applyHighlight]);

  const gotoMatch = (i: number) => {
    setInstance(i);
    if (iframeRef.current) editorCall(iframeRef.current, "gotoHighlight", { index: i });
  };

  const clearHighlight = () => {
    clearTarget();
    setMatchCount(0);
    setInstance(0);
    setNotFound(false);
    if (iframeRef.current) editorCall(iframeRef.current, "clearHighlight");
  };

  // the editor emits a "save" event on every autosave — debounce, pull the content
  // into the contract version, and signal the edit so the AI review re-runs
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    const onMsg = (ev: MessageEvent) => {
      const data: any = ev.data;
      if (!data || data.we !== 1 || data.event !== "save") return;
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(() => {
        api(`/contracts/${contractId}/document/sync`, { method: "POST" })
          .then((r: any) => {
            if (r?.changed) {
              useDocEdited.getState().bump();
              qc.invalidateQueries({ queryKey: ["document", contractId] });
              qc.invalidateQueries({ queryKey: ["contract", contractId] });
            }
          })
          .catch(() => {});
      }, 2500);
    };
    window.addEventListener("message", onMsg);
    return () => {
      window.removeEventListener("message", onMsg);
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, [contractId, qc]);

  if (q.isLoading) return <Spinner label="Loading document…" />;
  const d: any = q.data || {};
  const effectiveMode = d.canEdit ? mode : "view";
  const showMatches = !!targetQuote && !!d.enabled;

  if (!d.enabled) {
    return (
      <div className="card p-4">
        <Empty>
          The document editor service isn't running. Start it with <code>docker compose up -d word-editor</code>,
          or view the assembled text below.
        </Empty>
        {d.bodyText && (
          <pre className="mt-3 text-xs whitespace-pre-wrap font-serif bg-surface-2 p-3 rounded-[8px] max-h-[480px] overflow-y-auto">
            {d.bodyText}
          </pre>
        )}
      </div>
    );
  }

  if (!d.editorDocId) {
    return (
      <div className="card p-6 text-center">
        <p className="text-sm text-ink-faint mb-3">No document assembled yet.</p>
        <button className="btn btn-primary" disabled={assemble.isPending} onClick={() => assemble.mutate()}>
          <Icon.sparkle width={15} height={15} />
          {assemble.isPending ? "Assembling…" : "Assemble from template"}
        </button>
        {assemble.error && (
          <p className="text-xs mt-3" style={{ color: "var(--risk)" }}>
            {(assemble.error as Error).message || "Could not assemble the document."}
          </p>
        )}
      </div>
    );
  }

  const src =
    `${d.editorBaseUrl}/?embed=1&doc=${encodeURIComponent(d.editorDocId)}` +
    `&mode=${effectiveMode}&toolbar=1&statusbar=1` +
    `&user=${encodeURIComponent((user?.displayName || "User").split(" (")[0])}` +
    (d.token ? `&token=${encodeURIComponent(d.token)}` : "");

  return (
    <div className={`space-y-2 lg:flex lg:flex-col ${fullPage ? "flex flex-col flex-1 min-h-0" : "lg:h-full"}`}>
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="text-xs text-ink-faint">{d.changeSummary}</div>
        <div className="flex items-center gap-2">
          {showMatches && (
            <div className="flex items-center gap-1.5 text-xs rounded-[8px] border border-border bg-surface-2 px-2 py-1">
              <Icon.search width={13} height={13} className="text-ink-faint shrink-0" />
              {matchCount > 0 ? (
                <>
                  <span className="text-ink-faint whitespace-nowrap">
                    {matchCount} match{matchCount > 1 ? "es" : ""}
                  </span>
                  {matchCount > 1 && (
                    <span className="flex items-center gap-0.5">
                      {Array.from({ length: matchCount }, (_, i) => (
                        <button
                          key={i}
                          title={`Go to match ${i + 1}`}
                          onClick={() => gotoMatch(i)}
                          className={`w-5 h-5 rounded-[5px] text-[11px] transition-colors ${
                            instance === i ? "bg-[color:var(--ai)] text-white" : "text-ink-soft hover:bg-black/5 dark:hover:bg-white/10"
                          }`}
                        >
                          {i + 1}
                        </button>
                      ))}
                    </span>
                  )}
                </>
              ) : notFound ? (
                <span className="text-ink-faint italic whitespace-nowrap">quote not found in document</span>
              ) : (
                <span className="text-ink-faint italic whitespace-nowrap">locating…</span>
              )}
              <button
                title="Clear highlight"
                onClick={clearHighlight}
                className="text-ink-faint hover:text-ink-soft shrink-0"
              >
                <Icon.x width={13} height={13} />
              </button>
            </div>
          )}
          {d.canEdit && (
            <div className="flex rounded-[8px] border border-border overflow-hidden">
              {(["view", "edit"] as const).map((m) => (
                <button
                  key={m}
                  className={`px-3 py-1.5 text-xs ${
                    effectiveMode === m ? "bg-accent text-white" : "text-ink-soft hover:bg-surface-2"
                  }`}
                  onClick={() => setMode(m)}
                >
                  {m === "view" ? "Read" : "Edit"}
                </button>
              ))}
            </div>
          )}
          {!d.canEdit && <Badge tone="neutral">read-only</Badge>}
          {d.source !== "MIGRATED" && (
            <button className="btn" style={{ padding: "0.35rem 0.6rem" }} disabled={assemble.isPending} onClick={() => assemble.mutate()}>
              <Icon.refresh width={14} height={14} />
              Re-assemble
            </button>
          )}
          <a className="btn" style={{ padding: "0.35rem 0.6rem" }} href={src} target="_blank" rel="noreferrer">
            <Icon.externalLink width={14} height={14} />
            Open full
          </a>
        </div>
      </div>
      <div
        className={
          fullPage
            ? "card overflow-hidden flex-1 min-h-0"
            : "card overflow-hidden h-[70vh] lg:h-auto lg:flex-1 lg:min-h-0"
        }
      >
        <iframe
          key={effectiveMode}
          ref={iframeRef}
          title="Contract document"
          src={src}
          className="w-full h-full border-0"
        />
      </div>
    </div>
  );
}
