import { create } from "zustand";

export type HighlightTarget = { quote: string; seq: number };

let seq = 0;

/** Location highlight shared between the review panel, risk register and document panel. */
export const useHighlight = create<{
  target: HighlightTarget | null;
  locate: (quote: string) => void;
  clear: () => void;
}>((set) => ({
  target: null,
  locate: (quote) => set({ target: { quote, seq: ++seq } }),
  clear: () => set({ target: null }),
}));

const normalize = (s: string) =>
  s
    .replace(/[“”]/g, '"')
    .replace(/[‘’]/g, "'")
    .replace(/[–—]/g, "-")
    .replace(/\s+/g, " ")
    .trim();

/** Candidate phrases to try, in order — AI quotes may not match the document verbatim. */
export function quoteCandidates(quote: string): string[] {
  const q = normalize(quote || "");
  if (!q) return [];
  const out = [q];
  const firstSentence = q.split(/(?<=[.!?;])\s/)[0];
  if (firstSentence && firstSentence.length >= 12 && firstSentence !== q) out.push(firstSentence);
  const words = q.split(" ");
  const short = words.slice(0, Math.min(12, words.length)).join(" ");
  if (short.length >= 12 && !out.includes(short)) out.push(short);
  return out;
}