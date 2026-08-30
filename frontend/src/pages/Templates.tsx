import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, usePerms } from "../api";
import { Card, Badge, Spinner, Empty } from "../components/ui";
import { Icon } from "../components/icons";

export default function Templates() {
  const can = usePerms();
  const [q, setQ] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const list = useQuery({ queryKey: ["templates"], queryFn: () => api("/templates") });
  const detail = useQuery({
    queryKey: ["template", selected],
    queryFn: () => api(`/templates/${selected}`),
    enabled: !!selected,
  });

  const filtered = useMemo(() => {
    const t = q.toLowerCase().trim();
    return (list.data || []).filter(
      (x: any) =>
        !t ||
        x.name.toLowerCase().includes(t) ||
        (x.tags || "").toLowerCase().includes(t) ||
        (x.contractType || "").toLowerCase().includes(t)
    );
  }, [list.data, q]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-medium flex items-center gap-2">
            <Icon.library /> Template library
          </h1>
          <p className="text-sm text-ink-faint">Approved contract templates. Drafting assembles from these.</p>
        </div>
        {can("MANAGE_TEMPLATES") && (
          <a href="/admin?tab=templates" className="btn">
            <Icon.settings width={14} height={14} /> Manage templates
          </a>
        )}
      </div>

      <div className="relative max-w-sm">
        <Icon.search className="absolute left-2.5 top-2.5 text-ink-faint" width={16} height={16} />
        <input
          className="input pl-8"
          placeholder="Search name, tag or type…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {list.isLoading ? (
        <Spinner />
      ) : filtered.length === 0 ? (
        <Empty>No templates match.</Empty>
      ) : (
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-3 stagger">
          {filtered.map((t: any) => (
            <button
              key={t.id}
              onClick={() => setSelected(t.id)}
              className="card card-hover p-4 text-left"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="font-medium text-sm">{t.name}</div>
                <Badge tone="neutral">{t.contractType}</Badge>
              </div>
              <p className="text-xs text-ink-faint mt-1.5 line-clamp-3">{t.description || "—"}</p>
              <div className="flex flex-wrap gap-1 mt-2">
                {(t.tags || "").split(",").filter(Boolean).map((tag: string) => (
                  <span key={tag} className="chip">{tag.trim()}</span>
                ))}
              </div>
              <div className="text-[11px] text-ink-faint mt-2">
                {t.jurisdiction}/{t.language} · v{t.version} · {t.sectionCount} sections
              </div>
            </button>
          ))}
        </div>
      )}

      {selected && (
        <div className="fixed inset-0 z-40 flex items-center justify-center p-4">
          <div className="modal-backdrop absolute inset-0 bg-black/40" onClick={() => setSelected(null)} />
          <div className="modal-card relative card w-full max-w-3xl max-h-[85vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between px-5 py-3 border-b border-border">
              <div className="font-medium">{detail.data?.name || "Template"}</div>
              <button className="btn" style={{ padding: "0.3rem 0.5rem" }} onClick={() => setSelected(null)}>
                <Icon.x width={15} height={15} />
              </button>
            </div>
            <div className="overflow-y-auto p-5 space-y-4">
              {detail.isLoading ? (
                <Spinner />
              ) : (
                <>
                  <div>
                    <div className="text-xs text-ink-faint mb-1">Sections</div>
                    <ol className="text-sm space-y-1">
                      {(detail.data?.sections || []).map((s: any, i: number) => (
                        <li key={i} className="flex items-center justify-between border-b border-border pb-1">
                          <span>
                            {s.sortOrder}. {s.heading}
                            {s.isOptional && <span className="text-ink-faint"> (optional)</span>}
                          </span>
                          {s.concept && (
                            <Badge tone={s.defaultVariantTier === "PREFERRED" ? "ok" : "neutral"}>
                              {s.concept}
                            </Badge>
                          )}
                        </li>
                      ))}
                    </ol>
                  </div>
                  {detail.data?.bodyHtml && (
                    <div>
                      <div className="text-xs text-ink-faint mb-1">Preview</div>
                      <div
                        className="rte-content font-serif text-sm bg-surface-2 rounded-[8px] p-4 max-h-[40vh] overflow-y-auto"
                        dangerouslySetInnerHTML={{ __html: detail.data.bodyHtml }}
                      />
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
