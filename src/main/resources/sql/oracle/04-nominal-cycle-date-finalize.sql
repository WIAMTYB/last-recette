-- Run only after the reviewed backfill in 03. This statement fails if any plan
-- still lacks an anchor. All writers must remain stopped until it succeeds and
-- every application instance has been replaced with the new version.
ALTER TABLE EPARGNE_SUBSCRIPTION_PLAN MODIFY (CURRENT_NOMINAL_CYCLE_DATE NOT NULL);
