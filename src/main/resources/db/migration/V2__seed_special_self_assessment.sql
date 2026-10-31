INSERT INTO assessment_versions
    (id, assessment_id, version, scoring_version, title, description, instructions, estimated_minutes)
VALUES (
    'special-self-assessment-v1', 'special-self-assessment', 1, 1,
    'S.P.E.C.I.A.L. self-assessment',
    'A playful reflection on seven aspects of how you see yourself, not a validated measure of ability.',
    'Think about your usual experience during the past month. Choose how much you agree with each statement. '
    'This is not a medical, intelligence, or fitness test. Luck means perceived opportunity, not a prediction. '
    'No name or account is required. Only scores are saved, not your individual answers. '
    'Anyone with the unlisted result link can view your scores. Do not submit if you need private storage.',
    5
);

INSERT INTO questions (id, assessment_version_id, position, attribute, prompt) VALUES
('strength-1-v1', 'special-self-assessment-v1', 1, 'STRENGTH', 'Everyday lifting and carrying tasks feel manageable for me.'),
('strength-2-v1', 'special-self-assessment-v1', 2, 'STRENGTH', 'I feel confident in my ability to move ordinary household items safely.'),
('strength-3-v1', 'special-self-assessment-v1', 3, 'STRENGTH', 'I feel physically capable when daily tasks require some force.'),
('perception-1-v1', 'special-self-assessment-v1', 4, 'PERCEPTION', 'I notice small changes in familiar surroundings.'),
('perception-2-v1', 'special-self-assessment-v1', 5, 'PERCEPTION', 'I pick out relevant details when observing a busy scene.'),
('perception-3-v1', 'special-self-assessment-v1', 6, 'PERCEPTION', 'I notice subtle cues that help me understand what is happening around me.'),
('endurance-1-v1', 'special-self-assessment-v1', 7, 'ENDURANCE', 'I can sustain a comfortable effort through my usual daily activities.'),
('endurance-2-v1', 'special-self-assessment-v1', 8, 'ENDURANCE', 'I feel able to keep going through a reasonably demanding day with normal breaks.'),
('endurance-3-v1', 'special-self-assessment-v1', 9, 'ENDURANCE', 'My energy generally lasts for the activities I plan.'),
('charisma-1-v1', 'special-self-assessment-v1', 10, 'CHARISMA', 'I feel comfortable starting a conversation with someone new.'),
('charisma-2-v1', 'special-self-assessment-v1', 11, 'CHARISMA', 'I can help others feel heard during a conversation.'),
('charisma-3-v1', 'special-self-assessment-v1', 12, 'CHARISMA', 'I can explain my ideas in a way that engages other people.'),
('intelligence-1-v1', 'special-self-assessment-v1', 13, 'INTELLIGENCE', 'I feel comfortable breaking an unfamiliar problem into smaller parts.'),
('intelligence-2-v1', 'special-self-assessment-v1', 14, 'INTELLIGENCE', 'I can learn a new concept when I have time and suitable resources.'),
('intelligence-3-v1', 'special-self-assessment-v1', 15, 'INTELLIGENCE', 'I compare possible explanations before drawing a conclusion.'),
('agility-1-v1', 'special-self-assessment-v1', 16, 'AGILITY', 'I feel coordinated when performing familiar everyday movements.'),
('agility-2-v1', 'special-self-assessment-v1', 17, 'AGILITY', 'I can adjust my movements smoothly when a routine task changes.'),
('agility-3-v1', 'special-self-assessment-v1', 18, 'AGILITY', 'I feel comfortable with everyday tasks that need precise movements.'),
('luck-1-v1', 'special-self-assessment-v1', 19, 'LUCK', 'I feel that unexpected events sometimes create useful opportunities for me.'),
('luck-2-v1', 'special-self-assessment-v1', 20, 'LUCK', 'I tend to notice opportunities in chance encounters.'),
('luck-3-v1', 'special-self-assessment-v1', 21, 'LUCK', 'Looking back, I can see fortunate circumstances that helped me.');

INSERT INTO answer_options (id, question_id, position, label, score_value)
SELECT q.id || '-option-' || choice.value, q.id, choice.value, choice.label, choice.value
FROM questions q
CROSS JOIN (VALUES
    (1, 'Strongly disagree'),
    (2, 'Disagree'),
    (3, 'Neither agree nor disagree'),
    (4, 'Agree'),
    (5, 'Strongly agree')
) AS choice(value, label)
WHERE q.assessment_version_id = 'special-self-assessment-v1';
