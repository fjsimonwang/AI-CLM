-- ============================================================
-- V5 — Seed data (development / demo corpus)
-- Passwords: users are seeded with password_hash = 'SEED';
-- DevPasswordSeeder replaces it with bcrypt('demo1234') on boot.
--
-- UUID scheme (all hex): 1=team 2=entity 3=user 4=party 5=concept
-- 6=variant 7=template 8=workflow-def 9=contract a=misc
-- ============================================================

-- ---------- Legal teams & entities ----------
INSERT INTO legal_team (id, name, region, default_queue_sla_hours) VALUES
 ('11111111-1111-1111-1111-111111111101'::uuid, 'EMEA Legal', 'EMEA', 48),
 ('11111111-1111-1111-1111-111111111102'::uuid, 'Americas Legal', 'AMER', 48);

INSERT INTO legal_entity (id, legal_name, short_name, country_code, registration_number, default_governing_law, default_language, data_residency_region, legal_team_id) VALUES
 ('22222222-2222-2222-2222-222222222201'::uuid, 'Acme GmbH', 'ACME_GMBH', 'DE', 'HRB 12345',   'Germany',            'de', 'EU', '11111111-1111-1111-1111-111111111101'::uuid),
 ('22222222-2222-2222-2222-222222222202'::uuid, 'Acme SAS',  'ACME_SAS',  'FR', 'RCS 987654',  'France',             'fr', 'EU', '11111111-1111-1111-1111-111111111101'::uuid),
 ('22222222-2222-2222-2222-222222222203'::uuid, 'Acme Inc.', 'ACME_INC',  'US', 'DE-5551212',  'State of New York',  'en', 'US', '11111111-1111-1111-1111-111111111102'::uuid),
 ('22222222-2222-2222-2222-222222222204'::uuid, 'Acme Ltd',  'ACME_LTD',  'GB', 'CRN 04123456','England and Wales',  'en', 'UK', '11111111-1111-1111-1111-111111111101'::uuid);

-- ---------- Users ----------
INSERT INTO app_user (id, email, password_hash, display_name, default_entity_id, department, roles) VALUES
 ('33333333-3333-3333-3333-333333333301'::uuid, 'gc@acme.example',          'SEED', 'Dana Whitfield (General Counsel)', '22222222-2222-2222-2222-222222222203'::uuid, 'Legal',       'GENERAL_COUNSEL,LEGAL,APPROVER,ADMIN'),
 ('33333333-3333-3333-3333-333333333302'::uuid, 'lawyer.emea@acme.example', 'SEED', 'Priya Raman (EMEA Counsel)',       '22222222-2222-2222-2222-222222222201'::uuid, 'Legal',       'LEGAL,APPROVER'),
 ('33333333-3333-3333-3333-333333333303'::uuid, 'lawyer.amer@acme.example', 'SEED', 'Marcus Lee (Americas Counsel)',    '22222222-2222-2222-2222-222222222203'::uuid, 'Legal',       'LEGAL,APPROVER'),
 ('33333333-3333-3333-3333-333333333304'::uuid, 'finance@acme.example',     'SEED', 'Sofia Alvarez (Finance)',          '22222222-2222-2222-2222-222222222201'::uuid, 'Finance',     'FINANCE,APPROVER'),
 ('33333333-3333-3333-3333-333333333305'::uuid, 'procurement@acme.example', 'SEED', 'Tom Becker (Procurement)',         '22222222-2222-2222-2222-222222222201'::uuid, 'Procurement', 'REQUESTER'),
 ('33333333-3333-3333-3333-333333333306'::uuid, 'sales@acme.example',       'SEED', 'Ivy Chen (Sales)',                 '22222222-2222-2222-2222-222222222203'::uuid, 'Sales',       'REQUESTER');

UPDATE app_user SET manager_user_id = '33333333-3333-3333-3333-333333333301'::uuid WHERE email LIKE 'lawyer.%';
UPDATE app_user SET manager_user_id = '33333333-3333-3333-3333-333333333302'::uuid WHERE email IN ('procurement@acme.example');
UPDATE app_user SET manager_user_id = '33333333-3333-3333-3333-333333333303'::uuid WHERE email IN ('sales@acme.example');

INSERT INTO signing_authority (legal_entity_id, user_id, contract_type_code, max_value_amount, currency) VALUES
 ('22222222-2222-2222-2222-222222222201'::uuid, '33333333-3333-3333-3333-333333333302'::uuid, NULL,  500000, 'EUR'),
 ('22222222-2222-2222-2222-222222222202'::uuid, '33333333-3333-3333-3333-333333333302'::uuid, NULL,  500000, 'EUR'),
 ('22222222-2222-2222-2222-222222222204'::uuid, '33333333-3333-3333-3333-333333333302'::uuid, NULL,  500000, 'GBP'),
 ('22222222-2222-2222-2222-222222222203'::uuid, '33333333-3333-3333-3333-333333333301'::uuid, NULL, 5000000, 'USD'),
 ('22222222-2222-2222-2222-222222222201'::uuid, '33333333-3333-3333-3333-333333333304'::uuid, NULL,  250000, 'EUR');

