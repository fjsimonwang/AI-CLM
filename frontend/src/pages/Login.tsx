import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, useAuth } from "../api";

type DemoUser = { email: string; password: string; displayName: string; roles: string };

/** transitions.dev "error state shake" — replays the shake by removing the
 * class, forcing a reflow, then re-adding it (per the skill's own recipe). */
function useShake(trigger: number) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (!trigger) return;
    const el = ref.current;
    if (!el) return;
    el.classList.remove("is-shaking");
    void el.offsetWidth; // force reflow so the animation replays
    el.classList.add("is-shaking");
    const cs = getComputedStyle(document.documentElement);
    const ms = (name: string, fb: number) => {
      const v = parseFloat(cs.getPropertyValue(name));
      return Number.isFinite(v) ? v : fb;
    };
    const shakeMs = ms("--shake-dur-a", 80) * 2 + ms("--shake-dur-b", 60) * 2;
    const t = setTimeout(() => el.classList.remove("is-shaking"), shakeMs + 20);
    return () => clearTimeout(t);
  }, [trigger]);
  return ref;
}

function newSum() {
  const a = 2 + Math.floor(Math.random() * 8);
  const b = 2 + Math.floor(Math.random() * 8);
  return { a, b };
}

// demo-account group order + display labels — admin last
const ROLE_RANK: Record<string, number> = {
  REQUESTER: 0, APPROVER: 1, LEGAL: 2, GENERAL_COUNSEL: 3, FINANCE: 4, ADMIN: 99,
};
const ROLE_LABEL: Record<string, string> = {
  REQUESTER: "Requestor", APPROVER: "Approver", LEGAL: "Legal",
  GENERAL_COUNSEL: "General counsel", FINANCE: "Finance", ADMIN: "Admin",
};
const roleRank = (r: string) => (r in ROLE_RANK ? ROLE_RANK[r] : 50);
const roleLabel = (r: string) => ROLE_LABEL[r] || r.charAt(0) + r.slice(1).toLowerCase().replace(/_/g, " ");

