import { useRef, useState } from "react";
import { Icon } from "./icons";

export type PanelPos = { x: number; y: number; w: number; h: number };

const clamp = (lo: number, hi: number, v: number) => Math.max(lo, Math.min(hi, v));

type Opts = { defaultW?: number; defaultH?: number; defaultDockW?: number };

/**
 * State + gesture handlers for a panel that can be docked (a resizable-width column),
 * detached to a free-floating box (movable + corner-resizable), or closed.
 * `pos === null` → docked. Floating position/size and the docked width persist in localStorage.
 * Open/closed state is owned by the caller (kept next to the toggle button).
 */
export function useDockablePanel(storageKey: string, cardSelector: string, opts: Opts = {}) {
  const dW = opts.defaultW ?? 400;
  const dH = opts.defaultH ?? 560;
  const dDock = opts.defaultDockW ?? 380;

  const [pos, setPos] = useState<PanelPos | null>(() => {
    try {
      const s = JSON.parse(localStorage.getItem(storageKey) || "null");
      if (s && ["x", "y", "w", "h"].every((k) => typeof s[k] === "number")) return s as PanelPos;
    } catch { /* docked */ }
    return null;
  });
  const [dockW, setDockW] = useState<number>(() => {
    try {
      const n = Number(localStorage.getItem(storageKey + ".dw"));
      if (Number.isFinite(n) && n >= 260) return n;
    } catch { /* default */ }
    return dDock;
  });

  const posRef = useRef(pos);
  posRef.current = pos;

  const savePos = (p: PanelPos | null) => {
    try {
      if (p) localStorage.setItem(storageKey, JSON.stringify(p));
      else localStorage.removeItem(storageKey);
    } catch { /* ignore */ }
  };

  const dock = () => { setPos(null); savePos(null); };

  const drag = (e: React.MouseEvent, mode: "move" | "resize") => {
    if (mode === "move" && (e.target as HTMLElement).closest("button")) return;
    e.preventDefault();
    e.stopPropagation();
    const host = (e.currentTarget as HTMLElement).closest(cardSelector) as HTMLElement | null;
    const r = host?.getBoundingClientRect();
    const cur = posRef.current;
    const base: PanelPos = {
      x: cur?.x ?? r?.left ?? 48,
      y: cur?.y ?? r?.top ?? 48,
      w: cur?.w ?? Math.round(r?.width ?? dW),
      h: cur?.h ?? Math.round(r?.height ?? dH),
    };
    const s = { mx: e.clientX, my: e.clientY };
    let last: PanelPos = base;
    document.body.style.userSelect = "none";
    if (mode === "resize") document.body.style.cursor = "se-resize";
    const move = (ev: MouseEvent) => {
      last =
        mode === "move"
          ? {
              x: clamp(0, window.innerWidth - 120, base.x + ev.clientX - s.mx),
              y: clamp(8, window.innerHeight - 60, base.y + ev.clientY - s.my),
              w: base.w, h: base.h,
            }
          : {
              x: base.x, y: base.y,
              w: clamp(300, Math.max(320, window.innerWidth - base.x - 8), base.w + ev.clientX - s.mx),
              h: clamp(240, Math.max(260, window.innerHeight - base.y - 8), base.h + ev.clientY - s.my),
            };
      setPos(last);
    };
    const up = () => {
      document.body.style.userSelect = "";
      document.body.style.cursor = "";
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mouseup", up);
      setPos(last);
      savePos(last);
    };
    window.addEventListener("mousemove", move);
    window.addEventListener("mouseup", up);
  };

  const startDrag = (e: React.MouseEvent) => drag(e, "move");
  const startResize = (e: React.MouseEvent) => drag(e, "resize");

  // right-edge drag on the docked column
  const startDockResize = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const start = e.clientX;
    const w0 = dockW;
    let latest = w0;
    document.body.style.userSelect = "none";
    document.body.style.cursor = "col-resize";
    const move = (ev: MouseEvent) => {
      latest = clamp(280, Math.min(720, window.innerWidth * 0.6), w0 + ev.clientX - start);
      setDockW(latest);
    };
    const up = () => {
      document.body.style.userSelect = "";
      document.body.style.cursor = "";
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mouseup", up);
      try { localStorage.setItem(storageKey + ".dw", String(Math.round(latest))); } catch { /* ignore */ }
    };
    window.addEventListener("mousemove", move);
    window.addEventListener("mouseup", up);
  };

  return { pos, dockW, dock, startDrag, startResize, startDockResize };
}