-- ---------- Contract types ----------
INSERT INTO contract_type_definition (code, display_name, category, requires_legal_review_default, retention_years, base_risk, auto_issue_allowed, field_schema) VALUES
 ('NDA', 'Non-Disclosure Agreement', 'PROTECTIVE', false, 6, 15, true,
   '{"type":"object","properties":{"mutual":{"type":"boolean","title":"Mutual?"},"term_months":{"type":"integer","title":"Term (months)"},"purpose":{"type":"string","title":"Purpose of disclosure"},"data_processing":{"type":"boolean","title":"Involves personal data?"}}}'::jsonb),
 ('MSA', 'Master Services Agreement', 'COMMERCIAL', true, 10, 35, false,
   '{"type":"object","properties":{"term_months":{"type":"integer","title":"Initial term (months)"},"liability_cap_basis":{"type":"string","title":"Liability cap basis"},"payment_terms_days":{"type":"integer","title":"Payment terms (days)"}}}'::jsonb),
 ('DPA', 'Data Processing Agreement', 'PROTECTIVE', true, 10, 40, false, '{"type":"object","properties":{}}'::jsonb),
 ('SOW', 'Statement of Work', 'COMMERCIAL', false, 10, 20, true, '{"type":"object","properties":{"deliverables":{"type":"string","title":"Deliverables"},"start_date":{"type":"string","format":"date","title":"Start date"}}}'::jsonb),
 ('VENDOR_PURCHASE', 'Vendor Purchase Agreement', 'COMMERCIAL', true, 10, 30, false, '{"type":"object","properties":{"annual_value":{"type":"number","title":"Annual value"}}}'::jsonb),
 ('EMPLOYMENT', 'Employment Contract', 'HR', true, 15, 25, false, '{"type":"object","properties":{"role_title":{"type":"string","title":"Role title"},"start_date":{"type":"string","format":"date","title":"Start date"}}}'::jsonb);

-- ---------- Parties ----------
INSERT INTO party (id, legal_name, trading_name, country_code, party_type, industry, size_band, sanctions_check_status, sanctions_checked_at) VALUES
 ('44444444-4444-4444-4444-444444444401'::uuid, 'Meridian Logistics GmbH', 'Meridian', 'DE', 'VENDOR',   'Logistics', 'MID',   'CLEAR',        now() - interval '40 days'),
 ('44444444-4444-4444-4444-444444444402'::uuid, 'Northwind Traders Ltd',   'Northwind','GB', 'CUSTOMER', 'Retail',    'LARGE', 'CLEAR',        now() - interval '10 days'),
 ('44444444-4444-4444-4444-444444444403'::uuid, 'Contoso Cloud Inc',       'Contoso',  'US', 'VENDOR',   'Software',   'LARGE', 'CLEAR',        now() - interval '5 days'),
 ('44444444-4444-4444-4444-444444444404'::uuid, 'Globex Transport SAS',    'Globex',   'FR', 'VENDOR',   'Logistics', 'MID',   'NOT_SCREENED', NULL);

-- ---------- Clause library ----------
INSERT INTO clause_concept (id, concept_code, name, category, risk_category, is_core, owning_legal_team_id) VALUES
 ('55555555-5555-5555-5555-555555555501'::uuid, 'LIMITATION_OF_LIABILITY', 'Limitation of Liability',        'RISK',       'HIGH',   true, '11111111-1111-1111-1111-111111111101'::uuid),
 ('55555555-5555-5555-5555-555555555502'::uuid, 'CONFIDENTIALITY',         'Confidentiality',               'PROTECTIVE', 'MEDIUM', true, '11111111-1111-1111-1111-111111111101'::uuid),
 ('55555555-5555-5555-5555-555555555503'::uuid, 'IP_OWNERSHIP',            'Intellectual Property Ownership','COMMERCIAL', 'HIGH',   true, '11111111-1111-1111-1111-111111111101'::uuid),
 ('55555555-5555-5555-5555-555555555504'::uuid, 'TERMINATION',             'Termination',                   'COMMERCIAL', 'MEDIUM', true, '11111111-1111-1111-1111-111111111101'::uuid),
 ('55555555-5555-5555-5555-555555555505'::uuid, 'DATA_PROTECTION',         'Data Protection',               'PROTECTIVE', 'HIGH',   true, '11111111-1111-1111-1111-111111111101'::uuid),
 ('55555555-5555-5555-5555-555555555506'::uuid, 'GOVERNING_LAW',           'Governing Law',                 'BOILERPLATE','LOW',    true, '11111111-1111-1111-1111-111111111101'::uuid),
 ('55555555-5555-5555-5555-555555555507'::uuid, 'INDEMNITY',               'Indemnification',               'RISK',       'HIGH',   true, '11111111-1111-1111-1111-111111111101'::uuid);

