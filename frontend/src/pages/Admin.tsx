import { useSearchParams } from "react-router-dom";
import { usePerms } from "../api";
import { adminTabs } from "../components/Layout";
import { Icon } from "../components/icons";
import { AdminCrud, FieldDef } from "../components/AdminCrud";

const REGIONS = [
  { value: "EU", label: "EU" },
  { value: "US", label: "US" },
  { value: "UK", label: "UK" },
  { value: "GLOBAL", label: "Global" },
];
const ROLES = ["ADMIN", "GENERAL_COUNSEL", "LEGAL", "APPROVER", "FINANCE", "REQUESTER"].map((r) => ({ value: r, label: r }));
const TIERS = ["PREFERRED", "ACCEPTABLE", "FALLBACK", "UNACCEPTABLE"].map((t) => ({ value: t, label: t }));

const entityRef = { key: "entities", url: "/refdata/entities", labelKey: "legalName", valueKey: "id" };
const teamRef = { key: "teams", url: "/refdata/teams", labelKey: "name", valueKey: "id" };
const userRef = { key: "users", url: "/refdata/users", labelKey: "displayName", valueKey: "id" };
const typeRef = { key: "types", url: "/refdata/contract-types?includeInactive=true", labelKey: "displayName", valueKey: "code" };
const conceptRef = { key: "concepts", url: "/clauses/concepts", labelKey: "name", valueKey: "id" };
const templateRef = { key: "templates", url: "/templates", labelKey: "name", valueKey: "id" };
const workflowRef = { key: "workflows", url: "/admin/workflows", labelKey: "name", valueKey: "id" };

