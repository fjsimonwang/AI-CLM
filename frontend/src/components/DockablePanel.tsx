import { useRef, useState } from "react";
import { Icon } from "./icons";

export type PanelPos = { x: number; y: number; w: number; h: number };
/** Which edge/corner a floating panel is being resized from. */
export type ResizeDir = "e" | "s" | "se" | "ne";

const clamp = (lo: number, hi: number, v: number) => Math.max(lo, Math.min(hi, v));

// One-time-per-browser-session "Drag here to move" hint on the panel header. The first
// DockablePanel rendered in a session shows it; the flash + bubble time out purely via CSS.
let dragHintClaimed = false;
function claimDragHint(): boolean {
  if (dragHintClaimed) return false;
  dragHintClaimed = true;
  try {
    if (sessionStorage.getItem("clm.dragHintSeen") === "1") return false;
    sessionStorage.setItem("clm.dragHintSeen", "1");
  } catch { /* private mode: still show once this load */ }
  return true;
}

type Opts = { defaultW?: number; defaultH?: number; defaultDockW?: number };

/**
 * State + gesture handlers for a panel that can be docked (a width-resizable column),
 * detached to a free-floating box (movable + edge/corner-resizable), or closed.
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

  const drag = (e: React.MouseEvent, mode: "move" | ResizeDir) => {
    if (mode === "move" && (e.target as HTMLElement).closest("button")) return;
    e.preventDefault();
    e.stopPropagation();
    const host = (e.currentTarget as HTMLElement).closest(cardSelector) as HTMLElement | null;
    const r = host?.getBoundingClientRect();
    const cur = posRef.current;
    // when detaching from the dock, cap the height/width so the box fits on screen
    const base: PanelPos = {
      x: cur?.x ?? r?.left ?? 48,
      y: cur?.y ?? r?.top ?? 48,
      w: cur?.w ?? clamp(300, window.innerWidth - 24, Math.round(r?.width ?? dW)),
      h: cur?.h ?? clamp(240, Math.round(window.innerHeight * 0.86), Math.round(r?.height ?? dH)),
    };
    const s = { mx: e.clientX, my: e.clientY };
    let last: PanelPos = base;
    document.body.style.userSelect = "none";
    document.body.style.cursor =
      mode === "move" ? "grabbing"
      : mode === "e" ? "ew-resize"
      : mode === "s" ? "ns-resize"
      : mode === "ne" ? "nesw-resize"
      : "nwse-resize";

    const move = (ev: MouseEvent) => {
      const dx = ev.clientX - s.mx;
      const dy = ev.clientY - s.my;
      if (mode === "move") {
        last = {
          x: clamp(0, Math.max(0, window.innerWidth - base.w), base.x + dx),
          y: clamp(8, Math.max(8, window.innerHeight - base.h - 8), base.y + dy),
          w: base.w, h: base.h,
        };
      } else {
        let { x, y, w, h } = base;
        const maxW = Math.max(320, window.innerWidth - base.x - 8);
        if (mode === "e" || mode === "se" || mode === "ne") w = clamp(300, maxW, base.w + dx);
        if (mode === "s" || mode === "se") h = clamp(240, Math.max(260, window.innerHeight - base.y - 8), base.h + dy);
        if (mode === "ne") {
          const bottom = base.y + base.h;
          h = clamp(240, Math.max(260, bottom - 8), base.h - dy);
          y = bottom - h;
        }
        last = { x, y, w, h };
      }
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
  const startResize = (dir: ResizeDir) => (e: React.MouseEvent) => drag(e, dir);

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
 * Dock / close controls, plus edge + corner resize handles when floating (both the
 * top-right and bottom-right corners resize; the top-right keeps the bottom edge pinned).
 * One instance handles both modes — pass `pos` (floating) or omit it (docked).
 * `cardAttr` must match the `cardSelector` given to {@link useDockablePanel}.
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
  onResize?: (dir: ResizeDir) => (e: React.MouseEvent) => void;
  onDockResize?: (e: React.MouseEvent) => void;
  children: React.ReactNode;
}) {
  const floating = !!pos;
  const grip = (
    <svg viewBox="0 0 10 10" width={9} height={9} className="text-ink-faint">
      <path d="M9 1v8H1M9 5H5v4" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );

  // one-time-per-session "Drag here to move" hint — claimed at first render, timed out by CSS
  const [hint] = useState(claimDragHint);

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
        className={
          "relative flex items-center justify-between gap-2 py-2 border-b border-border cursor-move select-none shrink-0 " +
          (floating ? "pl-3 pr-6" : "px-3") +
          (hint ? " dock-hint-flash" : "")
        }
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
        {hint && (
          <div
            className="dock-hint-bubble absolute left-2 top-full mt-1 z-[60] inline-flex items-center gap-1 rounded-md px-2 py-1 text-[11px] font-medium text-white shadow-lg pointer-events-none whitespace-nowrap"
            style={{ background: "var(--accent)" }}
          >
            <span aria-hidden className="tracking-[0.12em] leading-none">⠿</span>
            Drag here to move
          </div>
        )}
      </div>

      <div className="flex-1 min-h-0 overflow-hidden">{children}</div>

      {floating && onResize && (
        <>
          {/* right edge */}
          <div
            onMouseDown={onResize("e")}
            title="Drag to resize width"
            className="absolute top-8 bottom-4 right-0 w-1.5 cursor-ew-resize hover:bg-[color:color-mix(in_srgb,var(--accent)_35%,transparent)]"
          />
          {/* bottom edge */}
          <div
            onMouseDown={onResize("s")}
            title="Drag to resize height"
            className="absolute left-6 right-4 bottom-0 h-1.5 cursor-ns-resize hover:bg-[color:color-mix(in_srgb,var(--accent)_35%,transparent)]"
          />
          {/* bottom-right corner */}
          <div
            onMouseDown={onResize("se")}
            title="Drag to resize"
            className="absolute right-0 bottom-0 w-4 h-4 cursor-nwse-resize flex items-end justify-end p-[2px]"
          >
            {grip}
          </div>
          {/* top-right corner (keeps the bottom edge pinned) */}
          <div
            onMouseDown={onResize("ne")}
            title="Drag to resize"
            className="absolute right-0 top-0 z-[41] w-4 h-4 cursor-nesw-resize flex items-start justify-end p-[2px]"
          >
            <span className="rotate-90">{grip}</span>
          </div>
        </>
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