INSERT INTO clause_variant (id, clause_concept_id, version_no, jurisdiction_code, language_code, body_text, position_tier, risk_tier, guidance_notes, status, approved_at) VALUES
 ('66666666-6666-6666-6666-666666660101'::uuid, '55555555-5555-5555-5555-555555555501'::uuid, 1, 'GLOBAL', 'en', 'Each party''s aggregate liability arising out of or related to this Agreement shall not exceed the total fees paid or payable in the twelve (12) months preceding the claim. Neither party shall be liable for indirect, incidental, or consequential damages.', 'PREFERRED', 'LOW', 'Standard 12-month fee cap, consequential damages excluded. Preferred position.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660102'::uuid, '55555555-5555-5555-5555-555555555501'::uuid, 1, 'GLOBAL', 'en', 'Each party''s aggregate liability shall not exceed two times (2x) the total fees paid in the twelve (12) months preceding the claim.', 'FALLBACK', 'MEDIUM', 'Up to 2x fees is a fallback position for strategic vendors; requires legal sign-off.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660103'::uuid, '55555555-5555-5555-5555-555555555501'::uuid, 1, 'GLOBAL', 'en', 'Liability under this Agreement is uncapped.', 'UNACCEPTABLE', 'HIGH', 'Uncapped liability is outside playbook. Blocks automated drafting; route to legal.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660201'::uuid, '55555555-5555-5555-5555-555555555502'::uuid, 1, 'GLOBAL', 'en', 'The Receiving Party shall protect Confidential Information using at least the degree of care it uses for its own confidential information and not less than reasonable care, and shall use it solely for the Purpose. Obligations survive for five (5) years after disclosure.', 'PREFERRED', 'LOW', 'Mutual, 5-year survival.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660202'::uuid, '55555555-5555-5555-5555-555555555502'::uuid, 1, 'GLOBAL', 'en', 'Confidentiality obligations survive for three (3) years after disclosure.', 'ACCEPTABLE', 'LOW', 'Shorter survival acceptable for low-sensitivity exchanges.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660301'::uuid, '55555555-5555-5555-5555-555555555503'::uuid, 1, 'GLOBAL', 'en', 'All deliverables and work product created specifically for the Customer under this Agreement shall be owned by the Customer upon full payment. Each party retains ownership of its pre-existing materials.', 'PREFERRED', 'LOW', 'Customer owns bespoke deliverables on payment.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660401'::uuid, '55555555-5555-5555-5555-555555555504'::uuid, 1, 'GLOBAL', 'en', 'Either party may terminate for material breach not cured within thirty (30) days of written notice. Either party may terminate for convenience on ninety (90) days'' written notice.', 'PREFERRED', 'LOW', 'Standard termination rights.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660501'::uuid, '55555555-5555-5555-5555-555555555505'::uuid, 1, 'EU', 'en', 'Where either party processes personal data on behalf of the other, the parties shall enter into the Data Processing Agreement attached as Annex A, which reflects Article 28 GDPR requirements.', 'PREFERRED', 'LOW', 'GDPR Art. 28 compliant DPA annex.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660601'::uuid, '55555555-5555-5555-5555-555555555506'::uuid, 1, 'DE', 'de', 'Dieser Vertrag unterliegt dem Recht der Bundesrepublik Deutschland unter Ausschluss des UN-Kaufrechts. Gerichtsstand ist Frankfurt am Main.', 'PREFERRED', 'LOW', 'German law, Frankfurt forum.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660602'::uuid, '55555555-5555-5555-5555-555555555506'::uuid, 1, 'GLOBAL', 'en', 'This Agreement is governed by the laws of the State of New York, and the parties submit to the exclusive jurisdiction of the courts located in New York County.', 'PREFERRED', 'LOW', 'New York law for the US entity.', 'ACTIVE', now()),
 ('66666666-6666-6666-6666-666666660701'::uuid, '55555555-5555-5555-5555-555555555507'::uuid, 1, 'GLOBAL', 'en', 'Each party shall indemnify the other against third-party claims arising from its breach of this Agreement or its gross negligence or wilful misconduct.', 'PREFERRED', 'MEDIUM', 'Mutual, breach-and-fault based.', 'ACTIVE', now());

