package lk.ijse.first_sem_final_project.controller;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 - Payments page.
 - tables: payments(payment_id, sale_id, payment_status, payment_method, payment_date, amount_paid), sales, customers
 */
public class PaymentController extends BasePageController<PaymentController.PaymentRow> {

    public record PaymentRow(int id, int saleId, String customer, String method, String date, double amount, String status) {}

    /** a sale that still has a balance to pay */
    private record SaleOpt(int id, String label, double balance) {
        @Override public String toString() { return label; }
    }

    private static final List<String> METHODS = List.of("Cash", "Card", "Online", "Bank Transfer");
    private static final List<String> STATUSES = List.of("Paid", "Partial", "Pending", "Refunded");

    @Override protected String pageKey() { return "Payment"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("PY", r.id()));
        textCol("Sale", 70, r -> code("SL", r.saleId()));
        textCol("Customer", 150, PaymentRow::customer);
        textCol("Method", 110, PaymentRow::method);
        textCol("Date", 120, PaymentRow::date);
        textCol("Amount (LKR)", 120, r -> money(r.amount()));
        badgeCol("Status", 100, r -> r.status());
        actionsCol(100, this::openEdit, this::delete);
    }

    @Override
    protected List<PaymentRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT py.payment_id, py.sale_id, COALESCE(cu.customer_name,'Walk-in') AS customer, "
                + "COALESCE(py.payment_method,'-') AS method, py.payment_date, COALESCE(py.amount_paid,0) AS amount, "
                + "COALESCE(py.payment_status,'') AS status "
                + "FROM payments py "
                + "LEFT JOIN sales s ON s.sale_id = py.sale_id "
                + "LEFT JOIN customers cu ON cu.customer_id = s.customer_id "
                + "ORDER BY py.payment_date DESC, py.payment_id DESC";
        List<PaymentRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String formattedDate = rs.getTimestamp("payment_date") != null ? rs.getTimestamp("payment_date").toString() : "";
                list.add(new PaymentRow(
                        rs.getInt("payment_id"),
                        rs.getInt("sale_id"),
                        rs.getString("customer"),
                        rs.getString("method"),
                        formattedDate,
                        rs.getDouble("amount"),
                        rs.getString("status")
                ));
            }
        }
        return list;
    }

    @Override
    protected boolean matches(PaymentRow r, String q) {
        return code("PY", r.id()).toLowerCase().contains(q) || code("SL", r.saleId()).toLowerCase().contains(q)
                || r.customer().toLowerCase().contains(q) || r.method().toLowerCase().contains(q)
                || r.status().toLowerCase().contains(q) || r.date().contains(q);
    }

    // ===================================================================== ADD PAYMENT
    @Override
    protected void onAdd() {
        List<SaleOpt> open = new ArrayList<>();
        String sql = "SELECT s.sale_id, COALESCE(cu.customer_name,'Walk-in') AS customer, s.total_amount, COALESCE(p.paid,0) AS paid "
                + "FROM sales s LEFT JOIN customers cu ON cu.customer_id = s.customer_id "
                + "LEFT JOIN (SELECT sale_id, SUM(amount_paid) AS paid FROM payments GROUP BY sale_id) p ON p.sale_id = s.sale_id "
                + "WHERE s.total_amount - COALESCE(p.paid,0) > 0.001 AND UPPER(COALESCE(s.order_status,'')) <> 'CANCELLED' "
                + "ORDER BY s.sale_id DESC";
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                double balance = rs.getDouble("total_amount") - rs.getDouble("paid");
                open.add(new SaleOpt(rs.getInt("sale_id"),
                        code("SL", rs.getInt("sale_id")) + "  –  " + rs.getString("customer") + "  –  balance LKR " + money(balance), balance));
            }
        } catch (SQLException ex) { dbError(ex); return; }

        if (open.isEmpty()) { message("No Pending Balance", "Every sale is fully paid. There is nothing to collect."); return; }

        ComboBox<SaleOpt> sale = combo(open, "Select sale");
        ComboBox<String> method = combo(METHODS, "Payment method");
        method.getSelectionModel().selectFirst();
        TextField amount = input("Amount", "");
        sale.valueProperty().addListener((o, a, s) -> { if (s != null) amount.setText(plain(Math.round(s.balance() * 100) / 100.0)); });

        GridPane g = grid();
        g.setPrefWidth(560);
        row(g, 0, "Sale", sale);
        row(g, 1, "Method", method);
        row(g, 2, "Amount", amount);

        boolean ok = showForm("Add Payment", g, () -> {
            if (sale.getValue() == null) return "Please select a sale.";
            Double a = num(amount.getText());
            if (a == null || a <= 0) return "Amount must be a number greater than 0.";
            if (a > sale.getValue().balance() + 0.001) return "Amount cannot be more than the balance (LKR " + money(sale.getValue().balance()) + ").";
            return null;
        }, "Save Payment", true);
        if (!ok) return;

        double paid = num(amount.getText());
        boolean fullyPaid = paid + 0.001 >= sale.getValue().balance();
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                exec(c, "INSERT INTO payments (sale_id, payment_status, payment_method, payment_date, amount_paid) VALUES (?,?,?,NOW(),?)",
                        sale.getValue().id(), fullyPaid ? "PAID" : "PARTIAL", method.getValue(), paid);
                if (fullyPaid)
                    exec(c, "UPDATE sales SET order_status = 'COMPLETED' WHERE sale_id = ?", sale.getValue().id());
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ===================================================================== EDIT (method + status only)
    private void openEdit(PaymentRow r) {
        Label info = new Label(code("SL", r.saleId()) + "  –  LKR " + money(r.amount()));
        info.getStyleClass().add("dlg-text");
        ComboBox<String> method = combo(METHODS, "Method");
        method.getSelectionModel().select(METHODS.contains(r.method()) ? r.method() : "Cash");
        ComboBox<String> status = combo(STATUSES, "Status");
        status.getSelectionModel().select(r.status());

        GridPane g = grid();
        row(g, 0, "Payment", info);
        row(g, 1, "Method", method);
        row(g, 2, "Status", status);

        boolean ok = showForm("Edit Payment " + code("PY", r.id()), g, () -> null, "Save", true);
        if (!ok) return;

        try (Connection c = conn()) {
            exec(c, "UPDATE payments SET payment_method = ?, payment_status = ? WHERE payment_id = ?",
                    method.getValue(), status.getValue().toUpperCase(), r.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ===================================================================== DELETE
    private void delete(PaymentRow r) {
        if (!confirm("Delete Payment", "Delete payment " + code("PY", r.id()) + " (LKR " + money(r.amount()) + ")?")) return;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                exec(c, "DELETE FROM payments WHERE payment_id = ?", r.id());
                // the sale has a balance again -> not completed any more
                exec(c, "UPDATE sales s SET s.order_status = 'PENDING' WHERE s.sale_id = ? AND UPPER(COALESCE(s.order_status,'')) = 'COMPLETED' "
                        + "AND s.total_amount - COALESCE((SELECT SUM(p.amount_paid) FROM payments p WHERE p.sale_id = s.sale_id),0) > 0.001", r.saleId());
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    @Override protected String exportName() { return "payments"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Sale", "Customer", "Method", "Date", "Amount", "Status"}; }
    @Override protected String[] csvRow(PaymentRow r) {
        return new String[]{code("PY", r.id()), code("SL", r.saleId()), r.customer(), r.method(), r.date(), plain(r.amount()), r.status()};
    }
}