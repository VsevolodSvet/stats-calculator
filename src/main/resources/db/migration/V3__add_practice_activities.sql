CREATE TABLE practice_activities (
    id varchar(80) PRIMARY KEY,
    activity_id varchar(60) NOT NULL,
    version integer NOT NULL CHECK (version > 0),
    title varchar(160) NOT NULL,
    description text NOT NULL,
    instructions text NOT NULL,
    coordination boolean NOT NULL,
    content jsonb NOT NULL,
    UNIQUE (activity_id, version)
);

CREATE TABLE practice_results (
    id uuid PRIMARY KEY,
    activity_version_id varchar(80) NOT NULL REFERENCES practice_activities(id),
    scoring_version integer NOT NULL CHECK (scoring_version = 1),
    created_at timestamp with time zone NOT NULL,
    mode varchar(10) NOT NULL CHECK (mode IN ('UNTIMED', 'TIMED')),
    input_method varchar(10) NOT NULL CHECK (input_method IN ('POINTER', 'KEYBOARD', 'MIXED')),
    elapsed_ms bigint,
    correct integer NOT NULL,
    total integer NOT NULL CHECK (total > 0),
    CHECK (correct BETWEEN 0 AND total),
    CHECK ((mode = 'UNTIMED' AND elapsed_ms IS NULL)
        OR (mode = 'TIMED' AND elapsed_ms BETWEEN 1 AND 86400000))
);

INSERT INTO practice_activities
    (id, activity_id, version, title, description, instructions, coordination, content)
VALUES (
    'visual-matching-v1', 'visual-matching', 1, 'Visual-detail matching',
    'Match six short signal codes. Untimed practice in noticing differences, not a vision or perception assessment.',
    'For each signal, select the identical code. There is no time limit. Zoom in as needed; assistive technology is welcome. '
    'Font rendering, vision, zoom and familiarity influence this exercise. Repeated attempts reflect practice, not measured improvement in perception.',
    false,
    '{"items":[
      {"id":"visual-1","prompt":"Match this signal: K7-M2-Q9","correctOptionId":"b","options":[
        {"id":"a","label":"K7-N2-Q9"},{"id":"b","label":"K7-M2-Q9"},{"id":"c","label":"K7-M2-O9"}]},
      {"id":"visual-2","prompt":"Match this signal: 4826-7319","correctOptionId":"c","options":[
        {"id":"a","label":"4826-7139"},{"id":"b","label":"4862-7319"},{"id":"c","label":"4826-7319"}]},
      {"id":"visual-3","prompt":"Match this signal: R4 / T8 / B3","correctOptionId":"a","options":[
        {"id":"a","label":"R4 / T8 / B3"},{"id":"b","label":"R4 / T3 / B8"},{"id":"c","label":"R4 / F8 / B3"}]},
      {"id":"visual-4","prompt":"Match this signal: AX-57-CP-24","correctOptionId":"b","options":[
        {"id":"a","label":"AX-57-PC-24"},{"id":"b","label":"AX-57-CP-24"},{"id":"c","label":"AX-75-CP-24"}]},
      {"id":"visual-5","prompt":"Match this signal: 6H2 : 9F4 : 3D8","correctOptionId":"c","options":[
        {"id":"a","label":"6H2 : 9F4 : 3B8"},{"id":"b","label":"6H2 : 9E4 : 3D8"},{"id":"c","label":"6H2 : 9F4 : 3D8"}]},
      {"id":"visual-6","prompt":"Match this signal: WEST / 328 / GATE","correctOptionId":"a","options":[
        {"id":"a","label":"WEST / 328 / GATE"},{"id":"b","label":"WEST / 382 / GATE"},{"id":"c","label":"WEST / 328 / DATE"}]}
    ]}'::jsonb
), (
    'reasoning-v1', 'reasoning', 1, 'Reasoning puzzles',
    'Six short logic and pattern puzzles. Untimed practice, not an IQ test.',
    'Choose one answer to each puzzle. There is no time limit. You may use notes and assistive tools. '
    'Language, education and familiarity with puzzles influence results. These original puzzles are not standardized or normed; do not compare scores between people.',
    false,
    '{"items":[
      {"id":"reason-1","prompt":"The sequence starts 3, 6, 12, 24. If each term doubles, what comes next?","correctOptionId":"b","options":[
        {"id":"a","label":"36"},{"id":"b","label":"48"},{"id":"c","label":"30"}]},
      {"id":"reason-2","prompt":"Every sealed crate has a label. Crate R is sealed. Which conclusion must follow?","correctOptionId":"a","options":[
        {"id":"a","label":"Crate R has a label."},{"id":"b","label":"Every labeled crate is sealed."},{"id":"c","label":"Crate R is empty."}]},
      {"id":"reason-3","prompt":"Mara arrives before Niko. Niko arrives before Oren. Who arrives first among these three?","correctOptionId":"c","options":[
        {"id":"a","label":"Niko"},{"id":"b","label":"Oren"},{"id":"c","label":"Mara"}]},
      {"id":"reason-4","prompt":"Three identical filters cost 18 tokens together. At the same unit price, what do five filters cost?","correctOptionId":"b","options":[
        {"id":"a","label":"24 tokens"},{"id":"b","label":"30 tokens"},{"id":"c","label":"36 tokens"}]},
      {"id":"reason-5","prompt":"Exactly one of three lamps is on. The red lamp is off and the green lamp is off. What can you conclude?","correctOptionId":"a","options":[
        {"id":"a","label":"The third lamp is on."},{"id":"b","label":"All three lamps are off."},{"id":"c","label":"The red lamp must be broken."}]},
      {"id":"reason-6","prompt":"A machine follows this rule: add 2 to its input, then multiply by 3. What is its output for input 4?","correctOptionId":"c","options":[
        {"id":"a","label":"14"},{"id":"b","label":"12"},{"id":"c","label":"18"}]}
    ]}'::jsonb
);

INSERT INTO practice_activities
    (id, activity_id, version, title, description, instructions, coordination, content)
SELECT 'coordination-v1', 'coordination', 1, 'Target-selection coordination',
    'Select twelve marked cells in a fixed grid. Choose untimed or elapsed-time practice; no countdown or ability ranking.',
    'Select the cell marked TARGET in each of twelve rounds. Every selection advances the round, including a miss. '
    'The grid never moves and supports mouse, touch and keyboard. Untimed mode is the default. '
    'Timed mode records elapsed time, not reaction time, with no deadline. Input device, screen size, keyboard navigation, '
    'browser scheduling and assistive technology affect timing. Choose a comfortable mode; stop if uncomfortable. '
    'Leaving the tab or window during timed practice interrupts timing; you can continue untimed or restart. '
    'Timing is browser-reported and cannot be verified by the server. Results are not comparable across devices or people.',
    true,
    jsonb_build_object('items', jsonb_agg(
        jsonb_build_object(
            'id', 'target-' || sequence.round,
            'prompt', 'Select the cell marked TARGET.',
            'correctOptionId', sequence.target::text,
            'options', (SELECT jsonb_agg(
                jsonb_build_object('id', cell::text, 'label', 'Cell ' || cell) ORDER BY cell)
                FROM generate_series(1, 9) cell)
        ) ORDER BY sequence.round
    ))
FROM (VALUES (1,5), (2,1), (3,9), (4,3), (5,7), (6,2),
             (7,8), (8,4), (9,6), (10,1), (11,5), (12,9)) AS sequence(round, target);
