package app;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   SPENDWISE EXPENSE MANAGEMENT SYSTEM (BACKEND)  ");
        System.out.println("==================================================");
        try (Connection conn = Database.getConnection()) {
            if (conn != null) {
                System.out.println("[SUCCESS] Connected to MySQL database (expense_management_4)!");
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Database connection failed: " + e.getMessage());
        }
    }
}