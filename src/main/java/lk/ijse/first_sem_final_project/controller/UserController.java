package lk.ijse.first_sem_final_project.controller;

import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Users page.  table: users(user_id, full_name, username, password_hash, role, phone, status) */
public class UserController extends BasePageController<UserController.UserRow> {

    public record UserRow(int id, String fullName, String username, String role, String phone, String status) {}

    private static final List<String> ROLES = List.of("Admin", "Manager", "Cashier");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{4,20}$");

    private static String statusLabel(String s) {
        return s != null && (s.equalsIgnoreCase("ACTIVE") || s.equals("1")) ? "Active" : "Inactive";
    }

    @Override protected String pageKey() { return "User"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("U", r.id()));
        textCol("Full Name", 180, UserRow::fullName);
        textCol("Username", 130, UserRow::username);
        textCol("Role", 100, UserRow::role);
        textCol("Phone", 120, UserRow::phone);
        badgeCol("Status", 100, r -> statusLabel(r.status()));
        actionsCol(100, this::openEdit, this::delete);
    }

    @Override
    protected List<UserRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT user_id, full_name, username, COALESCE(role,'') AS role, COALESCE(phone,'') AS phone, "
                + "COALESCE(status,'ACTIVE') AS status FROM users ORDER BY user_id";
        List<UserRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                list.add(new UserRow(rs.getInt("user_id"), rs.getString("full_name"), rs.getString("username"),
                        rs.getString("role"), rs.getString("phone"), rs.getString("status")));
        }
        return list;
    }

    @Override
    protected boolean matches(UserRow r, String q) {
        return code("U", r.id()).toLowerCase().contains(q) || text(r.fullName()).toLowerCase().contains(q)
                || text(r.username()).toLowerCase().contains(q) || r.role().toLowerCase().contains(q)
                || r.phone().toLowerCase().contains(q);
    }

    // ---------------------------------------------------------------- add / edit / delete
    @Override protected void onAdd() { openForm(null); }
    private void openEdit(UserRow r) { openForm(r); }

    private void openForm(UserRow edit) {
        TextField fullName = input("Full name", edit == null ? "" : edit.fullName());
        TextField username = input("Username", edit == null ? "" : edit.username());
        TextField phone = input("Phone number", edit == null ? "" : edit.phone());
        ComboBox<String> role = combo(ROLES, "Select role");
        role.getSelectionModel().select(edit == null ? "Cashier" : edit.role());
        ComboBox<String> status = combo(List.of("Active", "Inactive"), "Status");
        status.getSelectionModel().select(edit == null ? "Active" : statusLabel(edit.status()));
        PasswordField password = new PasswordField();
        password.setPromptText(edit == null ? "Password (min 6)" : "Leave blank to keep current");
        password.getStyleClass().add("dlg-field");

        GridPane g = grid();
        row(g, 0, "Full Name", fullName);
        row(g, 1, "Username", username);
        row(g, 2, "Phone", phone);
        row(g, 3, "Role", role);
        row(g, 4, "Status", status);
        row(g, 5, "Password", password);

        boolean ok = showForm(edit == null ? "Add User" : "Edit User", g, () -> {
            if (fullName.getText().trim().length() < 3) return "Please enter the full name.";
            if (!USERNAME.matcher(username.getText().trim()).matches()) return "Username must be 4-20 characters (letters, numbers, _ or .).";
            if (usernameTaken(username.getText().trim(), edit == null ? -1 : edit.id())) return "That username is already taken.";
            if (role.getValue() == null) return "Please select a role.";
            if (edit == null && password.getText().length() < 6) return "Password must be at least 6 characters.";
            if (edit != null && !password.getText().isEmpty() && password.getText().length() < 6) return "Password must be at least 6 characters.";
            return null;
        }, "Save", true);
        if (!ok) return;

        String st = "Active".equals(status.getValue()) ? "ACTIVE" : "INACTIVE";
        try (Connection c = conn()) {
            if (edit == null) {
                exec(c, "INSERT INTO users (full_name, username, password_hash, role, phone, status) VALUES (?,?,?,?,?,?)",
                        fullName.getText().trim(), username.getText().trim(), sha256(password.getText()),
                        role.getValue(), phone.getText().trim(), st);
            } else if (password.getText().isEmpty()) {
                exec(c, "UPDATE users SET full_name=?, username=?, role=?, phone=?, status=? WHERE user_id=?",
                        fullName.getText().trim(), username.getText().trim(), role.getValue(), phone.getText().trim(), st, edit.id());
            } else {
                exec(c, "UPDATE users SET full_name=?, username=?, password_hash=?, role=?, phone=?, status=? WHERE user_id=?",
                        fullName.getText().trim(), username.getText().trim(), sha256(password.getText()),
                        role.getValue(), phone.getText().trim(), st, edit.id());
            }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    private boolean usernameTaken(String username, int exceptId) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ? AND user_id <> ?")) {
            ps.setString(1, username);
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private void delete(UserRow r) {
        if (!confirm("Delete User", "Delete user \"" + r.fullName() + "\"? This cannot be undone.")) return;
        try (Connection c = conn()) {
            exec(c, "DELETE FROM users WHERE user_id = ?", r.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ---------------------------------------------------------------- export
    @Override protected String exportName() { return "users"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Full Name", "Username", "Role", "Phone", "Status"}; }
    @Override protected String[] csvRow(UserRow r) {
        return new String[]{code("U", r.id()), r.fullName(), r.username(), r.role(), r.phone(), statusLabel(r.status())};
    }
}