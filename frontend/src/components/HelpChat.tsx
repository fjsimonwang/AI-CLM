import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { api, useAuth } from "../api";
import { Icon } from "./icons";

type Msg = { role: "user" | "assistant"; content: string };
type Dock = { side: "left" | "right"; frac: number };

const ICON = 52;
const EDGE = 16;
const PANEL_W = 368;
const PANEL_H = 448;
const HEADER_H = 62;
const GREETING =
  "Hi! I'm the CLM help assistant. Ask me how to use or navigate this system, who can support you, or how the contract lifecycle works. I can explain — but I won't make changes to your data.";

// The icon stores WHERE it's docked (edge + vertical fraction of the viewport),
// never absolute pixels — so it stays glued to the edge when the window resizes.
function clampFrac(f: number) {
  return Math.min(0.98, Math.max(0.02, f));
}

function loadDock(): Dock {
  try {
    const raw = localStorage.getItem("clm-help-dock");
    if (raw) {
      const d = JSON.parse(raw);
      if ((d?.side === "left" || d?.side === "right") && typeof d?.frac === "number")
        return { side: d.side, frac: clampFrac(d.frac) };
    }
    const old = localStorage.getItem("clm-help-pos"); // migrate the pre-dock format
    if (old) {
      const p = JSON.parse(old);
      if (typeof p?.left === "number") {
        localStorage.removeItem("clm-help-pos");
        const dock: Dock = { side: p.left < window.innerWidth / 2 ? "left" : "right", frac: clampFrac(p.top / Math.max(1, window.innerHeight)) };
        localStorage.setItem("clm-help-dock", JSON.stringify(dock));
        return dock;
      }
    }
  } catch {
    /* ignore */
  }
  return { side: "right", frac: 0.75 };
}

function clampPos(p: { left: number; top: number }) {
  return {
    left: Math.min(Math.max(p.left, EDGE), window.innerWidth - ICON - EDGE),
    top: Math.min(Math.max(p.top, EDGE), window.innerHeight - ICON - EDGE),
  };
}

/** Absolute position of the docked icon for the current viewport size. */
function dockPos(d: Dock) {
  return {
    left: d.side === "left" ? EDGE : window.innerWidth - ICON - EDGE,
    top: Math.min(Math.max(d.frac * window.innerHeight, EDGE), window.innerHeight - ICON - EDGE),
  };
}

function loadPanelPos(): { left: number; top: number } | null {
  try {
    const raw = localStorage.getItem("clm-help-panel-pos");
    if (raw) {
      const p = JSON.parse(raw);
      if (typeof p?.left === "number" && typeof p?.top === "number")
        return { left: p.left, top: p.top };
    }
  } catch {
    /* ignore */
  }
  return null; // null = anchored above the icon
}

// ---------------------------------------------------------------------------
// Highlightable UI components. The assistant's replies may contain
// [[hl:KEY]] markers; the frontend turns each into an on-page outline.
// ---------------------------------------------------------------------------
const HIGHLIGHT_TARGETS: Record<string, () => Element | null> = {
  nav_dashboard: () => document.querySelector('aside a[href="/"]'),
  nav_new_request: () => document.querySelector('aside a[href="/intake"]'),
  nav_contracts: () => document.querySelector('aside a[href="/contracts"]'),
  nav_approvals: () => document.querySelector('aside a[href="/approvals"]'),
  nav_obligations: () => document.querySelector('aside a[href="/obligations"]'),
  nav_access: () => document.querySelector('aside a[href="/access"]'),
  nav_clauses: () => document.querySelector('aside a[href="/clauses"]'),
  nav_templates: () => document.querySelector('aside a[href="/templates"]'),
  nav_ai_log: () => document.querySelector('aside a[href="/ai-log"]'),
  nav_admin: () =>
    [...document.querySelectorAll("aside button")].find((b) => b.textContent?.includes("Administration")) ?? null,
  theme_selector: () => document.querySelector("header select"),
  sign_out: () =>
    [...document.querySelectorAll("header button")].find((b) => b.textContent?.includes("Sign out")) ?? null,
};

