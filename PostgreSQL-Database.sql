-- PostgreSQL Database Setup for Train Ticket Reservation System
-- This script creates the database schema for hosting environments

-- Create database (run this as superuser)
-- CREATE DATABASE train_reservation;

-- Connect to the database
-- \c train_reservation;

-- Create tables
CREATE TABLE customer (
    mailid VARCHAR(40) PRIMARY KEY,
    pword VARCHAR(60) NOT NULL,  -- Increased size for BCrypt hashes
    fname VARCHAR(20) NOT NULL,
    lname VARCHAR(20),
    addr VARCHAR(100),
    phno BIGINT NOT NULL
);

CREATE TABLE admin (
    mailid VARCHAR(40) PRIMARY KEY,
    pword VARCHAR(60) NOT NULL,  -- Increased size for BCrypt hashes
    fname VARCHAR(20) NOT NULL,
    lname VARCHAR(20),
    addr VARCHAR(100),
    phno BIGINT NOT NULL
);

CREATE TABLE train (
    tr_no INTEGER PRIMARY KEY,
    tr_name VARCHAR(70) NOT NULL,
    from_stn VARCHAR(20) NOT NULL,
    to_stn VARCHAR(20) NOT NULL,
    seats INTEGER NOT NULL,
    fare DECIMAL(6,2) NOT NULL
);

CREATE TABLE history (
    transid VARCHAR(36) PRIMARY KEY,
    mailid VARCHAR(40) REFERENCES customer(mailid),
    tr_no INTEGER,
    date DATE,
    from_stn VARCHAR(20) NOT NULL,
    to_stn VARCHAR(20) NOT NULL,
    seats INTEGER NOT NULL,
    amount DECIMAL(8,2) NOT NULL
);

-- Seat Lock Table for temporary seat reservations
-- Holds seats for 5 minutes during booking process
-- Prevents double booking by reserving seats before payment
CREATE TABLE seat_lock (
    lock_id SERIAL PRIMARY KEY,
    tr_no INTEGER NOT NULL REFERENCES train(tr_no),
    mailid VARCHAR(40) NOT NULL REFERENCES customer(mailid),
    seats_locked INTEGER NOT NULL,
    lock_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '5 minutes'),
    status VARCHAR(20) DEFAULT 'ACTIVE' -- ACTIVE, EXPIRED, RELEASED
);

-- Index for quick lock lookups by train and user
CREATE INDEX idx_seat_lock_train_user ON seat_lock(tr_no, mailid);
-- Index for cleanup of expired locks
CREATE INDEX idx_seat_lock_expiry ON seat_lock(expires_at) WHERE status = 'ACTIVE';

-- Insert admin with hashed password (admin -> $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa)
INSERT INTO admin VALUES('admin@demo.com','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa','System','Admin','Demo Address 123 colony',9874561230);

-- Insert customer with hashed password (shashi -> $2a$10$8K1p/a0dL1LXMIgoEDFrwOe6g7fKjYqGxHxKqKqKqKqKqKqKqKqKq)
INSERT INTO customer VALUES('shashi@demo.com','$2a$10$8K1p/a0dL1LXMIgoEDFrwOe6g7fKjYqGxHxKqKqKqKqKqKqKqKqKq','Shashi','Raj','Kolkata, West Bengal',954745222);

-- Insert train data
INSERT INTO train VALUES(10001,'JODHPUR EXP','HOWRAH','JODHPUR', 152, 490.50);
INSERT INTO train VALUES(10002,'YAMUNA EXP','GAYA','DELHI', 52, 550.50);
INSERT INTO train VALUES(10003,'NILANCHAL EXP','GAYA','HOWRAH', 92, 451);
INSERT INTO train VALUES(10004,'JAN SATABDI EXP','RANCHI','PATNA', 182, 550);
INSERT INTO train VALUES(10005,'GANGE EXP','MUMBAI','KERALA', 12, 945);
INSERT INTO train VALUES(10006,'GARIB RATH EXP','PATNA','DELHI', 1, 1450.75);

-- Insert booking history
INSERT INTO history VALUES('BBC374-NSDF-4673','shashi@demo.com',10001,'2024-02-02', 'HOWRAH', 'JODHPUR', 2, 981);
INSERT INTO history VALUES('BBC375-NSDF-4675','shashi@demo.com',10004,'2024-01-12', 'RANCHI', 'PATNA', 1, 550);
INSERT INTO history VALUES('BBC373-NSDF-4674','shashi@demo.com',10006,'2024-07-22', 'PATNA', 'DELHI', 3, 4352.25);

-- Create indexes for better performance
CREATE INDEX idx_customer_mailid ON customer(mailid);
CREATE INDEX idx_admin_mailid ON admin(mailid);
CREATE INDEX idx_train_tr_no ON train(tr_no);
CREATE INDEX idx_history_mailid ON history(mailid);
CREATE INDEX idx_history_date ON history(date);

-- Composite indexes for optimized query performance
-- Composite index on route search (from_stn, to_stn)
-- Optimizes queries like: SELECT * FROM train WHERE from_stn = ? AND to_stn = ?
-- Used by TrainServiceImpl.getTrainsBetweenStations() for fast route lookup
CREATE INDEX idx_route_search ON train(from_stn, to_stn);

-- Index on seat availability for availability checks
-- Optimizes seat availability queries and sorting by seat count
-- Prevents full table scans when checking train capacity
CREATE INDEX idx_train_seats ON train(seats);

-- Grant permissions (adjust as needed)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO your_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO your_user;

COMMIT;
