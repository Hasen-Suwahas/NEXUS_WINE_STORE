package lk.ijse.first_sem_final_project.controller;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Stock page.  tables: stock, products, categories */
public class StockController extends BasePageController<StockController.StockRow> {

    public record StockRow(int stockId, int productId, String product, String category,
                           int qty, int reorder, String updated) {}

    @Override
    protected String pageKey() { return "Stock"; }

    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("S", r.stockId()));
        textCol("Product", 200, StockRow::product);
        textCol("Category", 120, StockRow::category);
        textCol("Quantity", 90, r -> String.valueOf(r.qty()));
        textCol("Reorder Lvl", 100, r -> String.valueOf(r.reorder()));
        textCol("Last Updated", 140, StockRow::updated);
        badgeCol("Status", 120, r -> stockStatus(r.qty(), r.reorder()));
        actionsCol(80, this::openEdit, null);
    }

    @Override
    protected List<StockRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT s.stock_id, s.product_id, p.product_name, COALESCE(cat.category_name,'-') AS category, "
                + "s.quantity, COALESCE(p.reorder_level,0) AS reorder, s.last_updated "
                + "FROM stock s JOIN products p ON p.product_id = s.product_id "
                + "LEFT JOIN categories cat ON cat.category_id = p.category_id ORDER BY s.stock_id";
        List<StockRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("last_updated");
                String when = ts == null ? "-" : ts.toLocalDateTime().toString().replace('T', ' ').substring(0, 16);
                list.add(new StockRow(rs.getInt("stock_id"), rs.getInt("product_id"), rs.getString("product_name"),
                        rs.getString("category"), rs.getInt("quantity"), rs.getInt("reorder"), when));
            }
        }
        return list;
    }

    @Override
    protected boolean matches(StockRow r, String q) {
        return code("S", r.stockId()).toLowerCase().contains(q) || r.product().toLowerCase().contains(q)
                || r.category().toLowerCase().contains(q) || stockStatus(r.qty(), r.reorder()).toLowerCase().contains(q);
    }

    // ---------------------------------------------------------------- add stock (+ quantity)
    @Override
    protected void onAdd() {
        List<Item> products = loadItems("SELECT product_id, product_name FROM products ORDER BY product_name");
        ComboBox<Item> product = combo(products, "Select product");
        TextField qty = input("Quantity to add", "");

        GridPane g = grid();
        row(g, 0, "Product", product);
        row(g, 1, "Add Quantity", qty);

        boolean ok = showForm("Add Stock", g, () -> {
            if (product.getValue() == null) return "Please select a product.";
            Integer n = whole(qty.getText());
            if (n == null || n == 0) return "Quantity must be a whole number greater than 0.";
            return null;
        }, "Save", true);
        if (!ok) return;

        try (Connection c = conn()) {
            int n = whole(qty.getText());
            int updated = exec(c, "UPDATE stock SET quantity = quantity + ?, last_updated = NOW() WHERE product_id = ?",
                    n, product.getValue().id());
            if (updated == 0)
                exec(c, "INSERT INTO stock (product_id, quantity, last_updated) VALUES (?,?,NOW())", product.getValue().id(), n);
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ---------------------------------------------------------------- set exact quantity
    private void openEdit(StockRow r) {
        Label name = new Label(r.product());
        name.getStyleClass().add("dlg-text");
        TextField qty = input("Quantity", String.valueOf(r.qty()));

        GridPane g = grid();
        row(g, 0, "Product", name);
        row(g, 1, "Quantity", qty);

        boolean ok = showForm("Update Stock", g, () ->
                whole(qty.getText()) == null ? "Quantity must be a whole number." : null, "Save", true);
        if (!ok) return;

        try (Connection c = conn()) {
            exec(c, "UPDATE stock SET quantity = ?, last_updated = NOW() WHERE stock_id = ?", whole(qty.getText()), r.stockId());
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ---------------------------------------------------------------- export
    @Override protected String exportName() { return "stock"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Product", "Category", "Quantity", "Reorder Level", "Last Updated", "Status"}; }
    @Override protected String[] csvRow(StockRow r) {
        return new String[]{code("S", r.stockId()), r.product(), r.category(), String.valueOf(r.qty()),
                String.valueOf(r.reorder()), r.updated(), stockStatus(r.qty(), r.reorder())};
    }
}