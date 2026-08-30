-- ============================================================
-- V6 — Deeper contract type schemas, document linkage, comments
-- ============================================================

-- ---------- First-class contract columns (long tail stays in type_attributes JSONB) ----------
ALTER TABLE contract ADD COLUMN annual_value_amount NUMERIC(18,2);
ALTER TABLE contract ADD COLUMN payment_terms_days  INT;
ALTER TABLE contract ADD COLUMN renewal_type        TEXT;      -- NONE | AUTO | MANUAL
ALTER TABLE contract ADD COLUMN liability_summary    TEXT;
ALTER TABLE contract ADD COLUMN editor_document_id   TEXT;      -- word-editor document id for the primary draft

CREATE INDEX idx_contract_renewal ON contract(renewal_type);

ALTER TABLE template ADD COLUMN body_html TEXT;
ALTER TABLE template ADD COLUMN description TEXT;
ALTER TABLE template ADD COLUMN tags TEXT;                       -- comma-separated, for the library browser

ALTER TABLE contract_type_definition ADD COLUMN ui_groups JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE contract_type_definition ADD COLUMN icon TEXT;

-- ---------- Comment threads (plan: approval discussion, multi-participant, rich format) ----------
CREATE TABLE comment_thread (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   TEXT NOT NULL,          -- CONTRACT | WORKFLOW_TASK
    entity_id     TEXT NOT NULL,
    subject_ref   TEXT,                   -- optional: a clause / field / section anchor
    title         TEXT,
    status        TEXT NOT NULL DEFAULT 'OPEN',   -- OPEN | RESOLVED
    created_by    UUID NOT NULL REFERENCES app_user(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_by   UUID REFERENCES app_user(id),
    resolved_at   TIMESTAMPTZ
);
CREATE INDEX idx_comment_thread_entity ON comment_thread(entity_type, entity_id);

CREATE TABLE comment_message (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id     UUID NOT NULL REFERENCES comment_thread(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES app_user(id),
    body_html     TEXT NOT NULL,          -- sanitized rich text
    body_format   TEXT NOT NULL DEFAULT 'HTML',
    mentions      JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at     TIMESTAMPTZ,
    deleted       BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_comment_message_thread ON comment_message(thread_id, created_at);

-- ---------- Deep field schemas for the six focus contract types ----------
UPDATE contract_type_definition SET icon = 'shield-check',
  ui_groups = '["Scope","Term & renewal","Data & privacy","Governance"]'::jsonb,
  field_schema = '{
    "type":"object",
    "required":["mutual","term_months"],
    "properties":{
      "mutual":{"type":"boolean","title":"Mutual?","x-group":"Scope"},
      "purpose":{"type":"string","title":"Purpose of disclosure","x-multiline":true,"x-group":"Scope"},
      "confidential_scope":{"type":"string","title":"What information is covered","enum":["BUSINESS_ONLY","TECHNICAL_ONLY","BUSINESS_AND_TECHNICAL","ALL_NON_PUBLIC"],"x-group":"Scope"},
      "term_months":{"type":"integer","title":"Term (months)","x-group":"Term & renewal"},
      "survival_years":{"type":"integer","title":"Confidentiality survival (years)","x-group":"Term & renewal"},
      "return_or_destroy":{"type":"string","title":"On termination","enum":["RETURN","DESTROY","RETURN_OR_DESTROY","NO_OBLIGATION"],"x-group":"Term & renewal"},
      "data_processing":{"type":"boolean","title":"Involves personal data?","x-group":"Data & privacy"},
      "dpa_required":{"type":"boolean","title":"DPA annex required","x-group":"Data & privacy"},
      "residuals_clause":{"type":"boolean","title":"Residual knowledge permitted","x-group":"Governance"},
      "injunctive_relief":{"type":"boolean","title":"Injunctive relief acknowledged","x-group":"Governance"},
      "dispute_resolution":{"type":"string","title":"Dispute resolution","enum":["LITIGATION","ARBITRATION","MEDIATION_THEN_ARBITRATION"],"x-group":"Governance"}
    }
  }'::jsonb
WHERE code = 'NDA';

UPDATE contract_type_definition SET icon = 'briefcase',
  ui_groups = '["Commercial","Term & renewal","Liability & risk","IP & data","Service levels","Governance"]'::jsonb,
  field_schema = '{
    "type":"object",
    "required":["pricing_model","payment_terms_days","initial_term_months","liability_cap_basis"],
    "properties":{
      "pricing_model":{"type":"string","title":"Pricing model","enum":["FIXED_FEE","TIME_AND_MATERIALS","SUBSCRIPTION","USAGE_BASED","MILESTONE"],"x-group":"Commercial"},
      "total_contract_value":{"type":"number","title":"Total contract value","x-money":true,"x-group":"Commercial"},
      "annual_contract_value":{"type":"number","title":"Annual contract value","x-money":true,"x-group":"Commercial"},
      "currency":{"type":"string","title":"Currency","enum":["EUR","USD","GBP","CHF"],"x-group":"Commercial"},
      "payment_terms_days":{"type":"integer","title":"Payment terms (days)","x-group":"Commercial"},
      "invoicing_frequency":{"type":"string","title":"Invoicing frequency","enum":["MONTHLY","QUARTERLY","ANNUALLY","ON_MILESTONE"],"x-group":"Commercial"},
      "late_payment_interest_pct":{"type":"number","title":"Late payment interest (%)","x-group":"Commercial"},
      "expenses_reimbursable":{"type":"boolean","title":"Expenses reimbursable","x-group":"Commercial"},
      "initial_term_months":{"type":"integer","title":"Initial term (months)","x-group":"Term & renewal"},
      "renewal_type":{"type":"string","title":"Renewal","enum":["NONE","AUTO","MANUAL"],"x-group":"Term & renewal"},
      "renewal_term_months":{"type":"integer","title":"Renewal term (months)","x-group":"Term & renewal"},
      "termination_notice_days":{"type":"integer","title":"Termination notice (days)","x-group":"Term & renewal"},
      "termination_for_convenience":{"type":"boolean","title":"Termination for convenience allowed","x-group":"Term & renewal"},
      "liability_cap_basis":{"type":"string","title":"Liability cap basis","enum":["FEES_12M","FEES_TOTAL","MULTIPLE_OF_FEES","FIXED_AMOUNT","UNCAPPED"],"x-group":"Liability & risk"},
      "liability_cap_multiple":{"type":"number","title":"Cap multiple (x fees)","x-group":"Liability & risk"},
      "liability_cap_amount":{"type":"number","title":"Cap fixed amount","x-money":true,"x-group":"Liability & risk"},
      "consequential_damages_excluded":{"type":"boolean","title":"Consequential damages excluded","x-group":"Liability & risk"},
      "indemnification":{"type":"string","title":"Indemnification","enum":["MUTUAL","SUPPLIER_ONLY","CUSTOMER_ONLY","NONE"],"x-group":"Liability & risk"},
      "insurance_required":{"type":"boolean","title":"Insurance required","x-group":"Liability & risk"},
      "insurance_amount":{"type":"number","title":"Insurance minimum","x-money":true,"x-group":"Liability & risk"},
      "ip_ownership":{"type":"string","title":"IP in deliverables","enum":["CUSTOMER_OWNS","SUPPLIER_RETAINS","JOINT","LICENSE_ONLY"],"x-group":"IP & data"},
      "background_ip_license":{"type":"boolean","title":"Background IP licensed","x-group":"IP & data"},
      "data_processing":{"type":"boolean","title":"Processes personal data","x-group":"IP & data"},
      "data_residency":{"type":"string","title":"Data residency","enum":["EU","US","UK","GLOBAL"],"x-group":"IP & data"},
      "security_standard":{"type":"string","title":"Security standard","enum":["NONE","SOC2","ISO27001","CUSTOM"],"x-group":"IP & data"},
      "sla_applicable":{"type":"boolean","title":"SLA applies","x-group":"Service levels"},
      "sla_uptime_pct":{"type":"number","title":"Uptime commitment (%)","x-group":"Service levels"},
      "sla_service_credits":{"type":"boolean","title":"Service credits","x-group":"Service levels"},
      "support_hours":{"type":"string","title":"Support hours","enum":["8X5","16X5","24X7"],"x-group":"Service levels"},
      "exclusivity":{"type":"boolean","title":"Exclusivity","x-group":"Governance"},
      "territory":{"type":"string","title":"Territory","x-group":"Governance"},
      "subcontracting_allowed":{"type":"boolean","title":"Subcontracting allowed","x-group":"Governance"},
      "assignment_allowed":{"type":"boolean","title":"Assignment allowed","x-group":"Governance"},
      "dispute_resolution":{"type":"string","title":"Dispute resolution","enum":["LITIGATION","ARBITRATION_ICC","ARBITRATION_LCIA","MEDIATION_THEN_ARBITRATION"],"x-group":"Governance"}
    }
  }'::jsonb
WHERE code = 'MSA';

UPDATE contract_type_definition SET icon = 'lock',
  ui_groups = '["Processing","Data subjects & data","Security","Sub-processing & transfers","Rights & assistance"]'::jsonb,
  field_schema = '{
    "type":"object",
    "required":["role","processing_purpose","security_standard"],
    "properties":{
      "role":{"type":"string","title":"Our role","enum":["CONTROLLER","PROCESSOR","JOINT_CONTROLLER","SUBPROCESSOR"],"x-group":"Processing"},
      "processing_purpose":{"type":"string","title":"Purpose of processing","x-multiline":true,"x-group":"Processing"},
      "processing_duration":{"type":"string","title":"Duration of processing","enum":["TERM_OF_MAIN_AGREEMENT","FIXED_PERIOD","UNTIL_INSTRUCTED"],"x-group":"Processing"},
      "lawful_basis":{"type":"string","title":"Lawful basis","enum":["CONSENT","CONTRACT","LEGITIMATE_INTEREST","LEGAL_OBLIGATION"],"x-group":"Processing"},
      "data_categories":{"type":"string","title":"Categories of personal data","x-multiline":true,"x-group":"Data subjects & data"},
      "special_categories":{"type":"boolean","title":"Special-category data involved","x-group":"Data subjects & data"},
      "data_subject_types":{"type":"string","title":"Categories of data subjects","x-multiline":true,"x-group":"Data subjects & data"},
      "approx_volume":{"type":"string","title":"Approx. volume of records","enum":["UNDER_1K","1K_100K","100K_1M","OVER_1M"],"x-group":"Data subjects & data"},
      "security_standard":{"type":"string","title":"Security standard","enum":["ISO27001","SOC2_TYPE2","BOTH","CUSTOM_TOMS"],"x-group":"Security"},
      "encryption_at_rest":{"type":"boolean","title":"Encryption at rest","x-group":"Security"},
      "encryption_in_transit":{"type":"boolean","title":"Encryption in transit","x-group":"Security"},
      "breach_notice_hours":{"type":"integer","title":"Breach notification (hours)","x-group":"Security"},
      "pen_test_frequency":{"type":"string","title":"Penetration testing","enum":["ANNUAL","BIANNUAL","CONTINUOUS","NONE"],"x-group":"Security"},
      "subprocessing_allowed":{"type":"boolean","title":"Sub-processing permitted","x-group":"Sub-processing & transfers"},
      "subprocessor_approval":{"type":"string","title":"Sub-processor approval","enum":["PRIOR_WRITTEN","GENERAL_WITH_OBJECTION","NOT_APPLICABLE"],"x-group":"Sub-processing & transfers"},
      "international_transfers":{"type":"boolean","title":"International transfers","x-group":"Sub-processing & transfers"},
      "transfer_mechanism":{"type":"string","title":"Transfer mechanism","enum":["NONE","SCCS","ADEQUACY_DECISION","BCRS"],"x-group":"Sub-processing & transfers"},
      "dsr_assistance":{"type":"boolean","title":"Data-subject request assistance","x-group":"Rights & assistance"},
      "dpia_assistance":{"type":"boolean","title":"DPIA assistance","x-group":"Rights & assistance"},
      "audit_rights":{"type":"string","title":"Audit rights","enum":["ONSITE","THIRD_PARTY_REPORT","QUESTIONNAIRE_ONLY"],"x-group":"Rights & assistance"},
      "deletion_on_termination":{"type":"string","title":"On termination","enum":["DELETE","RETURN","RETURN_THEN_DELETE"],"x-group":"Rights & assistance"}
    }
  }'::jsonb
