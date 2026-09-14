import React, { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiForm } from "../api";
import { Card, Badge, Spinner, Empty } from "./ui";
import { Icon } from "./icons";
import { parseJsonish, safeStringify } from "./jsonEditors/shared";
import { TagListEditor } from "./jsonEditors/TagListEditor";
import { KeyValueEditor } from "./jsonEditors/KeyValueEditor";
import { KeyMultiValueEditor } from "./jsonEditors/KeyMultiValueEditor";

export type FieldDef = {
  key: string;
  label: string;
  type?: "text" | "textarea" | "number" | "boolean" | "select" | "multiselect" | "json" | "html" | "html-upload"
    | "tag-list" | "key-value" | "key-multi-value";
  options?: { value: string; label: string }[];
  optionsFrom?: string; // resource key from `refs`
  /** tag-list: resource key from `refs` supplying suggestion chips. */
  suggestionsFrom?: string;
  /** key-multi-value: resource key from `refs` supplying the row-key dropdown options. */
  keyOptionsFrom?: string;
  required?: boolean;
  help?: string;
  hideInTable?: boolean;
  uploadUrl?: string;
  /** multiselect only: text shown when nothing is ticked (defaults to "Any"). */
  emptyLabel?: string;
  /** Escape hatch for a compound visual editor that needs to read/write more than one
   *  form field at once (e.g. a workflow builder driven by `definition` but also reading
   *  the sibling `key`/`versionNo` fields). When set, this replaces the normal input for
   *  this field entirely — the Visual/Advanced(JSON) toggle still wraps it. */
  render?: (form: any, set: (k: string, v: any) => void, ctx: { optionsFor: (f: FieldDef) => { value: string; label: string }[] }) => React.ReactNode;
};

const STRUCTURED_TYPES = new Set(["tag-list", "key-value", "key-multi-value"]);

type Ref = { key: string; url: string; labelKey: string; valueKey?: string };

