# SpendWise — Personal Expense Management System

SpendWise is a secure, multi-user web-based expense tracking and monthly budgeting application built with modern Java Servlets, Apache Tomcat, and MySQL. It features a polished dark glassmorphism UI, robust user data isolation, and exact-date budget tracking.

## 🚀 Tech Stack
* **Backend:** Java 17 (Eclipse Adoptium), Jakarta Servlets, Apache Tomcat 10.1
* **Database:** MySQL 8.x (JDBC)
* **Frontend:** HTML5, CSS3 (Modern Dark Theme), Vanilla JavaScript, Chart.js

## 📁 Database Schema Setup
1. Open MySQL Workbench and run the script located in `database/expense_management_4.sql`.
2. This creates the `expense_management_4` database along with `users`, `expenses`, and `budgets` tables configured with foreign key constraints and cascade deletes.

## ⚙️ How to Run & Deploy
1. **Automated PowerShell Script:**
   You can quickly compile and deploy your project to Tomcat using the provided PowerShell script in the root directory:
   ```powershell
   Set-ExecutionPolicy Bypass -Scope Process
   .\run.ps1
2. Open your browser and navigate to:
  http://localhost:8080/ExpenseLedger_Simple/