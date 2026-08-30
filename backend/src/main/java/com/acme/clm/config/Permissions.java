package com.acme.clm.config;

import java.util.*;

/**
 * Feature-level permissions and the role → permission mapping (plan §12: RBAC plus
 * attribute-based scoping). Menu items, routes and API endpoints are gated on these.
 */
public final class Permissions {
    private Permissions() {}

    public static final String VIEW_DASHBOARD     = "VIEW_DASHBOARD";
    public static final String CREATE_INTAKE      = "CREATE_INTAKE";
    public static final String VIEW_CONTRACTS     = "VIEW_CONTRACTS";
    public static final String EDIT_CONTRACT      = "EDIT_CONTRACT";
    public static final String EDIT_DOCUMENT      = "EDIT_DOCUMENT";
    public static final String APPROVE            = "APPROVE";
    public static final String VIEW_OBLIGATIONS   = "VIEW_OBLIGATIONS";
    public static final String MANAGE_OBLIGATIONS = "MANAGE_OBLIGATIONS";
    public static final String VIEW_CLAUSES       = "VIEW_CLAUSES";
    public static final String MANAGE_CLAUSES     = "MANAGE_CLAUSES";
    public static final String VIEW_TEMPLATES     = "VIEW_TEMPLATES";
    public static final String MANAGE_TEMPLATES   = "MANAGE_TEMPLATES";
    public static final String VIEW_INQUIRY       = "VIEW_INQUIRY";
    public static final String VIEW_AI_LOG        = "VIEW_AI_LOG";
    public static final String VIEW_AUDIT         = "VIEW_AUDIT";
    public static final String MANAGE_MASTERDATA  = "MANAGE_MASTERDATA";
    public static final String MANAGE_USERS       = "MANAGE_USERS";
    public static final String MANAGE_WORKFLOWS   = "MANAGE_WORKFLOWS";
    public static final String COMMENT            = "COMMENT";

    private static final Set<String> ALL = Set.of(
            VIEW_DASHBOARD, CREATE_INTAKE, VIEW_CONTRACTS, EDIT_CONTRACT, EDIT_DOCUMENT, APPROVE,
            VIEW_OBLIGATIONS, MANAGE_OBLIGATIONS, VIEW_CLAUSES, MANAGE_CLAUSES, VIEW_TEMPLATES,
            MANAGE_TEMPLATES, VIEW_INQUIRY, VIEW_AI_LOG, VIEW_AUDIT, MANAGE_MASTERDATA, MANAGE_USERS,
            MANAGE_WORKFLOWS, COMMENT);

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            "ADMIN", ALL,
            "GENERAL_COUNSEL", ALL,
            "LEGAL", Set.of(VIEW_DASHBOARD, CREATE_INTAKE, VIEW_CONTRACTS, EDIT_CONTRACT, EDIT_DOCUMENT,
                    APPROVE, VIEW_OBLIGATIONS, MANAGE_OBLIGATIONS, VIEW_CLAUSES, MANAGE_CLAUSES,
                    VIEW_TEMPLATES, MANAGE_TEMPLATES, VIEW_INQUIRY, VIEW_AI_LOG, VIEW_AUDIT, COMMENT),
            "APPROVER", Set.of(VIEW_DASHBOARD, VIEW_CONTRACTS, APPROVE, VIEW_OBLIGATIONS,
                    VIEW_INQUIRY, VIEW_CLAUSES, COMMENT),
            "FINANCE", Set.of(VIEW_DASHBOARD, VIEW_CONTRACTS, APPROVE, VIEW_OBLIGATIONS,
                    VIEW_INQUIRY, COMMENT),
            "REQUESTER", Set.of(VIEW_DASHBOARD, CREATE_INTAKE, VIEW_CONTRACTS, VIEW_OBLIGATIONS,
                    VIEW_INQUIRY, COMMENT));

    /** Resolve the union of permissions for a comma-separated role string. */
    public static Set<String> forRoles(String rolesCsv) {
        Set<String> out = new LinkedHashSet<>();
        if (rolesCsv == null) return out;
        for (String r : rolesCsv.split(",")) {
            Set<String> p = BY_ROLE.get(r.trim());
            if (p != null) out.addAll(p);
        }
        return out;
    }

    public static Set<String> allPermissions() { return ALL; }
    public static Set<String> knownRoles() { return BY_ROLE.keySet(); }
}
