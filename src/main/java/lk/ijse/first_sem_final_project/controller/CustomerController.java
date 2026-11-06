package lk.ijse.first_sem_final_project.controller;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Customers page.  table: customers(customer_id, customer_name, phone, email, address) */
public class CustomerController extends BasePageController<CustomerController.CustomerRow> {

    public record CustomerRow(int id, String name, String phone, String email, String address) {}

    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{9,12}$");

    @Override protected String pageKey() { return "Customer"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 80, r -> code("CU", r.id()));
        textCol("Name", 170, CustomerRow::name);
        textCol("Phone", 120, CustomerRow::phone);
        textCol("Email", 190, CustomerRow::email);
        textCol("Address", 200, CustomerRow::address);
        actionsCol(100, this::openEdit, this::delete);
    }

    @Override
    protected List<CustomerRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT customer_id, customer_name, COALESCE(phone,'') AS phone, COALESCE(email,'') AS email, "
                + "COALESCE(address,'') AS address FROM customers ORDER BY customer_id";
        List<CustomerRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                list.add(new CustomerRow(rs.getInt("customer_id"), rs.getString("customer_name"),
                        rs.getString("phone"), rs.getString("email"), rs.getString("address")));
        }
        return list;
    }

    @Override
    protected boolean matches(CustomerRow r, String q) {
        return code("CU", r.id()).toLowerCase().contains(q) || text(r.name()).toLowerCase().contains(q)
                || r.phone().toLowerCase().contains(q) || r.email().toLowerCase().contains(q)
                || r.address().toLowerCase().contains(q);
    }

    @Override protected void onAdd() { openForm(null); }
    private void openEdit(CustomerRow r) { openForm(r); }

    private void openForm(CustomerRow edit) {
        TextField name = input("Customer name", edit == null ? "" : edit.name());
        TextField phone = input("Phone number", edit == null ? "" : edit.phone());
        TextField email = input("Email (optional)", edit == null ? "" : edit.email());
        TextField address = input("Address (optional)", edit == null ? "" : edit.address());

        GridPane g = grid();
        row(g, 0, "Name", name);
        row(g, 1, "Phone", phone);
        row(g, 2, "Email", email);
        row(g, 3, "Address", address);

        boolean ok = showForm(edit == null ? "Add Customer" : "Edit Customer", g, () -> {
            if (name.getText().trim().length() < 2) return "Customer name is required.";
            if (!phone.getText().isBlank() && !PHONE.matcher(phone.getText().trim()).matches()) return "Please enter a valid phone number (9-12 digits).";
            if (!email.getText().isBlank() && !email.getText().contains("@")) return "Please enter a valid email address.";
            return null;
        }, "Save", true);
        if (!ok) return;

        try (Connection c = conn()) {
            if (edit == null)
                exec(c, "INSERT INTO customers (customer_name, phone, email, address) VALUES (?,?,?,?)",
                        name.getText().trim(), phone.getText().trim(), email.getText().trim(), address.getText().trim());
            else
                exec(c, "UPDATE customers SET customer_name=?, phone=?, email=?, address=? WHERE customer_id=?",
                        name.getText().trim(), phone.getText().trim(), email.getText().trim(), address.getText().trim(), edit.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    private void delete(CustomerRow r) {
        if (!confirm("Delete Customer", "Delete customer \"" + r.name() + "\"? This cannot be undone.")) return;
        try (Connection c = conn()) {
            exec(c, "DELETE FROM customers WHERE customer_id = ?", r.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    @Override protected String exportName() { return "customers"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Name", "Phone", "Email", "Address"}; }
    @Override protected String[] csvRow(CustomerRow r) {
        return new String[]{code("CU", r.id()), r.name(), r.phone(), r.email(), r.address()};
    }
}