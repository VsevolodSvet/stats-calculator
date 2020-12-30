-- Table: users
CREATE TABLE users (
  id       INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL
)
  ENGINE = InnoDB;

-- Table: roles
CREATE TABLE roles (
  id   INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL
)
  ENGINE = InnoDB;

-- Table for mapping user and roles: user_roles
CREATE TABLE user_roles (
  user_id INT NOT NULL,
  role_id INT NOT NULL,

  FOREIGN KEY (user_id) REFERENCES users (id),
  FOREIGN KEY (role_id) REFERENCES roles (id),

  UNIQUE (user_id, role_id)
)
  ENGINE = InnoDB;

-- Table for stats
CREATE TABLE stats (
  id INT NOT NULL,
  strength INT NOT NULL,
  perception INT NOT NULL,
  endurance INT NOT NULL,
  charisma INT NOT NULL,
  intelligence INT NOT NULL,
  agility INT NOT NULL,
  luck INT NOT NULL,

  FOREIGN KEY (id) REFERENCES users (id),

  UNIQUE (id)
)
  ENGINE = InnoDB

-- Table for questions
-- NOTE: maybe should be split to tables questions/answers (depends on security)
CREATE TABLE questions (
  id   INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  category INT NOT NULL,
  weight   INT NOT NULL,
  question   JSON NOT NULL,
  answer   JSON NOT NULL,
  media_link   VARCHAR(255)
)
  ENGINE = InnoDB

-- Insert data

INSERT INTO users VALUES (1, 'admin', 'example@gmail.com', '$2a$11$uSXS6rLJ91WjgOHhEGDx..VGs7MkKZV68Lv5r1uwFu7HgtRn3dcXG'); --12345678

INSERT INTO roles VALUES (1, 'ROLE_USER');
INSERT INTO roles VALUES (2, 'ROLE_ADMIN');

INSERT INTO user_roles VALUES (1, 2);