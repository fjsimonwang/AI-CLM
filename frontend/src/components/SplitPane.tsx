import { useRef } from "react";
import { create } from "zustand";
import { Icon } from "./icons";

type SplitState = {
  frac: number;    // left panel fraction of the row width
  locked: boolean; // when true the separator is pinned — no drag, no hover/click expand
  expand: (side: "left" | "right") => void;
  setFrac: (frac: number) => void;
  toggleLock: () => void;
};

// Module-level store: the ratio survives navigation between pages but resets on reload.
export const useSplitStore = create<SplitState>((set, get) => ({
  frac: 0.75,
  locked: false,
  expand: (side) => {
    if (get().locked) return; // auto-expand resumes as soon as the divider is unlocked
    set({ frac: side === "left" ? 0.75 : 0.25 });
  },
  setFrac: (frac) => set({ frac }),
  toggleLock: () => set((s) => ({ locked: !s.locked })),
}));

export function SplitPane({ left, right }: { left: React.ReactNode; right: React.ReactNode }) {
  const frac = useSplitStore((s) => s.frac);
  const locked = useSplitStore((s) => s.locked);
  const colsRef = useRef<HTMLDivElement>(null);
  const hoverTimer = useRef<number | undefined>(undefined);

  // hovering a panel for ~0.5s expands it, same as clicking (no-op once dragged or locked)
  function startHover(side: "left" | "right") {
    cancelHover();
    if (locked) return;
    hoverTimer.current = window.setTimeout(() => useSplitStore.getState().expand(side), 500);
  }
  function cancelHover() {
    if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
  }

  function startDrag(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (useSplitStore.getState().locked) return;
    document.body.style.cursor = "col-resize";
    const onMove = (ev: MouseEvent) => {
      const rect = colsRef.current?.getBoundingClientRect();
      if (!rect) return;
      const f = Math.min(0.75, Math.max(0.25, (ev.clientX - rect.left) / rect.width));
      useSplitStore.getState().setFrac(f);
    };
    const onUp = () => {
      document.body.style.cursor = "";
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  }

  const LockIcon = locked ? Icon.splitLock : Icon.splitUnlock;

  return (
    <div ref={colsRef} className="flex flex-col lg:flex-row gap-3 lg:gap-1 items-stretch">
      <div
        data-split="left"
        className="min-w-0 max-lg:!flex-none"
        style={{ flex: frac, transition: "flex-grow 0.25s ease" }}
        onMouseDown={() => useSplitStore.getState().expand("left")}
        onMouseEnter={() => startHover("left")}
        onMouseLeave={cancelHover}
      >
        {left}
      </div>
      <div
        onMouseDown={startDrag}
        className={`hidden lg:flex shrink-0 items-center justify-center w-4 self-stretch group ${
          locked ? "cursor-default" : "cursor-col-resize"
        }`}
        title={locked ? "Position locked — click the lock to unlock and resize" : "Drag to resize panels"}
      >
        <button
          type="button"
          aria-pressed={locked}
          aria-label={locked ? "Unlock panel divider" : "Lock panel divider position"}
          onMouseDown={(e) => e.stopPropagation()}
          onClick={(e) => {
            e.stopPropagation();
            useSplitStore.getState().toggleLock();
          }}
          className={`flex items-center justify-center p-1 transition-colors ${
            locked
              ? "text-[color:var(--accent)]"
              : "text-[color:var(--ink-faint)] hover:text-[color:var(--accent)]"
          }`}
        >
          <LockIcon width={16} height={16} strokeWidth={2} />
        </button>
      </div>
      <div
        data-split="right"
        className="relative min-w-0 max-lg:!flex-none"
        style={{ flex: 1 - frac, transition: "flex-grow 0.25s ease" }}
        onMouseDown={() => useSplitStore.getState().expand("right")}
        onMouseEnter={() => startHover("right")}
        onMouseLeave={cancelHover}
      >
        {right}
      </div>
    </div>
  );
}
