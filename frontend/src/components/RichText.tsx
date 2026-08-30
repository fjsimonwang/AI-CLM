import React, { useEffect, useRef } from "react";
import { Icon } from "./icons";

/**
 * Dependency-free rich-text box (contentEditable + execCommand). Uncontrolled:
 * seeds from `initial` once, emits HTML on input. Parent remounts (via key) to reset.
 */
export function RichText({
  initial = "",
  onChange,
  placeholder,
  minHeight = 72,
}: {
  initial?: string;
  onChange: (html: string) => void;
  placeholder?: string;
  minHeight?: number;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current && initial) ref.current.innerHTML = initial;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const emit = () => onChange(ref.current?.innerHTML ?? "");
  const cmd = (c: string, arg?: string) => {
    ref.current?.focus();
    document.execCommand(c, false, arg);
    emit();
  };
  const link = () => {
    const url = prompt("Link URL");
    if (url) cmd("createLink", url);
  };

  const Btn = ({ c, icon, title }: { c: string; icon: keyof typeof Icon; title: string }) => {
    const I = Icon[icon];
    return (
      <button
        type="button"
        title={title}
        className="w-7 h-7 grid place-items-center rounded-[6px] text-ink-soft hover:bg-surface-2 hover:text-ink transition-colors"
        onMouseDown={(e) => e.preventDefault()}
        onClick={() => (c === "createLink" ? link() : cmd(c))}
      >
        <I width={15} height={15} />
      </button>
    );
  };

  return (
    <div className="rounded-[8px] border border-border bg-surface overflow-hidden focus-within:border-[color:var(--accent)] transition-colors">
      <div className="flex items-center gap-0.5 px-1.5 py-1 border-b border-border bg-surface-2">
        <Btn c="bold" icon="bold" title="Bold" />
        <Btn c="italic" icon="italic" title="Italic" />
        <Btn c="insertUnorderedList" icon="list" title="Bulleted list" />
        <Btn c="createLink" icon="link" title="Link" />
      </div>
      <div
        ref={ref}
        contentEditable
        suppressContentEditableWarning
        data-placeholder={placeholder}
        className="rte-content px-3 py-2 text-sm outline-none"
        style={{ minHeight }}
        onInput={emit}
        onBlur={emit}
      />
    </div>
  );
}