/** "Mention text [[hl:key]]" → segments for rendering + highlighting. */
function parseSegments(text: string): { hl: boolean; v: string }[] {
  return text
    .split(/(\[\[hl:[a-z_0-9]+\]\])/g)
    .filter((p) => p !== "")
    .map((p) => ({ hl: /^\[\[hl:([a-z_0-9]+)\]\]$/.test(p), v: p }));
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------
const SUGGESTIONS = [
  "How do I start a new contract request?",
  "Walk me through the approval flow",
  "Who do I contact for access problems?",
  "What happens when a contract is up for renewal?",
];

/**
 * Compact description of what's actually on the user's screen right now,
 * sent with every question so the assistant can ground its guidance in the
 * current page (sections, visible controls, the user's own permissions).
 */
function collectPageContext(pathname: string, search: string): string {
  const lines: string[] = [];
  lines.push(`route: ${pathname}${search || ""}`);
  const main = document.querySelector("main");
  const sectionTitles = [...(main?.querySelectorAll("h1, h2, h3") ?? [])]
    .map((h) => h.textContent?.trim().replace(/\s+/g, " "))
    .filter((t): t is string => !!t && t.length < 60)
    .slice(0, 12);
  if (sectionTitles.length) lines.push(`sections: ${[...new Set(sectionTitles)].join(" | ")}`);
  const controls = [...(main?.querySelectorAll("button, [role=tab]") ?? [])]
    .filter((b) => (b as HTMLElement).offsetParent !== null)
    .map((b) => b.textContent?.trim().replace(/\s+/g, " "))
    .filter((t): t is string => !!t && t.length > 0 && t.length < 40);
  const uniq = [...new Set(controls)].slice(0, 35);
  if (uniq.length) lines.push(`visible controls: ${uniq.join(" | ")}`);
  const auth = useAuth.getState();
  if (auth.user) {
    lines.push(`user roles: ${auth.user.roles.join(", ")}`);
    lines.push(`user permissions: ${auth.user.permissions.join(", ")}`);
  }
  return lines.join("\n");
}

/** Minimal markdown (**bold**, `code`) + [[hl:key]] locator buttons (React escapes the rest). */
function renderReply(text: string, onLocate: (key: string) => void) {
  const out: React.ReactNode[] = [];
  for (const seg of parseSegments(text)) {
    const raw = seg.v;
    if (seg.hl) {
      const key = raw.match(/^\[\[hl:([a-z_0-9]+)\]\]$/)?.[1] ?? "";
      out.push(
        <button
          key={`hl-${out.length}-${key}`}
          className="inline-flex items-center align-middle mx-0.5 px-1 py-0.5 rounded-[6px] border border-border bg-surface text-ink-soft hover:text-[color:var(--accent)] hover:border-[color:var(--accent)] transition-colors"
          title="Show this on the page"
          aria-label={`Show on page: ${key}`}
          onClick={() => onLocate(key)}
        >
          <Icon.eye width={12} height={12} />
        </button>
      );
      continue;
    }
    const parts = raw.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
    for (const p of parts) {
      if (!p) continue;
      if (p.startsWith("**") && p.endsWith("**")) out.push(<strong key={out.length}>{p.slice(2, -2)}</strong>);
      else if (p.startsWith("`") && p.endsWith("`"))
        out.push(
          <code key={out.length} className="px-1 py-0.5 rounded border border-border bg-surface text-[12px]">
            {p.slice(1, -1)}
          </code>
        );
      else out.push(<React.Fragment key={out.length}>{p}</React.Fragment>);
    }
  }
  return out;
}

type Turn = { reply: string; interactionId: string; modelLive: boolean };

// Page-aware "Ask me: …" nudges shown in a bubble beside the collapsed help icon.
// Longest matching route prefix wins.
const PAGE_PROMPTS: { prefix: string; qs: string[] }[] = [
  { prefix: "/intake", qs: [
    "How do I answer the intake questions faster?",
    "How do I upload a counterparty's paper contract?",
    "What does 'Generate Document Draft' do?",
    "How do I resume a saved draft request?",
  ] },
  { prefix: "/contracts/", qs: [
    "How do I ask a participant's agent a question here?",
    "How do I add someone to this contract?",
    "Where do I see why this contract was rejected?",
    "How do I detect related contracts with AI?",
  ] },
  { prefix: "/contracts", qs: [
    "How do I add a column to the contract table?",
    "How do I ask the portfolio a question?",
    "How do I save an inquiry as a dashboard chart?",
  ] },
  { prefix: "/approvals", qs: [
    "What does the AI approver briefing tell me?",
    "How do I reject a contract with a reason?",
    "Why can't I see the approve/reject buttons?",
  ] },
  { prefix: "/auto-reject", qs: [
    "How do I create an auto-rejection rule?",
    "What does 'Structure with AI' do to my rule?",
    "How do I dry-run a rule before it goes live?",
  ] },
  { prefix: "/obligations", qs: [
    "What does 'Verify' do to an obligation?",
    "How do I find obligations that need my review?",
  ] },
  { prefix: "/access", qs: [
    "How do I request access to more contracts?",
    "How do I approve someone's access request?",
  ] },
  { prefix: "/clauses", qs: [
    "How do I check a counterparty clause against the playbook?",
    "What do the clause tier badges mean?",
  ] },
  { prefix: "/templates", qs: ["How do I preview a template?", "Who can edit templates?"] },
  { prefix: "/ai-log", qs: ["How do I undo something the AI applied?", "What do the outcome badges mean?"] },
  { prefix: "/admin", qs: [
    "How do I upload a policy document for the help assistant?",
    "How do I scope a policy to certain roles or regions?",
    "How do I add a new contract type?",
  ] },
  { prefix: "/", qs: [
    "How can I add a new dashboard section?",
    "How do I rearrange or resize dashboard sections?",
    "What does the AI insight section show me?",
    "How do I hide a section I don't use?",
  ] },
];

function pickPagePrompt(path: string): string {
  const match = PAGE_PROMPTS
    .filter((p) => (p.prefix === "/" ? path === "/" : path.startsWith(p.prefix)))
    .sort((a, b) => b.prefix.length - a.prefix.length)[0];
  const qs = match?.qs ?? PAGE_PROMPTS[PAGE_PROMPTS.length - 1].qs;
  return qs[Math.floor(Math.random() * qs.length)];
}

export function HelpChat() {
  const loc = useLocation();
  const [dock, setDock] = useState<Dock>(loadDock);
  const [dragPos, setDragPos] = useState<{ left: number; top: number } | null>(null);
  const [, bumpViewport] = useState(0); // recompute docked position on resize
  const [open, setOpen] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [panelDragging, setPanelDragging] = useState(false);
  const [panelPos, setPanelPos] = useState<{ left: number; top: number } | null>(loadPanelPos);
  const [msgs, setMsgs] = useState<Msg[]>([{ role: "assistant", content: GREETING }]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [hlRing, setHlRing] = useState<{ left: number; top: number; width: number; height: number; tick: number } | null>(
    null
  );
  const [bubbleQ, setBubbleQ] = useState<string | null>(null);
  const [bubbleHidden, setBubbleHidden] = useState(false);
  const dragRef = useRef<{ sx: number; sy: number; ox: number; oy: number; moved: boolean } | null>(null);
  const panelDragRef = useRef<{ sx: number; sy: number; ox: number; oy: number; moved: boolean } | null>(null);
  const draggedRef = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // while dragging: free pixel position; otherwise derive from the dock state,
  // which is edge + vertical fraction — so it sticks to the edge on any resize
  const pos = dragPos ?? dockPos(dock);

  // keep the icon docked + a pinned panel on-screen when the window resizes
  useLayoutEffect(() => {
    const onResize = () => {
      bumpViewport((v) => v + 1);
      setPanelPos((pp) => {
        if (!pp) return pp;
        const w = Math.min(PANEL_W, window.innerWidth - 2 * EDGE);
        const h = Math.min(PANEL_H, window.innerHeight - 100);
        return {
          left: Math.min(Math.max(pp.left, EDGE), window.innerWidth - w - EDGE),
          top: Math.min(Math.max(pp.top, HEADER_H), Math.max(HEADER_H, window.innerHeight - h - EDGE)),
        };
      });
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [msgs, sending, open]);

  // A fresh page-specific "Ask me: …" nudge whenever the route changes (and the chat is closed).
  useEffect(() => {
    if (open) return;
    setBubbleHidden(false);
    setBubbleQ(pickPagePrompt(loc.pathname));
  }, [loc.pathname]); // eslint-disable-line react-hooks/exhaustive-deps

  // ---------------- icon drag ----------------
  const onPointerDown = (e: React.PointerEvent) => {
    e.preventDefault();
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    dragRef.current = { sx: e.clientX, sy: e.clientY, ox: pos.left, oy: pos.top, moved: false };
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const d = dragRef.current;
    if (!d) return;
    const dx = e.clientX - d.sx;
    const dy = e.clientY - d.sy;
    if (!d.moved && Math.abs(dx) + Math.abs(dy) > 4) {
      d.moved = true;
      setDragging(true);
    }
    if (d.moved) setDragPos(clampPos({ left: d.ox + dx, top: d.oy + dy }));
  };

  const onPointerUp = (e: React.PointerEvent) => {
    const d = dragRef.current;
    dragRef.current = null;
    setDragging(false);
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
    if (d?.moved) {
      draggedRef.current = true;
      // re-dock: snap to the nearest vertical edge, remember the vertical fraction
      const side: Dock["side"] = pos.left < window.innerWidth / 2 - ICON / 2 ? "left" : "right";
      const next: Dock = { side, frac: clampFrac(pos.top / Math.max(1, window.innerHeight)) };
      setDock(next);
      setDragPos(null);
      localStorage.setItem("clm-help-dock", JSON.stringify(next));
    }
  };

  // ---------------- panel drag (via its header) ----------------
  const panelDragStart = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest("button")) return; // header controls
    if (!open) return;
    e.preventDefault();
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    const r = panelRect(panelPos);
    panelDragRef.current = { sx: e.clientX, sy: e.clientY, ox: r.left, oy: r.top, moved: false };
  };

  const panelDragMove = (e: React.PointerEvent) => {
    const d = panelDragRef.current;
    if (!d) return;
    const dx = e.clientX - d.sx;
    const dy = e.clientY - d.sy;
    if (!d.moved && Math.abs(dx) + Math.abs(dy) > 4) {
      d.moved = true;
      setPanelDragging(true);
    }
    if (d.moved)
      setPanelPos(clampPanelRect({ left: d.ox + dx, top: d.oy + dy }));
  };

  const panelDragEnd = (e: React.PointerEvent) => {
    const d = panelDragRef.current;
    panelDragRef.current = null;
    setPanelDragging(false);
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
    if (d?.moved) setPanelPos((pp) => {
      const r = clampPanelRect({ left: d.ox + (e.clientX - d.sx), top: d.oy + (e.clientY - d.sy) });
      localStorage.setItem("clm-help-panel-pos", JSON.stringify(r));
      return r;
    });
  };

  const clampPanelRect = (r: { left: number; top: number }) => {
    const w = Math.min(PANEL_W, window.innerWidth - 2 * EDGE);
    const h = Math.min(PANEL_H, window.innerHeight - 100);
    return {
      left: Math.min(Math.max(r.left, EDGE), window.innerWidth - w - EDGE),
      top: Math.min(Math.max(r.top, HEADER_H), Math.max(HEADER_H, window.innerHeight - h - EDGE)),
    };
  };

  const panelRect = (explicit: { left: number; top: number } | null) => {
    const w = Math.min(PANEL_W, window.innerWidth - 2 * EDGE);
    const h = Math.min(PANEL_H, window.innerHeight - 100);
    if (explicit) return { left: explicit.left, top: explicit.top, w, h };
    // anchor above the icon, on the same side
    const onLeft = pos.left < window.innerWidth / 2;
    return {
      left: onLeft ? EDGE : window.innerWidth - w - EDGE,
      top: Math.max(HEADER_H, Math.min(pos.top - h - 12, window.innerHeight - h - EDGE)),
      w,
      h,
    };
  };

  // ---------------- highlight ----------------
  const hlTimers = useRef<{ scroll?: number; hide?: number }>({});
  const highlight = (key: string) => {
    const el = HIGHLIGHT_TARGETS[key]?.();
    if (!el) return;
    const tick = Date.now();
    window.clearTimeout(hlTimers.current.scroll);
    window.clearTimeout(hlTimers.current.hide);
    el.scrollIntoView({ block: "center", behavior: "smooth" });
    hlTimers.current.scroll = window.setTimeout(() => {
      const r = el.getBoundingClientRect();
      const visible =
        (r.width > 0 || r.height > 0) &&
        r.right > 0 &&
        r.bottom > 0 &&
        r.left < window.innerWidth &&
        r.top < window.innerHeight;
      if (!visible) return;
      setHlRing({ left: r.left, top: r.top, width: r.width, height: r.height, tick });
      hlTimers.current.hide = window.setTimeout(() => setHlRing((c) => (c?.tick === tick ? null : c)), 4000);
    }, 400);
  };

  // ---------------- chat actions ----------------
  const send = async (text: string) => {
    const q = text.trim();
    if (!q || sending) return;
    const next: Msg[] = [...msgs, { role: "user", content: q }];
    setMsgs(next);
    setInput("");
    setSending(true);
    try {
      const r = await api<Turn>("/ai/help", {
        method: "POST",
        json: { message: q, page: loc.pathname + loc.search, pageContext: collectPageContext(loc.pathname, loc.search), history: msgs },
      });
      setMsgs((m) => [...m, { role: "assistant", content: r.reply }]);
      const firstKey = parseSegments(r.reply).find((s) => s.hl)?.v.match(/^\[\[hl:([a-z_0-9]+)\]\]$/)?.[1];
      if (firstKey) hlTimers.current.hide = window.setTimeout(() => highlight(firstKey), 600);
    } catch {
      setMsgs((m) => [
        ...m,
        { role: "assistant", content: "Sorry — the help assistant is unavailable right now. Please try again shortly." },
      ]);
    } finally {
      setSending(false);
    }
  };

  const clearChat = () => {
    setMsgs([{ role: "assistant", content: GREETING }]);
    setInput("");
    setHlRing(null);
  };

  // toggle on click so keyboard activation works; skip when the click
  // was actually the end of a drag
  const onClick = () => {
    if (draggedRef.current) {
      draggedRef.current = false;
      return;
    }
    setOpen((o) => !o);
  };

  return (
    <>
      {open && (() => {
        const r = panelRect(panelPos);
        return (
          <div
            className={`fixed z-[60] card flex flex-col fade-in ${panelDragging ? "cursor-move" : ""}`}
            style={{
              left: r.left,
              top: r.top,
              width: r.w,
              height: r.h,
              boxShadow: "var(--sh-3)",
              overflow: "hidden",
              userSelect: panelDragging ? "none" : undefined,
            }}
          >
            <div
              className={`shrink-0 flex items-center gap-2 px-3 py-2.5 border-b border-border ${
                panelDragging ? "cursor-move" : "cursor-grab"
              }`}
              style={{ background: "var(--accent-soft)", touchAction: "none" }}
              onPointerDown={panelDragStart}
              onPointerMove={panelDragMove}
              onPointerUp={panelDragEnd}
              title="Drag to move"
            >
              <span className="w-7 h-7 rounded-full grid place-items-center text-white shrink-0" style={{ background: "var(--accent)" }}>
                <Icon.message width={15} height={15} />
              </span>
              <div className="flex-1 leading-tight min-w-0">
                <div className="text-sm font-medium text-ink">CLM Help</div>
                <div className="text-[11px] text-ink-faint">How to use the platform — I can't edit records</div>
              </div>
              <button
                className="w-7 h-7 grid place-items-center rounded-[8px] text-ink-faint hover:bg-surface-2 hover:text-ink"
                onClick={clearChat}
                disabled={sending}
                title="Clear conversation"
                aria-label="Clear conversation"
              >
                <Icon.trash width={15} height={15} />
              </button>
              <button
                className="w-7 h-7 grid place-items-center rounded-[8px] text-ink-faint hover:bg-surface-2 hover:text-ink"
                onClick={() => setOpen(false)}
                aria-label="Close help chat"
              >
                <Icon.x width={15} height={15} />
              </button>
            </div>

            <div ref={scrollRef} className="flex-1 overflow-y-auto px-3 py-3 space-y-2.5">
              {msgs.map((m, i) => (
                <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
                  <div
                    className={`max-w-[85%] rounded-[12px] px-3 py-2 text-[13px] leading-relaxed whitespace-pre-wrap ${
                      m.role === "user" ? "text-white" : "bg-surface-2 text-ink"
                    }`}
                    style={m.role === "user" ? { background: "var(--accent)", color: "#fff" } : undefined}
                  >
                    {m.role === "assistant" ? renderReply(m.content, highlight) : m.content}
                  </div>
                </div>
              ))}
              {sending && (
                <div className="flex justify-start">
                  <div className="rounded-[12px] px-3 py-2 bg-surface-2 text-ink-faint text-[13px]">Thinking…</div>
                </div>
              )}
              {msgs.length === 1 && !sending && (
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {SUGGESTIONS.map((s) => (
                    <button
                      key={s}
                      className="text-[12px] px-2.5 py-1.5 rounded-full border border-border text-ink-soft hover:bg-accent-soft hover:text-[color:var(--accent)] transition-colors text-left"
                      onClick={() => send(s)}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="shrink-0 border-t border-border p-2 flex items-end gap-2">
              <textarea
                className="input flex-1 resize-none"
                rows={1}
                placeholder="Ask about the system…"
                value={input}
                disabled={sending}
                style={{ maxHeight: 88 }}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    send(input);
                  }
                }}
              />
              <button
                className="btn shrink-0"
                style={{ padding: "0.45rem 0.6rem", background: "var(--accent)", color: "#fff", borderColor: "var(--accent)" }}
                disabled={sending || !input.trim()}
                onClick={() => send(input)}
                aria-label="Send"
              >
                <Icon.send width={15} height={15} />
              </button>
            </div>
          </div>
        );
      })()}

      {hlRing && (
        <div
          className="help-hl-ring"
          style={{
            left: hlRing.left,
            top: hlRing.top,
            width: hlRing.width,
            height: hlRing.height,
          }}
          key={hlRing.tick}
        />
      )}

      {!open && !dragging && !bubbleHidden && bubbleQ && (
        <div
          className="fixed z-[59] max-w-[240px] rounded-xl px-3 py-2 text-xs leading-snug shadow-lg pop-in"
          style={{
            top: Math.min(Math.max(pos.top + ICON / 2 - 22, EDGE), window.innerHeight - 80),
            ...(dock.side === "right"
              ? { right: window.innerWidth - pos.left + 10 }
              : { left: pos.left + ICON + 10 }),
            background: "var(--surface)",
            border: "1px solid color-mix(in srgb, var(--accent) 35%, var(--border))",
          }}
          role="button"
          tabIndex={0}
          onClick={() => { setOpen(true); send(bubbleQ); setBubbleHidden(true); }}
          onKeyDown={(e) => { if (e.key === "Enter") { setOpen(true); send(bubbleQ); setBubbleHidden(true); } }}
          title="Ask the help assistant this"
        >
          <button
            className="absolute -top-1.5 -right-1.5 w-4 h-4 grid place-items-center rounded-full text-[10px]"
            style={{ background: "var(--surface-2)", border: "1px solid var(--border)" }}
            onClick={(e) => { e.stopPropagation(); setBubbleHidden(true); }}
            aria-label="Dismiss"
          >
            ✕
          </button>
          <span className="text-ink-faint">Ask me: </span>
          <span className="text-ink font-medium">{bubbleQ}</span>
        </div>
      )}

      <button
        className={`fixed z-[60] rounded-full grid place-items-center text-white ${dragging ? "cursor-grabbing" : "cursor-grab"}`}
        style={{
          width: ICON,
          height: ICON,
          left: pos.left,
          top: pos.top,
          background: "var(--accent)",
          boxShadow: "var(--sh-3)",
          touchAction: "none",
          userSelect: "none",
          transition: dragging ? "none" : "left 200ms cubic-bezier(0.22, 1, 0.36, 1), top 120ms, transform 150ms",
        }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onClick={onClick}
        onPointerCancel={() => {
          dragRef.current = null;
          setDragPos(null);
          setDragging(false);
        }}
        title="Help & how-to chat"
        aria-label="Open help chat"
      >
        <Icon.message width={22} height={22} />
      </button>
    </>
  );
}