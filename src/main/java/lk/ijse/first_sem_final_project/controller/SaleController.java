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

    // columns
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

    // data
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

    // NEW SALE
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

        VBox box = new VBox(12, customer, pick, cartMsg, cartTable, total, method, paid);
        box.setPrefWidth(520);

        boolean ok = showForm("New Sale", box, () -> {
            if (cart.isEmpty()) return "Cart is empty.";
            Double pVal = num(paid.getText());
            if (pVal == null) return "Enter a valid paid amount.";
            if (pVal < cartTotal(cart)) return "Paid amount is less than total.";
            return null;
        }, "Checkout", true);

        if (!ok) return;

        saveSale(customer.getValue().id(), method.getValue(), num(paid.getText()), cart);
    }

    private List<Prod> loadProds() {
        List<Prod> list = new ArrayList<>();
        String sql = "SELECT p.product_id, p.product_name, p.selling_price, COALESCE(s.stock_qty, 0) AS stock "
                + "FROM products p "
                + "LEFT JOIN (SELECT product_id, SUM(quantity) AS stock_qty FROM stock GROUP BY product_id) s ON s.product_id = p.product_id "
                + "ORDER BY p.product_name";
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Prod(rs.getInt("product_id"), rs.getString("product_name"), rs.getDouble("selling_price"), rs.getInt("stock")));
            }
        } catch (SQLException e) { dbError(e); }
        return list;
    }

    private TableView<Line> buildCartTable(ObservableList<Line> cart, String priceHeader) {
        TableView<Line> tv = new TableView<>(cart);
        tv.setPrefHeight(180);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Line, String> cName = new TableColumn<>("Product");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));

        TableColumn<Line, String> cQty = new TableColumn<>("Qty");
        cQty.setPrefWidth(60);
        cQty.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().qty())));

        TableColumn<Line, String> cPrice = new TableColumn<>(priceHeader);
        cPrice.setPrefWidth(90);
        cPrice.setCellValueFactory(d -> new SimpleStringProperty(money(d.getValue().price())));

        TableColumn<Line, String> cSub = new TableColumn<>("Subtotal");
        cSub.setPrefWidth(90);
        cSub.setCellValueFactory(d -> new SimpleStringProperty(money(d.getValue().subtotal())));

        TableColumn<Line, Void> cAct = new TableColumn<>("");
        cAct.setPrefWidth(50);
        cAct.setCellFactory(tc -> new TableCell<>() {
            private final Button del = iconButton("✕", "pg-icon-delete");
            { del.setOnAction(e -> cart.remove(getTableRow().getItem())); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : del);
            }
        });

        tv.getColumns().addAll(cName, cQty, cPrice, cSub, cAct);
        return tv;
    }

    private String addToCart(ObservableList<Line> cart, Prod p, String qtyStr, double price, boolean checkStock) {
        if (p == null) return "Please select a product.";
        Integer q = whole(qtyStr);
        if (q == null || q <= 0) return "Quantity must be a positive whole number.";
        if (checkStock && q > p.stock()) return "Not enough stock available (" + p.stock() + " in stock).";

        for (int i = 0; i < cart.size(); i++) {
            Line l = cart.get(i);
            if (l.productId() == p.id()) {
                int newQty = l.qty() + q;
                if (checkStock && newQty > p.stock()) return "Total quantity exceeds stock (" + p.stock() + ").";
                cart.set(i, new Line(p.id(), p.name(), newQty, price));
                return null;
            }
        }
        cart.add(new Line(p.id(), p.name(), q, price));
        return null;
    }

    private double cartTotal(ObservableList<Line> cart) {
        double sum = 0;
        for (Line l : cart) sum += l.subtotal();
        return sum;
    }

    private void saveSale(int customerId, String method, double paidAmount, ObservableList<Line> cart) {
        double total = cartTotal(cart);
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int saleId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO sales (customer_id, user_id, sale_date, total_amount, order_status) VALUES (?,?,NOW(),?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    if (customerId == 0) ps.setNull(1, Types.INTEGER); else ps.setInt(1, customerId);
                    ps.setInt(2, Session.getInstance().getUserId());
                    ps.setDouble(3, total);
                    ps.setString(4, "Completed");
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); saleId = k.getInt(1); }
                }

                try (PreparedStatement psItem = c.prepareStatement(
                        "INSERT INTO sale_items (sale_id, product_id, qty, unit_price) VALUES (?,?,?,?)");
                     PreparedStatement psStock = c.prepareStatement(
                             "INSERT INTO stock (product_id, quantity, type, description, last_updated) VALUES (?,?, 'SALE', ?, NOW())")) {
                    for (Line l : cart) {
                        psItem.setInt(1, saleId);
                        psItem.setInt(2, l.productId());
                        psItem.setInt(3, l.qty());
                        psItem.setDouble(4, l.price());
                        psItem.addBatch();

                        psStock.setInt(1, l.productId());
                        psStock.setInt(2, -l.qty());
                        psStock.setString(3, "Sale #" + saleId);
                        psStock.addBatch();
                    }
                    psItem.executeBatch();
                    psStock.executeBatch();
                }

                try (PreparedStatement psPay = c.prepareStatement(
                        "INSERT INTO payments (sale_id, payment_date, amount_paid, payment_method) VALUES (?,NOW(),?,?)")) {
                    psPay.setInt(1, saleId);
                    psPay.setDouble(2, paidAmount);
                    psPay.setString(3, method);
                    psPay.executeUpdate();
                }

                c.commit();
                refresh();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            dbError(ex);
        }
    }

    private void showDetails(SaleRow r) {
        List<Detail> list = new ArrayList<>();
        String sql = "SELECT p.product_name, si.qty, si.unit_price, (si.qty * si.unit_price) AS sub "
                + "FROM sale_items si JOIN products p ON p.product_id = si.product_id WHERE si.sale_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, r.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Detail(rs.getString("product_name"), rs.getInt("qty"), rs.getDouble("unit_price"), rs.getDouble("sub")));
                }
            }
        } catch (SQLException e) { dbError(e); return; }

        TableView<Detail> tv = new TableView<>(FXCollections.observableArrayList(list));
        tv.setPrefHeight(200);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Detail, String> cName = new TableColumn<>("Product");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().product()));
        TableColumn<Detail, String> cQty = new TableColumn<>("Qty");
        cQty.setPrefWidth(60);
        cQty.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().qty())));
        TableColumn<Detail, String> cPrice = new TableColumn<>("Price");
        cPrice.setPrefWidth(90);
        cPrice.setCellValueFactory(d -> new SimpleStringProperty(money(d.getValue().price())));
        TableColumn<Detail, String> cSub = new TableColumn<>("Subtotal");
        cSub.setPrefWidth(90);
        cSub.setCellValueFactory(d -> new SimpleStringProperty(money(d.getValue().subtotal())));
        tv.getColumns().addAll(cName, cQty, cPrice, cSub);

        VBox box = new VBox(10, new Label("Sale #" + r.id() + " — " + r.customer()), tv);
        box.setPrefWidth(460);
        showForm("Sale Details", box, () -> null, "Close", false);
    }

    private void delete(SaleRow r) {
        if (!confirm("Delete Sale", "Delete Sale #" + r.id() + "? Stock will be restored.")) return;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // Restore stock items
                String fetchItems = "SELECT product_id, qty FROM sale_items WHERE sale_id = ?";
                try (PreparedStatement ps = c.prepareStatement(fetchItems)) {
                    ps.setInt(1, r.id());
                    try (ResultSet rs = ps.executeQuery();
                         PreparedStatement psStock = c.prepareStatement("INSERT INTO stock (product_id, quantity, type, description, last_updated) VALUES (?,?, 'RESTORE', ?, NOW())")) {
                        while (rs.next()) {
                            psStock.setInt(1, rs.getInt("product_id"));
                            psStock.setInt(2, rs.getInt("qty"));
                            psStock.setString(3, "Void Sale #" + r.id());
                            psStock.addBatch();
                        }
                        psStock.executeBatch();
                    }
                }

                exec(c, "DELETE FROM payments WHERE sale_id = ?", r.id());
                exec(c, "DELETE FROM sale_items WHERE sale_id = ?", r.id());
                exec(c, "DELETE FROM sales WHERE sale_id = ?", r.id());
                c.commit();
                refresh();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            dbError(ex);
        }
    }

    private Button iconButton(String text, String cls) {
        Button b = new Button(text);
        b.getStyleClass().addAll("pg-icon-btn", cls);
        return b;
    }

    private String titleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String whenText(Timestamp ts) {
        return ts == null ? "" : ts.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    @Override protected String exportName() { return "sales"; }
    @Override protected String[] csvHeader() { return new String[]{"ID", "Customer", "Cashier", "Date", "Total", "Paid", "Status"}; }
    @Override protected String[] csvRow(SaleRow r) {
        return new String[]{code("SL", r.id()), r.customer(), r.cashier(), r.date(), plain(r.total()), plain(r.paid()), r.status()};
    }
}