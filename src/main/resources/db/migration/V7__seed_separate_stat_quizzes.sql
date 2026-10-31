CREATE TEMP TABLE quiz_seed (
    stat text, position integer, title text, instructions text, seconds integer, task_weight integer, source text
) ON COMMIT DROP;

INSERT INTO quiz_seed VALUES
('strength', 1, 'Strength',
 'An original adult fitness self-report about usual experience over the past month, not a strength examination. No exercise or maximum lifts are required. Answer within your comfortable limits; never push through pain. Disability, health, equipment and opportunity affect responses. The score is a game summary, not medical or training advice.',
 0, 0, 'Background: CDC adult physical activity guidance, https://www.cdc.gov/physical-activity-basics/guidelines/adults.html . Original questions; no SportWiki questionnaire or validated norms are reproduced.'),
('perception', 2, 'Perception',
 'Six visual-detail and attention tasks share a 75-second server-controlled deadline. Select the matching detail; unanswered tasks count as misses. The timer continues when you switch tabs. Afterwards answer untimed everyday-attention questions. Vision, language, display and accessibility tools affect the outcome; this is not a vision test.',
 75, 70, 'Original visual-detail tasks and self-report prompts; not a validated perception assessment.'),
('endurance', 3, 'Endurance',
 'An original adult fitness self-report about usual activity over the past month. No running, breath-holding or exertion is required. Do not exercise to answer the questions. Health, disability, environment and opportunity affect responses. This is not a cardiovascular assessment or exercise prescription.',
 0, 0, 'Background: CDC adult physical activity guidance, https://www.cdc.gov/physical-activity-basics/guidelines/adults.html . Original questions; no validated fitness norms are used.'),
('charisma', 4, 'Charisma',
 'Choose how you would usually respond in six everyday social situations. This reflects communication choices, not attractiveness or a fixed personality trait. Culture, context and accessibility affect communication. No photo is requested or sent anywhere. Optional photo-presentation feedback is deferred pending provider privacy review and will never determine this score.',
 0, 0, 'Original communication scenarios, not a validated personality or charisma inventory.'),
('intelligence', 5, 'Intelligence',
 'Ten original reasoning and general-knowledge questions share a 180-second server-controlled deadline. Unanswered tasks count as misses; switching tabs does not pause time. Untimed reflection and optional education/work context follow. Education and occupation do not award points and are discarded. This is not an IQ test: no IQ, percentile or population comparison is calculated. English and prior exposure affect performance.',
 180, 70, 'Original reasoning and general-knowledge items; no proprietary IQ-test items, norm tables or IQ conversions.'),
('agility', 6, 'Agility',
 'Twelve fixed-grid coordination rounds share a 45-second server-controlled deadline. Select the marked cell with pointer, touch or Tab and Enter/Space. Every selection advances, including misses. Untimed coordination questions follow. Server deadlines include network delay; screen, device and accessibility tools matter. This is not reaction time or a medical motor assessment.',
 45, 70, 'Original fixed-grid coordination tasks and self-report prompts; no cross-device or population norms.'),
('luck', 7, 'Luck',
 'Three coin guesses and three six-sided die guesses are drawn by the server after each choice. Every option has the same chance within its game; there is no strategy, stake or reward. Afterwards reflect on perceived opportunities. The score is 70% random game outcome and 30% self-report, not a measure or prediction of real-world luck. Repeated attempts can change it substantially.',
 0, 70, 'Original chance games using a server-side cryptographically secure random generator. Coin match probability 1/2; die match probability 1/6.');

CREATE TEMP TABLE question_seed (
    stat text, position integer, prompt text, scored boolean DEFAULT true,
    labels text[] DEFAULT ARRAY['Never / not currently', 'Rarely', 'Sometimes', 'Often', 'Very often'],
    points integer[] DEFAULT ARRAY[0, 1, 2, 3, 4]
) ON COMMIT DROP;