-- ---------- Templates ----------
INSERT INTO template (id, name, contract_type_code, legal_entity_id, jurisdiction_code, language_code, version_no, owning_legal_team_id, approved_at) VALUES
 ('77777777-7777-7777-7777-777777777701'::uuid, 'NDA — EMEA (EN)', 'NDA', '22222222-2222-2222-2222-222222222201'::uuid, 'GLOBAL', 'en', 1, '11111111-1111-1111-1111-111111111101'::uuid, now()),
 ('77777777-7777-7777-7777-777777777702'::uuid, 'MSA — EMEA (EN)', 'MSA', '22222222-2222-2222-2222-222222222201'::uuid, 'GLOBAL', 'en', 1, '11111111-1111-1111-1111-111111111101'::uuid, now());

INSERT INTO template_section (template_id, sort_order, heading, is_optional, clause_concept_id, default_clause_variant_id, static_body) VALUES
 ('77777777-7777-7777-7777-777777777701'::uuid, 1, 'Purpose',             false, NULL, NULL, 'The parties wish to explore a potential business relationship ("the Purpose") and will disclose confidential information for that Purpose.'),
 ('77777777-7777-7777-7777-777777777701'::uuid, 2, 'Confidentiality',     false, '55555555-5555-5555-5555-555555555502'::uuid, '66666666-6666-6666-6666-666666660201'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777701'::uuid, 3, 'Data Protection',     true,  '55555555-5555-5555-5555-555555555505'::uuid, '66666666-6666-6666-6666-666666660501'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777701'::uuid, 4, 'Term and Termination',false, '55555555-5555-5555-5555-555555555504'::uuid, '66666666-6666-6666-6666-666666660401'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777701'::uuid, 5, 'Governing Law',       false, '55555555-5555-5555-5555-555555555506'::uuid, '66666666-6666-6666-6666-666666660601'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777702'::uuid, 1, 'Services',            false, NULL, NULL, 'The Supplier shall provide the services described in each Statement of Work agreed under this Agreement.'),
 ('77777777-7777-7777-7777-777777777702'::uuid, 2, 'Confidentiality',     false, '55555555-5555-5555-5555-555555555502'::uuid, '66666666-6666-6666-6666-666666660201'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777702'::uuid, 3, 'Intellectual Property',false,'55555555-5555-5555-5555-555555555503'::uuid, '66666666-6666-6666-6666-666666660301'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777702'::uuid, 4, 'Limitation of Liability',false,'55555555-5555-5555-5555-555555555501'::uuid, '66666666-6666-6666-6666-666666660101'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777702'::uuid, 5, 'Indemnification',     false, '55555555-5555-5555-5555-555555555507'::uuid, '66666666-6666-6666-6666-666666660701'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777702'::uuid, 6, 'Term and Termination',false, '55555555-5555-5555-5555-555555555504'::uuid, '66666666-6666-6666-6666-666666660401'::uuid, NULL),
 ('77777777-7777-7777-7777-777777777702'::uuid, 7, 'Governing Law',       false, '55555555-5555-5555-5555-555555555506'::uuid, '66666666-6666-6666-6666-666666660601'::uuid, NULL);

UPDATE contract_type_definition SET default_template_id = '77777777-7777-7777-7777-777777777701'::uuid WHERE code = 'NDA';
UPDATE contract_type_definition SET default_template_id = '77777777-7777-7777-7777-777777777702'::uuid WHERE code = 'MSA';

INSERT INTO merge_field (template_id, field_key, source_path, is_required) VALUES
 ('77777777-7777-7777-7777-777777777701'::uuid, 'counterparty_name', 'party.legal_name', true),
 ('77777777-7777-7777-7777-777777777701'::uuid, 'entity_name',       'entity.legal_name', true),
 ('77777777-7777-7777-7777-777777777701'::uuid, 'term_months',       'type_attributes.term_months', true);