export default function Login() {
  const nav = useNavigate();
  const setAuth = useAuth((s) => s.setAuth);
  const token = useAuth((s) => s.token);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [demo, setDemo] = useState<DemoUser[]>([]);
  const [err, setErr] = useState("");
  const [errAttempt, setErrAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const passwordShakeRef = useShake(errAttempt);

  // one-click demo login gated by a lightweight "not a robot" arithmetic check
  const [check, setCheck] = useState<{ user: DemoUser; a: number; b: number } | null>(null);
  const [answer, setAnswer] = useState("");
  const [checkErr, setCheckErr] = useState("");
  const [checkErrAttempt, setCheckErrAttempt] = useState(0);
  const answerShakeRef = useShake(checkErrAttempt);

  useEffect(() => {
    if (token) nav("/");
    api("/auth/demo-users").then(setDemo).catch(() => {});
  }, [token]);

  async function doLogin(e: string, p: string) {
    setBusy(true);
    setErr("");
    try {
      const res = await api("/auth/login", { method: "POST", json: { email: e, password: p } });
      setAuth(res.token, res.user);
      nav("/");
    } catch (e: any) {
      setErr(e.message || "Login failed");
      setErrAttempt((n) => n + 1);
      setCheck(null);
    } finally {
      setBusy(false);
    }
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    doLogin(email, password);
  };

  // the human check only needs to pass once per browser session
  const checkDone = () => {
    try { return sessionStorage.getItem("clm.humanCheckPassed") === "1"; } catch { return false; }
  };

  const openCheck = (user: DemoUser) => {
    if (checkDone()) {
      doLogin(user.email, user.password);
      return;
    }
    setAnswer("");
    setCheckErr("");
    setCheck({ user, ...newSum() });
  };

  const passCheck = (e: React.FormEvent) => {
    e.preventDefault();
    if (!check) return;
    if (parseInt(answer.trim(), 10) === check.a + check.b) {
      try { sessionStorage.setItem("clm.humanCheckPassed", "1"); } catch { /* ignore */ }
      doLogin(check.user.email, check.user.password);
    } else {
      setCheckErr("Not quite — try again.");
      setCheckErrAttempt((n) => n + 1);
      setAnswer("");
      setCheck({ user: check.user, ...newSum() });
    }
  };

  const grouped = useMemo(() => {
    const byRole: Record<string, DemoUser[]> = {};
    for (const u of demo) {
      const r = u.roles.split(",")[0];
      (byRole[r] ||= []).push(u);
    }
    return Object.entries(byRole).sort((a, b) => roleRank(a[0]) - roleRank(b[0]));
  }, [demo]);

  return (
    <div className="min-h-full flex items-center justify-center p-4 auth-scene">
      <div className="w-full max-w-sm">
        <div className="flex items-center gap-2 mb-6 justify-center">
          <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ background: "var(--accent)" }} />
          <span className="text-lg font-medium">CLM Platform</span>
        </div>
        <form onSubmit={submit} className="card auth-card p-5 space-y-3">
          <div>
            <label className="text-xs text-ink-faint">Email</label>
            <input className="input mt-1" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className={`t-input-wrap ${err ? "is-error" : ""}`}>
            <label className="text-xs text-ink-faint">Password</label>
            <input
              ref={passwordShakeRef}
              className={`input t-input mt-1 ${err ? "is-error" : ""}`}
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (err) setErr("");
              }}
            />
            <div className="t-error-msg text-xs mt-1" style={{ color: "var(--risk)" }}>{err}</div>
          </div>
          <button className="btn btn-primary w-full justify-center" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>

        {demo.length > 0 && (
          <div className="card p-4 mt-4">
            <div className="text-xs text-ink-faint mb-2">
              Demo accounts — one click to sign in (a quick human check once per session)
            </div>
            <div className="space-y-2.5 max-h-[46vh] overflow-y-auto pr-1">
              {grouped.map(([role, us]) => (
                <div key={role}>
                  <div className="text-[10px] uppercase tracking-wide text-ink-faint mb-1">{roleLabel(role)}</div>
                  <div className="space-y-1">
                    {us.map((u) => (
                      <button
                        key={u.email}
                        className="w-full text-left text-sm px-2 py-1.5 rounded-[8px] hover:bg-surface-2"
                        disabled={busy}
                        onClick={() => openCheck(u)}
                      >
                        <span className="text-ink">{u.displayName}</span>
                        <span className="text-ink-faint"> — {u.email}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {check && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-6"
          style={{ background: "rgba(15, 17, 21, 0.55)", backdropFilter: "blur(3px)" }}
          onClick={() => setCheck(null)}
        >
          <form
            className="card w-full max-w-xs p-6"
            onClick={(e) => e.stopPropagation()}
            onSubmit={passCheck}
          >
            <h2 className="text-base font-medium">Quick human check</h2>
            <p className="text-sm text-ink-soft mt-1">
              Signing in as <b>{check.user.displayName}</b>.
            </p>
            <div className={`t-input-wrap ${checkErr ? "is-error" : ""}`}>
              <label className="text-xs text-ink-faint mt-4 block">
                What is {check.a} + {check.b}?
              </label>
              <input
                ref={answerShakeRef}
                className={`input t-input mt-1 ${checkErr ? "is-error" : ""}`}
                autoFocus
                inputMode="numeric"
                value={answer}
                onChange={(e) => {
                  setAnswer(e.target.value);
                  if (checkErr) setCheckErr("");
                }}
              />
              <div className="t-error-msg text-xs mt-1.5" style={{ color: "var(--risk)" }}>{checkErr}</div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button type="button" className="btn" onClick={() => setCheck(null)}>Cancel</button>
              <button className="btn btn-primary" disabled={busy || !answer.trim()}>
                {busy ? "Signing in…" : "Continue"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
