import React, { useEffect, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth, api, usePending } from "../api";
import { Icon, IconName } from "./icons";

type NavItem = { to: string; label: string; icon: IconName; perm?: string; end?: boolean; badge?: string };
type NavGroup = { section: string; adminGroup?: boolean; items: NavItem[] };

export function adminTabs(can: (perm: string) => boolean): { key: string; label: string }[] {
  return [
    { key: "entities", label: "Legal entities" },
    { key: "teams", label: "Legal teams" },
    ...(can("MANAGE_USERS") ? [{ key: "users", label: "Users" }] : []),
    { key: "parties", label: "Parties" },
    { key: "types", label: "Contract types" },
    { key: "concepts", label: "Clause concepts" },
    { key: "variants", label: "Clause variants" },
    { key: "templates", label: "Templates" },
    { key: "playbooks", label: "Playbooks" },
    { key: "signing", label: "Signing authority" },
    { key: "rules", label: "Assignment rules" },
    ...(can("MANAGE_WORKFLOWS") ? [{ key: "workflows", label: "Workflows" }] : []),
    { key: "dimensions", label: "Access dimensions" },
    { key: "scopes", label: "Approver scopes" },
    { key: "grants", label: "Access grants" },
    { key: "review-rules", label: "AI review rules" },
  ];
}