-- ---------- Workflow definitions ----------
INSERT INTO workflow_definition (id, key, name, version_no, scope_expression, definition) VALUES
 ('88888888-8888-8888-8888-888888888801'::uuid, 'standard-approval', 'Standard Approval', 1,
  '{"contractTypes":["NDA","SOW"]}'::jsonb,
  '{"key":"standard-approval","version":1,"initial":"owner_approval","states":[
     {"key":"owner_approval","type":"task","taskType":"APPROVAL","assignment":{"role":"owner_manager"},"slaHours":24,"transitions":[{"on":"approve","to":"signature"},{"on":"reject","to":"closed_rejected"}]},
     {"key":"signature","type":"task","taskType":"SIGNATURE","assignment":{"role":"owner"},"guards":["signing_authority_valid","no_open_deviations"],"transitions":[{"on":"complete","to":"executed"}]},
     {"key":"executed","type":"end"},
     {"key":"closed_rejected","type":"end"}
   ]}'::jsonb),
 ('88888888-8888-8888-8888-888888888802'::uuid, 'legal-review', 'Legal Review', 1,
  '{"contractTypes":["MSA","DPA","VENDOR_PURCHASE","EMPLOYMENT"]}'::jsonb,
  '{"key":"legal-review","version":1,"initial":"legal_review","states":[
     {"key":"legal_review","type":"task","taskType":"REVIEW","assignment":{"role":"legal_team"},"slaHours":48,"escalation":{"afterHours":48,"to":"role:legal_manager"},"transitions":[{"on":"approve","to":"finance_review"},{"on":"request_changes","to":"drafting"},{"on":"reject","to":"closed_rejected"}]},
     {"key":"drafting","type":"task","taskType":"REVISION","assignment":{"role":"owner"},"transitions":[{"on":"resubmit","to":"legal_review"}]},
     {"key":"finance_review","type":"task","taskType":"APPROVAL","assignment":{"role":"finance_approver"},"slaHours":24,"transitions":[{"on":"approve","to":"signature"},{"on":"reject","to":"closed_rejected"}]},
     {"key":"signature","type":"task","taskType":"SIGNATURE","assignment":{"role":"signatory"},"guards":["signing_authority_valid","no_open_deviations"],"transitions":[{"on":"complete","to":"executed"}]},
     {"key":"executed","type":"end"},
     {"key":"closed_rejected","type":"end"}
   ]}'::jsonb);

UPDATE contract_type_definition SET default_workflow_id = '88888888-8888-8888-8888-888888888801'::uuid WHERE code IN ('NDA','SOW');
UPDATE contract_type_definition SET default_workflow_id = '88888888-8888-8888-8888-888888888802'::uuid WHERE code IN ('MSA','DPA','VENDOR_PURCHASE','EMPLOYMENT');

INSERT INTO assignment_rule (name, priority, condition_expression, target_type, target_id) VALUES
 ('EMEA legal queue',     10, '{"entityRegion":"EU"}'::jsonb, 'TEAM', '11111111-1111-1111-1111-111111111101'::uuid),
 ('Americas legal queue', 10, '{"entityRegion":"US"}'::jsonb, 'TEAM', '11111111-1111-1111-1111-111111111102'::uuid);

-- ---------- Sample contracts ----------
-- 1) Executed precedent NDA with Meridian (Nov 2025)
INSERT INTO contract (id, contract_number, contract_type_code, title, status, contracting_entity_id, governing_law_code, primary_language, effective_date, expiry_date, notice_period_days, risk_score, risk_tier, source, owner_user_id, assigned_lawyer_id, type_attributes, summary) VALUES
 ('99999999-9999-9999-9999-999999999901'::uuid, 'ACME_GMBH-NDA-2025-0041', 'NDA', 'Mutual NDA — Meridian Logistics', 'EXECUTED', '22222222-2222-2222-2222-222222222201'::uuid, 'Germany', 'en', DATE '2025-11-03', DATE '2028-11-03', 0, 12, 'LOW', 'NATIVE', '33333333-3333-3333-3333-333333333305'::uuid, '33333333-3333-3333-3333-333333333302'::uuid,
  '{"mutual":true,"term_months":36,"purpose":"Evaluation of a European road-freight framework","data_processing":true}'::jsonb,
  'Mutual 3-year NDA with Meridian Logistics GmbH under German law, including the standard GDPR Art. 28 DPA annex. Signed 3 Nov 2025.');

INSERT INTO contract_party (contract_id, party_id, role, signatory_name, signatory_email, signatory_title) VALUES
 ('99999999-9999-9999-9999-999999999901'::uuid, '44444444-4444-4444-4444-444444444401'::uuid, 'COUNTERPARTY', 'Klaus Berger', 'k.berger@meridian.example', 'Managing Director');

INSERT INTO contract_term (contract_id, term_key, term_value, term_type, effective_from, effective_to, extraction_confidence, verified_by, verified_at) VALUES
 ('99999999-9999-9999-9999-999999999901'::uuid, 'term_months',   '36'::jsonb,        'NUMBER',  DATE '2025-11-03', DATE '2028-11-03', 1.0, '33333333-3333-3333-3333-333333333302'::uuid, now()),
 ('99999999-9999-9999-9999-999999999901'::uuid, 'mutual',        'true'::jsonb,      'BOOLEAN', DATE '2025-11-03', NULL, 1.0, '33333333-3333-3333-3333-333333333302'::uuid, now()),
 ('99999999-9999-9999-9999-999999999901'::uuid, 'governing_law', '"Germany"'::jsonb, 'STRING',  DATE '2025-11-03', NULL, 1.0, '33333333-3333-3333-3333-333333333302'::uuid, now()),
 ('99999999-9999-9999-9999-999999999901'::uuid, 'confidentiality_survival_years', '5'::jsonb, 'NUMBER', DATE '2025-11-03', NULL, 0.95, '33333333-3333-3333-3333-333333333302'::uuid, now());

