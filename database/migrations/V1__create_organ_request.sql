CREATE TABLE IF NOT EXISTS organ_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    organ_type VARCHAR(40) NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    urgency VARCHAR(30) NOT NULL,
    notes VARCHAR(2000),
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_id BIGINT,
    reviewed_at DATETIME,
    rejection_reason VARCHAR(1000),
    CONSTRAINT fk_organ_request_patient FOREIGN KEY (patient_id) REFERENCES user_account(id),
    CONSTRAINT fk_organ_request_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES user_account(id),
    INDEX ix_organ_request_patient_submitted (patient_id, submitted_at),
    INDEX ix_organ_request_status_submitted (status, submitted_at)
);
