CREATE TABLE assessment_versions (
    id varchar(80) PRIMARY KEY,
    assessment_id varchar(60) NOT NULL,
    version integer NOT NULL CHECK (version > 0),
    scoring_version integer NOT NULL CHECK (scoring_version = 1),
    title varchar(160) NOT NULL,
    description text NOT NULL,
    instructions text NOT NULL,
    estimated_minutes integer NOT NULL CHECK (estimated_minutes > 0),
    UNIQUE (assessment_id, version)
);

CREATE TABLE questions (
    id varchar(100) PRIMARY KEY,
    assessment_version_id varchar(80) NOT NULL REFERENCES assessment_versions(id),
    position integer NOT NULL CHECK (position > 0),
    attribute varchar(20) NOT NULL CHECK (
        attribute IN ('STRENGTH', 'PERCEPTION', 'ENDURANCE', 'CHARISMA',
                      'INTELLIGENCE', 'AGILITY', 'LUCK')),
    prompt text NOT NULL,
    UNIQUE (assessment_version_id, position)
);

CREATE TABLE answer_options (
    id varchar(120) PRIMARY KEY,
    question_id varchar(100) NOT NULL REFERENCES questions(id),
    position integer NOT NULL CHECK (position BETWEEN 1 AND 5),
    label varchar(100) NOT NULL,
    score_value integer NOT NULL CHECK (score_value BETWEEN 1 AND 5),
    UNIQUE (question_id, position)
);

CREATE TABLE results (
    id uuid PRIMARY KEY,
    assessment_version_id varchar(80) NOT NULL REFERENCES assessment_versions(id),
    scoring_version integer NOT NULL CHECK (scoring_version = 1),
    created_at timestamp with time zone NOT NULL,
    strength integer NOT NULL CHECK (strength BETWEEN 1 AND 10),
    perception integer NOT NULL CHECK (perception BETWEEN 1 AND 10),
    endurance integer NOT NULL CHECK (endurance BETWEEN 1 AND 10),
    charisma integer NOT NULL CHECK (charisma BETWEEN 1 AND 10),
    intelligence integer NOT NULL CHECK (intelligence BETWEEN 1 AND 10),
    agility integer NOT NULL CHECK (agility BETWEEN 1 AND 10),
    luck integer NOT NULL CHECK (luck BETWEEN 1 AND 10)
);
