import React from "react";
import { Icon } from "../icons";

/** Rows of `key = value` text pairs, for a flat Record<string,string> like an
 *  assignment rule's conditionExpression ({"entityRegion":"EU"}). All rows must
 *  match for the rule to apply. */
export function KeyValueEditor({
  value,
  onChange,
  keySuggestions,
  keyPlaceholder,
  valuePlaceholder,
}: {
  value: Record<string, string>;
  onChange: (next: Record<string, string>) => void;
  keySuggestions?: string[];
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}) {
  const rows = Object.entries(value || {});

  const setRow = (i: number, k: string, v: string) => {
    const next = [...rows];
    next[i] = [k, v];
    onChange(Object.fromEntries(next));
  };
  const removeRow = (i: number) => {
    const next = rows.filter((_, idx) => idx !== i);
    onChange(Object.fromEntries(next));
  };
  const addRow = () => {
    let k = "field";
    let n = 1;
    while (value[k] !== undefined) k = `field${n++}`;
    onChange({ ...value, [k]: "" });
  };

  return (
    <div className="mt-1 space-y-1.5">
      {rows.length === 0 && <div className="text-xs text-ink-faint">No conditions — matches every contract.</div>}
      {rows.map(([k, v], i) => (
        <div key={i} className="flex items-center gap-1.5">
          <input
            className="input"
            style={{ flex: 1 }}
            list={keySuggestions ? "kv-key-suggestions" : undefined}
            value={k}
            placeholder={keyPlaceholder || "field"}
            onChange={(e) => setRow(i, e.target.value, v)}
          />
          <span className="text-ink-faint text-xs">=</span>
          <input
            className="input"
            style={{ flex: 1 }}
            value={v}
            placeholder={valuePlaceholder || "value"}
            onChange={(e) => setRow(i, k, e.target.value)}
          />
          <button type="button" className="btn" style={{ padding: "0.3rem 0.4rem" }} onClick={() => removeRow(i)}>
            <Icon.trash width={12} height={12} />
          </button>
        </div>
      ))}
      {keySuggestions && (
        <datalist id="kv-key-suggestions">
          {keySuggestions.map((s) => <option key={s} value={s} />)}
        </datalist>
      )}
      <button type="button" className="btn" style={{ padding: "0.3rem 0.6rem" }} onClick={addRow}>
        <Icon.plus width={12} height={12} /> Add condition
      </button>
    </div>
  );
}
