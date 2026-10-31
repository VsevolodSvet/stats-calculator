CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    username varchar(30) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CHECK (username ~ '^[a-z0-9_]{3,30}$')
);

ALTER TABLE results ADD COLUMN owner_id uuid REFERENCES accounts(id);
ALTER TABLE practice_results ADD COLUMN owner_id uuid REFERENCES accounts(id);

CREATE INDEX results_owner_history ON results (owner_id, created_at DESC, id DESC)
    WHERE owner_id IS NOT NULL;
CREATE INDEX practice_results_owner_history ON practice_results (owner_id, created_at DESC, id DESC)
    WHERE owner_id IS NOT NULL;
