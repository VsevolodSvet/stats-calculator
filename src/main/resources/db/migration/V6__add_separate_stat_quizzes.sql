CREATE TABLE stat_quizzes (
    stat varchar(20) NOT NULL CHECK (stat IN
        ('strength', 'perception', 'endurance', 'charisma', 'intelligence', 'agility', 'luck')),
    version integer NOT NULL CHECK (version > 0),
    position integer NOT NULL CHECK (position BETWEEN 1 AND 7),
    definition jsonb NOT NULL,
    PRIMARY KEY (stat, version)
);

CREATE TABLE stat_quiz_results (
    id uuid PRIMARY KEY,
    owner_id uuid REFERENCES accounts(id),
    stat varchar(20) NOT NULL,
    quiz_version integer NOT NULL,
    scoring_version integer NOT NULL CHECK (scoring_version > 0),
    created_at timestamp with time zone NOT NULL,
    score integer NOT NULL CHECK (score BETWEEN 1 AND 10),
    survey_percent integer NOT NULL CHECK (survey_percent BETWEEN 0 AND 100),
    tasks_correct integer NOT NULL CHECK (tasks_correct >= 0),
    tasks_total integer NOT NULL CHECK (tasks_total >= 0),
    tasks_answered integer NOT NULL CHECK (tasks_answered BETWEEN tasks_correct AND tasks_total),
    FOREIGN KEY (stat, quiz_version) REFERENCES stat_quizzes(stat, version)
);

CREATE INDEX stat_quiz_owner_history ON stat_quiz_results (owner_id, created_at DESC, id DESC)
    WHERE owner_id IS NOT NULL;
CREATE INDEX stat_quiz_owner_latest ON stat_quiz_results (owner_id, stat, created_at DESC, id DESC)
    WHERE owner_id IS NOT NULL;
