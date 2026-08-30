import { useRef } from "react";
import { create } from "zustand";

type SplitState = {
  frac: number;          // left panel fraction of the row width
  userAdjusted: boolean; // once dragged, click-to-expand is disabled for the session
  expand: (side: "left" | "right") => void;
  markAdjusted: (frac: number) => void;
};

// Module-level store: the ratio survives navigation between pages but resets on reload.
export const useSplitStore = create<SplitState>((set, get) => ({
  frac: 0.75,
  userAdjusted: false,
  expand: (side) => {
    if (get().userAdjusted) return;
    set({ frac: side === "left" ? 0.75 : 0.25 });
  },
  markAdjusted: (frac) => set({ frac, userAdjusted: true }),
}));

export function SplitPane({ left, right }: { left: React.ReactNode; right: React.ReactNode }) {
  const frac = useSplitStore((s) => s.frac);
  const colsRef = useRef<HTMLDivElement>(null);
  const hoverTimer = useRef<number | undefined>(undefined);

  // hovering a panel for ~0.5s expands it, same as clicking (no-op once dragged)
  function startHover(side: "left" | "right") {
    cancelHover();
    hoverTimer.current = window.setTimeout(() => useSplitStore.getState().expand(side), 500);
  }
  function cancelHover() {
    if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
  }

  function startDrag(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    document.body.style.cursor = "col-resize";
    let adjusted = false;
    const onMove = (ev: MouseEvent) => {
      const rect = colsRef.current?.getBoundingClientRect();
      if (!rect) return;
      const f = Math.min(0.75, Math.max(0.25, (ev.clientX - rect.left) / rect.width));
      if (!adjusted) {
        adjusted = true;
        useSplitStore.getState().markAdjusted(f);
      } else {
        useSplitStore.setState({ frac: f });
      }
    };
    const onUp = () => {
      document.body.style.cursor = "";
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  }

  return (
    <div ref={colsRef} className="flex flex-col lg:flex-row gap-3 items-stretch">
      <div
        className="min-w-0"
        style={{ flex: frac, transition: "flex-grow 0.25s ease" }}
        onMouseDown={() => useSplitStore.getState().expand("left")}
        onMouseEnter={() => startHover("left")}
        onMouseLeave={cancelHover}
      >
        {left}
      </div>
      <div
        onMouseDown={startDrag}
        className="hidden lg:flex shrink-0 items-center justify-center cursor-col-resize w-2 self-stretch"
        title="Drag to resize panels"
      >
        <div className="w-[3px] h-10 rounded-full bg-[color:var(--border)] hover:bg-[color:var(--accent)] transition-colors" />
      </div>
      <div
        className="relative min-w-0"
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