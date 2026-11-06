package lk.ijse.first_sem_final_project.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lk.ijse.first_sem_final_project.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Purchases page.  tables: purchases, purchase_items, stock, suppliers, users, products
 * Uses only the methods that ProductController already uses from BasePageController.
 */
public class PurchaseController extends BasePageController<PurchaseController.PurchaseRow> {

    public record PurchaseRow(int id, String supplier, String receivedBy, String date, int units, double total) {}

    private record Line(int productId, String name, int qty, double price) {
        double subtotal() { return qty * price; }
    }

    private record Prod(int id, String name, double cost, int stock) {
        @Override public String toString() { return name + "  –  cost LKR " + money(cost) + "  (" + stock + " in stock)"; }
    }

    private record Detail(String product, int qty, double cost, double subtotal) {}

    @Override protected String pageKey() { return "Purchase"; }

    // ================================================================= columns
    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("PU", r.id()));
        textCol("Supplier", 180, PurchaseRow::supplier);
        textCol("Received By", 130, PurchaseRow::receivedBy);
        textCol("Date", 120, PurchaseRow::date);
        textCol("Units", 80, r -> String.valueOf(r.units()));
        textCol("Total (LKR)", 120, r -> money(r.total()));
        addActionColumn();

        // double-click a row = view its items
        table.setRowFactory(tv -> {
            TableRow<PurchaseRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showDetails(row.getItem());
            });
            return row;
        });
    }

    private void addActionColumn() {
        TableColumn<PurchaseRow, Void> c = new TableColumn<>("Actions");
        c.setPrefWidth(90);
        c.setSortable(false);
        c.setCellFactory(tc -> new TableCell<>() {
            private final Button view = iconButton("👁", "pg-icon-edit");
            private final Button del = iconButton("🗑", "pg-icon-delete");
            private final HBox box = new HBox(10, view, del);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                view.setOnAction(e -> act(true));
                del.setOnAction(e -> act(false));
            }
            private void act(boolean show) {
                PurchaseRow r = getTableRow() == null ? null : getTableRow().getItem();
                if (r == null) return;
                if (show) showDetails(r); else delete(r);
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
        table.getColumns().add(c);
    }

    // ================================================================= data
    @Override
    protected List<PurchaseRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT pu.purchase_id, COALESCE(su.supplier_name,'-') AS supplier, COALESCE(u.full_name,'-') AS received_by, "
                + "pu.purchase_date, COALESCE(i.units,0) AS units, COALESCE(pu.total_amount,0) AS total "
                + "FROM purchases pu "
                + "LEFT JOIN suppliers su ON su.supplier_id = pu.supplier_id "
                + "LEFT JOIN users u ON u.user_id = pu.user_id "
                + "LEFT JOIN (SELECT purchase_id, SUM(quantity) AS units FROM purchase_items GROUP BY purchase_id) i ON i.purchase_id = pu.purchase_id "
                + "ORDER BY pu.purchase_date DESC, pu.purchase_id DESC";
        List<PurchaseRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new PurchaseRow(rs.getInt("purchase_id"), rs.getString("supplier"), rs.getString("received_by"),
                        whenText(rs.getTimestamp("purchase_date")), rs.getInt("units"), rs.getDouble("total")));
            }
        }
        return list;
    }

    @Override
    protected boolean matches(PurchaseRow r, String q) {
        return code("PU", r.id()).toLowerCase().contains(q) || r.supplier().toLowerCase().contains(q)
                || r.receivedBy().toLowerCase().contains(q) || r.date().contains(q);
    }

    // ================================================================= NEW PURCHASE
    @Override
    protected void onAdd() {
        List<Item> suppliers = loadItems("SELECT supplier_id, supplier_name FROM suppliers ORDER BY supplier_name");
        List<Prod> products = loadProds();

        ComboBox<Item> supplier = combo(suppliers, "Select supplier");
        ComboBox<Prod> product = combo(products, "Select product");
        TextField qty = input("Qty", "1");
        qty.setPrefWidth(70);
        qty.setMinWidth(70);
        TextField cost = input("Unit cost", "");
        cost.setPrefWidth(100);
        cost.setMinWidth(100);
        product.valueProperty().addListener((o, oldP, newP) -> {
            if (newP != null) cost.setText(plain(newP.cost()));
        });

        Button addBtn = new Button("＋ Add");
        addBtn.getStyleClass().add("pg-btn-gold");
        HBox pick = new HBox(10, product, qty, cost, addBtn);
        HBox.setHgrow(product, Priority.ALWAYS);

        ObservableList<Line> cart = FXCollections.observableArrayList();
        TableView<Line> cartTable = buildCartTable(cart, "Unit Cost");
        Label cartMsg = new Label();
        cartMsg.getStyleClass().add("dlg-error");
        Label total = new Label("Total:  LKR 0");
        total.getStyleClass().add("dlg-title");
        cart.addListener((ListChangeListener<Line>) change -> total.setText("Total:  LKR " + money(cartTotal(cart))));

        addBtn.setOnAction(e -> {
            Double unitCost = num(cost.getText());
            if (unitCost == null) { cartMsg.setText("Unit cost must be a valid number."); return; }
            String err = addToCart(cart, product.getValue(), qty.getText(), unitCost);
            cartMsg.setText(err == null ? "" : err);
            if (err == null) qty.setText("1");
        });

        GridPane g = grid();
        g.setPrefWidth(660);
        row(g, 0, "Supplier", supplier);
        g.add(pick, 0, 1, 2, 1);
        g.add(cartTable, 0, 2, 2, 1);
        g.add(cartMsg, 0, 3, 2, 1);
        g.add(total, 0, 4, 2, 1);

        boolean ok = showForm("New Purchase", g, () -> {
            if (supplier.getValue() == null) return "Please select a supplier.";
            if (cart.isEmpty()) return "Add at least one product to the purchase.";
            return null;
        }, "Save Purchase", true);
        if (!ok) return;

        double totalV = cartTotal(cart);
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int purchaseId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO purchases (purchase_date, user_id, supplier_id, total_amount) VALUES (NOW(),?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, findUserId(c));
                    ps.setInt(2, supplier.getValue().id());
                    ps.setDouble(3, totalV);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); purchaseId = k.getInt(1); }
                }
                for (Line l : cart) {
                    exec(c, "INSERT INTO purchase_items (purchase_id, product_id, unit_cost, subtotal, quantity) VALUES (?,?,?,?,?)",
                            purchaseId, l.productId(), l.price(), l.subtotal(), l.qty());
                    int updated = exec(c, "UPDATE stock SET quantity = quantity + ?, last_updated = NOW() WHERE product_id = ?",
                            l.qty(), l.productId());
                    if (updated == 0) {
                        exec(c, "INSERT INTO stock (product_id, quantity, last_updated) VALUES (?,?,NOW())", l.productId(), l.qty());
                    }
                }
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ================================================================= VIEW ITEMS
    private void showDetails(PurchaseRow r) {
        List<Detail> items = new ArrayList<>();
        String sql = "SELECT p.product_name, pi.quantity, pi.unit_cost, pi.subtotal FROM purchase_items pi "
                + "JOIN products p ON p.product_id = pi.product_id WHERE pi.purchase_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, r.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(new Detail(rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4)));
            }
        } catch (SQLException ex) { dbError(ex); return; }

        Label info = new Label(r.supplier() + "   •   received by " + r.receivedBy() + "   •   " + r.date());
        info.getStyleClass().add("dlg-label");
        Label sum = new Label("Total: LKR " + money(r.total()));
        sum.getStyleClass().add("dlg-text");

        TableView<Detail> t = new TableView<>(FXCollections.observableArrayList(items));
        t.getStyleClass().add("pg-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setFixedCellSize(36);
        t.setPrefHeight(Math.min(260, 44 + 36 * Math.max(items.size(), 1)));
        TableColumn<Detail, String> colProduct = mkCol("Product", 220, Detail::product);
        TableColumn<Detail, String> colQty = mkCol("Qty", 60, x -> String.valueOf(x.qty()));
        TableColumn<Detail, String> colCost = mkCol("Unit Cost", 100, x -> money(x.cost()));
        TableColumn<Detail, String> colSub = mkCol("Subtotal", 100, x -> money(x.subtotal()));
        t.getColumns().add(colProduct);
        t.getColumns().add(colQty);
        t.getColumns().add(colCost);
        t.getColumns().add(colSub);

        VBox box = new VBox(10, info, t, sum);
        box.setPrefWidth(560);
        showForm("Purchase " + code("PU", r.id()), box, () -> null, "Close", false);
    }

    // ================================================================= DELETE
    private void delete(PurchaseRow r) {
        if (!confirm("Delete Purchase", "Delete purchase " + code("PU", r.id()) + "?\nThe purchased units are removed from stock.")) return;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                exec(c, "UPDATE stock s JOIN purchase_items pi ON pi.product_id = s.product_id "
                        + "SET s.quantity = GREATEST(s.quantity - pi.quantity, 0), s.last_updated = NOW() WHERE pi.purchase_id = ?", r.id());
                exec(c, "DELETE FROM purchase_items WHERE purchase_id = ?", r.id());
                exec(c, "DELETE FROM purchases WHERE purchase_id = ?", r.id());
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ================================================================= export
    @Override protected String exportName() { return "purchases"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Supplier", "Received By", "Date", "Units", "Total"}; }
    @Override protected String[] csvRow(PurchaseRow r) {
        return new String[]{code("PU", r.id()), r.supplier(), r.receivedBy(), r.date(), String.valueOf(r.units()), plain(r.total())};
    }

    // ================================================================= private helpers (self-contained)
    private static Button iconButton(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("pg-icon-btn", styleClass);
        return b;
    }

    private static <S> TableColumn<S, String> mkCol(String title, double width, Function<S, String> value) {
        TableColumn<S, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        return c;
    }

    private static String whenText(Timestamp ts) {
        if (ts == null) return "-";
        LocalDateTime t = ts.toLocalDateTime();
        return (t.getHour() == 0 && t.getMinute() == 0) ? t.toLocalDate().toString()
                : t.toString().replace('T', ' ').substring(0, 16);
    }

    private static double cartTotal(ObservableList<Line> cart) {
        double sum = 0;
        for (Line l : cart) sum += l.subtotal();
        return sum;
    }

    /** returns an error text, or null when the product was added / merged into the cart */
    private String addToCart(ObservableList<Line> cart, Prod p, String qtyText, double unitCost) {
        if (p == null) return "Select a product first.";
        Integer q = whole(qtyText);
        if (q == null || q == 0) return "Quantity must be a whole number greater than 0.";
        int existing = 0;
        int index = -1;
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).productId() == p.id()) { existing = cart.get(i).qty(); index = i; }
        }
        Line line = new Line(p.id(), p.name(), existing + q, unitCost);
        if (index >= 0) cart.set(index, line); else cart.add(line);
        return null;
    }

    private TableView<Line> buildCartTable(ObservableList<Line> cart, String priceTitle) {
        TableView<Line> t = new TableView<>(cart);
        t.getStyleClass().add("pg-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setFixedCellSize(36);
        t.setPrefHeight(190);
        t.setMinHeight(190);
        t.setPlaceholder(new Label("No items added yet"));

        TableColumn<Line, String> cName = mkCol("Product", 220, Line::name);
        TableColumn<Line, String> cQty = mkCol("Qty", 60, l -> String.valueOf(l.qty()));
        TableColumn<Line, String> cPrice = mkCol(priceTitle, 90, l -> money(l.price()));
        TableColumn<Line, String> cSub = mkCol("Subtotal", 100, l -> money(l.subtotal()));

        TableColumn<Line, Void> cDel = new TableColumn<>("");
        cDel.setPrefWidth(40);
        cDel.setSortable(false);
        cDel.setCellFactory(tc -> new TableCell<>() {
            private final Button b = iconButton("✕", "pg-icon-delete");
            {
                b.setOnAction(e -> {
                    Line line = getTableRow() == null ? null : getTableRow().getItem();
                    if (line != null) cart.remove(line);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : b);
            }
        });

        t.getColumns().add(cName);
        t.getColumns().add(cQty);
        t.getColumns().add(cPrice);
        t.getColumns().add(cSub);
        t.getColumns().add(cDel);
        return t;
    }

    private List<Prod> loadProds() {
        String sql = "SELECT p.product_id, p.product_name, COALESCE(p.cost_price,0) AS cost, COALESCE(s.qty,0) AS qty "
                + "FROM products p LEFT JOIN (SELECT product_id, SUM(quantity) AS qty FROM stock GROUP BY product_id) s "
                + "ON s.product_id = p.product_id ORDER BY p.product_name";
        List<Prod> list = new ArrayList<>();
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(new Prod(rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getInt(4)));
        } catch (SQLException ex) { dbError(ex); }
        return list;
    }

    /** the logged-in user's id (looked up by name), or the first user as a fallback */
    private int findUserId(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT user_id FROM users WHERE full_name = ? LIMIT 1")) {
            ps.setString(1, Session.getName());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MIN(user_id) FROM users")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}