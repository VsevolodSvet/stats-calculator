CREATE TABLE profile_photos (
    owner_id uuid PRIMARY KEY REFERENCES accounts(id),
    revision uuid NOT NULL UNIQUE,
    image bytea NOT NULL CHECK (octet_length(image) BETWEEN 1 AND 5242880),
    updated_at timestamp with time zone NOT NULL
);