const NAV: NavGroup[] = [
  {
    section: "Work",
    items: [
      { to: "/", label: "Dashboard", icon: "dashboard", perm: "VIEW_DASHBOARD", end: true },
      { to: "/intake", label: "New request", icon: "sparkle", perm: "CREATE_INTAKE" },
      { to: "/contracts", label: "Contracts", icon: "file", perm: "VIEW_CONTRACTS" },
      { to: "/approvals", label: "Approvals", icon: "checkCircle", perm: "APPROVE", badge: "openTaskCount" },
      { to: "/obligations", label: "Obligations", icon: "bell", perm: "VIEW_OBLIGATIONS" },
      { to: "/access", label: "Access", icon: "shieldCheck", badge: "accessToDecideCount" },
    ],
  },
  {
    section: "Knowledge",
    items: [
      { to: "/clauses", label: "Clause library", icon: "book", perm: "VIEW_CLAUSES" },
      { to: "/templates", label: "Template library", icon: "library", perm: "VIEW_TEMPLATES" },
      { to: "/ai-log", label: "AI activity", icon: "activity", perm: "VIEW_AI_LOG" },
    ],
  },
  {
    section: "Configure",
    adminGroup: true,
    items: [{ to: "/admin", label: "Administration", icon: "settings", perm: "MANAGE_MASTERDATA" }],
  },
];

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout, can } = useAuth();
  const setAuth = useAuth((s) => s.setAuth);
  const token = useAuth((s) => s.token);
  const nav = useNavigate();
  const location = useLocation();
  const [theme, setTheme] = useState<string>(() => localStorage.getItem("clm-theme") || "system");
  const [collapsed, setCollapsed] = useState<boolean>(() => localStorage.getItem("clm-nav") === "1");
  const onAdmin = location.pathname === "/admin";
  const [adminOpen, setAdminOpen] = useState(false);
  const adminShown = !collapsed && can("MANAGE_MASTERDATA") && (onAdmin || adminOpen);
  const adminTab = new URLSearchParams(location.search).get("tab") || "entities";
  const pending = usePending();
  const badgeVal = (key?: string): number => (key ? Number((pending.data as any)?.[key] || 0) : 0);

  useEffect(() => {
    // refresh permissions in case the persisted profile is stale
    if (token) {
      api("/auth/me").then((u) => setAuth(token, u)).catch(() => {});
      // queue background briefing generation for open approval tasks, so briefings
      // are ready by the time the user opens the Approvals page
      api("/ai/briefings/prepare", { method: "POST" }).catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === "system") root.removeAttribute("data-theme");
    else root.setAttribute("data-theme", theme);
    localStorage.setItem("clm-theme", theme);
  }, [theme]);

  useEffect(() => {
    localStorage.setItem("clm-nav", collapsed ? "1" : "0");
  }, [collapsed]);

  const w = collapsed ? 64 : 236;

  return (
    <div className="min-h-full flex">
      <aside
        className="fixed left-0 top-0 bottom-0 z-30 flex flex-col border-r border-border bg-surface transition-[width] duration-200"
        style={{ width: w }}
      >
        <div className="h-14 flex items-center gap-2 px-4 shrink-0 border-b border-border">
          <span className="inline-block w-2.5 h-2.5 rounded-full shrink-0" style={{ background: "var(--accent)" }} />
          {!collapsed && <span className="font-medium text-ink whitespace-nowrap">CLM Platform</span>}
        </div>

        <nav className="flex-1 overflow-y-auto py-3 px-2 space-y-4">
          {NAV.map((group, gi) => {
            if ((group as any).adminGroup) {
              if (!can("MANAGE_MASTERDATA")) return null;
              return (
                <div key={group.section}>
                  <button
                    onClick={() => {
                      setAdminOpen(!adminShown);
                      nav("/admin");
                    }}
                    title={collapsed ? group.items[0].label : undefined}
                    className={`w-full group relative flex items-center gap-2.5 rounded-[8px] px-2.5 py-2 text-sm transition-colors ${
                      onAdmin
                        ? "bg-accent-soft text-[color:var(--accent)] font-medium"
                        : "text-ink-soft hover:bg-surface-2 hover:text-ink"
                    } ${collapsed ? "justify-center" : ""}`}
                  >
                    <Icon.settings className="shrink-0" />
                    {!collapsed && (
                      <>
                        <span className="whitespace-nowrap flex-1 text-left">Administration</span>
                        <Icon.chevronDown
                          width={14}
                          height={14}
                          className="shrink-0"
                          style={{ transition: "transform 0.2s", transform: adminShown ? "none" : "rotate(-90deg)" }}
                        />
                      </>
                    )}
                  </button>
                  {can("MANAGE_MASTERDATA") && !collapsed && (
                    <div className={`unfold ${adminShown ? "unfold-open" : ""}`} aria-hidden={!adminShown}>
                      <div className="mt-0.5 ml-4 border-l border-border pl-2 space-y-0.5">
                        {adminTabs(can).map((t) => {
                          const active = onAdmin && adminTab === t.key;
                          return (
                            <button
                              key={t.key}
                              onClick={() => nav(`/admin?tab=${t.key}`)}
                              className={`w-full text-left rounded-[6px] px-2 py-1.5 text-[13px] truncate transition-colors ${
                                active
                                  ? "text-ink font-medium bg-surface-2"
                                  : "text-ink-soft hover:bg-surface-2 hover:text-ink"
                              }`}
                            >
                              {t.label}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              );
            }
            const items = group.items.filter((it) => !it.perm || can(it.perm));
            if (!items.length) return null;
            return (
              <div key={group.section}>
                {!collapsed && (
                  <div className="px-2.5 pb-1 text-[10px] font-medium uppercase tracking-wider text-ink-faint">
                    {group.section}
                  </div>
                )}
                <div className="space-y-0.5">
                  {items.map((it) => {
                    const I = Icon[it.icon];
                    const n = badgeVal(it.badge);
                    return (
                      <NavLink
                        key={it.to}
                        to={it.to}
                        end={it.end}
                        title={collapsed ? it.label : undefined}
                        className={({ isActive }) =>
                          `group relative nav-link flex items-center gap-2.5 rounded-[8px] px-2.5 py-2 text-sm ${
                            isActive
                              ? "nav-link-active bg-accent-soft text-[color:var(--accent)] font-medium"
                              : "text-ink-soft hover:bg-surface-2 hover:text-ink"
                          } ${collapsed ? "justify-center" : ""}`
                        }
                      >
                        <I className="shrink-0" />
                        {!collapsed && <span className="whitespace-nowrap flex-1">{it.label}</span>}
                        {n > 0 && (
                          <span
                            className={`text-[10px] font-medium text-white rounded-full grid place-items-center pop-in ${
                              collapsed ? "absolute top-1 right-1 w-4 h-4" : "min-w-[18px] h-[18px] px-1"
                            }`}
                            style={{ background: "var(--risk)" }}
                          >
                            {n}
                          </span>
                        )}
                      </NavLink>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </nav>

        <div className="shrink-0 border-t border-border p-2">
          <button
            className="w-full flex items-center gap-2.5 rounded-[8px] px-2.5 py-2 text-sm text-ink-faint hover:bg-surface-2 hover:text-ink transition-colors"
            onClick={() => setCollapsed((v) => !v)}
          >
            {collapsed ? <Icon.chevronRight /> : <Icon.chevronLeft />}
            {!collapsed && <span>Collapse</span>}
          </button>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0" style={{ marginLeft: w, transition: "margin-left 200ms" }}>
        <header
          className="h-14 shrink-0 border-b border-border bg-surface/80 backdrop-blur sticky top-0 z-20 flex items-center gap-3 px-5"
          style={{ boxShadow: "0 2px 14px -8px rgba(16, 24, 40, 0.3)" }}
        >
          <div className="flex-1" />
          <select
            className="input"
            style={{ width: "auto", padding: "0.3rem 0.5rem" }}
            value={theme}
            onChange={(e) => setTheme(e.target.value)}
          >
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
          <div className="flex items-center gap-2">
            <div
              className="w-7 h-7 rounded-full grid place-items-center text-xs font-medium text-white shrink-0"
              style={{ background: "var(--accent)" }}
            >
              {(user?.displayName || "?").slice(0, 1)}
            </div>
            <div className="hidden md:block leading-tight">
              <div className="text-sm text-ink">{user?.displayName?.split(" (")[0]}</div>
              <div className="text-[11px] text-ink-faint">{(user?.roles || [])[0]}</div>
            </div>
          </div>
          <button
            className="btn"
            onClick={() => {
              logout();
              nav("/login");
            }}
          >
            Sign out
          </button>
        </header>
        <main className="flex-1 px-5 py-6 max-w-[1280px] w-full mx-auto fade-in">{children}</main>
      </div>
    </div>
  );
}
