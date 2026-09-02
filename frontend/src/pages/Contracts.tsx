import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api";
import { Card, Spinner } from "../components/ui";
import { DockablePanel, useDockablePanel } from "../components/DockablePanel";
import { InquiryPanel } from "../components/InquiryPanel";
import { ContractTable } from "../components/ContractTable";
import { Icon } from "../components/icons";

type Filters = {
  type?: string; status?: string; entityRegion?: string; counterparty?: string;
  expiringWithinDays?: string; minValue?: string; maxValue?: string;
};

// Inquiry filter keys (see InquiryPanel edit form) → contract-list query params.
function inquiryToContractFilters(a: Record<string, any> | null): Filters {
  if (!a) return {};
  const f: Filters = {};
  if (a.contract_type_code) f.type = String(a.contract_type_code);
  if (a.status) f.status = String(a.status);
  if (a.entity_region) f.entityRegion = String(a.entity_region);
  if (a.counterparty_name) f.counterparty = String(a.counterparty_name);
  if (a.expiring_within_days != null && a.expiring_within_days !== "") f.expiringWithinDays = String(a.expiring_within_days);
  if (a.min_value != null && a.min_value !== "") f.minValue = String(a.min_value);
  if (a.max_value != null && a.max_value !== "") f.maxValue = String(a.max_value);
  return f;
}

function ContractList({ f, set, inquiryActive, onClearInquiry }: {
  f: Filters;
  set: (k: keyof Filters, v: string) => void;
  inquiryActive: boolean;
  onClearInquiry: () => void;
}) {
  const types = useQuery({ queryKey: ["ctypes"], queryFn: () => api("/refdata/contract-types") });
  const list = useQuery({
    queryKey: ["contracts", f],
    queryFn: () => {
      const p = new URLSearchParams();
      Object.entries(f).forEach(([k, v]) => v && p.set(k, v));
      return api(`/contracts?${p.toString()}`);
    },
  });

  return (
    <div className="space-y-3 max-lg:h-auto max-lg:overflow-visible lg:h-[calc(100vh-160px)] lg:overflow-y-auto pr-1 pb-20">
      {inquiryActive && (
        <div className="flex items-center justify-between gap-2 rounded-[8px] px-3 py-2 text-xs"
          style={{ border: "1px solid var(--accent)", background: "var(--accent-soft)" }}>
          <span>Filtered by your AI inquiry — adjust below to refine.</span>
          <button className="link shrink-0 inline-flex items-center gap-1" onClick={onClearInquiry}>
            <Icon.x width={12} height={12} /> Show all
          </button>
        </div>
      )}

      <Card className="!p-3">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
          <select className="input" value={f.type || ""} onChange={(e) => set("type", e.target.value)}>
            <option value="">All types</option>
            {(types.data || []).map((t: any) => (
              <option key={t.code} value={t.code}>{t.displayName}</option>
            ))}
          </select>
          <select className="input" value={f.status || ""} onChange={(e) => set("status", e.target.value)}>
            <option value="">All statuses</option>
            {["DRAFT", "IN_REVIEW", "EXECUTED", "CLOSED_REJECTED"].map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <select className="input" value={f.entityRegion || ""} onChange={(e) => set("entityRegion", e.target.value)}>
            <option value="">All regions</option>
            <option value="EU">EMEA</option>
            <option value="US">Americas</option>
            <option value="UK">UK</option>
          </select>
          <input
            className="input"
            placeholder="Counterparty…"
            value={f.counterparty || ""}
            onChange={(e) => set("counterparty", e.target.value)}
          />
          <select
            className="input"
            value={f.expiringWithinDays || ""}
            onChange={(e) => set("expiringWithinDays", e.target.value)}
          >
            <option value="">Any expiry</option>
            <option value="30">Expiring ≤ 30d</option>
            <option value="90">Expiring ≤ 90d</option>
            <option value="180">Expiring ≤ 180d</option>
          </select>
        </div>
      </Card>

      {list.isLoading ? (
        <Spinner />
      ) : (
        <ContractTable rows={list.data || []} />
      )}
    </div>
  );
}

export default function Contracts() {
  const [manual, setManual] = useState<Filters>({});
  const [inquiryFilters, setInquiryFilters] = useState<Record<string, any> | null>(null);

  const effective = { ...inquiryToContractFilters(inquiryFilters), ...manual };
  const inquiryActive = Object.values(inquiryToContractFilters(inquiryFilters)).some(Boolean);

  const set = (k: keyof Filters, v: string) => setManual((s) => ({ ...s, [k]: v || undefined }));

  // the AI inquiry panel is a movable / dockable / resizable panel
  const [inquiryOpen, setInquiryOpen] = useState(() => {
    try { return localStorage.getItem("clm-contracts-inquiry.open") !== "0"; } catch { return true; }
  });
  const toggleInquiry = (v: boolean) => {
    setInquiryOpen(v);
    try { localStorage.setItem("clm-contracts-inquiry.open", v ? "1" : "0"); } catch { /* ignore */ }
  };
  const inq = useDockablePanel("clm-contracts-inquiry", "[data-inquiry-card]", { defaultW: 420, defaultDockW: 380 });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-medium">Contracts</h1>
        <Link to="/intake" className="btn btn-primary">New request</Link>
      </div>

      <div className="lg:flex lg:gap-3 lg:items-start">
        {inquiryOpen && (
          <DockablePanel
            title="AI inquiry"
            icon={<Icon.sparkle width={14} height={14} style={{ color: "var(--accent)" }} />}
            cardAttr="data-inquiry-card"
            pos={inq.pos}
            dockW={inq.dockW}
            onGrab={inq.startDrag}
            onDock={inq.pos ? inq.dock : undefined}
            onClose={() => toggleInquiry(false)}
            onResize={inq.startResize}
            onDockResize={inq.startDockResize}
          >
            <InquiryPanel onFilters={setInquiryFilters} />
          </DockablePanel>
        )}
        <div className="min-w-0 lg:flex-1">
          {!inquiryOpen && (
            <button
              className="btn mb-3"
              style={{ padding: "0.25rem 0.6rem", fontSize: "0.8125rem" }}
              onClick={() => toggleInquiry(true)}
            >
              <Icon.sparkle width={13} height={13} /> AI inquiry
            </button>
          )}
          <ContractList
            f={effective}
            set={set}
            inquiryActive={inquiryActive}
            onClearInquiry={() => setInquiryFilters(null)}
          />
        </div>
      </div>
    </div>
  );
}