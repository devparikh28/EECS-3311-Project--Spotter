-- Spotter local schema.
-- Every statement is idempotent, so migrate()
-- can run on every startup.

CREATE TABLE IF NOT EXISTS schema_version (
  version     INTEGER PRIMARY KEY,
  applied_at  TEXT NOT NULL
);

-- ---------- lifter ----------

CREATE TABLE IF NOT EXISTS lifter_profile (
  id                     INTEGER PRIMARY KEY,
  bodyweight_kg          REAL NOT NULL,
  weight_class_kg        REAL,
  goal                   TEXT NOT NULL,
  experience             TEXT NOT NULL,
  split_preference       TEXT NOT NULL
                           DEFAULT 'AUTO',
  display_unit           TEXT NOT NULL
                           DEFAULT 'KG',
  meet_date              TEXT,
  training_days_per_week INTEGER NOT NULL,
  vegetarian             INTEGER NOT NULL
                           DEFAULT 0,
  allows_dairy           INTEGER NOT NULL
                           DEFAULT 1,
  allows_eggs            INTEGER NOT NULL
                           DEFAULT 1
);

CREATE TABLE IF NOT EXISTS profile_equipment (
  profile_id  INTEGER NOT NULL,
  equipment   TEXT NOT NULL,
  PRIMARY KEY (profile_id, equipment),
  FOREIGN KEY (profile_id)
    REFERENCES lifter_profile (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS profile_limitation (
  id            INTEGER PRIMARY KEY,
  profile_id    INTEGER NOT NULL,
  description   TEXT NOT NULL,
  painful       INTEGER NOT NULL DEFAULT 0,
  note          TEXT,
  FOREIGN KEY (profile_id)
    REFERENCES lifter_profile (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS limitation_pattern (
  limitation_id INTEGER NOT NULL,
  pattern       TEXT NOT NULL,
  PRIMARY KEY (limitation_id, pattern),
  FOREIGN KEY (limitation_id)
    REFERENCES profile_limitation (id)
    ON DELETE CASCADE
);

-- ---------- exercise catalog ----------

CREATE TABLE IF NOT EXISTS exercise (
  id                INTEGER PRIMARY KEY,
  name              TEXT NOT NULL UNIQUE,
  movement_pattern  TEXT,
  competition_lift  TEXT
);

CREATE TABLE IF NOT EXISTS exercise_equipment (
  exercise_id  INTEGER NOT NULL,
  equipment    TEXT NOT NULL,
  PRIMARY KEY (exercise_id, equipment),
  FOREIGN KEY (exercise_id)
    REFERENCES exercise (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exercise_muscle (
  exercise_id  INTEGER NOT NULL,
  muscle_group TEXT NOT NULL,
  PRIMARY KEY (exercise_id, muscle_group),
  FOREIGN KEY (exercise_id)
    REFERENCES exercise (id)
    ON DELETE CASCADE
);

-- Alias learned when a CSV name is mapped.
CREATE TABLE IF NOT EXISTS exercise_alias (
  alias        TEXT PRIMARY KEY,
  exercise_id  INTEGER NOT NULL,
  FOREIGN KEY (exercise_id)
    REFERENCES exercise (id)
    ON DELETE CASCADE
);

-- ---------- logged training ----------

CREATE TABLE IF NOT EXISTS workout_session (
  id      INTEGER PRIMARY KEY,
  date    TEXT NOT NULL,
  source  TEXT NOT NULL,
  UNIQUE (date, source)
);

CREATE TABLE IF NOT EXISTS set_entry (
  id          INTEGER PRIMARY KEY,
  session_id  INTEGER NOT NULL,
  exercise_id INTEGER NOT NULL,
  weight_kg   REAL NOT NULL,
  reps        INTEGER NOT NULL,
  rpe         REAL,
  set_order   INTEGER NOT NULL,
  FOREIGN KEY (session_id)
    REFERENCES workout_session (id)
    ON DELETE CASCADE,
  FOREIGN KEY (exercise_id)
    REFERENCES exercise (id)
);

CREATE INDEX IF NOT EXISTS
  idx_set_entry_exercise
  ON set_entry (exercise_id);

-- ---------- recovery ----------

CREATE TABLE IF NOT EXISTS recovery_day (
  date            TEXT PRIMARY KEY,
  source          TEXT NOT NULL,
  recovery_score  INTEGER,
  hrv_ms          REAL,
  sleep_hours     REAL,
  soreness        INTEGER,
  energy          INTEGER
);

-- ---------- planned training ----------

CREATE TABLE IF NOT EXISTS training_block (
  id          INTEGER PRIMARY KEY,
  start_date  TEXT NOT NULL,
  progression TEXT NOT NULL,
  split_name  TEXT,
  origin      TEXT NOT NULL
                DEFAULT 'GENERATED',
  active      INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS week (
  id        INTEGER PRIMARY KEY,
  block_id  INTEGER NOT NULL,
  number    INTEGER NOT NULL,
  UNIQUE (block_id, number),
  FOREIGN KEY (block_id)
    REFERENCES training_block (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS planned_session (
  id              INTEGER PRIMARY KEY,
  week_id         INTEGER NOT NULL,
  date            TEXT NOT NULL,
  focus           TEXT,
  adjustment_note TEXT,
  FOREIGN KEY (week_id)
    REFERENCES week (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS
  exercise_prescription (
  id                 INTEGER PRIMARY KEY,
  session_id         INTEGER NOT NULL,
  exercise_id        INTEGER NOT NULL,
  sets               INTEGER NOT NULL,
  reps               INTEGER NOT NULL,
  target_rpe         REAL,
  load_kg            REAL NOT NULL,
  prescription_order INTEGER NOT NULL,
  FOREIGN KEY (session_id)
    REFERENCES planned_session (id)
    ON DELETE CASCADE,
  FOREIGN KEY (exercise_id)
    REFERENCES exercise (id)
);

-- ---------- nutrition ----------

CREATE TABLE IF NOT EXISTS macro_target (
  profile_id  INTEGER PRIMARY KEY,
  calories    INTEGER NOT NULL,
  protein_g   REAL NOT NULL,
  carbs_g     REAL NOT NULL,
  fat_g       REAL NOT NULL,
  FOREIGN KEY (profile_id)
    REFERENCES lifter_profile (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS food_item (
  id             INTEGER PRIMARY KEY,
  name           TEXT NOT NULL UNIQUE,
  calories_100g  REAL NOT NULL,
  protein_100g   REAL NOT NULL,
  carbs_100g     REAL NOT NULL,
  fat_100g       REAL NOT NULL,
  vegetarian     INTEGER NOT NULL DEFAULT 1,
  contains_dairy INTEGER NOT NULL DEFAULT 0,
  contains_egg   INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS daily_intake (
  date  TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS food_entry (
  id            INTEGER PRIMARY KEY,
  intake_date   TEXT NOT NULL,
  food_item_id  INTEGER NOT NULL,
  grams         REAL NOT NULL,
  FOREIGN KEY (intake_date)
    REFERENCES daily_intake (date)
    ON DELETE CASCADE,
  FOREIGN KEY (food_item_id)
    REFERENCES food_item (id)
);

-- ---------- agent memory ----------

CREATE TABLE IF NOT EXISTS memory_note (
  id          INTEGER PRIMARY KEY,
  created_at  TEXT NOT NULL,
  content     TEXT NOT NULL
);

-- ---------- saved CSV mappings ----------

CREATE TABLE IF NOT EXISTS import_profile (
  name        TEXT PRIMARY KEY,
  import_kind TEXT NOT NULL,
  mapping     TEXT NOT NULL
);
