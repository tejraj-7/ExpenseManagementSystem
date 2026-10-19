CREATE DATABASE IF NOT EXISTS expense_management_4;
USE expense_management_4;

DROP TABLE IF EXISTS expenses;
DROP TABLE IF EXISTS budgets;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE expenses (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(50) NOT NULL,
    payment_method VARCHAR(40) NOT NULL DEFAULT 'UPI',
    amount DECIMAL(10, 2) NOT NULL,
    expense_date DATE NOT NULL,
    notes VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE budgets (
    username VARCHAR(50) NOT NULL,
    budget_date DATE NOT NULL,
    budget_limit DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (username, budget_date),
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

-- Default Demo User
INSERT INTO users (username, password_hash)
VALUES ('AshBron_07', SHA2('password123', 256));

INSERT INTO budgets (username, budget_date, budget_limit)
VALUES ('AshBron_07', CURDATE(), 25000.00);

INSERT INTO expenses (username, title, category, payment_method, amount, expense_date, notes) VALUES
('AshBron_07', 'Cloud Server & Domain Renewal', 'Tech & Software', 'UPI', 1850.00, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'Annual project hosting');

SELECT * FROM users;
SELECT * FROM expenses;
SELECT * FROM budgets;