/**
 * The visible chrome around a dockable panel's content: a draggable header strip with
 * Dock / close controls, plus resize affordances. One instance handles both modes —
 * pass `pos` (floating) or omit it (docked). `cardAttr` must match the `cardSelector`
 * given to {@link useDockablePanel} (e.g. cardAttr="data-x-card", selector="[data-x-card]").
 */
export function DockablePanel({
  title,
  icon,
  cardAttr,
  pos,
  dockW,
  headerExtra,
  onGrab,
  onDock,
  onClose,
  onResize,
  onDockResize,
  children,
}: {
  title: string;
  icon?: React.ReactNode;
  cardAttr: string;
  pos?: PanelPos | null;
  dockW?: number;
  headerExtra?: React.ReactNode;
  onGrab: (e: React.MouseEvent) => void;
  onDock?: () => void;
  onClose: () => void;
  onResize?: (e: React.MouseEvent) => void;
  onDockResize?: (e: React.MouseEvent) => void;
  children: React.ReactNode;
}) {
  const floating = !!pos;
  return (
    <div
      {...{ [cardAttr]: "" }}
      className={
        "flex flex-col rounded-xl border border-border bg-surface overflow-hidden " +
        (floating
          ? "fixed z-40 shadow-2xl max-w-[96vw]"
          : "relative w-full lg:w-[var(--dpw)] lg:shrink-0 lg:sticky lg:top-3 lg:self-start " +
            "h-[70vh] lg:h-[calc(100vh-1.5rem)] shadow-sm max-lg:mb-3")
      }
      style={
        floating
          ? { left: pos!.x, top: pos!.y, width: pos!.w, height: pos!.h }
          : ({ "--dpw": `${dockW ?? 380}px` } as React.CSSProperties)
      }
    >
      <div
        className="flex items-center justify-between gap-2 px-3 py-2 border-b border-border cursor-move select-none shrink-0"
        style={{ background: "var(--surface-2)" }}
        onMouseDown={onGrab}
        title="Drag to move / detach"
      >
        <span className="text-sm font-medium text-ink-soft uppercase tracking-wide inline-flex items-center gap-1.5 min-w-0">
          <span aria-hidden className="tracking-[0.15em] text-ink-faint leading-none">⠿</span>
          {icon}
          <span className="truncate">{title}</span>
        </span>
        <span className="flex items-center gap-1.5 shrink-0">
          {headerExtra}
          {floating && onDock && (
            <button className="text-xs link" title="Dock to the side" onClick={onDock}>
              Dock
            </button>
          )}
          <button className="btn !p-1 !border-0" title="Hide panel" onClick={onClose}>
            <Icon.x width={14} height={14} />
          </button>
        </span>
      </div>

      <div className="flex-1 min-h-0 overflow-hidden">{children}</div>

      {floating && onResize && (
        <div
          onMouseDown={onResize}
          title="Drag to resize"
          className="absolute right-0 bottom-0 w-5 h-5 cursor-se-resize"
        >
          <svg viewBox="0 0 10 10" width={10} height={10} className="absolute right-[3px] bottom-[3px] text-ink-faint">
            <path d="M9 1v8H1M9 5H5v4" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
          </svg>
        </div>
      )}
      {!floating && onDockResize && (
        <div
          onMouseDown={onDockResize}
          title="Drag to resize width"
          className="hidden lg:block absolute top-0 right-0 h-full w-1.5 cursor-col-resize hover:bg-[color:color-mix(in_srgb,var(--accent)_35%,transparent)]"
        />
      )}
    </div>
  );
}