INSERT INTO contract_version (contract_id, version_no, version_label, change_summary, is_executed, created_at) VALUES
 ('99999999-9999-9999-9999-999999999901'::uuid, 1, 'Executed', 'Executed version countersigned by both parties.', true, now() - interval '120 days');

INSERT INTO clause_variant_usage (clause_variant_id, contract_id, was_modified) VALUES
 ('66666666-6666-6666-6666-666666660201'::uuid, '99999999-9999-9999-9999-999999999901'::uuid, false),
 ('66666666-6666-6666-6666-666666660501'::uuid, '99999999-9999-9999-9999-999999999901'::uuid, false),
 ('66666666-6666-6666-6666-666666660601'::uuid, '99999999-9999-9999-9999-999999999901'::uuid, false);

-- 2) Active MSA with Contoso Cloud (US) + child SOW + amendment
INSERT INTO contract (id, contract_number, contract_type_code, title, status, contracting_entity_id, governing_law_code, primary_language, effective_date, expiry_date, notice_period_days, auto_renew, renewal_term_months, value_amount, currency, value_basis, risk_score, risk_tier, source, owner_user_id, assigned_lawyer_id, type_attributes, summary) VALUES
 ('99999999-9999-9999-9999-999999999902'::uuid, 'ACME_INC-MSA-2025-0007', 'MSA', 'Master Services Agreement — Contoso Cloud', 'EXECUTED', '22222222-2222-2222-2222-222222222203'::uuid, 'State of New York', 'en', DATE '2025-02-01', DATE '2027-01-31', 60, true, 12, 1800000, 'USD', 'TCV', 48, 'MEDIUM', 'NATIVE', '33333333-3333-3333-3333-333333333305'::uuid, '33333333-3333-3333-3333-333333333303'::uuid,
  '{"term_months":24,"liability_cap_basis":"2x fees","payment_terms_days":45}'::jsonb,
  'Two-year cloud services MSA with Contoso Cloud Inc, auto-renewing for 12-month terms, 2x-fees liability cap (fallback tier), TCV ~$1.8m.');

INSERT INTO contract_party (contract_id, party_id, role, signatory_name, signatory_email, signatory_title) VALUES
 ('99999999-9999-9999-9999-999999999902'::uuid, '44444444-4444-4444-4444-444444444403'::uuid, 'COUNTERPARTY', 'Rachel Stone', 'rstone@contoso.example', 'VP Sales');

INSERT INTO contract_term (contract_id, term_key, term_value, term_type, effective_from, effective_to, extraction_confidence, verified_by, verified_at) VALUES
 ('99999999-9999-9999-9999-999999999902'::uuid, 'liability_cap',      '"2x trailing 12 months fees"'::jsonb, 'STRING',  DATE '2025-02-01', NULL, 1.0, '33333333-3333-3333-3333-333333333303'::uuid, now()),
 ('99999999-9999-9999-9999-999999999902'::uuid, 'payment_terms_days', '45'::jsonb,                          'NUMBER',  DATE '2025-02-01', NULL, 1.0, '33333333-3333-3333-3333-333333333303'::uuid, now()),
 ('99999999-9999-9999-9999-999999999902'::uuid, 'auto_renew',         'true'::jsonb,                        'BOOLEAN', DATE '2025-02-01', NULL, 1.0, '33333333-3333-3333-3333-333333333303'::uuid, now());

INSERT INTO contract_version (contract_id, version_no, version_label, change_summary, is_executed, created_at) VALUES
 ('99999999-9999-9999-9999-999999999902'::uuid, 1, 'Executed', 'Initial executed MSA.', true, now() - interval '200 days');

INSERT INTO clause_variant_usage (clause_variant_id, contract_id, was_modified) VALUES
 ('66666666-6666-6666-6666-666666660102'::uuid, '99999999-9999-9999-9999-999999999902'::uuid, false),
 ('66666666-6666-6666-6666-666666660301'::uuid, '99999999-9999-9999-9999-999999999902'::uuid, false),
 ('66666666-6666-6666-6666-666666660602'::uuid, '99999999-9999-9999-9999-999999999902'::uuid, false);

