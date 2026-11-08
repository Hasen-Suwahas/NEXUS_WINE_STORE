package lk.ijse.first_sem_final_project.controller;


import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportController {

    // EDIT THESE (same values you use in your LoginController)
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/nexus_vain_store?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "PUT_YOUR_MYSQL_PASSWORD_HERE";


    /** always a NEW connection, so it is safe to close it after each report */
    private static Connection openConnection() throws SQLException {
        if ("PUT_YOUR_MYSQL_PASSWORD_HERE".equals(DB_PASS))
            throw new SQLException("Put your MySQL password in ReportController.DB_PASS");
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }


    // ------------------------------------------------------------------ report definitions
    /**
     * @param dateCol   column used by the period filter (null = report has no period)
     * @param types     one letter per column: T = text, N = whole number, M = money
     * @param totalCol  index of the money column to add up (-1 = none)
     * The token DATE-comment inside the SQL is replaced by  "AND DATE(dateCol) >= ?"  (or removed for All Time).
     */
    private record Def(String title, String subtitle, String sql, String dateCol,
                       String[] headers, String types, int totalCol, String totalLabel) {}


    private record RowData(List<String> cells, double total) {}


    private static final Map<String, Def> REPORTS = new LinkedHashMap<>();


    private static final String NOT_CANCELLED = "UPPER(COALESCE(s.order_status,'')) <> 'CANCELLED'";


    static {
        REPORTS.put("sales", new Def("Sales Report", "Every sale with its total and payment status",
                "SELECT CONCAT('SL', LPAD(s.sale_id,3,'0')), CAST(DATE(s.sale_date) AS CHAR), "
                        + "COALESCE(cu.customer_name,'Walk-in'), COALESCE(u.full_name,'-'), "
                        + "COALESCE(s.total_amount,0), COALESCE(p.paid,0), COALESCE(s.order_status,'') "
                        + "FROM sales s "
                        + "LEFT JOIN customers cu ON cu.customer_id = s.customer_id "
                        + "LEFT JOIN users u ON u.user_id = s.user_id "
                        + "LEFT JOIN (SELECT sale_id, SUM(amount_paid) AS paid FROM payments GROUP BY sale_id) p ON p.sale_id = s.sale_id "
                        + "WHERE " + NOT_CANCELLED + " /*DATE*/ "
                        + "ORDER BY s.sale_date DESC, s.sale_id DESC",
                "s.sale_date",
                new String[]{"Sale", "Date", "Customer", "Cashier", "Total (LKR)", "Paid (LKR)", "Status"},
                "TTTTMMT", 4, "Total sales"));


        REPORTS.put("purchase", new Def("Purchase Report", "Every purchase from your suppliers",
                "SELECT CONCAT('PU', LPAD(pu.purchase_id,3,'0')), CAST(DATE(pu.purchase_date) AS CHAR), "
                        + "COALESCE(su.supplier_name,'-'), COALESCE(u.full_name,'-'), COALESCE(i.units,0), COALESCE(pu.total_amount,0) "
                        + "FROM purchases pu "
                        + "LEFT JOIN suppliers su ON su.supplier_id = pu.supplier_id "
                        + "LEFT JOIN users u ON u.user_id = pu.user_id "
                        + "LEFT JOIN (SELECT purchase_id, SUM(quantity) AS units FROM purchase_items GROUP BY purchase_id) i ON i.purchase_id = pu.purchase_id "
                        + "WHERE 1 = 1 /*DATE*/ "
                        + "ORDER BY pu.purchase_date DESC, pu.purchase_id DESC",
                "pu.purchase_date",
                new String[]{"Purchase", "Date", "Supplier", "Received By", "Units", "Total (LKR)"},
                "TTTTNM", 5, "Total purchases"));


        REPORTS.put("stock", new Def("Stock Report", "Current stock level of every product",
                "SELECT p.product_name, COALESCE(cat.category_name,'-'), COALESCE(s.qty,0), COALESCE(p.reorder_level,0), "
                        + "COALESCE(s.qty,0) * COALESCE(p.cost_price,0), "
                        + "CASE WHEN COALESCE(s.qty,0) <= 0 THEN 'Out of Stock' "
                        + "WHEN COALESCE(s.qty,0) <= COALESCE(p.reorder_level,0) THEN 'Low Stock' ELSE 'In Stock' END "
                        + "FROM products p "
                        + "LEFT JOIN categories cat ON cat.category_id = p.category_id "
                        + "LEFT JOIN (SELECT product_id, SUM(quantity) AS qty FROM stock GROUP BY product_id) s ON s.product_id = p.product_id "
                        + "ORDER BY COALESCE(s.qty,0), p.product_name",
                null,
                new String[]{"Product", "Category", "Quantity", "Reorder Level", "Stock Value (LKR)", "Status"},
                "TTNNMT", 4, "Total stock value"));


        REPORTS.put("customer", new Def("Customer Report", "What each customer has bought",
                "SELECT cu.customer_name, COALESCE(cu.phone,'-'), COUNT(s.sale_id), COALESCE(SUM(s.total_amount),0), "
                        + "COALESCE(CAST(DATE(MAX(s.sale_date)) AS CHAR),'-') "
                        + "FROM customers cu "
                        + "LEFT JOIN sales s ON s.customer_id = cu.customer_id AND " + NOT_CANCELLED + " /*DATE*/ "
                        + "GROUP BY cu.customer_id, cu.customer_name, cu.phone "
                        + "ORDER BY SUM(s.total_amount) DESC, cu.customer_name",
                "s.sale_date",
                new String[]{"Customer", "Phone", "Orders", "Total Spent (LKR)", "Last Purchase"},
                "TTNMT", 3, "Total from customers"));


        REPORTS.put("supplier", new Def("Supplier Report", "What you bought from each supplier",
                "SELECT su.supplier_name, COALESCE(su.contact_person,'-'), COALESCE(su.phone,'-'), "
                        + "COUNT(pu.purchase_id), COALESCE(SUM(pu.total_amount),0) "
                        + "FROM suppliers su "
                        + "LEFT JOIN purchases pu ON pu.supplier_id = su.supplier_id /*DATE*/ "
                        + "GROUP BY su.supplier_id, su.supplier_name, su.contact_person, su.phone "
                        + "ORDER BY SUM(pu.total_amount) DESC, su.supplier_name",
                "pu.purchase_date",
                new String[]{"Supplier", "Contact Person", "Phone", "Purchases", "Total Bought (LKR)"},
                "TTTNM", 4, "Total bought"));


        REPORTS.put("product", new Def("Product Report", "Sales, cost and profit of every product",
                "SELECT p.product_name, COALESCE(cat.category_name,'-'), COALESCE(x.qty,0), COALESCE(x.revenue,0), "
                        + "COALESCE(x.qty,0) * COALESCE(p.cost_price,0), "
                        + "COALESCE(x.revenue,0) - COALESCE(x.qty,0) * COALESCE(p.cost_price,0) "
                        + "FROM products p "
                        + "LEFT JOIN categories cat ON cat.category_id = p.category_id "
                        + "LEFT JOIN (SELECT si.product_id, SUM(si.quantity) AS qty, SUM(si.subtotal) AS revenue "
                        + "           FROM sale_items si JOIN sales s ON s.sale_id = si.sale_id "
                        + "           WHERE " + NOT_CANCELLED + " /*DATE*/ GROUP BY si.product_id) x ON x.product_id = p.product_id "
                        + "ORDER BY COALESCE(x.revenue,0) DESC, p.product_name",
                "s.sale_date",
                new String[]{"Product", "Category", "Qty Sold", "Revenue (LKR)", "Cost (LKR)", "Profit (LKR)"},
                "TTNMMM", 5, "Total profit"));
    }


    private static final List<String> PERIODS =
            List.of("Today", "Last 7 Days", "Last 30 Days", "This Month", "This Year", "All Time");


    // ------------------------------------------------------------------ FXML
    @FXML private Label lblTitle, lblSub, lblSummary;
    @FXML private javafx.scene.layout.GridPane cardsPane;
    @FXML private VBox detailPane;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> periodCombo;
    @FXML private TableView<RowData> reportTable;


    private final ObservableList<RowData> rows = FXCollections.observableArrayList();
    private FilteredList<RowData> filtered;
    private Def current;


    @FXML
    public void initialize() {
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        reportTable.setFixedCellSize(46);
        reportTable.setPlaceholder(new Label("No data for this report"));


        periodCombo.getItems().addAll(PERIODS);
        periodCombo.getSelectionModel().select("Last 30 Days");
        periodCombo.valueProperty().addListener((o, oldV, newV) -> {
            if (newV != null && current != null) load();
        });


        filtered = new FilteredList<>(rows, r -> true);
        reportTable.setItems(filtered);
        txtSearch.textProperty().addListener((o, oldV, newV) -> applySearch(newV));


        showCards();
    }


    // ------------------------------------------------------------------ cards <-> report
    @FXML
    private void openReport(ActionEvent e) {
        String key = (String) ((Node) e.getSource()).getUserData();
        current = REPORTS.get(key);
        if (current == null) return;


        lblTitle.setText(current.title());
        lblSub.setText(current.subtitle());
        boolean hasPeriod = current.dateCol() != null;
        periodCombo.setVisible(hasPeriod);
        periodCombo.setManaged(hasPeriod);
        txtSearch.clear();
        buildColumns(current);


        cardsPane.setVisible(false);
        cardsPane.setManaged(false);
        detailPane.setVisible(true);
        detailPane.setManaged(true);
        load();
    }


    @FXML
    private void back() { showCards(); }


    private void showCards() {
        current = null;
        rows.clear();
        lblTitle.setText("Reports");
        lblSub.setText("Generate and view business reports");
        detailPane.setVisible(false);
        detailPane.setManaged(false);
        cardsPane.setVisible(true);
        cardsPane.setManaged(true);
    }


    @FXML
    private void refresh() { if (current != null) load(); }


    // ------------------------------------------------------------------ table columns
    private void buildColumns(Def def) {
        reportTable.getColumns().clear();
        for (int i = 0; i < def.headers().length; i++) {
            final int idx = i;
            TableColumn<RowData, String> col = new TableColumn<>(def.headers()[i]);
            col.setSortable(false);
            col.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().cells().get(idx)));
            if ("Status".equals(def.headers()[i])) {
                col.setCellFactory(tc -> new TableCell<>() {
                    @Override protected void updateItem(String s, boolean empty) {
                        super.updateItem(s, empty);
                        if (empty || s == null || s.isBlank()) { setGraphic(null); return; }
                        Label badge = new Label(toTitle(s));
                        badge.getStyleClass().addAll("pg-badge", badgeClass(s));
                        setGraphic(badge);
                    }
                });
            }
            reportTable.getColumns().add(col);
        }
    }


    private static String toTitle(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase().replace("stock", "Stock");
    }


    private static String badgeClass(String s) {
        String t = s.toLowerCase();
        if (t.contains("cancel") || t.contains("unpaid") || t.contains("low") || t.contains("out")) return "pg-badge-red";
        if (t.contains("in stock") || t.equals("completed") || t.equals("paid") || t.equals("active")) return "pg-badge-green";
        return "pg-badge-gold";
    }


    // ------------------------------------------------------------------ load data (background)
    private LocalDate startDate() {
        LocalDate today = LocalDate.now();
        String p = periodCombo.getValue() == null ? "All Time" : periodCombo.getValue();
        switch (p) {
            case "Today":        return today;
            case "Last 7 Days":  return today.minusDays(6);
            case "Last 30 Days": return today.minusDays(29);
            case "This Month":   return today.withDayOfMonth(1);
            case "This Year":    return today.withDayOfYear(1);
            default:             return null;
        }
    }


    private void load() {
        final Def def = current;
        final LocalDate from = def.dateCol() == null ? null : startDate();


        reportTable.setPlaceholder(new Label("Loading..."));
        Task<List<RowData>> task = new Task<>() {
            @Override protected List<RowData> call() throws Exception {
                try (Connection c = openConnection()) { return ReportController.this.run(c, def,from); }
            }
        };
        task.setOnSucceeded(e -> {
            if (current != def) return;                      // user already went back / opened another report
            rows.setAll(task.getValue());
            reportTable.setPlaceholder(new Label("No data for this report"));
            applySearch(txtSearch.getText());
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            String why = task.getException().getMessage();
            reportTable.setPlaceholder(new Label("⚠ Cannot load report – " + (why == null ? "database error" : why)));
        });
        Thread t = new Thread(task, "report-load");
        t.setDaemon(true);
        t.start();
    }


    private List<RowData> run(Connection c, Def def, LocalDate from) throws SQLException {
        String sql = def.sql().replace("/*DATE*/", from == null ? "" : "AND DATE(" + def.dateCol() + ") >= ?");
        List<RowData> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (from != null) ps.setDate(1, java.sql.Date.valueOf(from));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<String> cells = new ArrayList<>();
                    double total = 0;
                    for (int i = 0; i < def.types().length(); i++) {
                        cells.add(format(def.types().charAt(i), rs, i + 1));
                        if (i == def.totalCol()) total = rs.getDouble(i + 1);
                    }
                    out.add(new RowData(cells, total));
                }
            }
        }
        return out;
    }


    private static String format(char type, ResultSet rs, int col) throws SQLException {
        switch (type) {
            case 'N':
                return String.format("%,d", rs.getLong(col));
            case 'M': {
                double v = rs.getDouble(col);
                return v == Math.rint(v) ? String.format("%,.0f", v) : String.format("%,.2f", v);
            }
            default: {
                String s = rs.getString(col);
                return s == null ? "-" : s;
            }
        }
    }


    // ------------------------------------------------------------------ search + summary line
    private void applySearch(String text) {
        String q = text == null ? "" : text.trim().toLowerCase();
        filtered.setPredicate(r -> q.isEmpty() || r.cells().stream().anyMatch(cell -> cell.toLowerCase().contains(q)));
        updateSummary();
    }


    private void updateSummary() {
        if (current == null) { lblSummary.setText(""); return; }
        double sum = 0;
        for (RowData r : filtered) sum += r.total();
        String text = filtered.size() + " records";
        if (current.totalCol() >= 0)
            text += "   •   " + current.totalLabel() + ":  LKR " + (sum == Math.rint(sum) ? String.format("%,.0f", sum) : String.format("%,.2f", sum));
        lblSummary.setText(text);
    }


    // ------------------------------------------------------------------ export (CSV)
    @FXML
    private void export() {
        if (current == null) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(current.title().toLowerCase().replace(' ', '-') + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File f = fc.showSaveDialog(reportTable.getScene().getWindow());
        if (f == null) return;
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) {
            w.print('\uFEFF');                                   // so Excel reads UTF-8 correctly
            w.println(csvLine(List.of(current.headers())));
            for (RowData r : filtered) w.println(csvLine(r.cells()));
        } catch (Exception ex) {
            reportTable.setPlaceholder(new Label("⚠ Export failed – " + ex.getMessage()));
        }
    }


    private static String csvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(cells.get(i) == null ? "" : cells.get(i).replace("\"", "\"\"")).append('"');
        }
        return sb.toString();
    }
}

