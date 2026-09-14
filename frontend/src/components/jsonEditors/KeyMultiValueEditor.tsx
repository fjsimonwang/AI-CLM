import React from "react";
import { Icon } from "../icons";
import { TagListEditor } from "./TagListEditor";

/** Rows of `dimension key -> tag list of values`, for Record<string,string[]> shapes:
 *  approver-scope / access-grant `constraints`, and workflow `scopeExpression`.
 *  An empty or ["*"] value list means "any" for that dimension. */
export function KeyMultiValueEditor({
  value,
  onChange,
  keyOptions,
  valueSuggestionsFor,
  addLabel,
}: {
  value: Record<string, string[]>;
  onChange: (next: Record<string, string[]>) => void;
  /** Known dimension codes to offer in the key dropdown; free text still allowed. */
  keyOptions?: { value: string; label: string }[];
  /** Optional per-row value suggestions, looked up by the row's current key. */
  valueSuggestionsFor?: (key: string) => string[] | undefined;
  addLabel?: string;
}) {
  const rows = Object.entries(value || {});

  const renameKey = (i: number, newKey: string) => {
    const next = [...rows];
    next[i] = [newKey, next[i][1]];
    onChange(Object.fromEntries(next));
  };
  const setValues = (i: number, values: string[]) => {
    const next = [...rows];
    next[i] = [next[i][0], values];
    onChange(Object.fromEntries(next));
  };
  const removeRow = (i: number) => onChange(Object.fromEntries(rows.filter((_, idx) => idx !== i)));
  const addRow = () => {
    const used = new Set(rows.map(([k]) => k));
    const firstUnused = (keyOptions || []).find((o) => !used.has(o.value))?.value;
    let k = firstUnused || "DIMENSION";
    let n = 1;
    while (value[k] !== undefined) k = `${firstUnused || "DIMENSION"}${n++}`;
    onChange({ ...value, [k]: [] });
  };

  const listId = React.useId();

  return (
    <div className="mt-1 space-y-2">
      {rows.length === 0 && <div className="text-xs text-ink-faint">No dimensions set — covers everything.</div>}
      {keyOptions && keyOptions.length > 0 && (
        <datalist id={listId}>
          {keyOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </datalist>
      )}
      {rows.map(([k, values], i) => (
        <div key={i} className="rounded-lg border border-border p-2 space-y-1.5">
          <div className="flex items-center gap-1.5">
            <input
              className="input" style={{ flex: 1 }} value={k} list={keyOptions?.length ? listId : undefined}
              onChange={(e) => renameKey(i, e.target.value)}
            />
            <button type="button" className="btn" style={{ padding: "0.3rem 0.4rem" }} onClick={() => removeRow(i)}>
              <Icon.trash width={12} height={12} />
            </button>
          </div>
          <TagListEditor
            value={Array.isArray(values) ? values : []}
            onChange={(v) => setValues(i, v)}
            suggestions={valueSuggestionsFor?.(k)}
            placeholder="Add value…"
          />
        </div>
      ))}
      <button type="button" className="btn" style={{ padding: "0.3rem 0.6rem" }} onClick={addRow}>
        <Icon.plus width={12} height={12} /> {addLabel || "Add dimension"}
      </button>
    </div>
  );
}
