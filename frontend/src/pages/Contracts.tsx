import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, money, date } from "../api";
import { Card, Badge, Spinner, Empty, statusTone, riskTone } from "../components/ui";
import { SplitPane, useSplitStore } from "../components/SplitPane";
import { InquiryPanel } from "../components/InquiryPanel";
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
      ) : (list.data || []).length === 0 ? (
        <Empty>No contracts match these filters.</Empty>
      ) : (
        <Card className="!p-0 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-ink-faint border-b border-border">
                <th className="px-3 py-2 font-medium">Contract</th>
                <th className="px-3 py-2 font-medium">Type</th>
                <th className="px-3 py-2 font-medium">Counterparty</th>
                <th className="px-3 py-2 font-medium">Entity</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium text-right">Value</th>
                <th className="px-3 py-2 font-medium">Expiry</th>
                <th className="px-3 py-2 font-medium">Risk</th>
              </tr>
            </thead>
            <tbody>
              {(list.data || []).map((c: any) => (
                <tr key={c.id} className="border-b border-border last:border-0 hover:bg-surface-2">
                  <td className="px-3 py-2.5">
                    <Link to={`/contracts/${c.id}`} className="link font-medium">{c.contractNumber}</Link>
                    <div className="text-xs text-ink-faint">{c.title}</div>
                  </td>
                  <td className="px-3 py-2.5">{c.type}</td>
                  <td className="px-3 py-2.5">{(c.counterparties || []).join(", ") || "—"}</td>
                  <td className="px-3 py-2.5">{c.entity}</td>
                  <td className="px-3 py-2.5"><Badge tone={statusTone(c.status)}>{c.status}</Badge></td>
                  <td className="px-3 py-2.5 text-right tabular">{money(c.valueAmount, c.currency)}</td>
                  <td className="px-3 py-2.5 tabular">
                    {date(c.expiryDate)}
                    {c.source === "MIGRATED" && <span title="migrated, some terms unverified"> ·<Badge tone="warn">migrated</Badge></span>}
                  </td>
                  <td className="px-3 py-2.5"><Badge tone={riskTone(c.riskTier)}>{c.riskTier || "—"}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
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

  // when an inquiry produces results, give the contract list the wide pane
  useEffect(() => {
    if (inquiryFilters) useSplitStore.setState({ frac: 0.25 });
  }, [inquiryFilters]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-medium">Contracts</h1>
        <Link to="/intake" className="btn btn-primary">New request</Link>
      </div>

      <SplitPane
        left={<InquiryPanel onFilters={setInquiryFilters} />}
        right={
          <ContractList
            f={effective}
            set={set}
            inquiryActive={inquiryActive}
            onClearInquiry={() => setInquiryFilters(null)}
          />
        }
      />
    </div>
  );
}