-- Extra demo accounts covering each access role in isolation and a spread of countries/regions,
-- so policy-document scoping and role-gated features can be exercised. Password: demo1234
-- (the 'SEED' hash is replaced at startup by DevPasswordSeeder).
INSERT INTO app_user (id, email, password_hash, display_name, default_entity_id, department, roles) VALUES
 ('33333333-3333-3333-3333-333333333311'::uuid, 'admin@acme.example',        'SEED', 'Alex Kerr (Platform Admin)',      '22222222-2222-2222-2222-222222222203'::uuid, 'IT',          'ADMIN'),
 ('33333333-3333-3333-3333-333333333312'::uuid, 'counsel.uk@acme.example',   'SEED', 'Nora Patel (UK Counsel)',         '22222222-2222-2222-2222-222222222204'::uuid, 'Legal',       'LEGAL'),
 ('33333333-3333-3333-3333-333333333313'::uuid, 'counsel.fr@acme.example',   'SEED', 'Luc Moreau (France Counsel)',     '22222222-2222-2222-2222-222222222202'::uuid, 'Legal',       'LEGAL'),
 ('33333333-3333-3333-3333-333333333314'::uuid, 'approver@acme.example',     'SEED', 'Wei Zhang (Deal Approver)',       '22222222-2222-2222-2222-222222222201'::uuid, 'Operations',  'APPROVER'),
 ('33333333-3333-3333-3333-333333333315'::uuid, 'finance.us@acme.example',   'SEED', 'Grace Obi (US Finance)',          '22222222-2222-2222-2222-222222222203'::uuid, 'Finance',     'FINANCE'),
 ('33333333-3333-3333-3333-333333333316'::uuid, 'requester.uk@acme.example', 'SEED', 'Sam Ellis (UK Requester)',        '22222222-2222-2222-2222-222222222204'::uuid, 'Sales',       'REQUESTER');
