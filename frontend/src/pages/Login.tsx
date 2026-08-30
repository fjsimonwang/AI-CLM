import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, useAuth } from "../api";

export default function Login() {
  const nav = useNavigate();
  const setAuth = useAuth((s) => s.setAuth);
  const token = useAuth((s) => s.token);
  const [email, setEmail] = useState("gc@acme.example");
  const [password, setPassword] = useState("demo1234");
  const [demo, setDemo] = useState<any[]>([]);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (token) nav("/");
    api("/auth/demo-users").then(setDemo).catch(() => {});
  }, [token]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr("");
    try {
      const res = await api("/auth/login", { method: "POST", json: { email, password } });
      setAuth(res.token, res.user);
      nav("/");
    } catch (e: any) {
      setErr(e.message || "Login failed");
    } finally {
      setBusy(false);
    }
  }

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
            <div className="text-xs text-ink-faint mb-2">Demo accounts — click to fill (password {demo[0]?.password})</div>
            <div className="space-y-1">
              {demo.map((u) => (
                <button
                  key={u.email}
                  className="w-full text-left text-sm px-2 py-1.5 rounded-[8px] hover:bg-surface-2"
                  onClick={() => {
                    setEmail(u.email);
                    setPassword(u.password);
                  }}
                >
                  <span className="text-ink">{u.displayName}</span>
                  <span className="text-ink-faint"> — {u.roles.split(",")[0]}</span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
