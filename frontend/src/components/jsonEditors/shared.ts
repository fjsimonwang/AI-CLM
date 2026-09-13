/** Shared helpers for the visual JSON-config editors (workflow builder, field schema
 *  builder, condition/constraint editors). Kept in one place so the option lists here
 *  stay easy to compare against the backend switch statements they mirror. */

/** Accepts either an already-parsed value or a raw JSON string (list endpoints in this
 *  app return both, depending on the entity) and always returns a usable value. */
export function parseJsonish<T>(v: any, fallback: T): T {
  if (v == null || v === "") return fallback;
  if (typeof v === "string") {
    try {
      return JSON.parse(v) as T;
    } catch {
      return fallback;
    }
  }
  return v as T;
}

export function safeStringify(v: any): string {
  try {
    return JSON.stringify(v ?? {}, null, 2);
  } catch {
    return "{}";
  }
}

/** Mirrors WorkflowService.resolveRole/roleLabel (backend/src/main/java/com/acme/clm/service/WorkflowService.java). */
export const WORKFLOW_ROLES = [
  { value: "owner", label: "Contract owner" },
  { value: "signatory", label: "Authorised signatory" },
  { value: "owner_manager", label: "Owner's manager" },
  { value: "legal_team", label: "Legal team" },
  { value: "legal_manager", label: "Legal manager" },
  { value: "finance_approver", label: "Finance approver" },
];

export function roleLabel(role: string): string {
  const found = WORKFLOW_ROLES.find((r) => r.value === role);
  if (found) return found.label;
  if (!role) return "Assignee";
  return role.charAt(0).toUpperCase() + role.slice(1).replace(/_/g, " ");
}

/** Mirrors the taskType values WorkflowService/Approvals understand. */
export const WORKFLOW_TASK_TYPES = [
  { value: "REVIEW", label: "Review" },
  { value: "APPROVAL", label: "Approval" },
  { value: "SIGNATURE", label: "Signature" },
  { value: "REVISION", label: "Revision (sent back to the requestor)" },
];

/** Only these two guard names are actually evaluated by
 *  WorkflowService.evaluateGuards — any other string in `guards` is silently
 *  ignored by the engine, so those still round-trip as free-form chips. */
export const WORKFLOW_GUARDS = [
  { value: "signing_authority_valid", label: "Signing authority must be valid for the contract value" },
  { value: "no_open_deviations", label: "No unacknowledged clause deviations" },
];

/** Mirrors the field types FieldCatalog.forType() actually derives from a JSON-Schema property. */
export const CONTRACT_FIELD_TYPES = [
  { value: "text", label: "Text" },
  { value: "textarea", label: "Long text" },
  { value: "number", label: "Number" },
  { value: "boolean", label: "Yes / No" },
  { value: "date", label: "Date" },
  { value: "enum", label: "Dropdown (choose one)" },
];

export function newId(): string {
  return Math.random().toString(36).slice(2, 9);
}
