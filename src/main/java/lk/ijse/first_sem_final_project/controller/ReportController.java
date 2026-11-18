package lk.ijse.first_sem_final_project.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ReportController {

    private static final String SALES = "Sales report";
    private static final String DAILY = "Daily sales summary";
    private static final String TOP   = "Top selling products";
    private static final String STOCK = "Stock report";
    private static final String LOW   = "Low stock report";

    private static final String NOT_CANCELLED = " UPPER(COALESCE(s.order_status,'')) <> 'CANCELLED' ";
    private static final DateTimeFormatter DAY      = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.ENGLISH);

    private static final String ERR_COLOR = "#e0786b";
    private static final String MUTED     = "#8f8677";

    /** One finished report: table content plus three summary figures. */
    private record Result(List<String> headers, List<Boolean> numeric, List<List<String>> rows,
                          String[] statCaptions, String[] statValues) {}

    @FXML private ComboBox<String> cmbReport;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private HBox boxDates;
    @FXML private Button btnGenerate, btnExport;
    @FXML private Label lblTitle, lblSub, lblStatus;
    @FXML private Label lblCap1, lblCap2, lblCap3, lblVal1, lblVal2, lblVal3;
    @FXML private TableView<ObservableList<String>> tblReport;

    private Result lastResult;
    private String lastTitle = "report";
    private int requestId = 0;          // used to ignore results of an older, slower request

    @FXML
    public void initialize() {
        dpTo.setValue(LocalDate.now());
        dpFrom.setValue(LocalDate.now().minusDays(29));

        cmbReport.getItems().addAll(SALES, DAILY, TOP, STOCK, LOW);
        cmbReport.getSelectionModel().selectFirst();
        cmbReport.valueProperty().addListener((o, a, v) -> {
            if (v != null) runReport();
        });

        tblReport.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tblReport.setPlaceholder(new Label("Nothing to show for this selection"));
        btnExport.setDisable(true);

        Platform.runLater(this::runReport);
    }

    // ---------------- buttons ----------------

    @FXML
    private void generate(ActionEvent e) {
        runReport();
    }

    /** Today / 7 days / 30 days / This month chips. */
    @FXML
    private void quickRange(ActionEvent e) {
        String key = String.valueOf(((Node) e.getSource()).getUserData());
        LocalDate today = LocalDate.now();
        LocalDate from = switch (key) {
            case "0"  -> today;
            case "7"  -> today.minusDays(6);
            case "30" -> today.minusDays(29);
            default   -> today.withDayOfMonth(1);
        };
        dpFrom.setValue(from);
        dpTo.setValue(today);
        runReport();
    }

    @FXML
    private void exportCsv(ActionEvent e) {
        if (lastResult == null || lastResult.rows().isEmpty()) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Save report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        fc.setInitialFileName(lastTitle.toLowerCase().replace(' ', '-') + "-" + LocalDate.now() + ".csv");
        File file = fc.showSaveDialog(btnExport.getScene().getWindow());
        if (file == null) return;

        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write('\uFEFF'); // lets Excel read the file as UTF-8
            w.write(csvLine(lastResult.headers(), null));
            w.newLine();
            for (List<String> row : lastResult.rows()) {
                w.write(csvLine(row, lastResult.numeric()));
                w.newLine();
            }
            setStatus("Saved " + file.getName(), false);
        } catch (IOException ex) {
            ex.printStackTrace();
            setStatus("Could not save the file", true);
        }
    }

    private static String csvLine(List<String> cells, List<Boolean> numeric) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            String v = cells.get(i) == null ? "" : cells.get(i);
            if (numeric != null && numeric.get(i)) v = v.replace(",", "");   // plain numbers for Excel
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) v = "\"" + v.replace("\"", "\"\"") + "\"";
            if (i > 0) sb.append(',');
            sb.append(v);
        }
        return sb.toString();
    }

    // ---------------- running a report ----------------

    private void runReport() {
        final String type = cmbReport.getValue();
        if (type == null) return;

        final boolean needsDates = !(STOCK.equals(type) || LOW.equals(type));
        boxDates.setDisable(!needsDates);
        boxDates.setOpacity(needsDates ? 1 : 0.4);

        final LocalDate from = dpFrom.getValue();
        final LocalDate to = dpTo.getValue();
        if (needsDates) {
            if (from == null || to == null) { setStatus("Pick both dates first", true); return; }
            if (from.isAfter(to))           { setStatus("The start date is after the end date", true); return; }
        }

        final int myId = ++requestId;
        setBusy(true);

        Task<Result> task = new Task<>() {
            @Override protected Result call() throws Exception { return build(type, from, to); }
        };
        task.setOnSucceeded(ev -> {
            if (myId != requestId) return;          // a newer request is running, drop this one
            show(task.getValue(), type, needsDates, from, to);
            setBusy(false);
        });
        task.setOnFailed(ev -> {
            if (myId != requestId) return;
            Throwable ex = task.getException();
            ex.printStackTrace();
            String why = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (why.length() > 120) why = why.substring(0, 120) + "…";
            setStatus("Could not load the report: " + why, true);
            setBusy(false);
        });
        Thread t = new Thread(task, "report-loader");
        t.setDaemon(true);
        t.start();
    }

    private Result build(String type, LocalDate from, LocalDate to) throws SQLException {
        try (Connection c = lk.ijse.first_sem_final_project.db.DBConnection.getInstance().getConnection()) {
            return switch (type) {
                case SALES -> salesReport(c, from, to);
                case DAILY -> dailyReport(c, from, to);
                case TOP   -> topProducts(c, from, to);
                case STOCK -> stockReport(c, false);
                default    -> stockReport(c, true);
            };
        }
    }

    /** start of "from" day, inclusive */
    private static Timestamp start(LocalDate d) { return Timestamp.valueOf(d.atStartOfDay()); }
    /** start of the day after "to", exclusive (so the whole "to" day is included) */
    private static Timestamp end(LocalDate d)   { return Timestamp.valueOf(d.plusDays(1).atStartOfDay()); }

    // ---------------- the reports ----------------

    private Result salesReport(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT s.sale_id, s.sale_date, COALESCE(s.order_status,'') st, s.total_amount "
                + "FROM sales s WHERE s.sale_date >= ? AND s.sale_date < ? AND" + NOT_CANCELLED
                + "ORDER BY s.sale_date DESC";
        List<List<String>> rows = new ArrayList<>();
        double revenue = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, start(from));
            ps.setTimestamp(2, end(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double amt = rs.getDouble("total_amount");
                    revenue += amt;
                    Timestamp ts = rs.getTimestamp("sale_date");
                    rows.add(Arrays.asList(nz(rs.getString("sale_id")),
                            ts == null ? "" : ts.toLocalDateTime().format(DAY_TIME),
                            nz(rs.getString("st")), money(amt)));
                }
            }
        }
        double avg = rows.isEmpty() ? 0 : revenue / rows.size();
        return new Result(List.of("Sale ID", "Date", "Status", "Amount (LKR)"),
                List.of(false, false, false, true), rows,
                new String[]{"Orders", "Revenue", "Average order"},
                new String[]{count(rows.size()), "LKR " + money(revenue), "LKR " + money(avg)});
    }

    private Result dailyReport(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(s.sale_date) d, COUNT(*) n, COALESCE(SUM(s.total_amount),0) t "
                + "FROM sales s WHERE s.sale_date >= ? AND s.sale_date < ? AND" + NOT_CANCELLED
                + "GROUP BY DATE(s.sale_date) ORDER BY d";
        List<List<String>> rows = new ArrayList<>();
        long orders = 0;
        double revenue = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, start(from));
            ps.setTimestamp(2, end(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long n = rs.getLong("n");
                    double t = rs.getDouble("t");
                    orders += n;
                    revenue += t;
                    rows.add(Arrays.asList(rs.getDate("d").toLocalDate().format(DAY), count(n),
                            money(t), money(n == 0 ? 0 : t / n)));
                }
            }
        }
        return new Result(List.of("Date", "Orders", "Revenue (LKR)", "Average order (LKR)"),
                List.of(false, true, true, true), rows,
                new String[]{"Days with sales", "Orders", "Revenue"},
                new String[]{count(rows.size()), count(orders), "LKR " + money(revenue)});
    }

    private Result topProducts(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT p.product_name, COALESCE(cat.category_name,'-') cat, "
                + "SUM(si.quantity) units, SUM(si.quantity * si.unit_price) revenue "
                + "FROM sale_items si "
                + "JOIN sales s ON s.sale_id = si.sale_id "
                + "JOIN products p ON p.product_id = si.product_id "
                + "LEFT JOIN categories cat ON cat.category_id = p.category_id "
                + "WHERE s.sale_date >= ? AND s.sale_date < ? AND" + NOT_CANCELLED
                + "GROUP BY p.product_id, p.product_name, cat.category_name "
                + "ORDER BY units DESC LIMIT 20";
        List<List<String>> rows = new ArrayList<>();
        long units = 0;
        double revenue = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, start(from));
            ps.setTimestamp(2, end(to));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    long u = rs.getLong("units");
                    double r = rs.getDouble("revenue");
                    units += u;
                    revenue += r;
                    rows.add(Arrays.asList(String.valueOf(rank++), nz(rs.getString("product_name")),
                            nz(rs.getString("cat")), count(u), money(r)));
                }
            }
        }
        return new Result(List.of("#", "Product", "Category", "Units sold", "Revenue (LKR)"),
                List.of(false, false, false, true, true), rows,
                new String[]{"Products listed", "Units sold", "Revenue"},
                new String[]{count(rows.size()), count(units), "LKR " + money(revenue)});
    }

    private Result stockReport(Connection c, boolean lowOnly) throws SQLException {
        String sql = "SELECT p.product_name, COALESCE(cat.category_name,'-') cat, "
                + "COALESCE(SUM(st.quantity),0) qty, p.reorder_level, p.unit_price "
                + "FROM products p "
                + "LEFT JOIN categories cat ON cat.category_id = p.category_id "
                + "LEFT JOIN stock st ON st.product_id = p.product_id "
                + "GROUP BY p.product_id, p.product_name, cat.category_name, p.reorder_level, p.unit_price "
                + "ORDER BY p.product_name";
        List<List<String>> rows = new ArrayList<>();
        long units = 0, outCount = 0, toReorder = 0;
        double value = 0;
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long qty = rs.getLong("qty");
                long reorder = rs.getLong("reorder_level");
                double price = rs.getDouble("unit_price");
                String status = qty <= 0 ? "Out of stock" : qty <= reorder ? "Low" : "OK";
                if (lowOnly && status.equals("OK")) continue;

                units += qty;
                value += qty * price;
                if (qty <= 0) outCount++;
                toReorder += Math.max(reorder - qty, 0);
                rows.add(Arrays.asList(nz(rs.getString("product_name")), nz(rs.getString("cat")), count(qty),
                        count(reorder), status, money(qty * price)));
            }
        }
        List<String> headers = List.of("Product", "Category", "In stock", "Reorder level", "Status", "Stock value (LKR)");
        List<Boolean> numeric = List.of(false, false, true, true, false, true);
        if (lowOnly) {
            return new Result(headers, numeric, rows,
                    new String[]{"Items below reorder level", "Out of stock", "Units to reorder"},
                    new String[]{count(rows.size()), count(outCount), count(toReorder)});
        }
        return new Result(headers, numeric, rows,
                new String[]{"Products", "Units in stock", "Stock value"},
                new String[]{count(rows.size()), count(units), "LKR " + money(value)});
    }

    // ---------------- showing the result ----------------

    private void show(Result r, String type, boolean needsDates, LocalDate from, LocalDate to) {
        lastResult = r;
        lastTitle = type;

        tblReport.getColumns().clear();
        for (int i = 0; i < r.headers().size(); i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(r.headers().get(i));
            col.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().get(idx)));
            col.setSortable(false);
            if (r.numeric().get(i)) {
                col.getStyleClass().add("num");
                col.setStyle("-fx-alignment: CENTER-RIGHT;");
            }
            tblReport.getColumns().add(col);
        }
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        for (List<String> row : r.rows()) data.add(FXCollections.observableArrayList(row));
        tblReport.setItems(data);

        lblTitle.setText(type);
        lblSub.setText(needsDates ? from.format(DAY) + "  –  " + to.format(DAY)
                : "As of " + LocalDate.now().format(DAY));

        lblCap1.setText(r.statCaptions()[0]);  lblVal1.setText(r.statValues()[0]);
        lblCap2.setText(r.statCaptions()[1]);  lblVal2.setText(r.statValues()[1]);
        lblCap3.setText(r.statCaptions()[2]);  lblVal3.setText(r.statValues()[2]);

        btnExport.setDisable(r.rows().isEmpty());
        setStatus(r.rows().size() + (r.rows().size() == 1 ? " row" : " rows") + "  ·  generated "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), false);
    }

    private void setBusy(boolean busy) {
        btnGenerate.setDisable(busy);
        if (busy) setStatus("Loading…", false);
    }

    private void setStatus(String msg, boolean error) {
        lblStatus.setText(msg);
        lblStatus.setStyle("-fx-text-fill: " + (error ? ERR_COLOR : MUTED) + ";");
    }

    private static String nz(String s)    { return s == null ? "" : s; }
    private static String money(double v) { return String.format(Locale.US, "%,.2f", v); }
    private static String count(long v)   { return String.format(Locale.US, "%,d", v); }
}