-- Existing schemas only. Stop all pods and other plan writers until completion
-- of 04-nominal-cycle-date-finalize.sql and deployment of the updated application.
ALTER TABLE EPARGNE_SUBSCRIPTION_PLAN ADD (CURRENT_NOMINAL_CYCLE_DATE DATE);

-- Backfill CURRENT_NOMINAL_CYCLE_DATE for every plan from its reviewed schedule
-- and lifecycle audit history before running 04. Do not change CURRENT_CYCLE_DUE_DATE,
-- NEXT_EXECUTION_DATE, RETRY_DEADLINE_DATE or existing execution keys.
-- The old executable date alone cannot identify the nominal month: February 2
-- could represent January 31 shifted over a weekend or a fresh February resume.
-- Preferred-day changes while a cycle was due can also hide its original day.
-- For normal cycles use the original preferred day capped at that month's end;
-- for a freshly resumed cycle use the resume date before weekend adjustment.
-- Example after confirming the January anchor in audit history:
-- UPDATE EPARGNE_SUBSCRIPTION_PLAN
-- SET CURRENT_NOMINAL_CYCLE_DATE = DATE '2026-01-31'
-- WHERE REFERENCE = 'EP000001';

SELECT REFERENCE, FREQUENCY, PREFERRED_DAY_OF_MONTH, CURRENT_CYCLE_DUE_DATE
FROM EPARGNE_SUBSCRIPTION_PLAN
WHERE CURRENT_NOMINAL_CYCLE_DATE IS NULL
ORDER BY ID;
