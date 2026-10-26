CREATE DATABASE IF NOT EXISTS smart_donation_db;
USE smart_donation_db;

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    blood_group VARCHAR(30),
    phone VARCHAR(30),
    city VARCHAR(120),
    latitude DOUBLE NOT NULL DEFAULT 0,
    longitude DOUBLE NOT NULL DEFAULT 0,
    blood_donor BOOLEAN NOT NULL DEFAULT FALSE,
    organ_donor BOOLEAN NOT NULL DEFAULT FALSE,
    available_for_donation BOOLEAN NOT NULL DEFAULT TRUE,
    last_donation_date DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS blood_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_name VARCHAR(120) NOT NULL,
    contact_email VARCHAR(160) NOT NULL,
    contact_phone VARCHAR(30),
    hospital_name VARCHAR(160),
    city VARCHAR(120),
    latitude DOUBLE NOT NULL DEFAULT 0,
    longitude DOUBLE NOT NULL DEFAULT 0,
    required_blood_group VARCHAR(30) NOT NULL,
    units_required INT NOT NULL DEFAULT 1,
    urgency VARCHAR(40),
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS donation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    blood_request_id BIGINT,
    donated_on DATE,
    donation_type VARCHAR(60),
    location VARCHAR(160),
    notes TEXT,
    CONSTRAINT fk_history_donor FOREIGN KEY (donor_id) REFERENCES user_account(id),
    CONSTRAINT fk_history_request FOREIGN KEY (blood_request_id) REFERENCES blood_request(id)
);

CREATE TABLE IF NOT EXISTS organ_donation_preference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL UNIQUE,
    organs TEXT,
    consent_statement TEXT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_organ_donor FOREIGN KEY (donor_id) REFERENCES user_account(id)
);

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

CREATE TABLE IF NOT EXISTS donor_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    blood_request_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    channel VARCHAR(30),
    message TEXT,
    sent_at DATETIME,
    CONSTRAINT fk_notification_donor FOREIGN KEY (donor_id) REFERENCES user_account(id),
    CONSTRAINT fk_notification_request FOREIGN KEY (blood_request_id) REFERENCES blood_request(id)
);