-- child SOW
INSERT INTO contract (id, contract_number, contract_type_code, title, status, contracting_entity_id, parent_contract_id, relationship_type, governing_law_code, primary_language, effective_date, expiry_date, value_amount, currency, risk_score, risk_tier, source, owner_user_id, type_attributes, summary) VALUES
 ('99999999-9999-9999-9999-999999999903'::uuid, 'ACME_INC-SOW-2025-0019', 'SOW', 'SOW #1 — Data platform migration', 'EXECUTED', '22222222-2222-2222-2222-222222222203'::uuid, '99999999-9999-9999-9999-999999999902'::uuid, 'CHILD_OF', 'State of New York', 'en', DATE '2025-03-15', DATE '2025-12-31', 620000, 'USD', 18, 'LOW', 'NATIVE', '33333333-3333-3333-3333-333333333305'::uuid,
  '{"deliverables":"Lift-and-shift of the analytics warehouse to Contoso Cloud, incl. runbooks","start_date":"2025-03-15"}'::jsonb,
  'First SOW under the Contoso MSA: analytics warehouse migration, fixed fee $620k, ends Dec 2025.');

INSERT INTO contract_party (contract_id, party_id, role, signatory_name, signatory_email) VALUES
 ('99999999-9999-9999-9999-999999999903'::uuid, '44444444-4444-4444-4444-444444444403'::uuid, 'COUNTERPARTY', 'Rachel Stone', 'rstone@contoso.example');

INSERT INTO contract_version (contract_id, version_no, version_label, is_executed, created_at) VALUES
 ('99999999-9999-9999-9999-999999999903'::uuid, 1, 'Executed', true, now() - interval '150 days');

-- amendment to the MSA (extends payment terms)
INSERT INTO contract (id, contract_number, contract_type_code, title, status, contracting_entity_id, parent_contract_id, relationship_type, governing_law_code, primary_language, effective_date, source, owner_user_id, assigned_lawyer_id, summary) VALUES
 ('99999999-9999-9999-9999-999999999904'::uuid, 'ACME_INC-AMD-2025-0003', 'MSA', 'Amendment No. 1 — Contoso Cloud MSA', 'EXECUTED', '22222222-2222-2222-2222-222222222203'::uuid, '99999999-9999-9999-9999-999999999902'::uuid, 'AMENDS', 'State of New York', 'en', DATE '2025-09-01', 'NATIVE', '33333333-3333-3333-3333-333333333305'::uuid, '33333333-3333-3333-3333-333333333303'::uuid,
  'Amendment No. 1 to the Contoso Cloud MSA: payment terms moved from 45 to 60 days; all other terms unchanged.');

INSERT INTO contract_term (contract_id, term_key, term_value, term_type, effective_from, effective_to, extraction_confidence, verified_by, verified_at) VALUES
 ('99999999-9999-9999-9999-999999999904'::uuid, 'payment_terms_days', '60'::jsonb, 'NUMBER', DATE '2025-09-01', NULL, 1.0, '33333333-3333-3333-3333-333333333303'::uuid, now());

INSERT INTO contract_version (contract_id, version_no, version_label, is_executed, created_at) VALUES
 ('99999999-9999-9999-9999-999999999904'::uuid, 1, 'Executed', true, now() - interval '30 days');

-- 3) Migrated legacy vendor agreement with unverified data (Globex, expiring soon)
INSERT INTO contract (id, contract_number, contract_type_code, title, status, contracting_entity_id, governing_law_code, primary_language, effective_date, expiry_date, notice_period_days, auto_renew, renewal_term_months, value_amount, currency, risk_score, risk_tier, source, owner_user_id, summary) VALUES
 ('99999999-9999-9999-9999-999999999905'::uuid, 'ACME_SAS-VEN-2019-0114', 'VENDOR_PURCHASE', 'Transport services — Globex (legacy)', 'EXECUTED', '22222222-2222-2222-2222-222222222202'::uuid, 'France', 'fr', DATE '2019-06-01', (CURRENT_DATE + 26), 90, true, 12, 240000, 'EUR', 55, 'HIGH', 'MIGRATED', '33333333-3333-3333-3333-333333333305'::uuid,
  'Legacy French-law transport services agreement with Globex Transport SAS, migrated from a shared drive. Auto-renews; notice window opens soon. Several terms unverified.');

INSERT INTO contract_party (contract_id, party_id, role) VALUES
 ('99999999-9999-9999-9999-999999999905'::uuid, '44444444-4444-4444-4444-444444444404'::uuid, 'COUNTERPARTY');

INSERT INTO contract_term (contract_id, term_key, term_value, term_type, effective_from, extraction_confidence) VALUES
 ('99999999-9999-9999-9999-999999999905'::uuid, 'auto_renew',         'true'::jsonb,       'BOOLEAN', DATE '2019-06-01', 0.62),
 ('99999999-9999-9999-9999-999999999905'::uuid, 'notice_period_days', '90'::jsonb,         'NUMBER',  DATE '2019-06-01', 0.55),
 ('99999999-9999-9999-9999-999999999905'::uuid, 'liability_cap',      '"uncapped"'::jsonb, 'STRING',  DATE '2019-06-01', 0.48);

