import React, { useEffect, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth, api, usePending } from "../api";
import { Icon, IconName } from "./icons";
import { HelpChat } from "./HelpChat";

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
      { to: "/auto-reject", label: "Auto-rejection", icon: "shieldX", perm: "APPROVE" },
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

function useIsMobile() {
  const [m, setM] = useState(() => window.matchMedia("(max-width: 1023px)").matches);
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 1023px)");
    const fn = (e: MediaQueryListEvent) => setM(e.matches);
    mq.addEventListener("change", fn);
    return () => mq.removeEventListener("change", fn);
  }, []);
  return m;
}

export function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout, can } = useAuth();
  const setAuth = useAuth((s) => s.setAuth);
  const token = useAuth((s) => s.token);
  const nav = useNavigate();
  const location = useLocation();
  const isMobile = useIsMobile();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [theme, setTheme] = useState<string>(() => localStorage.getItem("clm-theme") || "system");
  const [collapsed, setCollapsed] = useState<boolean>(() => localStorage.getItem("clm-nav") === "1");
  const navCollapsed = collapsed && !isMobile;
  const c = navCollapsed;
  const onAdmin = location.pathname === "/admin";
  const [adminOpen, setAdminOpen] = useState(false);
  const adminShown = !c && can("MANAGE_MASTERDATA") && (onAdmin || adminOpen);
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

  // close the mobile drawer whenever the route changes
  useEffect(() => { setMobileOpen(false); }, [location.pathname, location.search]);

  const w = collapsed ? 64 : 236;

  return (
    <div className="min-h-full flex">
      {isMobile && mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 backdrop-blur-[2px] modal-backdrop lg:hidden"
          onClick={() => setMobileOpen(false)}
          aria-label="Close navigation"
        />
      )}
      <aside
        className={`fixed left-0 top-0 bottom-0 z-50 flex flex-col border-r border-border bg-surface transition-[width,transform] duration-200 ${
          isMobile && !mobileOpen ? "shadow-none" : ""
        }`}
        style={{
          width: isMobile ? 264 : w,
          transform: isMobile && !mobileOpen ? "translateX(-100%)" : "none",
        }}
      >
        <div className="h-14 flex items-center gap-2 px-4 shrink-0 border-b border-border">
          <span className="inline-block w-2.5 h-2.5 rounded-full shrink-0" style={{ background: "var(--accent)" }} />
          {!c && <span className="font-medium text-ink whitespace-nowrap">CLM Platform</span>}
          {isMobile && (
            <button
              className="ml-auto w-8 h-8 grid place-items-center rounded-[8px] text-ink-faint hover:bg-surface-2 hover:text-ink"
              onClick={() => setMobileOpen(false)}
              aria-label="Close navigation"
            >
              <Icon.x width={16} height={16} />
            </button>
          )}
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
                    title={c ? group.items[0].label : undefined}
                    className={`w-full group relative flex items-center gap-2.5 rounded-[8px] px-2.5 py-2 text-sm transition-colors ${
                      onAdmin
                        ? "bg-accent-soft text-[color:var(--accent)] font-medium"
                        : "text-ink-soft hover:bg-surface-2 hover:text-ink"
                    } ${c ? "justify-center" : ""}`}
                  >
                    <Icon.settings className="shrink-0" />
                    {!c && (
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
                  {can("MANAGE_MASTERDATA") && !c && (
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
                {!c && (
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
                        title={c ? it.label : undefined}
                        className={({ isActive }) =>
                          `group relative nav-link flex items-center gap-2.5 rounded-[8px] px-2.5 py-2 text-sm ${
                            isActive
                              ? "nav-link-active bg-accent-soft text-[color:var(--accent)] font-medium"
                              : "text-ink-soft hover:bg-surface-2 hover:text-ink"
                          } ${c ? "justify-center" : ""}`
                        }
                      >
                        <I className="shrink-0" />
                        {!c && <span className="whitespace-nowrap flex-1">{it.label}</span>}
                        {n > 0 && (
                          <span
                            className={`text-[10px] font-medium text-white rounded-full grid place-items-center pop-in ${
                              c ? "absolute top-1 right-1 w-4 h-4" : "min-w-[18px] h-[18px] px-1"
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

        <div className="shrink-0 border-t border-border p-2 hidden lg:block">
          <button
            className="w-full flex items-center gap-2.5 rounded-[8px] px-2.5 py-2 text-sm text-ink-faint hover:bg-surface-2 hover:text-ink transition-colors"
            onClick={() => setCollapsed((v) => !v)}
          >
            {c ? <Icon.chevronRight /> : <Icon.chevronLeft />}
            {!c && <span>Collapse</span>}
          </button>
        </div>
      </aside>

      <div
        className="flex-1 flex flex-col min-w-0"
        style={{ marginLeft: isMobile ? 0 : w, transition: "margin-left 200ms" }}
      >
        <header
          className="h-14 shrink-0 border-b border-border bg-surface/80 backdrop-blur sticky top-0 z-20 flex items-center gap-2 px-3 sm:gap-3 sm:px-5"
          style={{ boxShadow: "0 2px 14px -8px rgba(16, 24, 40, 0.3)" }}
        >
          <button
            className="btn lg:hidden shrink-0"
            style={{ padding: "0.35rem 0.55rem" }}
            onClick={() => setMobileOpen(true)}
            aria-label="Open navigation"
          >
            <Icon.menu width={16} height={16} />
          </button>
          <div className="flex-1" />
          <AgentOptInChip />
          <select
            className="input hidden sm:block"
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
            className="btn shrink-0"
            style={{ padding: "0.35rem 0.7rem" }}
            onClick={() => {
              logout();
              nav("/login");
            }}
          >
            Sign out
          </button>
        </header>
        <main className="flex-1 px-3 sm:px-5 py-4 sm:py-6 max-w-[1280px] w-full mx-auto fade-in">{children}</main>
      </div>
      <HelpChat />
    </div>
  );
}

/** Per-user agent participation toggle — off means the user's agent neither sends nor receives. */
function AgentOptInChip() {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["me-agent-setting"],
    queryFn: () => api("/me/agent-setting") as Promise<{ agentOptIn: boolean }>,
  });
  const set = useMutation({
    mutationFn: (enabled: boolean) => api("/me/agent-setting", { method: "PATCH", json: { enabled } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["me-agent-setting"] });
      qc.invalidateQueries({ queryKey: ["agent-status"] });
      qc.invalidateQueries({ queryKey: ["agent-agents"] });
    },
  });
  const on = !!q.data?.agentOptIn;
  return (
    <button
      className="btn shrink-0"
      style={{ padding: "0.3rem 0.6rem", fontSize: "12px" }}
      disabled={q.isLoading || set.isPending}
      title={
        on
          ? "Agent collaboration: your agent may join contract discussions. Click to turn it off."
          : "Agent collaboration is off: your agent sends and receives nothing. Click to turn it on."
      }
      onClick={() => set.mutate(!on)}
    >
      <Icon.bot
        width={15}
        height={15}
        style={{ color: on ? "#7c3aed" : "currentColor", transition: "color 0.2s" }}
      />
      <span
        className="relative inline-flex w-8 h-[18px] rounded-full transition-colors duration-200 shrink-0"
        style={{ background: on ? "#22c55e" : "var(--ink-faint)" }}
      >
        <span
          className="absolute top-[2px] left-[2px] w-[14px] h-[14px] rounded-full bg-white shadow"
          style={{ transition: "transform 0.2s", transform: on ? "translateX(14px)" : "none" }}
        />
      </span>
      <span className="hidden sm:inline">Agent&nbsp;talk</span>
    </button>
  );
}
