import React from "react";

/** Minimal stroke icon set — 24×24, 1.6 stroke, currentColor. */
const S = (p: React.SVGProps<SVGSVGElement>) => ({
  width: 18,
  height: 18,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  ...p,
});

export const Icon = {
  dashboard: (p: any) => (<svg {...S(p)}><rect x="3" y="3" width="8" height="10" rx="1.5"/><rect x="13" y="3" width="8" height="6" rx="1.5"/><rect x="13" y="13" width="8" height="8" rx="1.5"/><rect x="3" y="17" width="8" height="4" rx="1.5"/></svg>),
  sparkle: (p: any) => (<svg {...S(p)}><path d="M12 3l1.8 4.9L19 9.7l-4.2 2.9L15 18l-3-3-3 3 .2-5.4L5 9.7l5.2-1.8z"/></svg>),
  file: (p: any) => (<svg {...S(p)}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h6"/></svg>),
  search: (p: any) => (<svg {...S(p)}><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>),
  check: (p: any) => (<svg {...S(p)}><path d="M4 12l5 5L20 6"/></svg>),
  checkCircle: (p: any) => (<svg {...S(p)}><circle cx="12" cy="12" r="9"/><path d="M8 12l3 3 5-5"/></svg>),
  bell: (p: any) => (<svg {...S(p)}><path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6z"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>),
  book: (p: any) => (<svg {...S(p)}><path d="M5 4a2 2 0 0 1 2-2h11v18H7a2 2 0 0 0-2 2z"/><path d="M5 20a2 2 0 0 0 2 2h11"/></svg>),
  library: (p: any) => (<svg {...S(p)}><path d="M4 4v16M9 4v16"/><rect x="12" y="4" width="9" height="16" rx="1.5"/><path d="M15 8h3M15 12h3"/></svg>),
  activity: (p: any) => (<svg {...S(p)}><path d="M3 12h4l3 8 4-16 3 8h4"/></svg>),
  settings: (p: any) => (<svg {...S(p)}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2V21a2 2 0 0 1-4 0v-.1A1.7 1.7 0 0 0 6.2 19l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.7 1.7 0 0 0 4.6 13H4.5a2 2 0 0 1 0-4h.1A1.7 1.7 0 0 0 6 6.2l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.7 1.7 0 0 0 11 4.6V4.5a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 2.9 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1A1.7 1.7 0 0 0 21 11.5h.1a2 2 0 0 1 0 4H21z"/></svg>),
  chevronLeft: (p: any) => (<svg {...S(p)}><path d="M15 6l-6 6 6 6"/></svg>),
  chevronRight: (p: any) => (<svg {...S(p)}><path d="M9 6l6 6-6 6"/></svg>),
  chevronDown: (p: any) => (<svg {...S(p)}><path d="M6 9l6 6 6-6"/></svg>),
  plus: (p: any) => (<svg {...S(p)}><path d="M12 5v14M5 12h14"/></svg>),
  edit: (p: any) => (<svg {...S(p)}><path d="M4 20h4L19 9a2 2 0 0 0-3-3L5 17z"/><path d="M14 6l4 4"/></svg>),
  trash: (p: any) => (<svg {...S(p)}><path d="M4 7h16M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13"/></svg>),
  x: (p: any) => (<svg {...S(p)}><path d="M6 6l12 12M18 6L6 18"/></svg>),
  building: (p: any) => (<svg {...S(p)}><rect x="4" y="3" width="16" height="18" rx="1.5"/><path d="M9 8h.01M12 8h.01M15 8h.01M9 12h.01M12 12h.01M15 12h.01M10 21v-4h4v4"/></svg>),
  users: (p: any) => (<svg {...S(p)}><circle cx="9" cy="8" r="3"/><path d="M3 20a6 6 0 0 1 12 0"/><path d="M16 5.5a3 3 0 0 1 0 5M21 20a6 6 0 0 0-4-5.7"/></svg>),
  handshake: (p: any) => (<svg {...S(p)}><path d="M8 13l3 3 2-2 3 3"/><path d="M3 9l4-4 5 3 5-3 4 4-4 5-2-2"/></svg>),
  route: (p: any) => (<svg {...S(p)}><circle cx="6" cy="18" r="2.5"/><circle cx="18" cy="6" r="2.5"/><path d="M8.5 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h2.5"/></svg>),
  shield: (p: any) => (<svg {...S(p)}><path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/></svg>),
  shieldCheck: (p: any) => (<svg {...S(p)}><path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/></svg>),
  briefcase: (p: any) => (<svg {...S(p)}><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2M3 12h18"/></svg>),
  cloud: (p: any) => (<svg {...S(p)}><path d="M7 18a4 4 0 0 1 0-8 5 5 0 0 1 9.6-1.3A3.5 3.5 0 0 1 18 18z"/></svg>),
  lock: (p: any) => (<svg {...S(p)}><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>),
  clipboard: (p: any) => (<svg {...S(p)}><rect x="6" y="4" width="12" height="17" rx="2"/><path d="M9 4V3h6v1M9 10h6M9 14h6"/></svg>),
  cart: (p: any) => (<svg {...S(p)}><circle cx="9" cy="20" r="1.5"/><circle cx="17" cy="20" r="1.5"/><path d="M3 4h2l2.4 12h10L20 8H6"/></svg>),
  user: (p: any) => (<svg {...S(p)}><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>),
  clock: (p: any) => (<svg {...S(p)}><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>),
  message: (p: any) => (<svg {...S(p)}><path d="M21 12a8 8 0 0 1-11.6 7.1L3 21l1.9-6.4A8 8 0 1 1 21 12z"/></svg>),
  history: (p: any) => (<svg {...S(p)}><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 4v4h4M12 8v4l3 2"/></svg>),
  bold: (p: any) => (<svg {...S(p)}><path d="M7 5h6a3.5 3.5 0 0 1 0 7H7zM7 12h7a3.5 3.5 0 0 1 0 7H7z"/></svg>),
  italic: (p: any) => (<svg {...S(p)}><path d="M10 5h8M6 19h8M14 5l-4 14"/></svg>),
  list: (p: any) => (<svg {...S(p)}><path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/></svg>),
  link: (p: any) => (<svg {...S(p)}><path d="M10 14a4 4 0 0 0 6 .5l3-3a4 4 0 0 0-6-6l-1.5 1.5M14 10a4 4 0 0 0-6-.5l-3 3a4 4 0 0 0 6 6l1.5-1.5"/></svg>),
  externalLink: (p: any) => (<svg {...S(p)}><path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/></svg>),
  refresh: (p: any) => (<svg {...S(p)}><path d="M4 9a8 8 0 0 1 14-3l2 2M20 15a8 8 0 0 1-14 3l-2-2"/><path d="M20 4v4h-4M4 20v-4h4"/></svg>),
  upload: (p: any) => (<svg {...S(p)}><path d="M12 15V3M7 8l5-5 5 5"/><path d="M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4"/></svg>),
  warning: (p: any) => (<svg {...S(p)}><path d="M12 3l10 18H2z"/><path d="M12 9v5M12 18h.01"/></svg>),
  arrowRight: (p: any) => (<svg {...S(p)}><path d="M5 12h14M13 6l6 6-6 6"/></svg>),
  menu: (p: any) => (<svg {...S(p)}><path d="M4 6h16M4 12h16M4 18h16"/></svg>),
  send: (p: any) => (<svg {...S(p)}><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>),
  eye: (p: any) => (<svg {...S(p)}><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>),
  eyeOff: (p: any) => (<svg {...S(p)}><path d="M3 3l18 18"/><path d="M10.6 5.1A9.8 9.8 0 0 1 12 5c6.5 0 10 7 10 7a17.4 17.4 0 0 1-3.2 3.9M6.6 6.6C4 8.2 2 12 2 12s3.5 7 10 7c1.8 0 3.4-.5 4.8-1.2"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>),
  arrowUp: (p: any) => (<svg {...S(p)}><path d="M12 19V5M5 12l7-7 7 7"/></svg>),
  arrowDown: (p: any) => (<svg {...S(p)}><path d="M12 5v14M5 12l7 7 7-7"/></svg>),
  halfWidth: (p: any) => (<svg {...S(p)}><rect x="3" y="6" width="9" height="12" rx="2"/><rect x="15" y="6" width="6" height="12" rx="2" opacity="0.3"/></svg>),
  fullWidth: (p: any) => (<svg {...S(p)}><rect x="3" y="6" width="18" height="12" rx="2"/></svg>),
};

export type IconName = keyof typeof Icon;

export function DynIcon({ name, ...rest }: { name?: string } & React.SVGProps<SVGSVGElement>) {
  const key = (name || "") as IconName;
  const C = Icon[key] || Icon.file;
  return <C {...rest} />;
}
