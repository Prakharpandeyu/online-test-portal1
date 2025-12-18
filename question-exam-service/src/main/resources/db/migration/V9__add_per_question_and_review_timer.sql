ALTER TABLE exams
    ADD COLUMN per_question_seconds INT NOT NULL DEFAULT 30,
    ADD COLUMN review_minutes INT NOT NULL DEFAULT 5;