export function AdminCrud({
  title,
  description,
  listUrl,
  saveUrl,
  deleteUrl,
  detailUrl,
  idKey = "id",
  fields,
  columns,
  refs = [],
  renderCell,
}: {
  title: string;
  description?: string;
  listUrl: string;
  saveUrl: string;
  deleteUrl?: string;
  /** When set, editing an existing row fetches `${detailUrl}/${id}` first — needed for
   *  fields the list projection omits (e.g. an uploaded `bodyHtml`). */
  detailUrl?: string;
  idKey?: string;
  fields: FieldDef[];
  columns: { key: string; label: string }[];
  refs?: Ref[];
  renderCell?: (row: any, key: string) => React.ReactNode;
}) {
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ["admin", listUrl], queryFn: () => api(listUrl) });
  const refData = useQuery({
    queryKey: ["admin-refs", refs.map((r) => r.url).join()],
    queryFn: async () => {
      const out: Record<string, any[]> = {};
      for (const r of refs) out[r.key] = await api(r.url);
      return out;
    },
    enabled: refs.length > 0,
  });

  const [editing, setEditing] = useState<any | null>(null);
  const [openingId, setOpeningId] = useState<string | null>(null);

  async function openEdit(row: any) {
    const id = row[idKey];
    if (!detailUrl || !id) { setEditing({ ...row }); return; }
    setOpeningId(String(id));
    try {
      const full = await api(`${detailUrl}/${id}`);
      setEditing({ ...row, ...full });
    } catch {
      setEditing({ ...row }); // fall back to the list row rather than blocking the edit
    } finally {
      setOpeningId(null);
    }
  }
  const save = useMutation({
    mutationFn: (body: any) => api(saveUrl, { method: "POST", json: body }),
    onSuccess: () => {
      setEditing(null);
      qc.invalidateQueries({ queryKey: ["admin", listUrl] });
      qc.invalidateQueries();
    },
  });
  const del = useMutation({
    mutationFn: (id: string) => api(`${deleteUrl}/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", listUrl] }),
  });

  const optionsFor = (f: FieldDef): { value: string; label: string }[] => {
    if (f.options) return f.options;
    if (f.optionsFrom && refData.data) {
      const ref = refs.find((r) => r.key === f.optionsFrom);
      const rows = refData.data[f.optionsFrom] || [];
      return rows.map((row: any) => ({
        value: String(row[ref?.valueKey || "id"] ?? row.id ?? row.code),
        label: String(row[ref?.labelKey || "name"] ?? row.displayName ?? row.name ?? row.legalName ?? row.code),
      }));
    }
    return [];
  };

  const rows = list.data || [];

  return (
    <div className="space-y-3">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="font-medium">{title}</h2>
          {description && <p className="text-xs text-ink-faint mt-0.5">{description}</p>}
        </div>
        <button className="btn btn-primary" onClick={() => setEditing({})}>
          <Icon.plus width={14} height={14} /> Add
        </button>
      </div>

      {list.isLoading ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <Empty>Nothing here yet.</Empty>
      ) : (
        <Card className="!p-0 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-ink-faint border-b border-border">
                {columns.map((c) => (
                  <th key={c.key} className="px-3 py-2 font-medium">{c.label}</th>
                ))}
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row: any, i: number) => (
                <tr key={row[idKey] || i} className="border-b border-border last:border-0 hover:bg-surface-2">
                  {columns.map((c) => (
                    <td key={c.key} className="px-3 py-2.5">
                      {renderCell ? renderCell(row, c.key) : cell(row[c.key])}
                    </td>
                  ))}
                  <td className="px-3 py-2.5 text-right whitespace-nowrap">
                    <button
                      className="btn"
                      style={{ padding: "0.25rem 0.5rem" }}
                      disabled={openingId === String(row[idKey])}
                      onClick={() => openEdit(row)}
                    >
                      <Icon.edit width={13} height={13} />
                    </button>{" "}
                    {deleteUrl && (
                      <button
                        className="btn"
                        style={{ padding: "0.25rem 0.5rem", borderColor: "var(--risk)", color: "var(--risk)" }}
                        onClick={() => confirm("Delete this record?") && del.mutate(row[idKey])}
                      >
                        <Icon.trash width={13} height={13} />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {editing && (
        <EditModal
          title={`${editing[idKey] || editing.code ? "Edit" : "New"} — ${title}`}
          fields={fields}
          initial={editing}
          optionsFor={optionsFor}
          busy={save.isPending}
          error={(save.error as any)?.message}
          onClose={() => setEditing(null)}
          onSave={(body) => save.mutate(body)}
        />
      )}
    </div>
  );
}

function cell(v: any) {
  if (v == null || v === "") return <span className="text-ink-faint">—</span>;
  if (typeof v === "boolean") return <Badge tone={v ? "ok" : "neutral"}>{v ? "yes" : "no"}</Badge>;
  if (typeof v === "object") return <span className="text-xs text-ink-faint">{JSON.stringify(v).slice(0, 60)}</span>;
  return String(v);
}

function EditModal({
  title,
  fields,
  initial,
  optionsFor,
  onClose,
  onSave,
  busy,
  error,
}: {
  title: string;
  fields: FieldDef[];
  initial: any;
  optionsFor: (f: FieldDef) => { value: string; label: string }[];
  onClose: () => void;
  onSave: (body: any) => void;
  busy?: boolean;
  error?: string;
}) {
  const [form, setForm] = useState<any>(() => ({ ...initial }));
  const [uploadErr, setUploadErr] = useState<Record<string, string>>({});
  const [advanced, setAdvanced] = useState<Record<string, boolean>>({});
  useEffect(() => setForm({ ...initial }), [initial]);
  const set = (k: string, v: any) => setForm((f: any) => ({ ...f, [k]: v }));
  const isWide = fields.some((f) => !!f.render);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="modal-backdrop absolute inset-0 bg-black/45" onClick={onClose} />
      <div className={`modal-card relative card w-full max-h-[88vh] overflow-hidden flex flex-col ${isWide ? "max-w-3xl" : "max-w-lg"}`}>
        <div className="flex items-center justify-between px-5 py-3 border-b border-border">
          <div className="font-medium text-sm">{title}</div>
          <button className="btn" style={{ padding: "0.3rem 0.5rem" }} onClick={onClose}>
            <Icon.x width={15} height={15} />
          </button>
        </div>
        <div className="overflow-y-auto p-5 space-y-3">
          {fields.map((f) => {
            const v = form[f.key];
            const isStructured = STRUCTURED_TYPES.has(f.type as string) || !!f.render;
            let parseError: string | null = null;
            if (isStructured && typeof v === "string" && v.trim() !== "") {
              try { JSON.parse(v); } catch (e: any) { parseError = e?.message || "Invalid JSON"; }
            }
            const showAdvanced = isStructured && (advanced[f.key] || !!parseError);
            return (
              <div key={f.key}>
                <div className="flex items-center justify-between gap-2">
                  <label className="text-xs text-ink-faint">
                    {f.label}
                    {f.required && <span style={{ color: "var(--risk)" }}> *</span>}
                  </label>
                  {isStructured && (
                    <div className="flex gap-0.5 shrink-0">
                      <button
                        type="button"
                        className={`text-[10px] rounded-full px-2 py-0.5 border ${!showAdvanced ? "border-[color:var(--accent)] text-[color:var(--accent)]" : "border-border text-ink-faint"}`}
                        onClick={() => setAdvanced((a) => ({ ...a, [f.key]: false }))}
                        disabled={!!parseError}
                        title={parseError ? "Fix the JSON below before returning to the visual editor" : undefined}
                      >
                        Visual
                      </button>
                      <button
                        type="button"
                        className={`text-[10px] rounded-full px-2 py-0.5 border ${showAdvanced ? "border-[color:var(--accent)] text-[color:var(--accent)]" : "border-border text-ink-faint"}`}
                        onClick={() => setAdvanced((a) => ({ ...a, [f.key]: true }))}
                      >
                        Advanced (JSON)
                      </button>
                    </div>
                  )}
                </div>
                {parseError && (
                  <div className="text-[11px] mt-0.5" style={{ color: "var(--risk)" }}>Invalid JSON: {parseError}</div>
                )}
                {isStructured && !showAdvanced ? (
                  f.render ? (
                    f.render(form, set, { optionsFor })
                  ) : f.type === "tag-list" ? (
                    <TagListEditor
                      value={parseJsonish<string[]>(v, [])}
                      onChange={(next) => set(f.key, next)}
                      suggestions={f.suggestionsFrom ? optionsFor({ ...f, optionsFrom: f.suggestionsFrom }).map((o) => o.label) : undefined}
                    />
                  ) : f.type === "key-value" ? (
                    <KeyValueEditor value={parseJsonish<Record<string, string>>(v, {})} onChange={(next) => set(f.key, next)} />
                  ) : (
                    <KeyMultiValueEditor
                      value={parseJsonish<Record<string, string[]>>(v, {})}
                      onChange={(next) => set(f.key, next)}
                      keyOptions={f.keyOptionsFrom ? optionsFor({ ...f, optionsFrom: f.keyOptionsFrom }) : undefined}
                    />
                  )
                ) : isStructured ? (
                  <textarea
                    className="input mt-1 font-mono text-xs"
                    rows={10}
                    value={typeof v === "string" ? v : safeStringify(v)}
                    onChange={(e) => set(f.key, e.target.value)}
                  />
                ) : f.type === "boolean" ? (
                  <div className="flex gap-1 mt-1">
                    {[["Yes", true], ["No", false]].map(([l, val]) => (
                      <button
                        key={l as string}
                        type="button"
                        className={`btn flex-1 justify-center ${
                          (v === true || v === "true") === val ? "btn-primary" : ""
                        }`}
                        style={{ padding: "0.35rem" }}
                        onClick={() => set(f.key, val)}
                      >
                        {l}
                      </button>
                    ))}
                  </div>
                ) : f.type === "select" ? (
                  <select className="input mt-1" value={v ?? ""} onChange={(e) => set(f.key, e.target.value)}>
                    <option value="">— select —</option>
                    {optionsFor(f).map((o) => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                ) : f.type === "multiselect" ? (
                  (() => {
                    const picked = new Set(
                      String(v ?? "").split(",").map((s) => s.trim().toUpperCase()).filter(Boolean),
                    );
                    const toggle = (val: string) => {
                      const u = val.toUpperCase();
                      picked.has(u) ? picked.delete(u) : picked.add(u);
                      set(f.key, [...picked].join(","));
                    };
                    return (
                      <div className="mt-1 flex flex-wrap gap-1.5">
                        {optionsFor(f).map((o) => {
                          const on = picked.has(o.value.toUpperCase());
                          return (
                            <button
                              key={o.value}
                              type="button"
                              onClick={() => toggle(o.value)}
                              className={`text-xs rounded-full px-2.5 py-1 border transition-colors ${
                                on
                                  ? "border-[color:var(--accent)] bg-accent-soft text-[color:var(--accent)] font-medium"
                                  : "border-border text-ink-soft hover:border-[color:var(--accent)]"
                              }`}
                            >
                              {o.label}
                            </button>
                          );
                        })}
                        {picked.size === 0 && (
                          <span className="text-xs text-ink-faint self-center">{f.emptyLabel || "Any"}</span>
                        )}
                      </div>
                    );
                  })()
                ) : f.type === "textarea" || f.type === "html" || f.type === "html-upload" ? (
                  <>
                    {f.type === "html-upload" && f.uploadUrl && (
                      <div className="flex items-center gap-2 mb-1.5">
                        <label
                          className="btn cursor-pointer"
                          style={{ padding: "0.35rem 0.7rem" }}
                          title="Upload a Word (.docx) or text document; its content becomes the body"
                        >
                          <Icon.upload width={14} height={14} /> Upload .docx / .txt / .html
                          <input
                            type="file"
                            className="hidden"
                            accept=".docx,.doc,.html,.htm,.txt,.md"
                            onChange={async (e) => {
                              const file = e.target.files?.[0];
                              e.target.value = "";
                              if (!file || !f.uploadUrl) return;
                              const fd = new FormData();
                              fd.append("file", file);
                              try {
                                const data = await apiForm<{ html: string }>(f.uploadUrl, fd);
                                setUploadErr((s) => ({ ...s, [f.key]: "" }));
                                set(f.key, data.html);
                              } catch (err: any) {
                                setUploadErr((s) => ({ ...s, [f.key]: err.message || String(err) }));
                              }
                            }}
                          />
                        </label>
                        {uploadErr[f.key] && (
                          <span className="text-xs" style={{ color: "var(--risk)" }}>{uploadErr[f.key]}</span>
                        )}
                      </div>
                    )}
                    <textarea className="input mt-1" rows={f.type !== "textarea" ? 8 : 3} value={v ?? ""} onChange={(e) => set(f.key, e.target.value)} />
                    {f.type === "html-upload" && f.help && <div className="text-[11px] text-ink-faint mt-0.5">{f.help}</div>}
                  </>
                ) : f.type === "json" ? (
                  <textarea
                    className="input mt-1 font-mono text-xs"
                    rows={10}
                    value={typeof v === "string" ? v : JSON.stringify(v ?? {}, null, 2)}
                    onChange={(e) => set(f.key, e.target.value)}
                  />
                ) : f.type === "number" ? (
                  <input className="input mt-1" type="number" value={v ?? ""} onChange={(e) => set(f.key, e.target.value === "" ? "" : Number(e.target.value))} />
                ) : (
                  <input className="input mt-1" value={v ?? ""} onChange={(e) => set(f.key, e.target.value)} />
                )}
                {f.help && <div className="text-[11px] text-ink-faint mt-0.5">{f.help}</div>}
              </div>
            );
          })}
          {error && <div className="text-xs" style={{ color: "var(--risk)" }}>{error}</div>}
        </div>
        <div className="flex gap-2 justify-end px-5 py-3 border-t border-border">
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy} onClick={() => onSave(form)}>
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </div>
    </div>
  );
}
