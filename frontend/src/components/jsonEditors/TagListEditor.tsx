import React, { useState } from "react";
import { Icon } from "../icons";

/** Chip editor for a plain string[]. Type + Enter/comma to add; click a suggestion to add it. */
export function TagListEditor({
  value,
  onChange,
  suggestions,
  placeholder,
}: {
  value: string[];
  onChange: (next: string[]) => void;
  suggestions?: string[];
  placeholder?: string;
}) {
  const [draft, setDraft] = useState("");

  const add = (raw: string) => {
    const v = raw.trim();
    if (!v || value.includes(v)) return;
    onChange([...value, v]);
  };
  const remove = (v: string) => onChange(value.filter((x) => x !== v));

  const unusedSuggestions = (suggestions || []).filter((s) => !value.includes(s));

  return (
    <div className="mt-1">
      <div className="flex flex-wrap gap-1.5">
        {value.map((v) => (
          <span
            key={v}
            className="chip inline-flex items-center gap-1 text-xs"
            style={{ borderColor: "var(--accent)", color: "var(--accent)" }}
          >
            {v}
            <button type="button" onClick={() => remove(v)} className="hover:opacity-70">
              <Icon.x width={10} height={10} />
            </button>
          </span>
        ))}
        <input
          className="input"
          style={{ width: 160, padding: "0.2rem 0.5rem" }}
          value={draft}
          placeholder={placeholder || "Add…"}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === ",") {
              e.preventDefault();
              add(draft);
              setDraft("");
            } else if (e.key === "Backspace" && !draft && value.length > 0) {
              remove(value[value.length - 1]);
            }
          }}
          onBlur={() => {
            if (draft.trim()) { add(draft); setDraft(""); }
          }}
        />
      </div>
      {unusedSuggestions.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-1.5">
          {unusedSuggestions.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => add(s)}
              className="text-[11px] rounded-full px-2 py-0.5 border border-border text-ink-faint hover:border-[color:var(--accent)] hover:text-[color:var(--accent)]"
            >
              + {s}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