INSERT INTO contract_version (contract_id, version_no, version_label, is_executed, created_at) VALUES
 ('99999999-9999-9999-9999-999999999905'::uuid, 1, 'Migrated original', true, now() - interval '400 days');

-- 4) In-flight draft NDA with Northwind (sitting in approval)
INSERT INTO contract (id, contract_number, contract_type_code, title, status, contracting_entity_id, governing_law_code, primary_language, risk_score, risk_tier, source, owner_user_id, assigned_lawyer_id, type_attributes, summary) VALUES
 ('99999999-9999-9999-9999-999999999906'::uuid, 'ACME_LTD-NDA-2026-0002', 'NDA', 'Mutual NDA — Northwind Traders', 'IN_REVIEW', '22222222-2222-2222-2222-222222222204'::uuid, 'England and Wales', 'en', 18, 'LOW', 'NATIVE', '33333333-3333-3333-3333-333333333306'::uuid, '33333333-3333-3333-3333-333333333302'::uuid,
  '{"mutual":true,"term_months":24,"purpose":"Retail distribution pilot in the UK","data_processing":false}'::jsonb,
  'Draft mutual NDA with Northwind Traders Ltd for a UK retail distribution pilot. Awaiting owner-manager approval.');

INSERT INTO contract_party (contract_id, party_id, role) VALUES
 ('99999999-9999-9999-9999-999999999906'::uuid, '44444444-4444-4444-4444-444444444402'::uuid, 'COUNTERPARTY');

INSERT INTO contract_version (contract_id, version_no, version_label, change_summary, is_executed, created_at) VALUES
 ('99999999-9999-9999-9999-999999999906'::uuid, 1, 'Draft v1', 'Generated from NDA — EMEA (EN) template.', false, now() - interval '2 days');

-- ---------- Obligations ----------
INSERT INTO obligation (contract_id, obligation_type, description, due_date, owner_user_id, owning_department, status, extraction_confidence, alert_lead_days) VALUES
 ('99999999-9999-9999-9999-999999999902'::uuid, 'RENEWAL_NOTICE', 'Decide whether to renew or serve non-renewal notice on the Contoso Cloud MSA (60-day notice).', (CURRENT_DATE + 40), '33333333-3333-3333-3333-333333333305'::uuid, 'Procurement', 'OPEN',    1.0,  30),
 ('99999999-9999-9999-9999-999999999902'::uuid, 'PAYMENT',        'Quarterly platform fee to Contoso Cloud.',                                                     (CURRENT_DATE + 12), '33333333-3333-3333-3333-333333333304'::uuid, 'Finance',     'OPEN',    1.0,  14),
 ('99999999-9999-9999-9999-999999999903'::uuid, 'DELIVERABLE',    'Contoso to deliver migration runbooks and sign-off pack.',                                     (CURRENT_DATE - 5),  '33333333-3333-3333-3333-333333333305'::uuid, 'Procurement', 'OVERDUE', 0.88, 7),
 ('99999999-9999-9999-9999-999999999905'::uuid, 'RENEWAL_NOTICE', 'Globex transport agreement auto-renews — notice window opens shortly. Unverified notice period (90d?).', (CURRENT_DATE + 26), '33333333-3333-3333-3333-333333333305'::uuid, 'Procurement', 'OPEN', 0.55, 30);

-- ---------- Precedent link (Northwind NDA drew on the Meridian NDA) ----------
INSERT INTO precedent_link (contract_id, precedent_contract_id, match_score, match_reasons, used_for, fields_inherited) VALUES
 ('99999999-9999-9999-9999-999999999906'::uuid, '99999999-9999-9999-9999-999999999901'::uuid, 0.72,
  '["Same contract type (NDA)","Both mutual","Recent executed precedent (Nov 2025)","Same requesting-department pattern"]'::jsonb,
  'PREFILL', '{"mutual":true,"term_months":24}'::jsonb);

-- ---------- Workflow instance for the in-flight NDA ----------
INSERT INTO workflow_instance (id, contract_id, workflow_definition_id, workflow_key, workflow_version_no, current_state, status, sla_due_at) VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01'::uuid, '99999999-9999-9999-9999-999999999906'::uuid, '88888888-8888-8888-8888-888888888801'::uuid, 'standard-approval', 1, 'owner_approval', 'RUNNING', now() + interval '20 hours');

INSERT INTO workflow_task (workflow_instance_id, state_key, task_type, assigned_role_expression, assigned_user_id, status, due_at) VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01'::uuid, 'owner_approval', 'APPROVAL', 'owner_manager', '33333333-3333-3333-3333-333333333303'::uuid, 'OPEN', now() + interval '20 hours');