INSERT INTO question_seed (stat, position, prompt) VALUES
('strength', 1, 'I include comfortable muscle-strengthening activity in my usual week.'),
('strength', 2, 'I can carry my usual shopping or daily items comfortably.'),
('strength', 3, 'I can rise from my usual chair using the support that is normal for me without unusual effort.'),
('strength', 4, 'I can move ordinary household objects within my comfortable limits.'),
('strength', 5, 'I feel in control of everyday pushing and pulling movements.'),
('strength', 6, 'I can maintain good form during the strengthening movements I already know.'),
('strength', 7, 'My usual lifting or carrying tasks feel manageable rather than near my limit.'),
('strength', 8, 'I can repeat familiar light resistance movements without losing control.'),
('endurance', 1, 'I include comfortable sustained movement in my usual week.'),
('endurance', 2, 'I can maintain a comfortable walking, wheeling or comparable movement pace.'),
('endurance', 3, 'I can complete my ordinary daily activity without needing unplanned long rests.'),
('endurance', 4, 'During familiar moderate activity, I can keep a pace at which I can still talk.'),
('endurance', 5, 'I can sustain a familiar activity for the duration I planned within my comfortable limits.'),
('endurance', 6, 'After familiar moderate activity, I return comfortably to my usual breathing.'),
('endurance', 7, 'I spread comfortable movement across several days rather than only one occasional effort.'),
('endurance', 8, 'I feel able to maintain my usual activity pace through an ordinary day.'),
('perception', 1, 'I notice small differences when comparing two everyday objects or documents.'),
('perception', 2, 'I can find a relevant detail without repeatedly overlooking it.'),
('perception', 3, 'I notice changes in a familiar environment.'),
('perception', 4, 'I check details rather than relying only on my first impression.'),
('agility', 1, 'I feel coordinated when using my usual input device or assistive controls.'),
('agility', 2, 'I can adjust a familiar movement when an object changes position.'),
('agility', 3, 'I can perform familiar hand movements accurately at a comfortable pace.'),
('agility', 4, 'I can switch between familiar actions without losing track of their order.'),
('luck', 1, 'I notice unexpected opportunities in my everyday life.'),
('luck', 2, 'I feel that chance events have recently worked in my favor.'),
('luck', 3, 'I find useful possibilities in situations I did not plan.'),
('luck', 4, 'I remember recent pleasant surprises.'),
('intelligence', 1, 'I break unfamiliar problems into smaller steps.'),
('intelligence', 2, 'I check an explanation against evidence before accepting it.'),
('intelligence', 3, 'I try a different approach when my first solution does not work.');

INSERT INTO question_seed (stat, position, prompt, labels, points) VALUES
('charisma', 1, 'A colleague is explaining a concern. What would you usually do first?',
 ARRAY['Interrupt with my solution', 'Wait quietly but plan my reply', 'Ask a clarifying question', 'Reflect their concern and check I understood'], ARRAY[0,1,3,4]),
('charisma', 2, 'You disagree with someone in a group. What is your usual response?',
 ARRAY['Make the disagreement personal', 'Withdraw without explaining', 'State my view respectfully', 'Acknowledge their view and explore the specific disagreement'], ARRAY[0,1,3,4]),
('charisma', 3, 'A new person joins an existing group. What would you usually do?',
 ARRAY['Ignore them', 'Wait for someone else to greet them', 'Introduce myself', 'Welcome them and offer a low-pressure way to join'], ARRAY[0,1,3,4]),
('charisma', 4, 'Someone declines your invitation. What would you usually do?',
 ARRAY['Pressure them to change their mind', 'Assume they dislike me', 'Accept their answer', 'Accept their boundary warmly without demanding an explanation'], ARRAY[0,1,3,4]),
('charisma', 5, 'You need to explain an unfamiliar idea. How do you usually approach it?',
 ARRAY['Use jargon and speak faster', 'Repeat the same explanation', 'Use a relevant example', 'Adapt the example and invite questions without putting anyone on the spot'], ARRAY[0,1,3,4]),
('charisma', 6, 'You realize your words hurt someone. What would you usually do?',
 ARRAY['Dismiss their reaction', 'Explain why I meant well only', 'Acknowledge the impact and apologize', 'Acknowledge the impact, apologize and ask how to avoid repeating it'], ARRAY[0,1,3,4]);

INSERT INTO question_seed (stat, position, prompt, scored, labels, points) VALUES
('intelligence', 4, 'Optional context: which learning background best describes you? This does not affect the score and is not saved.',
 false, ARRAY['Prefer not to say', 'School education', 'Vocational or apprenticeship learning', 'College or university', 'Self-directed or another path'], ARRAY[0,0,0,0,0]),
('intelligence', 5, 'Optional context: which area best describes your current work or study? This does not affect the score and is not saved.',
 false, ARRAY['Prefer not to say', 'Technical or scientific', 'People, care or service', 'Creative or communication', 'Trades, practical or operational', 'Studying, not working or another area'], ARRAY[0,0,0,0,0,0]);

CREATE TEMP TABLE task_seed (
    stat text, position integer, prompt text, kind text DEFAULT 'CHOICE',
    answer text, labels text[]
) ON COMMIT DROP;

