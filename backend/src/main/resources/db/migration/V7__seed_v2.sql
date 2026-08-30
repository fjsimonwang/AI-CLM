-- ============================================================
-- V7 — Template library bodies, richer contract data, sample discussion
-- ============================================================

UPDATE template SET
  description = 'Standard mutual non-disclosure agreement for EMEA entities. GDPR Art. 28 DPA annex included when personal data is in scope.',
  tags = 'NDA,EMEA,mutual,standard',
  body_html = '<h1>Mutual Non-Disclosure Agreement</h1>'
    || '<p>This Agreement is entered into between <strong>{{entity_name}}</strong> ("Discloser/Recipient") and '
    || '<strong>{{counterparty_name}}</strong> ("Recipient/Discloser").</p>'
    || '<h2>1. Purpose</h2><p>The parties wish to explore a potential business relationship ("the Purpose") '
    || 'and will disclose confidential information for that Purpose.</p>'
    || '<h2>2. Confidentiality</h2><p>The Receiving Party shall protect Confidential Information using at least '
    || 'the degree of care it uses for its own confidential information and not less than reasonable care, and '
    || 'shall use it solely for the Purpose. Obligations survive for five (5) years after disclosure.</p>'
    || '<h2>3. Data Protection</h2><p>Where either party processes personal data on behalf of the other, the '
    || 'parties shall enter into the Data Processing Agreement attached as Annex A (Article 28 GDPR).</p>'
    || '<h2>4. Term and Termination</h2><p>Either party may terminate for material breach not cured within '
    || 'thirty (30) days of written notice. Either party may terminate for convenience on ninety (90) days'' notice.</p>'
    || '<h2>5. Governing Law</h2><p>This Agreement is governed by the laws of {{governing_law}}.</p>'
WHERE id = '77777777-7777-7777-7777-777777777701'::uuid;

UPDATE template SET
  description = 'Master services agreement for EMEA entities. Preferred liability cap (12 months fees), mutual indemnity, customer-owned deliverables.',
  tags = 'MSA,EMEA,services,standard',
  body_html = '<h1>Master Services Agreement</h1>'
    || '<p>Between <strong>{{entity_name}}</strong> ("Customer") and <strong>{{counterparty_name}}</strong> ("Supplier").</p>'
    || '<h2>1. Services</h2><p>The Supplier shall provide the services described in each Statement of Work agreed under this Agreement.</p>'
    || '<h2>2. Confidentiality</h2><p>Each party shall protect the other''s Confidential Information and use it solely to perform this Agreement.</p>'
    || '<h2>3. Intellectual Property</h2><p>All deliverables created specifically for the Customer are owned by the Customer upon full payment. '
    || 'Each party retains its pre-existing materials.</p>'
    || '<h2>4. Limitation of Liability</h2><p>Each party''s aggregate liability shall not exceed the fees paid or payable in the twelve (12) '
    || 'months preceding the claim. Neither party is liable for indirect or consequential damages.</p>'
    || '<h2>5. Indemnification</h2><p>Each party shall indemnify the other against third-party claims arising from its breach, gross negligence or wilful misconduct.</p>'
    || '<h2>6. Term and Termination</h2><p>Material breach cure period of thirty (30) days; termination for convenience on ninety (90) days'' notice.</p>'
    || '<h2>7. Governing Law</h2><p>This Agreement is governed by the laws of {{governing_law}}.</p>'
WHERE id = '77777777-7777-7777-7777-777777777702'::uuid;