WHERE code = 'DPA';

-- repurpose SOW into a deep consulting statement of work
UPDATE contract_type_definition SET icon = 'clipboard-list', display_name = 'Consulting Statement of Work',
  ui_groups = '["Engagement","Commercial","Deliverables","Risk"]'::jsonb,
  field_schema = '{
    "type":"object",
    "required":["engagement_type","start_date","fee_amount"],
    "properties":{
      "engagement_type":{"type":"string","title":"Engagement type","enum":["FIXED_SCOPE","STAFF_AUG","MANAGED_SERVICE","ADVISORY"],"x-group":"Engagement"},
      "start_date":{"type":"string","format":"date","title":"Start date","x-group":"Engagement"},
      "end_date":{"type":"string","format":"date","title":"Target end date","x-group":"Engagement"},
      "roles_committed":{"type":"string","title":"Roles / team committed","x-multiline":true,"x-group":"Engagement"},
      "location":{"type":"string","title":"Delivery location","enum":["ONSITE","REMOTE","HYBRID"],"x-group":"Engagement"},
      "fee_amount":{"type":"number","title":"Fee amount","x-money":true,"x-group":"Commercial"},
      "fee_basis":{"type":"string","title":"Fee basis","enum":["FIXED","DAY_RATE","MILESTONE","RETAINER"],"x-group":"Commercial"},
      "day_rate":{"type":"number","title":"Blended day rate","x-money":true,"x-group":"Commercial"},
      "expenses_cap":{"type":"number","title":"Expenses cap","x-money":true,"x-group":"Commercial"},
      "payment_terms_days":{"type":"integer","title":"Payment terms (days)","x-group":"Commercial"},
      "deliverables":{"type":"string","title":"Deliverables","x-multiline":true,"x-group":"Deliverables"},
      "acceptance_criteria":{"type":"string","title":"Acceptance criteria","x-multiline":true,"x-group":"Deliverables"},
      "acceptance_period_days":{"type":"integer","title":"Acceptance period (days)","x-group":"Deliverables"},
      "ip_ownership":{"type":"string","title":"IP in deliverables","enum":["CUSTOMER_OWNS","SUPPLIER_RETAINS","JOINT"],"x-group":"Deliverables"},
      "key_person_clause":{"type":"boolean","title":"Key-person clause","x-group":"Risk"},
      "liability_cap_basis":{"type":"string","title":"Liability cap basis","enum":["FEES_PAID","MULTIPLE_OF_FEES","FIXED_AMOUNT","UNCAPPED"],"x-group":"Risk"},
      "background_check_required":{"type":"boolean","title":"Background checks required","x-group":"Risk"}
    }
  }'::jsonb
