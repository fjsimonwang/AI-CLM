import { create } from "zustand";
import { persist } from "zustand/middleware";
import { QueryClient } from "@tanstack/react-query";

const API_BASE = (import.meta as any).env?.VITE_API_BASE ?? "/api";

// Shared client — auth actions clear it so no data leaks across user sessions.
export const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 1 } },
});

type User = {
  id: string;
  email: string;
  displayName: string;
  department: string;
  roles: string[];
  permissions: string[];
  defaultEntityId: string;
  defaultEntityName: string;
};

type AuthState = {
  token: string | null;
  user: User | null;
  setAuth: (token: string, user: User) => void;
  logout: () => void;
  can: (perm: string) => boolean;
};

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      setAuth: (token, user) => {
        const prev = get().user;
        if (!prev || prev.id !== user.id) queryClient.clear();
        set({ token, user });
      },
      logout: () => {
        queryClient.clear();
        set({ token: null, user: null });
      },
      can: (perm) => !!get().user?.permissions?.includes(perm),
    }),
    { name: "clm-auth" }
  )
);

/** Hook helper: `const can = usePerms(); can('APPROVE')`. */
export function usePerms() {
  return useAuth((s) => s.can);
}

// ---- "what needs my attention" — shared across dashboard + sidebar badges ----
import { useQuery } from "@tanstack/react-query";
export function usePending() {
  const token = useAuth((s) => s.token);
  return useQuery({
    queryKey: ["me-summary"],
    queryFn: () => api("/me/summary"),
    enabled: !!token,
    refetchInterval: 60000,
    staleTime: 20000,
  });
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/** Multipart upload (no JSON content-type — the browser sets the form boundary). */
export async function apiForm<T = any>(path: string, form: FormData): Promise<T> {
  const token = useAuth.getState().token;
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: form,
  });
  if (res.status === 401 && path !== "/auth/login") useAuth.getState().logout();
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data?.message || res.statusText);
  return data as T;
}

/** Authenticated GET returning the raw response text (e.g. HTML previews). */
export async function apiText(path: string): Promise<string> {
  const token = useAuth.getState().token;
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (res.status === 401) useAuth.getState().logout();
  if (!res.ok) throw new ApiError(res.status, res.statusText || "Request failed");
  return res.text();
}

/** Authenticated GET returning a Blob (file downloads). */
export async function apiBlob(path: string): Promise<Blob> {
  const token = useAuth.getState().token;
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (res.status === 401) useAuth.getState().logout();
  if (!res.ok) throw new ApiError(res.status, res.statusText || "Request failed");
  return res.blob();
}

export async function api<T = any>(
  path: string,
  opts: RequestInit & { json?: any } = {}
): Promise<T> {
  const { json, headers, ...rest } = opts;
  const token = useAuth.getState().token;
  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  });
  if (res.status === 401 && path !== "/auth/login") useAuth.getState().logout();
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, data?.message || res.statusText);
  }
  return data as T;
}

export function apiStream(
  path: string,
  body: any,
  handlers: {
    onToken?: (chunk: string) => void;
    onStatus?: (s: string) => void;
    onComplete?: (data: any) => void;
    onError?: (e: string) => void;
  }
): () => void {
  const token = useAuth.getState().token;
  const controller = new AbortController();
  let done = false;
  fetch(`${API_BASE}${path}`, {
    method: "POST",
    signal: controller.signal,
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })
    .then(async (res) => {
      if (!res.ok || !res.body) {
        handlers.onError?.(`HTTP ${res.status}`);
        return;
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = "";
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done: streamDone, value } = await reader.read();
        if (streamDone) break;
        buf += decoder.decode(value, { stream: true });
        const events = buf.split("\n\n");
        buf = events.pop() || "";
        for (const ev of events) {
          const lines = ev.split("\n");
          let name = "message";
          let data = "";
          for (const l of lines) {
            if (l.startsWith("event:")) name = l.slice(6).trim();
            else if (l.startsWith("data:")) data += l.slice(5).trim();
          }
          if (name === "token") handlers.onToken?.(data);
          else if (name === "status") handlers.onStatus?.(data);
          else if (name === "complete") {
            done = true;
            handlers.onComplete?.(safeParse(data));
          } else if (name === "error") {
            done = true;
            handlers.onError?.(data);
          }
        }
      }
    })
    .catch((e) => {
      if (!done && e.name !== "AbortError") handlers.onError?.(String(e));
    });
  return () => {
    done = true;
    controller.abort();
  };
}

function safeParse(s: string) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

// ---- formatting helpers ----
export function money(amount: number | null | undefined, currency?: string | null) {
  if (amount == null) return "—";
  try {
    return new Intl.NumberFormat(undefined, {
      style: currency ? "currency" : "decimal",
      currency: currency || undefined,
      maximumFractionDigits: 0,
    }).format(Number(amount));
  } catch {
    return `${currency || ""} ${Number(amount).toLocaleString()}`.trim();
  }
}

export function date(d: string | null | undefined) {
  if (!d) return "—";
  const dt = new Date(d);
  if (isNaN(dt.getTime())) return String(d);
  return dt.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function fromNow(d: string | null | undefined) {
  if (!d) return "—";
  const dt = new Date(d).getTime();
  const diff = dt - Date.now();
  const days = Math.round(diff / 86400000);
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });
  if (Math.abs(days) < 1) {
    const hrs = Math.round(diff / 3600000);
    return rtf.format(hrs, "hour");
  }
  return rtf.format(days, "day");
}
