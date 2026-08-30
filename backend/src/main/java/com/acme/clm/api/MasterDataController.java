package com.acme.clm.api;

import com.acme.clm.common.ApiExceptions;
import com.acme.clm.common.Json;
import com.acme.clm.config.CurrentUser;
import com.acme.clm.config.Permissions;
import com.acme.clm.domain.*;
import com.acme.clm.repo.Repos;
import com.acme.clm.service.AuditService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Master-data maintenance (plan §14.2: "build the configuration UI alongside each configurable
 * feature"). Every list of allowed values the intake form and AI rely on is edited here.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('PERM_MANAGE_MASTERDATA')")
public class MasterDataController {

    private final Repos.LegalEntities entities;
    private final Repos.LegalTeams teams;
    private final Repos.Users users;
    private final Repos.Parties parties;
    private final Repos.ContractTypes types;
    private final Repos.ClauseConcepts concepts;
    private final Repos.ClauseVariants variants;
    private final Repos.Templates templates;
    private final Repos.TemplateSections sections;
    private final Repos.AssignmentRules assignmentRules;
    private final Repos.SigningAuthorities signingAuthorities;
    private final Repos.WorkflowDefinitions workflows;
    private final Repos.AccessDimensions accessDimensions;
    private final Repos.ApproverScopes approverScopes;
    private final Repos.AccessGrants accessGrants;
    private final Repos.AiReviewRules reviewRules;
    private final Repos.Playbooks playbooks;
    private final com.acme.clm.service.AccessService access;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUser current;
    private final com.acme.clm.service.DocToHtmlService docToHtml;

    public MasterDataController(Repos.LegalEntities entities, Repos.LegalTeams teams, Repos.Users users,
                               Repos.Parties parties, Repos.ContractTypes types, Repos.ClauseConcepts concepts,
                               Repos.ClauseVariants variants, Repos.Templates templates, Repos.TemplateSections sections,
                               Repos.AssignmentRules assignmentRules, Repos.SigningAuthorities signingAuthorities,
                               Repos.WorkflowDefinitions workflows, Repos.AccessDimensions accessDimensions,
                               Repos.ApproverScopes approverScopes, Repos.AccessGrants accessGrants,
                               com.acme.clm.service.AccessService access, PasswordEncoder encoder, AuditService audit,
                               CurrentUser current, Repos.AiReviewRules reviewRules, Repos.Playbooks playbooks,
                               com.acme.clm.service.DocToHtmlService docToHtml) {
        this.entities = entities;
        this.teams = teams;
        this.users = users;
        this.parties = parties;
        this.types = types;
        this.concepts = concepts;
        this.variants = variants;
        this.templates = templates;
        this.sections = sections;
        this.assignmentRules = assignmentRules;
        this.signingAuthorities = signingAuthorities;
        this.workflows = workflows;
        this.accessDimensions = accessDimensions;
        this.approverScopes = approverScopes;
        this.accessGrants = accessGrants;
        this.reviewRules = reviewRules;
        this.playbooks = playbooks;
        this.access = access;
        this.encoder = encoder;
        this.audit = audit;
        this.current = current;
        this.docToHtml = docToHtml;
    }

    // ---- helpers ----
    private static String s(Map<String, Object> b, String k) { Object v = b.get(k); return v == null ? null : String.valueOf(v); }
    private static Integer i(Map<String, Object> b, String k) { Object v = b.get(k); try { return v == null ? null : (int) Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; } }
    private static Boolean bool(Map<String, Object> b, String k) { Object v = b.get(k); return v == null ? null : Boolean.valueOf(String.valueOf(v).equalsIgnoreCase("true")); }
    private static BigDecimal dec(Map<String, Object> b, String k) { Object v = b.get(k); try { return v == null || String.valueOf(v).isBlank() ? null : new BigDecimal(String.valueOf(v)); } catch (Exception e) { return null; } }
    private static UUID uid(Map<String, Object> b, String k) { Object v = b.get(k); try { return v == null || String.valueOf(v).isBlank() ? null : UUID.fromString(String.valueOf(v)); } catch (Exception e) { return null; } }
    private void log(String type, String id, String action, Object after) { audit.record(type, id, action, current.id(), null, after); }