INSERT INTO task_seed (stat, position, prompt, answer, labels) VALUES
('perception', 1, 'Match this signal exactly: AB8-D06', '2', ARRAY['AB8-DO6','AB8-D06','A88-D06']),
('perception', 2, 'Which string contains exactly three letter X characters?', '1', ARRAY['X7X2X','X7K2X','X7XX2X']),
('perception', 3, 'Match this signal exactly: 71I-L10', '3', ARRAY['711-L10','71I-LI0','71I-L10']),
('perception', 4, 'Which sequence differs from the other two?', '2', ARRAY['P4-Q9-R2','P4-Q8-R2','P4-Q9-R2']),
('perception', 5, 'Match this signal exactly: mn-rn-nm', '1', ARRAY['mn-rn-nm','nm-rn-nm','mn-mn-nm']),
('perception', 6, 'Which option contains the pair 39 exactly once?', '3', ARRAY['3939','9383','7392']),
('intelligence', 1, 'What comes next: 3, 6, 12, 24, ...?', '2', ARRAY['30','48','36','60']),
('intelligence', 2, 'All lanterns in a box are blue. This object is a lantern from that box. What follows?', '1', ARRAY['It is blue','Every blue object is a lantern','The box is blue','Nothing can be inferred']),
('intelligence', 3, 'A notebook and pen cost 9 credits total. The notebook costs 5 credits more than the pen. What does the pen cost?', '3', ARRAY['4','3','2','1']),
('intelligence', 4, 'Which does not belong by shape: triangle, square, circle, cube?', '4', ARRAY['Triangle','Square','Circle','Cube']),
('intelligence', 5, 'Five equally fast machines make five parts in five minutes. How long do ten machines take to make ten parts?', '2', ARRAY['10 minutes','5 minutes','2.5 minutes','20 minutes']),
('intelligence', 6, 'If today is Tuesday, what day is it 10 days later?', '1', ARRAY['Friday','Thursday','Saturday','Sunday']),
('intelligence', 7, 'Which organ primarily pumps blood through the body?', '3', ARRAY['Liver','Lung','Heart','Kidney']),
('intelligence', 8, 'Which planet is closest to the Sun?', '2', ARRAY['Venus','Mercury','Mars','Earth']),
('intelligence', 9, 'At standard atmospheric pressure, pure water freezes at which Celsius temperature?', '1', ARRAY['0','10','32','100']),
('intelligence', 10, 'Which process lets green plants use light energy to make sugars?', '4', ARRAY['Evaporation','Fermentation','Condensation','Photosynthesis']);

INSERT INTO task_seed (stat, position, prompt, kind, answer, labels)
SELECT 'agility', round, 'Select the marked target cell. A miss also advances the round.', 'GRID',
       target::text, ARRAY['1','2','3','4','5','6','7','8','9']
FROM (VALUES (1,5),(2,1),(3,9),(4,3),(5,7),(6,2),(7,8),(8,4),(9,6),(10,5),(11,3),(12,7)) rounds(round,target);

INSERT INTO task_seed (stat, position, prompt, kind, answer, labels)
SELECT 'luck', round, 'Coin game: guess the next independent server draw (1 in 2 chance).',
       'CHANCE', NULL, ARRAY['Heads','Tails'] FROM generate_series(1,3) round;
INSERT INTO task_seed (stat, position, prompt, kind, answer, labels)
SELECT 'luck', round, 'Die game: guess the next independent server draw (1 in 6 chance).',
       'CHANCE', NULL, ARRAY['1','2','3','4','5','6'] FROM generate_series(4,6) round;

INSERT INTO stat_quizzes (stat, version, position, definition)
SELECT s.stat, 1, s.position, jsonb_build_object(
    'stat', s.stat, 'version', 1, 'title', s.title, 'instructions', s.instructions,
    'timeLimitSeconds', s.seconds, 'taskWeightPercent', s.task_weight, 'source', s.source,
    'questions', (SELECT jsonb_agg(jsonb_build_object(
        'id', s.stat || '-q' || q.position, 'prompt', q.prompt, 'scored', q.scored,
        'options', (SELECT jsonb_agg(jsonb_build_object(
            'id', i::text, 'label', q.labels[i], 'value', q.points[i]) ORDER BY i)
            FROM generate_subscripts(q.labels, 1) i)
    ) ORDER BY q.position) FROM question_seed q WHERE q.stat = s.stat),
    'tasks', coalesce((SELECT jsonb_agg(jsonb_build_object(
        'id', s.stat || '-t' || t.position, 'prompt', t.prompt, 'kind', t.kind, 'answer', t.answer,
        'options', (SELECT jsonb_agg(jsonb_build_object(
            'id', i::text, 'label', t.labels[i], 'value', 0) ORDER BY i)
            FROM generate_subscripts(t.labels, 1) i)
    ) ORDER BY t.position) FROM task_seed t WHERE t.stat = s.stat), '[]'::jsonb)
) FROM quiz_seed s;
