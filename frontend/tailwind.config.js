/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: ["class", '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        bg: "var(--bg)",
        surface: "var(--surface)",
        "surface-2": "var(--surface-2)",
        border: "var(--border)",
        ink: "var(--ink)",
        "ink-soft": "var(--ink-soft)",
        "ink-faint": "var(--ink-faint)",
        accent: "var(--accent)",
        "accent-soft": "var(--accent-soft)",
        risk: "var(--risk)",
        warn: "var(--warn)",
        ok: "var(--ok)",
        ai: "var(--ai)",
        "ai-soft": "var(--ai-soft)",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "-apple-system", "Segoe UI", "sans-serif"],
        serif: ["Georgia", "Cambria", "serif"],
      },
      borderRadius: { card: "12px", control: "8px" },
      fontSize: {
        xs: ["0.75rem", "1rem"],
        sm: ["0.8125rem", "1.25rem"],
        base: ["0.9375rem", "1.5rem"],
        lg: ["1.0625rem", "1.6rem"],
        xl: ["1.375rem", "1.8rem"],
        "2xl": ["1.75rem", "2.1rem"],
      },
    },
  },
  plugins: [],
};