export default function Admin() {
  const can = usePerms();
  const [sp, setSp] = useSearchParams();
  const tab = adminTabs(can).some((t) => t.key === sp.get("tab")) ? sp.get("tab")! : "entities";
  const go = (t: string) => setSp({ tab: t });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-medium flex items-center gap-2">
          <Icon.settings /> Administration
        </h1>
        <p className="text-sm text-ink-faint">
          Every list of allowed values the intake form and AI rely on is maintained here — nothing is invented by AI.
        </p>
      </div>

      {tab === "entities" && (
        <AdminCrud
          title="Legal entities"
          description="The contracting entities users can choose from."
          listUrl="/refdata/entities"
          saveUrl="/admin/entities"
          refs={[teamRef]}
          columns={[
            { key: "shortName", label: "Code" },
            { key: "legalName", label: "Legal name" },
            { key: "countryCode", label: "Country" },
            { key: "governingLaw", label: "Governing law" },
            { key: "region", label: "Region" },
          ]}
          fields={[
            { key: "legalName", label: "Legal name", required: true },
            { key: "shortName", label: "Short code", required: true },
            { key: "countryCode", label: "Country code (ISO)", required: true },
            { key: "registrationNumber", label: "Registration number" },
            { key: "defaultGoverningLaw", label: "Default governing law" },
            { key: "defaultLanguage", label: "Default language", type: "text" },
            { key: "dataResidencyRegion", label: "Data residency region", type: "select", options: REGIONS },
            { key: "legalTeamId", label: "Legal team", type: "select", optionsFrom: "teams" },
            { key: "status", label: "Status", type: "select", options: [{ value: "ACTIVE", label: "Active" }, { value: "INACTIVE", label: "Inactive" }] },
          ]}
        />
      )}

      {tab === "teams" && (
        <AdminCrud
          title="Legal teams"
          listUrl="/refdata/teams"
          saveUrl="/admin/teams"
          columns={[
            { key: "name", label: "Name" },
            { key: "region", label: "Region" },
            { key: "slaHours", label: "Queue SLA (h)" },
          ]}
          fields={[
            { key: "name", label: "Name", required: true },
            { key: "region", label: "Region" },
            { key: "defaultQueueSlaHours", label: "Default queue SLA (hours)", type: "number" },
          ]}
        />
      )}

      {tab === "users" && (
        <AdminCrud
          title="Users"
          description="Assign roles. Roles determine which menus and features each person can access."
          listUrl="/admin/users"
          saveUrl="/admin/users"
          refs={[entityRef, userRef]}
          columns={[
            { key: "displayName", label: "Name" },
            { key: "email", label: "Email" },
            { key: "department", label: "Department" },
            { key: "roles", label: "Roles" },
            { key: "status", label: "Status" },
          ]}
          fields={[
            { key: "displayName", label: "Display name", required: true },
            { key: "email", label: "Email", required: true },
            { key: "department", label: "Department" },
            { key: "roles", label: "Roles (comma-separated)", help: ROLES.map((r) => r.value).join(", ") },
            { key: "defaultEntityId", label: "Default entity", type: "select", optionsFrom: "entities" },
            { key: "managerUserId", label: "Manager", type: "select", optionsFrom: "users" },
            { key: "password", label: "Password (leave blank to keep)", type: "text" },
            { key: "status", label: "Status", type: "select", options: [{ value: "ACTIVE", label: "Active" }, { value: "DISABLED", label: "Disabled" }] },
          ]}
        />
      )}

      {tab === "parties" && (
        <AdminCrud
          title="Counterparties"
          description="Known counterparties. Users pick from these during intake; the AI never creates them silently."
          listUrl="/refdata/parties"
          saveUrl="/admin/parties"
          columns={[
            { key: "legalName", label: "Legal name" },
            { key: "country", label: "Country" },
            { key: "type", label: "Type" },
            { key: "industry", label: "Industry" },
            { key: "sanctionsStatus", label: "Sanctions" },
          ]}
          fields={[
            { key: "legalName", label: "Legal name", required: true },
            { key: "tradingName", label: "Trading name" },
            { key: "countryCode", label: "Country code" },
            { key: "partyType", label: "Type", type: "select", options: ["CUSTOMER", "VENDOR", "PARTNER", "EMPLOYEE", "OTHER"].map((v) => ({ value: v, label: v })) },
            { key: "industry", label: "Industry" },
            { key: "sizeBand", label: "Size", type: "select", options: ["SMALL", "MID", "LARGE"].map((v) => ({ value: v, label: v })) },
            { key: "sanctionsCheckStatus", label: "Sanctions status", type: "select", options: ["NOT_SCREENED", "CLEAR", "FLAGGED"].map((v) => ({ value: v, label: v })) },
          ]}
        />
      )}

      {tab === "types" && (
        <AdminCrud
          title="Contract types"
          description="Adding a type or changing its field schema needs no deployment. The schema drives the intake form."
          listUrl="/refdata/contract-types?includeInactive=true"
          saveUrl="/admin/contract-types"
          idKey="code"
          refs={[templateRef, workflowRef]}
          columns={[
            { key: "code", label: "Code" },
            { key: "displayName", label: "Name" },
            { key: "category", label: "Category" },
            { key: "baseRisk", label: "Base risk" },
            { key: "isActive", label: "Active" },
          ]}
          fields={[
            { key: "code", label: "Code", required: true, help: "Uppercase, no spaces (e.g. LICENSE)" },
            { key: "displayName", label: "Display name", required: true },
            { key: "category", label: "Category", type: "select", options: ["PROTECTIVE", "COMMERCIAL", "HR", "BOILERPLATE"].map((v) => ({ value: v, label: v })) },
            { key: "icon", label: "Icon name", help: "e.g. shield, briefcase, cloud, lock" },
            { key: "baseRisk", label: "Base risk (0-100)", type: "number" },
            { key: "retentionYears", label: "Retention (years)", type: "number" },
            { key: "requiresLegalReviewDefault", label: "Requires legal review by default", type: "boolean" },
            { key: "autoIssueAllowed", label: "Auto-issue allowed", type: "boolean" },
            { key: "isActive", label: "Active", type: "boolean" },
            { key: "defaultTemplateId", label: "Default template", type: "select", optionsFrom: "templates" },
            { key: "defaultWorkflowId", label: "Default workflow", type: "select", optionsFrom: "workflows" },
            { key: "fieldSchema", label: "Field schema (JSON Schema)", type: "json", help: "properties, required, x-group / x-money / x-multiline hints" },
            { key: "uiGroups", label: "UI groups (JSON array)", type: "json" },
          ]}
        />
      )}

      {tab === "concepts" && (
        <AdminCrud
          title="Clause concepts"
          listUrl="/clauses/concepts"
          saveUrl="/admin/clause-concepts"
          refs={[teamRef]}
          columns={[
            { key: "conceptCode", label: "Code" },
            { key: "name", label: "Name" },
            { key: "category", label: "Category" },
            { key: "riskCategory", label: "Risk" },
            { key: "isCore", label: "Core" },
          ]}
          fields={[
            { key: "conceptCode", label: "Concept code", required: true },
            { key: "name", label: "Name", required: true },
            { key: "category", label: "Category" },
            { key: "description", label: "Description", type: "textarea" },
            { key: "riskCategory", label: "Risk category", type: "select", options: ["LOW", "MEDIUM", "HIGH"].map((v) => ({ value: v, label: v })) },
            { key: "isCore", label: "Core clause", type: "boolean" },
            { key: "owningLegalTeamId", label: "Owning legal team", type: "select", optionsFrom: "teams" },
          ]}
        />
      )}

      {tab === "variants" && (
        <AdminCrud
          title="Clause variants"
          description="Playbook positions. Variants are never hard-deleted — deprecate them instead."
          listUrl="/clauses/variants"
          saveUrl="/admin/clause-variants"
          refs={[conceptRef]}
          columns={[
            { key: "concept", label: "Concept" },
            { key: "positionTier", label: "Tier" },
            { key: "jurisdiction", label: "Jurisdiction" },
            { key: "language", label: "Lang" },
            { key: "status", label: "Status" },
          ]}
          fields={[
            { key: "clauseConceptId", label: "Concept", type: "select", optionsFrom: "concepts", required: true },
            { key: "positionTier", label: "Position tier", type: "select", options: TIERS },
            { key: "riskTier", label: "Risk tier", type: "select", options: ["LOW", "MEDIUM", "HIGH"].map((v) => ({ value: v, label: v })) },
            { key: "jurisdictionCode", label: "Jurisdiction", type: "text" },
            { key: "languageCode", label: "Language", type: "text" },
            { key: "bodyText", label: "Clause text", type: "textarea", required: true },
            { key: "guidanceNotes", label: "Guidance notes", type: "textarea" },
            { key: "status", label: "Status", type: "select", options: [{ value: "ACTIVE", label: "Active" }, { value: "DEPRECATED", label: "Deprecated" }] },
          ]}
        />
      )}

      {tab === "templates" && (
        <AdminCrud
          title="Templates"
          listUrl="/templates"
          saveUrl="/admin/templates"
          detailUrl="/templates"
          refs={[typeRef, entityRef, teamRef]}
          columns={[
            { key: "name", label: "Name" },
            { key: "contractType", label: "Type" },
            { key: "jurisdiction", label: "Jurisdiction" },
            { key: "sectionCount", label: "Sections" },
            { key: "status", label: "Status" },
          ]}
          fields={[
            { key: "name", label: "Name", required: true },
            { key: "contractTypeCode", label: "Contract type", type: "select", optionsFrom: "types", required: true },
            { key: "legalEntityId", label: "Legal entity (blank = global)", type: "select", optionsFrom: "entities" },
            { key: "jurisdictionCode", label: "Jurisdiction", type: "text" },
            { key: "languageCode", label: "Language", type: "text" },
            { key: "description", label: "Description", type: "textarea" },
            { key: "tags", label: "Tags (comma-separated)" },
            { key: "bodyHtml", label: "Body", type: "html-upload", uploadUrl: "/admin/upload-doc",
              help: "Upload a Word (.docx), .html or .txt document, or edit the HTML directly. Merge fields like {{counterparty_name}} are supported." },
            { key: "status", label: "Status", type: "select", options: [{ value: "ACTIVE", label: "Active" }, { value: "DRAFT", label: "Draft" }, { value: "DEPRECATED", label: "Deprecated" }] },
          ]}
        />
      )}

      {tab === "playbooks" && (
        <AdminCrud
          title="Playbooks"
          description="Negotiation playbooks the AI applies to review and drafting, scoped by contract type, entity or jurisdiction."
          listUrl="/playbooks"
          saveUrl="/admin/playbooks"
          deleteUrl="/admin/playbooks"
          detailUrl="/playbooks"
          refs={[typeRef, entityRef]}
          columns={[
            { key: "name", label: "Name" },
            { key: "contractType", label: "Type" },
            { key: "entity", label: "Entity" },
            { key: "jurisdiction", label: "Jurisdiction" },
            { key: "isActive", label: "Active" },
          ]}
          fields={[
            { key: "name", label: "Name", required: true },
            { key: "contractTypeCode", label: "Contract type (blank = any)", type: "select", optionsFrom: "types" },
            { key: "legalEntityId", label: "Legal entity (blank = any)", type: "select", optionsFrom: "entities" },
            { key: "jurisdiction", label: "Jurisdiction (blank = any)", type: "text" },
            { key: "language", label: "Language" },
            { key: "description", label: "Description", type: "textarea" },
            { key: "bodyHtml", label: "Body", type: "html-upload", uploadUrl: "/admin/upload-doc",
              help: "Upload a Word (.docx), .html or .txt playbook document, or edit the HTML directly. Applied automatically to AI reviews of matching contracts." },
            { key: "isActive", label: "Active", type: "boolean" },
          ]}
        />
      )}

      {tab === "signing" && (
        <AdminCrud
          title="Signing authority"
          description="Enforced as a hard guard before signature dispatch."
          listUrl="/admin/signing-authorities"
          saveUrl="/admin/signing-authorities"
          deleteUrl="/admin/signing-authorities"
          refs={[entityRef, userRef, typeRef]}
          columns={[
            { key: "entity", label: "Entity" },
            { key: "user", label: "Authorised user" },
            { key: "contractTypeCode", label: "Type" },
            { key: "maxValueAmount", label: "Max value" },
            { key: "currency", label: "Ccy" },
          ]}
          fields={[
            { key: "legalEntityId", label: "Entity", type: "select", optionsFrom: "entities", required: true },
            { key: "userId", label: "Authorised user", type: "select", optionsFrom: "users", required: true },
            { key: "contractTypeCode", label: "Contract type (blank = all)", type: "select", optionsFrom: "types" },
            { key: "maxValueAmount", label: "Max value", type: "number" },
            { key: "currency", label: "Currency", type: "text" },
            { key: "validFrom", label: "Valid from (YYYY-MM-DD)", type: "text" },
            { key: "validTo", label: "Valid to (YYYY-MM-DD, blank = open)", type: "text" },
          ]}
        />
      )}

      {tab === "rules" && (
        <AdminCrud
          title="Assignment rules"
          listUrl="/admin/assignment-rules"
          saveUrl="/admin/assignment-rules"
          refs={[teamRef]}
          columns={[
            { key: "name", label: "Name" },
            { key: "priority", label: "Priority" },
            { key: "targetType", label: "Target" },
            { key: "isActive", label: "Active" },
          ]}
          fields={[
            { key: "name", label: "Name", required: true },
            { key: "priority", label: "Priority (lower first)", type: "number" },
            { key: "targetType", label: "Target type", type: "select", options: ["USER", "TEAM", "ROUND_ROBIN", "LOAD_BALANCED"].map((v) => ({ value: v, label: v })) },
            { key: "targetId", label: "Target team", type: "select", optionsFrom: "teams" },
            { key: "conditionExpression", label: "Condition (JSON)", type: "json" },
            { key: "isActive", label: "Active", type: "boolean" },
          ]}
        />
      )}

      {tab === "workflows" && (
        <AdminCrud
          title="Workflow definitions"
          description="JSON state machines. Publishing a new version never alters in-flight instances."
          listUrl="/admin/workflows"
          saveUrl="/admin/workflows"
          columns={[
            { key: "key", label: "Key" },
            { key: "name", label: "Name" },
            { key: "versionNo", label: "Version" },
            { key: "status", label: "Status" },
          ]}
          fields={[
            { key: "key", label: "Key", required: true },
            { key: "name", label: "Name", required: true },
            { key: "status", label: "Status", type: "select", options: [{ value: "PUBLISHED", label: "Published" }, { value: "DRAFT", label: "Draft" }] },
            { key: "scopeExpression", label: "Scope (JSON)", type: "json" },
            { key: "definition", label: "Definition (JSON state machine)", type: "json" },
          ]}
        />
      )}

      {tab === "dimensions" && (
        <AdminCrud
          title="Access dimensions"
          description="Freely define the dimensions people can request access along. 'derivation' says how a contract's value on this dimension is computed."
          listUrl="/admin/access/dimensions"
          saveUrl="/admin/access/dimensions"
          idKey="code"
          columns={[
            { key: "code", label: "Code" },
            { key: "name", label: "Name" },
            { key: "derivation", label: "Derivation" },
            { key: "isActive", label: "Active" },
          ]}
          fields={[
            { key: "code", label: "Code", required: true, help: "Uppercase, no spaces (e.g. BUSINESS_UNIT)" },
            { key: "name", label: "Name", required: true },
            { key: "description", label: "Description", type: "textarea" },
            {
              key: "derivation",
              label: "Derivation",
              type: "select",
              required: true,
              options: [
                { value: "entity:data_residency_region", label: "Contracting entity — region" },
                { value: "entity:short_name", label: "Contracting entity — code" },
                { value: "entity:country_code", label: "Contracting entity — country" },
                { value: "field:contract_type_code", label: "Contract — type" },
                { value: "field:confidentiality_level", label: "Contract — confidentiality" },
                { value: "field:status", label: "Contract — status" },
                { value: "owner_department", label: "Owner's department" },
              ],
            },
            { key: "valueOptions", label: "Suggested values (JSON array)", type: "json" },
            { key: "sortOrder", label: "Sort order", type: "number" },
            { key: "isActive", label: "Active", type: "boolean" },
          ]}
        />
      )}

      {tab === "scopes" && (
        <AdminCrud
          title="Approver scopes"
          description="Which access an approver owns. A request is approvable only by a scope that covers it on every constrained dimension. Constraints JSON like {&quot;REGION&quot;:[&quot;EU&quot;],&quot;FUNCTION&quot;:[&quot;Sales&quot;]} — omit a dimension (or use [&quot;*&quot;]) for 'any'."
          listUrl="/admin/access/approver-scopes"
          saveUrl="/admin/access/approver-scopes"
          deleteUrl="/admin/access/approver-scopes"
          refs={[userRef]}
          columns={[
            { key: "user", label: "Approver" },
            { key: "name", label: "Name" },
            { key: "scope", label: "Owns" },
            { key: "isActive", label: "Active" },
          ]}
          fields={[
            { key: "userId", label: "Approver", type: "select", optionsFrom: "users", required: true },
            { key: "name", label: "Scope name", required: true },
            { key: "constraints", label: "Constraints (JSON)", type: "json", help: '{ "REGION": ["EU","UK"], "FUNCTION": ["Sales"] }' },
            { key: "isActive", label: "Active", type: "boolean" },
          ]}
        />
      )}

      {tab === "grants" && (
        <AdminCrud
          title="Access grants"
          description="Direct grants (bypassing the request flow). Revoking sets status to REVOKED."
          listUrl="/admin/access/grants"
          saveUrl="/admin/access/grants"
          deleteUrl="/admin/access/grants"
          refs={[userRef]}
          columns={[
            { key: "user", label: "User" },
            { key: "name", label: "Name" },
            { key: "scopeText", label: "Scope" },
            { key: "status", label: "Status" },
          ]}
          fields={[
            { key: "userId", label: "User", type: "select", optionsFrom: "users", required: true },
            { key: "name", label: "Name" },
            { key: "constraints", label: "Constraints (JSON)", type: "json", help: '{ "CONTRACT_TYPE": ["MSA","SOW"] }' },
            { key: "status", label: "Status", type: "select", options: [{ value: "ACTIVE", label: "Active" }, { value: "REVOKED", label: "Revoked" }] },
          ]}
        />
      )}

      {tab === "review-rules" && (
        <AdminCrud
          title="AI review rules"
          description="The rule checklist the AI checks every draft against on the Document tab. Lower sort runs first."
          listUrl="/admin/ai-review-rules"
          saveUrl="/admin/ai-review-rules"
          deleteUrl="/admin/ai-review-rules"
          idKey="code"
          columns={[
            { key: "sort", label: "Order" },
            { key: "code", label: "Code" },
            { key: "label", label: "Label" },
            { key: "severity", label: "Severity" },
            { key: "isActive", label: "Active" },
          ]}
          fields={[
            { key: "code", label: "Code", required: true, help: "Stable identifier reported back in AI findings." },
            { key: "label", label: "Label", required: true },
            { key: "instruction", label: "Instruction for the AI", type: "textarea", required: true },
            { key: "severity", label: "Severity", type: "select", options: ["CRITICAL", "HIGH", "MEDIUM", "LOW"].map((s) => ({ value: s, label: s })) },
            { key: "isActive", label: "Active", type: "boolean" },
            { key: "sort", label: "Sort order", type: "number" },
          ]}
        />
      )}
    </div>
  );
}