    // ============================ Legal entities ============================
    @PostMapping("/entities")
    public LegalEntity saveEntity(@RequestBody Map<String, Object> b) {
        LegalEntity e = uid(b, "id") != null ? entities.findById(uid(b, "id")).orElseThrow() : new LegalEntity();
        if (b.containsKey("legalName")) e.legalName = s(b, "legalName");
        if (b.containsKey("shortName")) e.shortName = s(b, "shortName");
        if (b.containsKey("countryCode")) e.countryCode = s(b, "countryCode");
        if (b.containsKey("registrationNumber")) e.registrationNumber = s(b, "registrationNumber");
        if (b.containsKey("defaultGoverningLaw")) e.defaultGoverningLaw = s(b, "defaultGoverningLaw");
        if (b.containsKey("defaultLanguage")) e.defaultLanguage = s(b, "defaultLanguage");
        if (b.containsKey("dataResidencyRegion")) e.dataResidencyRegion = s(b, "dataResidencyRegion");
        if (b.containsKey("legalTeamId")) e.legalTeamId = uid(b, "legalTeamId");
        if (b.containsKey("status")) e.status = s(b, "status");
        entities.save(e);
        log("LEGAL_ENTITY", e.id.toString(), "SAVED", Map.of("shortName", e.shortName));
        return e;
    }

    // ============================ Legal teams ============================
    @PostMapping("/teams")
    public LegalTeam saveTeam(@RequestBody Map<String, Object> b) {
        LegalTeam t = uid(b, "id") != null ? teams.findById(uid(b, "id")).orElseThrow() : new LegalTeam();
        if (b.containsKey("name")) t.name = s(b, "name");
        if (b.containsKey("region")) t.region = s(b, "region");
        if (b.containsKey("defaultQueueSlaHours")) t.defaultQueueSlaHours = Optional.ofNullable(i(b, "defaultQueueSlaHours")).orElse(48);
        teams.save(t);
        log("LEGAL_TEAM", t.id.toString(), "SAVED", Map.of("name", t.name));
        return t;
    }

