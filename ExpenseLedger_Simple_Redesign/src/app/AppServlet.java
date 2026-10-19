package app;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@WebServlet("/api")
public class AppServlet extends HttpServlet {

    private String getSessionUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return (session != null) ? (String) session.getAttribute("user") : null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        String user = getSessionUser(req);

        if ("session".equals(action)) {
            resp.setContentType("application/json;charset=UTF-8");
            PrintWriter out = resp.getWriter();
            if (user != null) {
                out.print("{\"authenticated\":true,\"username\":\"" + Conditions.escapeJson(user) + "\"}");
            } else {
                out.print("{\"authenticated\":false}");
            }
            return;
        }

        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().print("{\"error\":\"Unauthorized. Please log in.\"}");
            return;
        }

        if ("exportCSV".equals(action)) {
            resp.setContentType("text/csv;charset=UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"SpendWise_Report.csv\"");
            PrintWriter out = resp.getWriter();
            out.println("ID,Title,Category,Payment Method,Amount,Date,Notes");

            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id, title, category, payment_method, amount, expense_date, notes FROM expenses WHERE username = ? ORDER BY expense_date DESC")) {
                ps.setString(1, user);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    out.println(rs.getInt("id") + "," +
                                "\"" + rs.getString("title").replace("\"", "\"\"") + "\"," +
                                "\"" + rs.getString("category") + "\"," +
                                "\"" + rs.getString("payment_method") + "\"," +
                                rs.getDouble("amount") + "," +
                                rs.getString("expense_date") + "," +
                                "\"" + (rs.getString("notes") != null ? rs.getString("notes").replace("\"", "\"\"") : "") + "\"");
                }
            } catch (SQLException e) {
                out.println("Error generating CSV: " + e.getMessage());
            }
            return;
        }

        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String month = req.getParameter("month");
        if (Conditions.isNullOrEmpty(month)) {
            month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }

        try (Connection conn = Database.getConnection()) {
            String search = req.getParameter("search");
            String category = req.getParameter("category");
            String from = req.getParameter("from");
            String to = req.getParameter("to");
            String sort = req.getParameter("sort");

            StringBuilder sql = new StringBuilder("SELECT id, title, category, payment_method, amount, expense_date, notes FROM expenses WHERE username = ?");
            if (!Conditions.isNullOrEmpty(search)) sql.append(" AND (title LIKE ? OR category LIKE ? OR notes LIKE ?)");
            if (!Conditions.isNullOrEmpty(category) && !"All".equalsIgnoreCase(category)) sql.append(" AND category = ?");
            if (!Conditions.isNullOrEmpty(from)) sql.append(" AND expense_date >= ?");
            if (!Conditions.isNullOrEmpty(to)) sql.append(" AND expense_date <= ?");

            if ("amount_desc".equals(sort)) sql.append(" ORDER BY amount DESC, expense_date DESC");
            else if ("amount_asc".equals(sort)) sql.append(" ORDER BY amount ASC, expense_date DESC");
            else if ("date_asc".equals(sort)) sql.append(" ORDER BY expense_date ASC, id ASC");
            else sql.append(" ORDER BY expense_date DESC, id DESC");

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            int idx = 1;
            ps.setString(idx++, user);
            if (!Conditions.isNullOrEmpty(search)) {
                String l = "%" + search.trim() + "%";
                ps.setString(idx++, l); ps.setString(idx++, l); ps.setString(idx++, l);
            }
            if (!Conditions.isNullOrEmpty(category) && !"All".equalsIgnoreCase(category)) ps.setString(idx++, category);
            if (!Conditions.isNullOrEmpty(from)) ps.setString(idx++, from);
            if (!Conditions.isNullOrEmpty(to)) ps.setString(idx++, to);

            ResultSet rs = ps.executeQuery();
            StringBuilder exJson = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) exJson.append(",");
                first = false;
                exJson.append("{")
                      .append("\"id\":").append(rs.getInt("id")).append(",")
                      .append("\"title\":\"").append(Conditions.escapeJson(rs.getString("title"))).append("\",")
                      .append("\"category\":\"").append(Conditions.escapeJson(rs.getString("category"))).append("\",")
                      .append("\"paymentMethod\":\"").append(Conditions.escapeJson(rs.getString("payment_method"))).append("\",")
                      .append("\"amount\":").append(rs.getDouble("amount")).append(",")
                      .append("\"date\":\"").append(rs.getString("expense_date")).append("\",")
                      .append("\"notes\":\"").append(Conditions.escapeJson(rs.getString("notes"))).append("\"")
                      .append("}");
            }
            exJson.append("]");

            double budget = 25000.0;
            try (PreparedStatement bps = conn.prepareStatement("SELECT budget_limit FROM budgets WHERE username = ? AND category = 'Overall' AND DATE_FORMAT(budget_date, '%Y-%m') = ?")) {
                bps.setString(1, user);
                bps.setString(2, month);
                ResultSet brs = bps.executeQuery();
                if (brs.next()) budget = brs.getDouble("budget_limit");
            }

            double allTime = 0, monthSpent = 0, highest = 0, avg = 0;
            int count = 0;
            try (PreparedStatement srs = conn.prepareStatement("SELECT COALESCE(SUM(amount),0), COALESCE(MAX(amount),0), COALESCE(AVG(amount),0), COUNT(*) FROM expenses WHERE username = ?")) {
                srs.setString(1, user);
                ResultSet sr = srs.executeQuery();
                if (sr.next()) { allTime = sr.getDouble(1); highest = sr.getDouble(2); avg = sr.getDouble(3); count = sr.getInt(4); }
            }
            try (PreparedStatement mps = conn.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE username = ? AND DATE_FORMAT(expense_date, '%Y-%m') = ?")) {
                mps.setString(1, user); mps.setString(2, month);
                ResultSet mr = mps.executeQuery();
                if (mr.next()) monthSpent = mr.getDouble(1);
            }

            StringBuilder catJson = new StringBuilder("[");
            try (PreparedStatement crs = conn.prepareStatement("SELECT category, SUM(amount) as total FROM expenses WHERE username = ? GROUP BY category ORDER BY total DESC")) {
                crs.setString(1, user);
                ResultSet cr = crs.executeQuery();
                boolean cf = true;
                while (cr.next()) {
                    if (!cf) catJson.append(","); cf = false;
                    catJson.append("{\"category\":\"").append(Conditions.escapeJson(cr.getString("category"))).append("\",\"total\":").append(cr.getDouble("total")).append("}");
                }
            }
            catJson.append("]");

            StringBuilder monJson = new StringBuilder("[");
            try (PreparedStatement mrs = conn.prepareStatement("SELECT DATE_FORMAT(expense_date, '%Y-%m') as ym, SUM(amount) as total FROM expenses WHERE username = ? GROUP BY ym ORDER BY ym ASC LIMIT 6")) {
                mrs.setString(1, user);
                ResultSet mr = mrs.executeQuery();
                boolean mf = true;
                while (mr.next()) {
                    if (!mf) monJson.append(","); mf = false;
                    monJson.append("{\"month\":\"").append(mr.getString("ym")).append("\",\"total\":").append(mr.getDouble("total")).append("}");
                }
            }
            monJson.append("]");

            out.print("{"
                    + "\"month\":\"" + Conditions.escapeJson(month) + "\","
                    + "\"budget\":" + budget + ","
                    + "\"monthSpent\":" + monthSpent + ","
                    + "\"totalAllTime\":" + allTime + ","
                    + "\"highestExpense\":" + highest + ","
                    + "\"averageExpense\":" + avg + ","
                    + "\"expenseCount\":" + count + ","
                    + "\"expenses\":" + exJson + ","
                    + "\"categoryTotals\":" + catJson + ","
                    + "\"monthlyTotals\":" + monJson
                    + "}");

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Database error: " + Conditions.escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String action = req.getParameter("action");
        String user = getSessionUser(req);

        try (Connection conn = Database.getConnection()) {
            if ("register".equals(action)) {
                String u = req.getParameter("username");
                String p = req.getParameter("password");
                if (Conditions.isNullOrEmpty(u) || Conditions.isNullOrEmpty(p)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"success\":false,\"message\":\"Username and password are required.\"}");
                    return;
                }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
                    ins.setString(1, u.trim());
                    ins.setString(2, Conditions.sha256(p.trim()));
                    ins.executeUpdate();
                }
                out.print("{\"success\":true,\"message\":\"Account created successfully!\",\"redirect\":\"index.html?registered=success\"}");
                return;
            }

            if ("login".equals(action)) {
                String u = req.getParameter("username");
                String p = req.getParameter("password");
                if (Conditions.isNullOrEmpty(u) || Conditions.isNullOrEmpty(p)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"success\":false,\"message\":\"Username and password are required.\"}");
                    return;
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM users WHERE username = ? AND password_hash = ?")) {
                    ps.setString(1, u.trim());
                    ps.setString(2, Conditions.sha256(p.trim()));
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        HttpSession s = req.getSession(true);
                        s.setAttribute("user", rs.getString("username"));
                        out.print("{\"success\":true,\"redirect\":\"app.html\"}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        out.print("{\"success\":false,\"message\":\"Invalid username or password.\"}");
                    }
                }
                return;
            }

            if ("logout".equals(action)) {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                out.print("{\"success\":true}");
                return;
            }

            if (user == null) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"success\":false,\"message\":\"Session expired. Please log in again.\"}");
                return;
            }

            if ("addExpense".equals(action)) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO expenses (username, title, category, payment_method, amount, expense_date, notes) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, user);
                    ps.setString(2, req.getParameter("title").trim());
                    ps.setString(3, req.getParameter("category"));
                    ps.setString(4, req.getParameter("paymentMethod"));
                    ps.setDouble(5, Double.parseDouble(req.getParameter("amount")));
                    ps.setString(6, req.getParameter("date"));
                    ps.setString(7, req.getParameter("notes"));
                    ps.executeUpdate();
                }
                out.print("{\"success\":true,\"message\":\"Expense added successfully.\"}");

            } else if ("editExpense".equals(action)) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE expenses SET title=?, category=?, payment_method=?, amount=?, expense_date=?, notes=? WHERE id=? AND username=?")) {
                    ps.setString(1, req.getParameter("title").trim());
                    ps.setString(2, req.getParameter("category"));
                    ps.setString(3, req.getParameter("paymentMethod"));
                    ps.setDouble(4, Double.parseDouble(req.getParameter("amount")));
                    ps.setString(5, req.getParameter("date"));
                    ps.setString(6, req.getParameter("notes"));
                    ps.setInt(7, Integer.parseInt(req.getParameter("id")));
                    ps.setString(8, user);
                    ps.executeUpdate();
                }
                out.print("{\"success\":true,\"message\":\"Expense updated successfully.\"}");

            } else if ("deleteExpense".equals(action)) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM expenses WHERE id=? AND username=?")) {
                    ps.setInt(1, Integer.parseInt(req.getParameter("id")));
                    ps.setString(2, user);
                    ps.executeUpdate();
                }
                out.print("{\"success\":true,\"message\":\"Expense deleted.\"}");

            } else if ("setBudget".equals(action)) {
                String month = req.getParameter("month");
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO budgets (username, category, budget_date, budget_limit) VALUES (?, 'Overall', ?, ?) ON DUPLICATE KEY UPDATE budget_limit = ?")) {
                    ps.setString(1, user);
                    ps.setString(2, LocalDate.now().toString());
                    ps.setDouble(3, Double.parseDouble(req.getParameter("budget")));
                    ps.setDouble(4, Double.parseDouble(req.getParameter("budget")));
                    ps.executeUpdate();
                }
                out.print("{\"success\":true,\"message\":\"Monthly budget updated.\"}");
            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"success\":false,\"message\":\"Error: " + Conditions.escapeJson(e.getMessage()) + "\"}");
        }
    }
}