-- Additional library templates (US MSA, DPA annex, consulting SOW)
INSERT INTO template (id, name, contract_type_code, legal_entity_id, jurisdiction_code, language_code, version_no, owning_legal_team_id, approved_at, description, tags, body_html) VALUES
 ('77777777-7777-7777-7777-777777777703'::uuid, 'MSA — Americas (EN)', 'MSA', '22222222-2222-2222-2222-222222222203'::uuid, 'GLOBAL', 'en', 1, '11111111-1111-1111-1111-111111111102'::uuid, now(),
  'Master services agreement for the US entity. New York law, arbitration (ICC), 2x-fees fallback cap available for strategic suppliers.',
  'MSA,Americas,services',
  '<h1>Master Services Agreement (US)</h1><p>Between <strong>{{entity_name}}</strong> and <strong>{{counterparty_name}}</strong>.</p>'
   || '<h2>1. Services</h2><p>As described in each Order Form or SOW.</p>'
   || '<h2>2. Fees and Payment</h2><p>Net {{payment_terms_days}} days from invoice date.</p>'
   || '<h2>3. Limitation of Liability</h2><p>Aggregate liability capped at the greater of fees paid in the prior 12 months. '
   || 'A 2x cap may apply to designated strategic suppliers with legal approval.</p>'
   || '<h2>4. Governing Law; Dispute Resolution</h2><p>New York law. Disputes resolved by ICC arbitration seated in New York.</p>'),
 ('77777777-7777-7777-7777-777777777704'::uuid, 'DPA Annex — GDPR Art. 28 (EN)', 'DPA', NULL, 'EU', 'en', 1, '11111111-1111-1111-1111-111111111101'::uuid, now(),
  'Standalone data processing agreement reflecting GDPR Article 28. Attach to any agreement where personal data is processed on behalf of the other party.',
  'DPA,GDPR,privacy,annex',
  '<h1>Data Processing Agreement</h1><p>This DPA forms part of the agreement between <strong>{{entity_name}}</strong> (Controller) '
   || 'and <strong>{{counterparty_name}}</strong> (Processor).</p>'
   || '<h2>1. Scope and Roles</h2><p>The Processor processes personal data only on documented instructions from the Controller.</p>'
   || '<h2>2. Security</h2><p>The Processor implements appropriate technical and organisational measures, including encryption in transit and at rest.</p>'
   || '<h2>3. Sub-processing</h2><p>General written authorisation with a right to object; the Processor remains liable for its sub-processors.</p>'
   || '<h2>4. International Transfers</h2><p>Transfers outside the EEA rely on the EU Standard Contractual Clauses.</p>'
   || '<h2>5. Data Subject Rights & Breach</h2><p>The Processor assists the Controller and notifies breaches without undue delay and within 72 hours.</p>'),
 ('77777777-7777-7777-7777-777777777705'::uuid, 'Consulting SOW (EN)', 'SOW', NULL, 'GLOBAL', 'en', 1, '11111111-1111-1111-1111-111111111102'::uuid, now(),
  'Statement of work template for fixed-scope and staff-augmentation consulting engagements under an existing MSA.',
  'SOW,consulting,services',
  '<h1>Statement of Work</h1><p>Under the Master Services Agreement between <strong>{{entity_name}}</strong> and <strong>{{counterparty_name}}</strong>.</p>'
   || '<h2>1. Engagement</h2><p>Type: {{engagement_type}}. Start date: {{start_date}}.</p>'
   || '<h2>2. Deliverables</h2><p>{{deliverables}}</p>'
   || '<h2>3. Fees</h2><p>{{fee_amount}} on a {{fee_basis}} basis, invoiced per the MSA. Payment terms: net {{payment_terms_days}} days.</p>'
   || '<h2>4. Acceptance</h2><p>The Customer has {{acceptance_period_days}} days to review each deliverable against the agreed acceptance criteria.</p>'
   || '<h2>5. Intellectual Property</h2><p>{{ip_ownership}}.</p>');

-- Point the consulting SOW type at its template
UPDATE contract_type_definition SET default_template_id = '77777777-7777-7777-7777-777777777705'::uuid WHERE code = 'SOW';

-- Enrich the Contoso MSA with the deep field set + first-class columns
UPDATE contract SET
  annual_value_amount = 900000,
  payment_terms_days = 60,
  renewal_type = 'AUTO',
  liability_summary = '2x trailing-12-months fees (fallback tier) — legal-approved for strategic vendor',
  type_attributes = '{
    "pricing_model":"SUBSCRIPTION","total_contract_value":1800000,"annual_contract_value":900000,"currency":"USD",
    "payment_terms_days":60,"invoicing_frequency":"QUARTERLY","expenses_reimbursable":false,
    "initial_term_months":24,"renewal_type":"AUTO","renewal_term_months":12,"termination_notice_days":60,
    "termination_for_convenience":true,"liability_cap_basis":"MULTIPLE_OF_FEES","liability_cap_multiple":2,
    "consequential_damages_excluded":true,"indemnification":"MUTUAL","insurance_required":true,"insurance_amount":5000000,
    "ip_ownership":"CUSTOMER_OWNS","data_processing":true,"data_residency":"US","security_standard":"SOC2",
    "sla_applicable":true,"sla_uptime_pct":99.9,"sla_service_credits":true,"support_hours":"24X7",
    "exclusivity":false,"subcontracting_allowed":true,"assignment_allowed":false,"dispute_resolution":"ARBITRATION_ICC"
  }'::jsonb
WHERE id = '99999999-9999-9999-9999-999999999902'::uuid;

UPDATE contract SET payment_terms_days = 90, renewal_type = 'AUTO',
  liability_summary = 'Uncapped (legacy) — flagged for renegotiation at renewal'
WHERE id = '99999999-9999-9999-9999-999999999905'::uuid;

-- Sample discussion thread on the in-flight Northwind NDA
INSERT INTO comment_thread (id, entity_type, entity_id, title, status, created_by, created_at) VALUES
 ('cccccccc-cccc-cccc-cccc-cccccccccc01'::uuid, 'CONTRACT', '99999999-9999-9999-9999-999999999906', 'Term length for the pilot', 'OPEN',
  '33333333-3333-3333-3333-333333333302'::uuid, now() - interval '1 day');

INSERT INTO comment_message (thread_id, author_user_id, body_html, created_at) VALUES
 ('cccccccc-cccc-cccc-cccc-cccccccccc01'::uuid, '33333333-3333-3333-3333-333333333302'::uuid,
  '<p>Northwind asked for a <strong>3-year</strong> term. Our standard for a pilot is 2 years — are we comfortable extending?</p>', now() - interval '1 day'),
 ('cccccccc-cccc-cccc-cccc-cccccccccc01'::uuid, '33333333-3333-3333-3333-333333333306'::uuid,
  '<p>2 years is fine commercially. The pilot outcome will be clear well before then.</p>', now() - interval '20 hours'),
 ('cccccccc-cccc-cccc-cccc-cccccccccc01'::uuid, '33333333-3333-3333-3333-333333333301'::uuid,
  '<p>Agreed — keep it at <strong>24 months</strong>. If they push back, offer a mutual 12-month extension option.</p>', now() - interval '18 hours');