WHERE code = 'SOW';

UPDATE contract_type_definition SET icon = 'shopping-cart',
  ui_groups = '["Commercial","Supply","Quality & compliance","Risk"]'::jsonb,
  field_schema = '{
    "type":"object",
    "required":["annual_value","payment_terms_days","pricing_basis"],
    "properties":{
      "annual_value":{"type":"number","title":"Annual value","x-money":true,"x-group":"Commercial"},
      "currency":{"type":"string","title":"Currency","enum":["EUR","USD","GBP","CHF"],"x-group":"Commercial"},
      "pricing_basis":{"type":"string","title":"Pricing basis","enum":["FIRM_FIXED","INDEXED","COST_PLUS","VOLUME_TIERED"],"x-group":"Commercial"},
      "price_index":{"type":"string","title":"Price index","enum":["NONE","CPI","PPI","CUSTOM"],"x-group":"Commercial"},
      "payment_terms_days":{"type":"integer","title":"Payment terms (days)","x-group":"Commercial"},
      "early_payment_discount_pct":{"type":"number","title":"Early payment discount (%)","x-group":"Commercial"},
      "minimum_commitment":{"type":"number","title":"Minimum purchase commitment","x-money":true,"x-group":"Supply"},
      "lead_time_days":{"type":"integer","title":"Lead time (days)","x-group":"Supply"},
      "delivery_terms":{"type":"string","title":"Incoterms","enum":["EXW","FCA","CIP","DAP","DDP"],"x-group":"Supply"},
      "exclusivity":{"type":"boolean","title":"Exclusive supply","x-group":"Supply"},
      "quality_standard":{"type":"string","title":"Quality standard","enum":["NONE","ISO9001","IATF16949","CUSTOM"],"x-group":"Quality & compliance"},
      "audit_rights":{"type":"boolean","title":"Customer audit rights","x-group":"Quality & compliance"},
      "code_of_conduct":{"type":"boolean","title":"Supplier code of conduct accepted","x-group":"Quality & compliance"},
      "conflict_minerals":{"type":"boolean","title":"Conflict minerals declaration","x-group":"Quality & compliance"},
      "liability_cap_basis":{"type":"string","title":"Liability cap basis","enum":["ORDER_VALUE","ANNUAL_VALUE","MULTIPLE","UNCAPPED"],"x-group":"Risk"},
      "warranty_months":{"type":"integer","title":"Warranty period (months)","x-group":"Risk"},
      "product_recall_terms":{"type":"boolean","title":"Recall cost allocation defined","x-group":"Risk"}
    }
  }'::jsonb
