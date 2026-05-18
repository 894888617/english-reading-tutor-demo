-- Production database schema reference for the first-phase AI reading tutor.
-- The current application persists books/evaluations as local JSON files; use this schema when switching to an RDBMS.

CREATE TABLE IF NOT EXISTS books (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  english_title VARCHAR(255),
  level VARCHAR(64),
  source_file_name VARCHAR(255),
  source_file_type VARCHAR(32),
  cover_url VARCHAR(512),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS book_pages (
  id VARCHAR(64) PRIMARY KEY,
  book_id VARCHAR(64) NOT NULL,
  page_no INT NOT NULL,
  image_url VARCHAR(512),
  raw_text TEXT,
  need_ocr BOOLEAN DEFAULT FALSE,
  parse_error TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS book_sentences (
  id VARCHAR(64) PRIMARY KEY,
  book_id VARCHAR(64) NOT NULL,
  page_id VARCHAR(64) NOT NULL,
  sentence_index INT NOT NULL,
  english TEXT NOT NULL,
  chinese TEXT,
  keywords_json TEXT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ocr_results (
  id VARCHAR(64) PRIMARY KEY,
  book_id VARCHAR(64) NOT NULL,
  page_id VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  text TEXT,
  confidence DECIMAL(5,2),
  blocks_json TEXT,
  raw_result_json TEXT,
  created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tts_audio_cache (
  id VARCHAR(64) PRIMARY KEY,
  book_id VARCHAR(64),
  page_id VARCHAR(64),
  sentence_id VARCHAR(64),
  text_hash VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  voice VARCHAR(128) NOT NULL,
  language VARCHAR(32) NOT NULL,
  speed DECIMAL(4,2) NOT NULL,
  pitch DECIMAL(4,2) NOT NULL,
  volume DECIMAL(4,2) NOT NULL,
  format VARCHAR(16) NOT NULL,
  audio_url VARCHAR(512) NOT NULL,
  duration_ms BIGINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS speech_evaluations (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64),
  book_id VARCHAR(64),
  page_id VARCHAR(64),
  sentence_id VARCHAR(64),
  reference_text TEXT NOT NULL,
  audio_url VARCHAR(512),
  total_score INT,
  accuracy_score INT,
  fluency_score INT,
  completeness_score INT,
  clarity_score INT,
  provider VARCHAR(64),
  raw_result_json TEXT,
  created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS speech_evaluation_words (
  id VARCHAR(64) PRIMARY KEY,
  evaluation_id VARCHAR(64) NOT NULL,
  word VARCHAR(128) NOT NULL,
  score INT,
  correct BOOLEAN,
  actual_word VARCHAR(128),
  start_time DECIMAL(10,3),
  end_time DECIMAL(10,3),
  phoneme_errors_json TEXT,
  created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reading_progress (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  book_id VARCHAR(64) NOT NULL,
  page_id VARCHAR(64),
  sentence_id VARCHAR(64),
  status VARCHAR(32),
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reading_tasks (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64),
  book_id VARCHAR(64) NOT NULL,
  assigned_by VARCHAR(64),
  due_at TIMESTAMP,
  status VARCHAR(32),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
