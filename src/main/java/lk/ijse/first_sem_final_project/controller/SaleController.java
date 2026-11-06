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
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sales page.  tables: sales, sale_items, payments, stock, customers, users, products
 * Uses only the methods that ProductController already uses from BasePageController.
 */
public class SaleController extends BasePageController<SaleController.SaleRow> {

    public record SaleRow(int id, String customer, String cashier, String date, double total, double paid, String status) {}

    private record Line(int productId, String name, int qty, double price) {
        double subtotal() { return qty * price; }
    }

    private record Prod(int id, String name, double price, int stock) {
        @Override public String toString() { return name + "  –  LKR " + money(price) + "  (" + stock + " in stock)"; }
    }

    private record Detail(String product, int qty, double price, double subtotal) {}

    @Override protected String pageKey() { return "Sale"; }

    // ================================================================= columns
    @Override
    protected void setupColumns() {
        textCol("ID", 70, r -> code("SL", r.id()));
        textCol("Customer", 150, SaleRow::customer);
        textCol("Cashier", 120, SaleRow::cashier);
        textCol("Date", 120, SaleRow::date);
        textCol("Total (LKR)", 110, r -> money(r.total()));
        textCol("Paid (LKR)", 110, r -> money(r.paid()));
        badgeCol("Status", 110, r -> titleCase(r.status()));
        addActionColumn();

        // double-click a row = view its items
        table.setRowFactory(tv -> {
            TableRow<SaleRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showDetails(row.getItem());
            });
            return row;
        });
    }

    private void addActionColumn() {
        TableColumn<SaleRow, Void> c = new TableColumn<>("Actions");
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
                SaleRow r = getTableRow() == null ? null : getTableRow().getItem();
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
    protected List<SaleRow> fetchRows(Connection c) throws SQLException {
        String sql = "SELECT s.sale_id, COALESCE(cu.customer_name,'Walk-in') AS customer, COALESCE(u.full_name,'-') AS cashier, "
                + "s.sale_date, COALESCE(s.total_amount,0) AS total, COALESCE(p.paid,0) AS paid, COALESCE(s.order_status,'') AS status "
                + "FROM sales s "
                + "LEFT JOIN customers cu ON cu.customer_id = s.customer_id "
                + "LEFT JOIN users u ON u.user_id = s.user_id "
                + "LEFT JOIN (SELECT sale_id, SUM(amount_paid) AS paid FROM payments GROUP BY sale_id) p ON p.sale_id = s.sale_id "
                + "ORDER BY s.sale_date DESC, s.sale_id DESC";
        List<SaleRow> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SaleRow(rs.getInt("sale_id"), rs.getString("customer"), rs.getString("cashier"),
                        whenText(rs.getTimestamp("sale_date")), rs.getDouble("total"), rs.getDouble("paid"),
                        rs.getString("status")));
            }
        }
        return list;
    }

    @Override
    protected boolean matches(SaleRow r, String q) {
        return code("SL", r.id()).toLowerCase().contains(q) || r.customer().toLowerCase().contains(q)
                || r.cashier().toLowerCase().contains(q) || r.date().contains(q) || r.status().toLowerCase().contains(q);
    }

    // ================================================================= NEW SALE
    @Override
    protected void onAdd() {
        List<Item> customers = new ArrayList<>();
        customers.add(new Item(0, "Walk-in customer"));
        customers.addAll(loadItems("SELECT customer_id, customer_name FROM customers ORDER BY customer_name"));
        List<Prod> products = loadProds();

        ComboBox<Item> customer = combo(customers, "Customer");
        customer.getSelectionModel().selectFirst();

        ComboBox<Prod> product = combo(products, "Select product");
        TextField qty = input("Qty", "1");
        qty.setPrefWidth(80);
        qty.setMinWidth(80);
        Button addBtn = new Button("＋ Add");
        addBtn.getStyleClass().add("pg-btn-gold");
        HBox pick = new HBox(10, product, qty, addBtn);
        HBox.setHgrow(product, Priority.ALWAYS);

        ObservableList<Line> cart = FXCollections.observableArrayList();
        TableView<Line> cartTable = buildCartTable(cart, "Price");
        Label cartMsg = new Label();
        cartMsg.getStyleClass().add("dlg-error");
        Label total = new Label("Total:  LKR 0");
        total.getStyleClass().add("dlg-title");

        ComboBox<String> method = combo(List.of("Cash", "Card", "Online"), "Payment method");
        method.getSelectionModel().selectFirst();
        TextField paid = input("Amount paid", "0");

        Runnable refreshTotal = () -> {
            double sum = cartTotal(cart);
            total.setText("Total:  LKR " + money(sum));
            paid.setText(plain(sum));
        };
        cart.addListener((ListChangeListener<Line>) change -> refreshTotal.run());

        addBtn.setOnAction(e -> {
            Prod p = product.getValue();
            String err = addToCart(cart, p, qty.getText(), p == null ? 0 : p.price(), true);
            cartMsg.setText(err == null ? "" : err);
            if (err == null) qty.setText("1");
        });

        GridPane g = grid();
        g.setPrefWidth(640);
        row(g, 0, "Customer", customer);
        g.add(pick, 0, 1, 2, 1);
        g.add(cartTable, 0, 2, 2, 1);
        g.add(cartMsg, 0, 3, 2, 1);
        g.add(total, 0, 4, 2, 1);
        row(g, 5, "Payment", method);
        row(g, 6, "Amount Paid", paid);

        boolean ok = showForm("New Sale", g, () -> {
            if (cart.isEmpty()) return "Add at least one product to the sale.";
            double sum = cartTotal(cart);
            Double p = num(paid.getText());
            if (p == null) return "Amount paid must be a valid number.";
            if (p > sum + 0.001) return "Amount paid cannot be more than the total.";
            return null;
        }, "Complete Sale", true);
        if (!ok) return;

        double totalV = cartTotal(cart);
        double paidV = num(paid.getText());
        boolean fullyPaid = paidV + 0.001 >= totalV;

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int saleId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO sales (customer_id, user_id, sale_date, total_amount, order_status) VALUES (?,?,NOW(),?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    if (customer.getValue().id() == 0) ps.setNull(1, Types.INTEGER);
                    else ps.setInt(1, customer.getValue().id());
                    ps.setInt(2, findUserId(c));
                    ps.setDouble(3, totalV);
                    ps.setString(4, fullyPaid ? "COMPLETED" : "PENDING");
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); saleId = k.getInt(1); }
                }
                for (Line l : cart) {
                    exec(c, "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal) VALUES (?,?,?,?,?)",
                            saleId, l.productId(), l.qty(), l.price(), l.subtotal());
                    int updated = exec(c, "UPDATE stock SET quantity = quantity - ?, last_updated = NOW() WHERE product_id = ? AND quantity >= ?",
                            l.qty(), l.productId(), l.qty());
                    if (updated == 0) throw new SQLException("Not enough stock for " + l.name());
                }
                if (paidV > 0) {
                    exec(c, "INSERT INTO payments (sale_id, payment_status, payment_method, payment_date, amount_paid) VALUES (?,?,?,NOW(),?)",
                            saleId, fullyPaid ? "PAID" : "PARTIAL", method.getValue(), paidV);
                }
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ================================================================= VIEW ITEMS
    private void showDetails(SaleRow r) {
        List<Detail> items = new ArrayList<>();
        String sql = "SELECT p.product_name, si.quantity, si.unit_price, si.subtotal FROM sale_items si "
                + "JOIN products p ON p.product_id = si.product_id WHERE si.sale_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, r.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(new Detail(rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4)));
            }
        } catch (SQLException ex) { dbError(ex); return; }

        Label info = new Label(r.customer() + "   •   " + r.cashier() + "   •   " + r.date());
        info.getStyleClass().add("dlg-label");
        Label sums = new Label("Total: LKR " + money(r.total()) + "     Paid: LKR " + money(r.paid())
                + "     Balance: LKR " + money(Math.max(0, r.total() - r.paid())));
        sums.getStyleClass().add("dlg-text");

        TableView<Detail> t = new TableView<>(FXCollections.observableArrayList(items));
        t.getStyleClass().add("pg-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setFixedCellSize(36);
        t.setPrefHeight(Math.min(260, 44 + 36 * Math.max(items.size(), 1)));
        TableColumn<Detail, String> colProduct = mkCol("Product", 220, Detail::product);
        TableColumn<Detail, String> colQty = mkCol("Qty", 60, x -> String.valueOf(x.qty()));
        TableColumn<Detail, String> colPrice = mkCol("Unit Price", 100, x -> money(x.price()));
        TableColumn<Detail, String> colSub = mkCol("Subtotal", 100, x -> money(x.subtotal()));
        t.getColumns().add(colProduct);
        t.getColumns().add(colQty);
        t.getColumns().add(colPrice);
        t.getColumns().add(colSub);

        VBox box = new VBox(10, info, t, sums);
        box.setPrefWidth(560);
        showForm("Sale " + code("SL", r.id()), box, () -> null, "Close", false);
    }

    // ================================================================= DELETE
    private void delete(SaleRow r) {
        if (!confirm("Delete Sale", "Delete sale " + code("SL", r.id()) + "?\nThe sold items go back into stock.")) return;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                exec(c, "UPDATE stock s JOIN sale_items si ON si.product_id = s.product_id "
                        + "SET s.quantity = s.quantity + si.quantity, s.last_updated = NOW() WHERE si.sale_id = ?", r.id());
                exec(c, "DELETE FROM payments WHERE sale_id = ?", r.id());
                exec(c, "DELETE FROM sale_items WHERE sale_id = ?", r.id());
                exec(c, "DELETE FROM sales WHERE sale_id = ?", r.id());
                c.commit();
            } catch (SQLException ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { dbError(ex); return; }
        refresh();
    }

    // ================================================================= export
    @Override protected String exportName() { return "sales"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Customer", "Cashier", "Date", "Total", "Paid", "Status"}; }
    @Override protected String[] csvRow(SaleRow r) {
        return new String[]{code("SL", r.id()), r.customer(), r.cashier(), r.date(), plain(r.total()), plain(r.paid()), titleCase(r.status())};
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

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) return "-";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
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
    private String addToCart(ObservableList<Line> cart, Prod p, String qtyText, double price, boolean checkStock) {
        if (p == null) return "Select a product first.";
        Integer q = whole(qtyText);
        if (q == null || q == 0) return "Quantity must be a whole number greater than 0.";
        int existing = 0;
        int index = -1;
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).productId() == p.id()) { existing = cart.get(i).qty(); index = i; }
        }
        if (checkStock && existing + q > p.stock()) return "Only " + p.stock() + " in stock for " + p.name() + ".";
        Line line = new Line(p.id(), p.name(), existing + q, price);
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
        String sql = "SELECT p.product_id, p.product_name, COALESCE(p.selling_price,0) AS price, COALESCE(s.qty,0) AS qty "
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