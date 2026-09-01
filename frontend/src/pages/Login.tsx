import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, useAuth } from "../api";

type DemoUser = { email: string; password: string; displayName: string; roles: string };

function newSum() {
  const a = 2 + Math.floor(Math.random() * 8);
  const b = 2 + Math.floor(Math.random() * 8);
  return { a, b };
}

export default function Login() {
  const nav = useNavigate();
  const setAuth = useAuth((s) => s.setAuth);
  const token = useAuth((s) => s.token);
  const [email, setEmail] = useState("gc@acme.example");
  const [password, setPassword] = useState("demo1234");
  const [demo, setDemo] = useState<DemoUser[]>([]);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  // one-click demo login gated by a lightweight "not a robot" arithmetic check
  const [check, setCheck] = useState<{ user: DemoUser; a: number; b: number } | null>(null);
  const [answer, setAnswer] = useState("");
  const [checkErr, setCheckErr] = useState("");

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
      setCheck(null);
    } finally {
      setBusy(false);
    }
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    doLogin(email, password);
  };

  const openCheck = (user: DemoUser) => {
    setAnswer("");
    setCheckErr("");
    setCheck({ user, ...newSum() });
  };

  const passCheck = (e: React.FormEvent) => {
    e.preventDefault();
    if (!check) return;
    if (parseInt(answer.trim(), 10) === check.a + check.b) {
      doLogin(check.user.email, check.user.password);
    } else {
      setCheckErr("Not quite — try again.");
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
    return byRole;
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
          <div>
            <label className="text-xs text-ink-faint">Password</label>
            <input
              className="input mt-1"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {err && <div className="text-xs" style={{ color: "var(--risk)" }}>{err}</div>}
          <button className="btn btn-primary w-full justify-center" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>

        {demo.length > 0 && (
          <div className="card p-4 mt-4">
            <div className="text-xs text-ink-faint mb-2">
              Demo accounts — one click to sign in (a quick human check follows)
            </div>
            <div className="space-y-2.5 max-h-[46vh] overflow-y-auto pr-1">
              {Object.entries(grouped).map(([role, us]) => (
                <div key={role}>
                  <div className="text-[10px] uppercase tracking-wide text-ink-faint mb-1">{role}</div>
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
            <label className="text-xs text-ink-faint mt-4 block">
              What is {check.a} + {check.b}?
            </label>
            <input
              className="input mt-1"
              autoFocus
              inputMode="numeric"
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
            />
            {checkErr && <div className="text-xs mt-1.5" style={{ color: "var(--risk)" }}>{checkErr}</div>}
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
