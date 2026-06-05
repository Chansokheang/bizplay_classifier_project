-- ============================================================
-- Seed data: departments + staff for corp_no = '1234567890'
-- Idempotent: safe to re-run (ON CONFLICT DO NOTHING on the
-- existing unique constraints).
--   conversational_department: UNIQUE (corp_no, name)
--   conversational_staff:      UNIQUE (department_id, name, position)
-- ============================================================

-- 0) Ensure the corp (and its group) exist, since corp_no is an FK target.
INSERT INTO corp_group (corp_group_cd)
VALUES ('GRP_TEST')
ON CONFLICT (corp_group_cd) DO NOTHING;

INSERT INTO corp (corp_no, corp_group_id, corp_name)
SELECT '1234567890', cg.corp_group_id, 'Test Corp'
FROM corp_group cg
WHERE cg.corp_group_cd = 'GRP_TEST'
ON CONFLICT (corp_no) DO NOTHING;

-- 1) Departments
INSERT INTO conversational_department (corp_no, name)
VALUES
    ('1234567890', 'Sales'),
    ('1234567890', 'Marketing'),
    ('1234567890', 'Engineering'),
    ('1234567890', 'Finance'),
    ('1234567890', 'Human Resources')
ON CONFLICT (corp_no, name) DO NOTHING;

-- 2) Staff (joined to their department by name within this corp)
INSERT INTO conversational_staff (department_id, name, position)
SELECT d.id, v.name, v.position
FROM (
    VALUES
        ('Sales',           'John Doe',      'Manager'),
        ('Sales',           'Mike Ross',     'Staff'),
        ('Sales',           'Rachel Zane',   'Associate'),
        ('Marketing',       'Jane Smith',    'Specialist'),
        ('Marketing',       'Tom Hardy',     'Lead'),
        ('Engineering',     'Alice Johnson', 'Senior Engineer'),
        ('Engineering',     'Bob Martin',    'Engineer'),
        ('Engineering',     'Charlie Park',  'Intern'),
        ('Finance',         'David Kim',     'Analyst'),
        ('Finance',         'Emma Wilson',   'Controller'),
        ('Human Resources', 'Grace Lee',     'HR Manager'),
        ('Human Resources', 'Henry Cho',     'Recruiter')
) AS v(department_name, name, position)
JOIN conversational_department d
    ON d.corp_no = '1234567890' AND d.name = v.department_name
ON CONFLICT (department_id, name, position) DO NOTHING;

-- 3) Verify
SELECT d.name AS department, s.name AS staff, s.position
FROM conversational_staff s
JOIN conversational_department d ON d.id = s.department_id
WHERE d.corp_no = '1234567890'
ORDER BY d.name, s.name;