WHERE code = 'VENDOR_PURCHASE';

UPDATE contract_type_definition SET icon = 'user',
  ui_groups = '["Role","Compensation","Terms","Restrictive covenants"]'::jsonb,
  field_schema = '{
    "type":"object",
    "required":["role_title","start_date","base_salary","employment_type"],
    "properties":{
      "role_title":{"type":"string","title":"Role title","x-group":"Role"},
      "department":{"type":"string","title":"Department","x-group":"Role"},
      "reporting_to":{"type":"string","title":"Reports to","x-group":"Role"},
      "work_location":{"type":"string","title":"Work location","enum":["OFFICE","REMOTE","HYBRID"],"x-group":"Role"},
      "start_date":{"type":"string","format":"date","title":"Start date","x-group":"Role"},
      "employment_type":{"type":"string","title":"Employment type","enum":["PERMANENT","FIXED_TERM","CONTRACTOR"],"x-group":"Role"},
      "fixed_term_end":{"type":"string","format":"date","title":"Fixed-term end date","x-group":"Role"},
      "base_salary":{"type":"number","title":"Base salary (annual)","x-money":true,"x-group":"Compensation"},
      "currency":{"type":"string","title":"Currency","enum":["EUR","USD","GBP","CHF"],"x-group":"Compensation"},
      "bonus_target_pct":{"type":"number","title":"Target bonus (% of base)","x-group":"Compensation"},
      "equity_grant":{"type":"boolean","title":"Equity grant","x-group":"Compensation"},
      "equity_details":{"type":"string","title":"Equity details","x-multiline":true,"x-group":"Compensation"},
      "benefits_package":{"type":"string","title":"Benefits package","enum":["STANDARD","ENHANCED","EXECUTIVE"],"x-group":"Compensation"},
      "probation_months":{"type":"integer","title":"Probation period (months)","x-group":"Terms"},
      "notice_period_days":{"type":"integer","title":"Notice period (days)","x-group":"Terms"},
      "annual_leave_days":{"type":"integer","title":"Annual leave (days)","x-group":"Terms"},
      "non_compete_months":{"type":"integer","title":"Non-compete (months)","x-group":"Restrictive covenants"},
      "non_solicit_months":{"type":"integer","title":"Non-solicitation (months)","x-group":"Restrictive covenants"},
      "ip_assignment":{"type":"boolean","title":"IP assignment clause","x-group":"Restrictive covenants"},
      "garden_leave":{"type":"boolean","title":"Garden leave permitted","x-group":"Restrictive covenants"}
    }
  }'::jsonb
WHERE code = 'EMPLOYMENT';
