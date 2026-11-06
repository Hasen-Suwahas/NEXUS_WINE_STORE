package lk.ijse.first_sem_final_project.controller;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Categories page.  table: categories(category_id, category_name, description) */
public class CategoryController extends BasePageController<CategoryController.CategoryRow> {

    public record CategoryRow(int id, String name, String description, int products) {}

    @Override protected String pageKey() { return "Category"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("C", r.id()));
        textCol("Category Name", 200, CategoryRow::name);
        textCol("Description", 280, CategoryRow::description);
        textCol("Products", 90, r -> String.valueOf(r.products()));
        actionsCol(100, this::openEdit, this::delete);
    }

    @Override
    protected List<CategoryRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT cat.category_id, cat.category_name, COALESCE(cat.description,'') AS descr, COUNT(p.product_id) AS products "
                + "FROM categories cat LEFT JOIN products p ON p.category_id = cat.category_id "
                + "GROUP BY cat.category_id, cat.category_name, cat.description ORDER BY cat.category_id";
        List<CategoryRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next())
                list.add(new CategoryRow(rs.getInt("category_id"), rs.getString("category_name"),
                        rs.getString("descr"), rs.getInt("products")));
        }
        return list;
    }

    @Override
    protected boolean matches(CategoryRow r, String q) {
        return code("C", r.id()).toLowerCase().contains(q) || text(r.name()).toLowerCase().contains(q)
                || r.description().toLowerCase().contains(q);
    }

    @Override protected void onAdd() { openForm(null); }
    private void openEdit(CategoryRow r) { openForm(r); }

    private void openForm(CategoryRow edit) {
        TextField name = input("Category name", edit == null ? "" : edit.name());
        TextField desc = input("Description (optional)", edit == null ? "" : edit.description());

        GridPane g = grid();
        row(g, 0, "Name", name);
        row(g, 1, "Description", desc);

        boolean ok = showForm(edit == null ? "Add Category" : "Edit Category", g,
                () -> name.getText().isBlank() ? "Category name is required." : null, "Save", true);
        if (!ok) return;

        try (Connection c = conn()) {
            if (edit == null)
                exec(c, "INSERT INTO categories (category_name, description) VALUES (?,?)", name.getText().trim(), desc.getText().trim());
            else
                exec(c, "UPDATE categories SET category_name=?, description=? WHERE category_id=?", name.getText().trim(), desc.getText().trim(), edit.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    private void delete(CategoryRow r) {
        if (r.products() > 0) {
            message("Cannot Delete", "\"" + r.name() + "\" still has " + r.products() + " product(s).\nMove or delete those products first.");
            return;
        }
        if (!confirm("Delete Category", "Delete \"" + r.name() + "\"?")) return;
        try (Connection c = conn()) {
            exec(c, "DELETE FROM categories WHERE category_id = ?", r.id());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    @Override protected String exportName() { return "categories"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Name", "Description", "Products"}; }
    @Override protected String[] csvRow(CategoryRow r) {
        return new String[]{code("C", r.id()), r.name(), r.description(), String.valueOf(r.products())};
    }
}