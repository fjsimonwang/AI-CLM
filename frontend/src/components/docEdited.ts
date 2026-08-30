import { create } from "zustand";

/**
 * Bumped whenever the document is edited in the embedded editor (synced to the
 * server version). Lets the review panel and submit flow react to edits.
 */
export const useDocEdited = create<{
  seq: number;
  lastAt: number;
  bump: () => void;
}>((set) => ({
  seq: 0,
  lastAt: 0,
  bump: () => set((s) => ({ seq: s.seq + 1, lastAt: Date.now() })),
}));