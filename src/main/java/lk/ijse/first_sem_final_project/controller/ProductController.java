package lk.ijse.first_sem_final_project.controller;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Products page.  tables: products, categories, stock */
public class ProductController extends BasePageController<ProductController.ProductRow> {

    public record ProductRow(int id, String name, int categoryId, String category, String description,
                             double cost, double price, int stock, int reorder) {}

    @Override protected String pageKey() { return "Product"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("P", r.id()));
        textCol("Name", 200, ProductRow::name);
        textCol("Category", 130, ProductRow::category);
        textCol("Price (LKR)", 110, r -> money(r.price()));
        textCol("Stock", 80, r -> String.valueOf(r.stock()));
        badgeCol("Status", 120, r -> stockStatus(r.stock(), r.reorder()));
        actionsCol(100, this::openEdit, this::delete);
    }

    @Override
    protected List<ProductRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT p.product_id, p.product_name, p.category_id, COALESCE(cat.category_name,'-') AS category, "
                + "COALESCE(p.description,'') AS descr, COALESCE(p.cost_price,0) AS cost, COALESCE(p.selling_price,0) AS price, "
                + "COALESCE(s.qty,0) AS qty, COALESCE(p.reorder_level,0) AS reorder "
                + "FROM products p "
                + "LEFT JOIN categories cat ON cat.category_id = p.category_id "
                + "LEFT JOIN (SELECT product_id, SUM(quantity) AS qty FROM stock GROUP BY product_id) s ON s.product_id = p.product_id "
                + "ORDER BY p.product_id";
        List<ProductRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ProductRow(rs.getInt("product_id"), rs.getString("product_name"), rs.getInt("category_id"),
                        rs.getString("category"), rs.getString("descr"), rs.getDouble("cost"), rs.getDouble("price"),
                        rs.getInt("qty"), rs.getInt("reorder")));
            }
        }
        return list;
    }

    @Override
    protected boolean matches(ProductRow r, String q) {
        return code("P", r.id()).toLowerCase().contains(q) || r.name().toLowerCase().contains(q)
                || r.category().toLowerCase().contains(q) || stockStatus(r.stock(), r.reorder()).toLowerCase().contains(q);
    }

    // ---------------------------------------------------------------- add / edit / delete
    @Override protected void onAdd() { openForm(null); }
    private void openEdit(ProductRow r) { openForm(r); }

    private void openForm(ProductRow edit) {
        List<Item> cats = loadItems("SELECT category_id, category_name FROM categories ORDER BY category_name");

        TextField name = input("Product name", edit == null ? "" : edit.name());
        ComboBox<Item> cat = combo(cats, "Select category");
        if (edit != null) cats.stream().filter(i -> i.id() == edit.categoryId()).findFirst()
                .ifPresent(i -> cat.getSelectionModel().select(i));
        TextField desc = input("Description (optional)", edit == null ? "" : edit.description());
        TextField cost = input("0.00", edit == null ? "" : plain(edit.cost()));
        TextField price = input("0.00", edit == null ? "" : plain(edit.price()));
        TextField reorder = input("10", edit == null ? "10" : String.valueOf(edit.reorder()));
        TextField stock = input("0", edit == null ? "0" : String.valueOf(edit.stock()));
        stock.setDisable(edit != null);              // stock is changed on the Stock page

        GridPane g = grid();
        row(g, 0, "Name", name);
        row(g, 1, "Category", cat);
        row(g, 2, "Description", desc);
        row(g, 3, "Cost Price", cost);
        row(g, 4, "Selling Price", price);
        row(g, 5, "Reorder Level", reorder);
        row(g, 6, edit == null ? "Opening Stock" : "Current Stock", stock);

        boolean ok = showForm(edit == null ? "Add Product" : "Edit Product", g, () -> {
            if (name.getText().isBlank()) return "Product name is required.";
            if (cat.getValue() == null) return "Please select a category.";
            if (num(cost.getText()) == null) return "Cost price must be a valid number.";
            if (num(price.getText()) == null) return "Selling price must be a valid number.";
            if (whole(reorder.getText()) == null) return "Reorder level must be a whole number.";
            if (edit == null && whole(stock.getText()) == null) return "Opening stock must be a whole number.";
            return null;
        }, "Save", true);
        if (!ok) return;

        double costV = num(cost.getText());
        double priceV = num(price.getText());
        int reorderV = whole(reorder.getText());

        try (Connection c = conn()) {
            if (edit == null) {
                int stockV = whole(stock.getText());
                c.setAutoCommit(false);
                try {
                    int newId;
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO products (product_name, category_id, description, reorder_level, cost_price, selling_price) VALUES (?,?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, name.getText().trim());
                        ps.setInt(2, cat.getValue().id());
                        ps.setString(3, desc.getText().trim());
                        ps.setInt(4, reorderV);
                        ps.setDouble(5, costV);
                        ps.setDouble(6, priceV);
                        ps.executeUpdate();
                        try (ResultSet k = ps.getGeneratedKeys()) { k.next(); newId = k.getInt(1); }
                    }
                    exec(c, "INSERT INTO stock (product_id, quantity, last_updated) VALUES (?,?,NOW())", newId, stockV);
                    c.commit();
                } catch (SQLException ex) { c.rollback(); throw ex; }
            } else {
                exec(c, "UPDATE products SET product_name=?, category_id=?, description=?, reorder_level=?, cost_price=?, selling_price=? WHERE product_id=?",
                        name.getText().trim(), cat.getValue().id(), desc.getText().trim(), reorderV, costV, priceV, edit.id());
            }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    private void delete(ProductRow r) {
        if (!confirm("Delete Product", "Delete \"" + r.name() + "\"? This cannot be undone.")) return;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                exec(c, "DELETE FROM stock WHERE product_id = ?", r.id());
                exec(c, "DELETE FROM products WHERE product_id = ?", r.id());
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ---------------------------------------------------------------- export
    @Override protected String exportName() { return "products"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Name", "Category", "Cost", "Price", "Stock", "Reorder Level", "Status"}; }
    @Override protected String[] csvRow(ProductRow r) {
        return new String[]{code("P", r.id()), r.name(), r.category(), plain(r.cost()), plain(r.price()),
                String.valueOf(r.stock()), String.valueOf(r.reorder()), stockStatus(r.stock(), r.reorder())};
    }
}