    // ============================ Users ============================
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PERM_MANAGE_USERS')")
    public List<Map<String, Object>> listUsers() {
        return users.findAll().stream().sorted(Comparator.comparing(u -> u.email)).map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id); m.put("email", u.email); m.put("displayName", u.displayName);
            m.put("department", u.department); m.put("roles", u.roles); m.put("status", u.status);
            m.put("defaultEntityId", u.defaultEntityId); m.put("managerUserId", u.managerUserId);
            return m;
        }).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('PERM_MANAGE_USERS')")
    public Map<String, Object> saveUser(@RequestBody Map<String, Object> b) {
        AppUser u = uid(b, "id") != null ? users.findById(uid(b, "id")).orElseThrow() : new AppUser();
        if (b.containsKey("email")) u.email = s(b, "email");
        if (b.containsKey("displayName")) u.displayName = s(b, "displayName");
        if (b.containsKey("department")) u.department = s(b, "department");
        if (b.containsKey("roles")) {
            String roles = s(b, "roles");
            for (String r : roles.split(",")) {
                if (!Permissions.knownRoles().contains(r.trim()))
                    throw new ApiExceptions.BadRequestException("Unknown role: " + r);
            }
            u.roles = roles;
        }
        if (b.containsKey("defaultEntityId")) u.defaultEntityId = uid(b, "defaultEntityId");
        if (b.containsKey("managerUserId")) u.managerUserId = uid(b, "managerUserId");
        if (b.containsKey("status")) u.status = s(b, "status");
        if (u.id == null) {
            String pw = b.containsKey("password") ? s(b, "password") : "demo1234";
            u.passwordHash = encoder.encode(pw);
        } else if (b.containsKey("password") && !s(b, "password").isBlank()) {
            u.passwordHash = encoder.encode(s(b, "password"));
        }
        users.save(u);
        log("APP_USER", u.id.toString(), "SAVED", Map.of("email", u.email, "roles", u.roles));
        return Map.of("id", u.id, "email", u.email, "roles", u.roles, "status", u.status);
    }

    // ============================ Parties ============================
    @PostMapping("/parties")
    public Party saveParty(@RequestBody Map<String, Object> b) {
        Party p = uid(b, "id") != null ? parties.findById(uid(b, "id")).orElseThrow() : new Party();
        if (b.containsKey("legalName")) p.legalName = s(b, "legalName");
        if (b.containsKey("tradingName")) p.tradingName = s(b, "tradingName");
        if (b.containsKey("countryCode")) p.countryCode = s(b, "countryCode");
        if (b.containsKey("registrationNumber")) p.registrationNumber = s(b, "registrationNumber");
        if (b.containsKey("partyType")) p.partyType = s(b, "partyType");
        if (b.containsKey("industry")) p.industry = s(b, "industry");
        if (b.containsKey("sizeBand")) p.sizeBand = s(b, "sizeBand");
        if (b.containsKey("sanctionsCheckStatus")) {
            p.sanctionsCheckStatus = s(b, "sanctionsCheckStatus");
            p.sanctionsCheckedAt = java.time.Instant.now();
        }
        parties.save(p);
        log("PARTY", p.id.toString(), "SAVED", Map.of("legalName", p.legalName));
        return p;
    }

    // ============================ Contract types ============================
    @PostMapping("/contract-types")
    public ContractTypeDefinition saveType(@RequestBody Map<String, Object> b) {
        String code = s(b, "code");
        if (code == null || code.isBlank()) throw new ApiExceptions.BadRequestException("code is required");
        ContractTypeDefinition t = types.findById(code).orElseGet(() -> { var x = new ContractTypeDefinition(); x.code = code; return x; });
        if (b.containsKey("displayName")) t.displayName = s(b, "displayName");
        if (b.containsKey("category")) t.category = s(b, "category");
        if (b.containsKey("icon")) t.icon = s(b, "icon");
        if (b.containsKey("requiresLegalReviewDefault")) t.requiresLegalReviewDefault = Boolean.TRUE.equals(bool(b, "requiresLegalReviewDefault"));
        if (b.containsKey("autoIssueAllowed")) t.autoIssueAllowed = Boolean.TRUE.equals(bool(b, "autoIssueAllowed"));
        if (b.containsKey("retentionYears")) t.retentionYears = Optional.ofNullable(i(b, "retentionYears")).orElse(7);
        if (b.containsKey("baseRisk")) t.baseRisk = Optional.ofNullable(i(b, "baseRisk")).orElse(20);
        if (b.containsKey("isActive")) t.isActive = Boolean.TRUE.equals(bool(b, "isActive"));
        if (b.containsKey("defaultTemplateId")) t.defaultTemplateId = uid(b, "defaultTemplateId");
        if (b.containsKey("defaultWorkflowId")) t.defaultWorkflowId = uid(b, "defaultWorkflowId");
        if (b.containsKey("fieldSchema")) {
            Object fs = b.get("fieldSchema");
            String json = fs instanceof String ? (String) fs : Json.write(fs);
            Json.read(json); // validate
            t.fieldSchema = json;
        }
        if (b.containsKey("uiGroups")) {
            Object g = b.get("uiGroups");
            t.uiGroups = g instanceof String ? (String) g : Json.write(g);
        }
        types.save(t);
        log("CONTRACT_TYPE", code, "SAVED", Map.of("displayName", t.displayName));
        return t;
    }

    // ============================ Clause concepts & variants ============================
    @PostMapping("/clause-concepts")
    public ClauseConcept saveConcept(@RequestBody Map<String, Object> b) {
        ClauseConcept c = uid(b, "id") != null ? concepts.findById(uid(b, "id")).orElseThrow() : new ClauseConcept();
        if (b.containsKey("conceptCode")) c.conceptCode = s(b, "conceptCode");
        if (b.containsKey("name")) c.name = s(b, "name");
        if (b.containsKey("category")) c.category = s(b, "category");
        if (b.containsKey("description")) c.description = s(b, "description");
        if (b.containsKey("riskCategory")) c.riskCategory = s(b, "riskCategory");
        if (b.containsKey("isCore")) c.isCore = Boolean.TRUE.equals(bool(b, "isCore"));
        if (b.containsKey("owningLegalTeamId")) c.owningLegalTeamId = uid(b, "owningLegalTeamId");
        concepts.save(c);
        log("CLAUSE_CONCEPT", c.id.toString(), "SAVED", Map.of("name", c.name));
        return c;
    }

    @PostMapping("/clause-variants")
    public ClauseVariant saveVariant(@RequestBody Map<String, Object> b) {
        ClauseVariant v = uid(b, "id") != null ? variants.findById(uid(b, "id")).orElseThrow() : new ClauseVariant();
        if (b.containsKey("clauseConceptId")) v.clauseConceptId = uid(b, "clauseConceptId");
        if (b.containsKey("jurisdictionCode")) v.jurisdictionCode = s(b, "jurisdictionCode");
        if (b.containsKey("languageCode")) v.languageCode = s(b, "languageCode");
        if (b.containsKey("bodyText")) v.bodyText = s(b, "bodyText");
        if (b.containsKey("positionTier")) v.positionTier = s(b, "positionTier");
        if (b.containsKey("riskTier")) v.riskTier = s(b, "riskTier");
        if (b.containsKey("guidanceNotes")) v.guidanceNotes = s(b, "guidanceNotes");
        if (b.containsKey("translationStatus")) v.translationStatus = s(b, "translationStatus");
        if (b.containsKey("status")) v.status = s(b, "status");   // ACTIVE | DEPRECATED — never hard-deleted
        if (v.id == null) v.approvedBy = current.id();
        variants.save(v);
        log("CLAUSE_VARIANT", v.id.toString(), "SAVED", Map.of("tier", v.positionTier));
        return v;
    }

    // ============================ Templates ============================
    @PostMapping("/templates")
    public Template saveTemplate(@RequestBody Map<String, Object> b) {
        Template t = uid(b, "id") != null ? templates.findById(uid(b, "id")).orElseThrow() : new Template();
        if (b.containsKey("name")) t.name = s(b, "name");
        if (b.containsKey("contractTypeCode")) t.contractTypeCode = s(b, "contractTypeCode");
        if (b.containsKey("legalEntityId")) t.legalEntityId = uid(b, "legalEntityId");
        if (b.containsKey("jurisdictionCode")) t.jurisdictionCode = s(b, "jurisdictionCode");
        if (b.containsKey("languageCode")) t.languageCode = s(b, "languageCode");
        if (b.containsKey("status")) t.status = s(b, "status");
        if (b.containsKey("description")) t.description = s(b, "description");
        if (b.containsKey("tags")) t.tags = s(b, "tags");
        if (b.containsKey("bodyHtml")) t.bodyHtml = s(b, "bodyHtml");
        if (b.containsKey("owningLegalTeamId")) t.owningLegalTeamId = uid(b, "owningLegalTeamId");
        if (t.id == null) { t.approvedBy = current.id(); t.approvedAt = java.time.Instant.now(); }
        templates.save(t);
        log("TEMPLATE", t.id.toString(), "SAVED", Map.of("name", t.name));
        return t;
    }

    /** Upload a .docx/.html/.txt document and get its HTML body back (template & playbook bodies). */
    @PostMapping(value = "/upload-doc", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadDoc(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String html = docToHtml.convert(file);
        log("DOCUMENT", java.util.UUID.randomUUID().toString(), "DOC_UPLOADED", Map.of("file", String.valueOf(file.getOriginalFilename())));
        return Map.of("html", html);
    }

    // ============================ Playbooks ============================
    @PostMapping("/playbooks")
    public Playbook savePlaybook(@RequestBody Map<String, Object> b) {
        Playbook p = uid(b, "id") != null ? playbooks.findById(uid(b, "id")).orElseThrow() : new Playbook();
        if (b.containsKey("name")) p.name = s(b, "name");
        if (b.containsKey("contractTypeCode")) {
            String ct = s(b, "contractTypeCode");
            p.contractTypeCode = ct == null || ct.isBlank() ? null : ct;
        }
        if (b.containsKey("legalEntityId")) p.legalEntityId = uid(b, "legalEntityId");
        if (b.containsKey("jurisdiction")) p.jurisdiction = s(b, "jurisdiction");
        if (b.containsKey("language")) p.language = s(b, "language");
        if (b.containsKey("description")) p.description = s(b, "description");
        if (b.containsKey("bodyHtml")) p.bodyHtml = s(b, "bodyHtml");
        if (b.containsKey("isActive")) p.isActive = Boolean.TRUE.equals(bool(b, "isActive"));
        if (p.id == null) p.createdBy = current.id();
        if (p.name == null || p.name.isBlank()) throw new ApiExceptions.BadRequestException("name is required");
        playbooks.save(p);
        log("PLAYBOOK", p.id.toString(), "SAVED", Map.of("name", p.name));
        return p;
    }

    @DeleteMapping("/playbooks/{id}")
    public void deletePlaybook(@PathVariable UUID id) {
        playbooks.findById(id).ifPresent(p -> {
            playbooks.delete(p);
            log("PLAYBOOK", id.toString(), "DELETED", Map.of("name", p.name));
        });
    }

    @PostMapping("/template-sections")
    public TemplateSection saveSection(@RequestBody Map<String, Object> b) {
        TemplateSection sec = uid(b, "id") != null ? sections.findById(uid(b, "id")).orElseThrow() : new TemplateSection();
        if (b.containsKey("templateId")) sec.templateId = uid(b, "templateId");
        if (b.containsKey("sortOrder")) sec.sortOrder = Optional.ofNullable(i(b, "sortOrder")).orElse(0);
        if (b.containsKey("heading")) sec.heading = s(b, "heading");
        if (b.containsKey("isOptional")) sec.isOptional = Boolean.TRUE.equals(bool(b, "isOptional"));
        if (b.containsKey("clauseConceptId")) sec.clauseConceptId = uid(b, "clauseConceptId");
        if (b.containsKey("defaultClauseVariantId")) sec.defaultClauseVariantId = uid(b, "defaultClauseVariantId");
        if (b.containsKey("staticBody")) sec.staticBody = s(b, "staticBody");
        sections.save(sec);
        return sec;
    }

    @DeleteMapping("/template-sections/{id}")
    public void deleteSection(@PathVariable UUID id) { sections.deleteById(id); }

    // ============================ Assignment rules ============================
    @GetMapping("/assignment-rules")
    public List<AssignmentRule> rules() { return assignmentRules.findAll(); }

    @PostMapping("/assignment-rules")
    public AssignmentRule saveRule(@RequestBody Map<String, Object> b) {
        AssignmentRule r = uid(b, "id") != null ? assignmentRules.findById(uid(b, "id")).orElseThrow() : new AssignmentRule();
        if (b.containsKey("name")) r.name = s(b, "name");
        if (b.containsKey("priority")) r.priority = Optional.ofNullable(i(b, "priority")).orElse(100);
        if (b.containsKey("targetType")) r.targetType = s(b, "targetType");
        if (b.containsKey("targetId")) r.targetId = uid(b, "targetId");
        if (b.containsKey("isActive")) r.isActive = Boolean.TRUE.equals(bool(b, "isActive"));
        if (b.containsKey("conditionExpression")) {
            Object ce = b.get("conditionExpression");
            r.conditionExpression = ce instanceof String ? (String) ce : Json.write(ce);
        }
        assignmentRules.save(r);
        return r;
    }

    // ============================ Signing authorities ============================
    @GetMapping("/signing-authorities")
    public List<Map<String, Object>> signingAuths() {
        return signingAuthorities.findAll().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id); m.put("legalEntityId", a.legalEntityId); m.put("userId", a.userId);
            m.put("user", users.findById(a.userId).map(u -> u.displayName).orElse(null));
            m.put("entity", entities.findById(a.legalEntityId).map(e -> e.shortName).orElse(null));
            m.put("contractTypeCode", a.contractTypeCode);
            m.put("maxValueAmount", a.maxValueAmount); m.put("currency", a.currency);
            m.put("validFrom", a.validFrom); m.put("validTo", a.validTo);
            return m;
        }).toList();
    }

    @PostMapping("/signing-authorities")
    public SigningAuthority saveSigningAuth(@RequestBody Map<String, Object> b) {
        SigningAuthority a = uid(b, "id") != null ? signingAuthorities.findById(uid(b, "id")).orElseThrow() : new SigningAuthority();
        if (b.containsKey("legalEntityId")) a.legalEntityId = uid(b, "legalEntityId");
        if (b.containsKey("userId")) a.userId = uid(b, "userId");
        if (b.containsKey("contractTypeCode")) a.contractTypeCode = s(b, "contractTypeCode");
        if (b.containsKey("maxValueAmount")) a.maxValueAmount = dec(b, "maxValueAmount");
        if (b.containsKey("currency")) a.currency = s(b, "currency");
        if (b.containsKey("validFrom")) a.validFrom = b.get("validFrom") == null ? LocalDate.now() : LocalDate.parse(s(b, "validFrom"));
        if (b.containsKey("validTo")) a.validTo = b.get("validTo") == null || s(b, "validTo").isBlank() ? null : LocalDate.parse(s(b, "validTo"));
        signingAuthorities.save(a);
        log("SIGNING_AUTHORITY", a.id.toString(), "SAVED", null);
        return a;
    }

    @DeleteMapping("/signing-authorities/{id}")
    public void deleteSigningAuth(@PathVariable UUID id) { signingAuthorities.deleteById(id); }

    // ============================ Workflow definitions ============================
    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('PERM_MANAGE_WORKFLOWS')")
    public List<Map<String, Object>> workflowDefs() {
        return workflows.findAll().stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.id); m.put("key", w.key); m.put("name", w.name);
            m.put("versionNo", w.versionNo); m.put("status", w.status);
            m.put("definition", Json.read(w.definition));
            m.put("scopeExpression", Json.read(w.scopeExpression));
            return m;
        }).toList();
    }

    // ============================ Access dimensions ============================
    @GetMapping("/access/dimensions")
    public List<AccessDimension> accessDims() { return accessDimensions.findAll(); }

    @PostMapping("/access/dimensions")
    public AccessDimension saveAccessDim(@RequestBody Map<String, Object> b) {
        String code = s(b, "code");
        if (code == null || code.isBlank()) throw new ApiExceptions.BadRequestException("code is required");
        AccessDimension d = accessDimensions.findById(code).orElseGet(() -> { var x = new AccessDimension(); x.code = code; return x; });
        if (b.containsKey("name")) d.name = s(b, "name");
        if (b.containsKey("description")) d.description = s(b, "description");
        if (b.containsKey("derivation")) d.derivation = s(b, "derivation");
        if (b.containsKey("sortOrder")) d.sortOrder = Optional.ofNullable(i(b, "sortOrder")).orElse(100);
        if (b.containsKey("isActive")) d.isActive = Boolean.TRUE.equals(bool(b, "isActive"));
        if (b.containsKey("valueOptions")) {
            Object v = b.get("valueOptions");
            d.valueOptions = v instanceof String ? (String) v : Json.write(v);
        }
        accessDimensions.save(d);
        log("ACCESS_DIMENSION", code, "SAVED", Map.of("name", d.name));
        return d;
    }

    // ============================ Approver scopes ============================
    @GetMapping("/access/approver-scopes")
    public List<Map<String, Object>> approverScopeList() {
        return approverScopes.findAll().stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", x.id);
            m.put("userId", x.userId);
            m.put("user", users.findById(x.userId).map(u -> u.displayName).orElse("?"));
            m.put("name", x.name);
            m.put("constraints", access.constraintsOf(x.constraints));
            m.put("scope", access.humanScope(access.constraintsOf(x.constraints)));
            m.put("isActive", x.isActive);
            return m;
        }).toList();
    }

    @PostMapping("/access/approver-scopes")
    public ApproverScope saveApproverScope(@RequestBody Map<String, Object> b) {
        ApproverScope x = uid(b, "id") != null ? approverScopes.findById(uid(b, "id")).orElseThrow() : new ApproverScope();
        if (b.containsKey("userId")) x.userId = uid(b, "userId");
        if (b.containsKey("name")) x.name = s(b, "name");
        if (b.containsKey("isActive")) x.isActive = Boolean.TRUE.equals(bool(b, "isActive"));
        if (b.containsKey("constraints")) {
            Object v = b.get("constraints");
            x.constraints = v instanceof String ? (String) v : Json.write(v);
        }
        approverScopes.save(x);
        log("APPROVER_SCOPE", x.id.toString(), "SAVED", null);
        return x;
    }

    @DeleteMapping("/access/approver-scopes/{id}")
    public void deleteApproverScope(@PathVariable UUID id) { approverScopes.deleteById(id); }

    // ============================ Direct grants ============================
    @GetMapping("/access/grants")
    public List<Map<String, Object>> grantList() {
        return accessGrants.findAll().stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>(access.describeGrant(g));
            m.put("userId", g.userId);
            m.put("user", users.findById(g.userId).map(u -> u.displayName).orElse("?"));
            m.put("scopeText", access.humanScope(access.constraintsOf(g.constraints)));
            return m;
        }).toList();
    }

    @PostMapping("/access/grants")
    public AccessGrant saveGrant(@RequestBody Map<String, Object> b) {
        AccessGrant g = uid(b, "id") != null ? accessGrants.findById(uid(b, "id")).orElseThrow() : new AccessGrant();
        if (b.containsKey("userId")) g.userId = uid(b, "userId");
        if (b.containsKey("name")) g.name = s(b, "name");
        if (b.containsKey("status")) g.status = s(b, "status");
        if (g.id == null) { g.source = "DIRECT"; g.grantedBy = current.id(); }
        if (b.containsKey("constraints")) {
            Object v = b.get("constraints");
            g.constraints = v instanceof String ? (String) v : Json.write(v);
        }
        accessGrants.save(g);
        log("ACCESS_GRANT", g.id.toString(), "SAVED", Map.of("user", String.valueOf(g.userId)));
        return g;
    }

    @DeleteMapping("/access/grants/{id}")
    public void revokeGrant(@PathVariable UUID id) {
        accessGrants.findById(id).ifPresent(g -> { g.status = "REVOKED"; accessGrants.save(g); });
    }

    @PostMapping("/workflows")
    @PreAuthorize("hasAuthority('PERM_MANAGE_WORKFLOWS')")
    public Map<String, Object> saveWorkflow(@RequestBody Map<String, Object> b) {
        WorkflowDefinition w = uid(b, "id") != null ? workflows.findById(uid(b, "id")).orElseThrow() : new WorkflowDefinition();
        if (b.containsKey("key")) w.key = s(b, "key");
        if (b.containsKey("name")) w.name = s(b, "name");
        if (b.containsKey("status")) w.status = s(b, "status");
        if (b.containsKey("definition")) {
            Object d = b.get("definition");
            String json = d instanceof String ? (String) d : Json.write(d);
            Json.read(json);
            w.definition = json;
        }
        if (b.containsKey("scopeExpression")) {
            Object sc = b.get("scopeExpression");
            w.scopeExpression = sc instanceof String ? (String) sc : Json.write(sc);
        }
        workflows.save(w);
        log("WORKFLOW_DEFINITION", w.id.toString(), "SAVED", Map.of("key", w.key));
        return Map.of("id", w.id, "key", w.key, "versionNo", w.versionNo);
    }

    // ============================ AI document review rules ============================
    @GetMapping("/ai-review-rules")
    public List<AiReviewRule> aiReviewRules() { return reviewRules.findAllByOrderBySortAsc(); }

    @PostMapping("/ai-review-rules")
    public AiReviewRule saveAiReviewRule(@RequestBody Map<String, Object> b) {
        String code = s(b, "code");
        if (code == null || code.isBlank()) throw new ApiExceptions.BadRequestException("code is required");
        AiReviewRule r = reviewRules.findByCodeIgnoreCase(code).orElseGet(() -> {
            var x = new AiReviewRule();
            x.code = code;
            x.createdBy = current.id();
            return x;
        });
        if (b.containsKey("label")) r.label = s(b, "label");
        if (b.containsKey("instruction")) r.instruction = s(b, "instruction");
        if (b.containsKey("severity")) r.severity = s(b, "severity");
        if (b.containsKey("isActive")) r.isActive = Boolean.TRUE.equals(bool(b, "isActive"));
        if (b.containsKey("sort")) r.sort = Optional.ofNullable(i(b, "sort")).orElse(100);
        if (r.label == null || r.instruction == null)
            throw new ApiExceptions.BadRequestException("label and instruction are required");
        reviewRules.save(r);
        log("AI_REVIEW_RULE", r.code, "SAVED", Map.of("label", r.label, "severity", r.severity));
        return r;
    }

    @DeleteMapping("/ai-review-rules/{code}")
    public void deleteAiReviewRule(@PathVariable String code) {
        reviewRules.findByCodeIgnoreCase(code).ifPresent(reviewRules::delete);
    }
}
