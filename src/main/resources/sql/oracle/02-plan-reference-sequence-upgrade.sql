-- Existing schemas only: do not run after the updated 01-recurring-plan-schema.sql.
-- Stop all application pods and other plan writers before running this script.
-- Deploy the sequence-based allocator to every pod before restarting any writer.
-- Run once as the application schema owner; an existing sequence is an error.
DECLARE
    next_reference NUMBER;
BEGIN
    SELECT NVL(MAX(CASE
        WHEN REGEXP_LIKE(REFERENCE, '^EP[0-9]+$')
        THEN TO_NUMBER(SUBSTR(REFERENCE, 3))
    END), 0) + 1
    INTO next_reference
    FROM EPARGNE_SUBSCRIPTION_PLAN;

    IF next_reference > 999999999999999999 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Plan reference range exhausted');
    END IF;

    EXECUTE IMMEDIATE 'CREATE SEQUENCE EPARGNE_PLAN_REFERENCE_SEQ START WITH '
        || TO_CHAR(next_reference, 'FM999999999999999999')
        || ' INCREMENT BY 1 MAXVALUE 999999999999999999 NOCYCLE CACHE 20 NOORDER';
END;
/
