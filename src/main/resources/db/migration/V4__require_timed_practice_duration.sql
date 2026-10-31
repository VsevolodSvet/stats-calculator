ALTER TABLE practice_results
    ADD CONSTRAINT practice_results_timed_duration_required
    CHECK (mode <> 'TIMED' OR elapsed_ms IS NOT NULL);
