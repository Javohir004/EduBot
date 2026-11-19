-- Reference data: default plans. Prices are initial assumptions and can be changed by the platform admin.
insert into subscription_plan (id, code, name, monthly_price, sort_order, created_at, updated_at)
values (uuidv7(), 'START', 'Start', 199000.00, 1, now(), now()),
       (uuidv7(), 'STANDARD', 'Standard', 449000.00, 2, now(), now()),
       (uuidv7(), 'PREMIUM', 'Premium', 899000.00, 3, now(), now());

insert into plan_entitlement (plan_id, entitlement_key, value)
select p.id, e.entitlement_key, e.value
from subscription_plan p
         join (values ('START', 'MAX_STUDENTS', 100),
                      ('START', 'MAX_TEACHERS', 5),
                      ('START', 'MAX_GROUPS', 15),
                      ('START', 'FEATURE_NOTIFICATIONS', 1),
                      ('START', 'FEATURE_PARENT_PORTAL', 0),
                      ('START', 'FEATURE_EXPORT', 0),
                      ('STANDARD', 'MAX_STUDENTS', 300),
                      ('STANDARD', 'MAX_TEACHERS', 15),
                      ('STANDARD', 'MAX_GROUPS', 50),
                      ('STANDARD', 'FEATURE_NOTIFICATIONS', 1),
                      ('STANDARD', 'FEATURE_PARENT_PORTAL', 1),
                      ('STANDARD', 'FEATURE_EXPORT', 0),
                      ('PREMIUM', 'MAX_STUDENTS', 1000),
                      ('PREMIUM', 'MAX_TEACHERS', 100),
                      ('PREMIUM', 'MAX_GROUPS', 300),
                      ('PREMIUM', 'FEATURE_NOTIFICATIONS', 1),
                      ('PREMIUM', 'FEATURE_PARENT_PORTAL', 1),
                      ('PREMIUM', 'FEATURE_EXPORT', 1)) as e(plan_code, entitlement_key, value)
              on e.plan_code = p.code;
