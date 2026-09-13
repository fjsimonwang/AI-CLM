import React from "react";
import { Icon } from "../icons";
import { TagListEditor } from "./TagListEditor";
import { parseJsonish, CONTRACT_FIELD_TYPES } from "./shared";

type SchemaField = {
  key: string;
  title: string;
  type: "text" | "textarea" | "number" | "boolean" | "date" | "enum";
  required: boolean;
  money: boolean;
  group: string;
  enumOptions: string[];
  extra: Record<string, any>;
};

function parseFieldSchema(raw: any): { fields: SchemaField[]; passthrough: Record<string, any> } {
  const schema = parseJsonish<any>(raw, {});
  const { type: _t, properties, required, ...passthrough } = schema || {};
  const req = new Set(Array.isArray(required) ? required : []);
  const props = properties || {};
  const fields: SchemaField[] = Object.keys(props).map((key) => {
    const p = props[key] || {};
    const { type: pType, title, enum: penum, format, "x-multiline": multiline, "x-money": money, "x-group": group, ...extra } = p;
    let ftype: SchemaField["type"] = "text";
    if (Array.isArray(penum)) ftype = "enum";
    else if (pType === "boolean") ftype = "boolean";
    else if (pType === "number" || pType === "integer") ftype = "number";
    else if (format === "date") ftype = "date";
    else if (multiline) ftype = "textarea";
    return {
      key, title: title || "", type: ftype, required: req.has(key),
      money: !!money, group: group || "", enumOptions: Array.isArray(penum) ? penum.map(String) : [],
      extra,
    };
  });
  return { fields, passthrough };
}

function buildFieldSchema(fields: SchemaField[], passthrough: Record<string, any>): any {
  const properties: Record<string, any> = {};
  const required: string[] = [];
  for (const f of fields) {
    if (!f.key) continue;
    const prop: any = { ...f.extra };
    if (f.title) prop.title = f.title;
    if (f.type === "enum") { prop.type = "string"; prop.enum = f.enumOptions; }
    else if (f.type === "date") { prop.type = "string"; prop.format = "date"; }
    else if (f.type === "textarea") { prop.type = "string"; prop["x-multiline"] = true; }
    else if (f.type === "number") { prop.type = "number"; }
    else if (f.type === "boolean") { prop.type = "boolean"; }
    else { prop.type = "string"; }
    if (f.money) prop["x-money"] = true;
    if (f.group) prop["x-group"] = f.group;
    properties[f.key] = prop;
    if (f.required) required.push(f.key);
  }
  return { ...passthrough, type: "object", properties, required };
}

export function FieldSchemaBuilder({
  fieldSchema,
  groups,
  onChange,
}: {
  fieldSchema: any;
  /** Group names, managed by the sibling "Field groups" tag-list field — read-only here. */
  groups: string[];
  onChange: (next: any) => void;
}) {
  const { fields, passthrough } = parseFieldSchema(fieldSchema);

  const emit = (nextFields: SchemaField[]) => onChange(buildFieldSchema(nextFields, passthrough));

  const updateField = (idx: number, patch: Partial<SchemaField>) =>
    emit(fields.map((f, i) => (i === idx ? { ...f, ...patch } : f)));

  const renameKey = (idx: number, key: string) => updateField(idx, { key });

  const addField = () => {
    let key = "new_field", n = 1;
    while (fields.some((f) => f.key === key)) key = `new_field_${n++}`;
    emit([...fields, { key, title: "", type: "text", required: false, money: false, group: groups[0] || "", enumOptions: [], extra: {} }]);
  };
  const removeField = (idx: number) => emit(fields.filter((_, i) => i !== idx));
  const move = (idx: number, dir: -1 | 1) => {
    const j = idx + dir;
    if (j < 0 || j >= fields.length) return;
    const next = [...fields];
    [next[idx], next[j]] = [next[j], next[idx]];
    emit(next);
  };

  return (
    <div className="mt-1 space-y-3">
      <div className="space-y-2">
        {fields.map((f, idx) => (
          <div key={idx} className="rounded-lg border border-border p-3 space-y-2">
            <div className="flex items-center gap-2">
              <input
                className="input font-mono text-xs" style={{ flex: 1 }} value={f.key}
                onChange={(e) => renameKey(idx, e.target.value)} placeholder="field_key"
              />
              <input
                className="input" style={{ flex: 1.4 }} value={f.title}
                onChange={(e) => updateField(idx, { title: e.target.value })} placeholder="Label shown to users"
              />
              <div className="flex gap-0.5 shrink-0">
                <button type="button" className="btn" style={{ padding: "0.3rem 0.4rem" }} disabled={idx === 0} onClick={() => move(idx, -1)}>
                  <Icon.arrowUp width={12} height={12} />
                </button>
                <button type="button" className="btn" style={{ padding: "0.3rem 0.4rem" }} disabled={idx === fields.length - 1} onClick={() => move(idx, 1)}>
                  <Icon.arrowDown width={12} height={12} />
                </button>
                <button type="button" className="btn" style={{ padding: "0.3rem 0.4rem", borderColor: "var(--risk)", color: "var(--risk)" }} onClick={() => removeField(idx)}>
                  <Icon.trash width={12} height={12} />
                </button>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2 items-end">
              <div>
                <label className="text-[11px] text-ink-faint">Type</label>
                <select className="input mt-1" value={f.type} onChange={(e) => updateField(idx, { type: e.target.value as SchemaField["type"] })}>
                  {CONTRACT_FIELD_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label className="text-[11px] text-ink-faint">Group</label>
                <select className="input mt-1" value={f.group} onChange={(e) => updateField(idx, { group: e.target.value })}>
                  <option value="">— none —</option>
                  {groups.map((g) => <option key={g} value={g}>{g}</option>)}
                </select>
              </div>
              <div className="flex gap-3 pb-1.5">
                <label className="flex items-center gap-1.5 text-xs">
                  <input type="checkbox" checked={f.required} onChange={(e) => updateField(idx, { required: e.target.checked })} /> Required
                </label>
                {f.type === "number" && (
                  <label className="flex items-center gap-1.5 text-xs">
                    <input type="checkbox" checked={f.money} onChange={(e) => updateField(idx, { money: e.target.checked })} /> Money
                  </label>
                )}
              </div>
            </div>

            {f.type === "enum" && (
              <div>
                <label className="text-[11px] text-ink-faint">Dropdown options</label>
                <TagListEditor value={f.enumOptions} onChange={(v) => updateField(idx, { enumOptions: v })} placeholder="Add option…" />
              </div>
            )}
          </div>
        ))}
      </div>

      <button type="button" className="btn" onClick={addField}>
        <Icon.plus width={14} height={14} /> Add field
      </button>
    </div>
  );
}
