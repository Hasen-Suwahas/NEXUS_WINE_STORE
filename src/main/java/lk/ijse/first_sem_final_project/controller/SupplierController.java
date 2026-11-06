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

/** Suppliers page.  table: suppliers(supplier_id, supplier_name, contact_person, phone, email, address) */
public class SupplierController extends BasePageController<SupplierController.SupplierRow> {

    public record SupplierRow(int id, String name, String contact, String phone, String email, String address) {}

    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{9,12}$");

    @Override protected String pageKey() { return "Supplier"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 80, r -> code("SU", r.id()));
        textCol("Supplier", 170, SupplierRow::name);
        textCol("Contact Person", 140, SupplierRow::contact);
        textCol("Phone", 110, SupplierRow::phone);
        textCol("Email", 170, SupplierRow::email);
        textCol("Address", 170, SupplierRow::address);
        actionsCol(100, this::openForm, this::delete);
    }

    @Override
    protected List<SupplierRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT supplier_id, supplier_name, COALESCE(contact_person,'') AS contact, COALESCE(phone,'') AS phone, "
                + "COALESCE(email,'') AS email, COALESCE(address,'') AS address FROM suppliers ORDER BY supplier_id";
        List<SupplierRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                list.add(new SupplierRow(rs.getInt("supplier_id"), rs.getString("supplier_name"), rs.getString("contact"),
                        rs.getString("phone"), rs.getString("email"), rs.getString("address")));
        }
        return list;
    }

    @Override
    protected boolean matches(SupplierRow r, String q) {
        return code("SU", r.id()).toLowerCase().contains(q) || text(r.name()).toLowerCase().contains(q)
                || r.contact().toLowerCase().contains(q) || r.phone().toLowerCase().contains(q)
                || r.email().toLowerCase().contains(q) || r.address().toLowerCase().contains(q);
    }

    @Override protected void onAdd() { openForm(null); }

    private void openForm(SupplierRow edit) {
        TextField name = input("Supplier name", edit == null ? "" : edit.name());
        TextField contact = input("Contact person", edit == null ? "" : edit.contact());
        TextField phone = input("Phone number", edit == null ? "" : edit.phone());
        TextField email = input("Email (optional)", edit == null ? "" : edit.email());
        TextField address = input("Address (optional)", edit == null ? "" : edit.address());

        GridPane g = grid();
        row(g, 0, "Supplier", name);
        row(g, 1, "Contact Person", contact);
        row(g, 2, "Phone", phone);
        row(g, 3, "Email", email);
        row(g, 4, "Address", address);

        boolean ok = showForm(edit == null ? "Add Supplier" : "Edit Supplier", g, () -> {
            if (name.getText().trim().length() < 2) return "Supplier name is required.";
            if (!phone.getText().isBlank() && !PHONE.matcher(phone.getText().trim()).matches()) return "Please enter a valid phone number (9-12 digits).";
            if (!email.getText().isBlank() && !email.getText().contains("@")) return "Please enter a valid email address.";
            return null;
        }, "Save", true);
        if (!ok) return;

        try (Connection c = conn()) {
            if (edit == null)
                exec(c, "INSERT INTO suppliers (supplier_name, contact_person, phone, email, address) VALUES (?,?,?,?,?)",
                        name.getText().trim(), contact.getText().trim(), phone.getText().trim(), email.getText().trim(), address.getText().trim());
            else
                exec(c, "UPDATE suppliers SET supplier_name=?, contact_person=?, phone=?, email=?, address=? WHERE supplier_id=?",
                        name.getText().trim(), contact.getText().trim(), phone.getText().trim(), email.getText().trim(), address.getText().trim(), edit.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    private void delete(SupplierRow r) {
        if (!confirm("Delete Supplier", "Delete supplier \"" + r.name() + "\"? This cannot be undone.")) return;
        try (Connection c = conn()) {
            exec(c, "DELETE FROM suppliers WHERE supplier_id = ?", r.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    @Override protected String exportName() { return "suppliers"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Supplier", "Contact Person", "Phone", "Email", "Address"}; }
    @Override protected String[] csvRow(SupplierRow r) {
        return new String[]{code("SU", r.id()), r.name(), r.contact(), r.phone(), r.email(), r.address()};
    }
}