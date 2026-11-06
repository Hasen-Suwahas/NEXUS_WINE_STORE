package lk.ijse.first_sem_final_project.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lk.ijse.first_sem_final_project.db.DBConnection;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Shared behaviour of every "Manage ..." page:
 * live table, search, pagination, CSV export, date/time, and styled add/edit/delete dialogs.
 * A page controller only supplies columns, the SELECT query and its add/edit/delete code.
 */
public abstract class BasePageController<T> {

    private static final String CSS = "/style/pages.css";
    private static final int ROW_HEIGHT = 46;
    private static final int REFRESH_SECONDS = 15;

    // ---- injected from the FXML ----
    @FXML protected TableView<T> table;
    @FXML protected TextField txtSearch;
    @FXML protected Label lblDate, lblTime, lblCount;
    @FXML protected HBox pagination;

    private final List<T> all = new ArrayList<>();
    private List<T> filtered = new ArrayList<>();
    private int page = 0;
    private int pageSize = 6;
    private Timeline clock, autoRefresh;

    /** small id + name pair for combo boxes */
    protected record Item(int id, String name) {
        @Override public String toString() { return name; }
    }

    protected abstract String pageKey();

    // ---- what a page must provide ----
    protected abstract void setupColumns();
    protected abstract List<T> fetchRows(Connection c) throws SQLException;
    protected abstract boolean matches(T row, String lowerCaseQuery);
    protected abstract void onAdd();
    protected abstract String exportName();
    protected abstract String[] csvHeader();
    protected abstract String[] csvRow(T row);

    // =====================================================================
    @FXML
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label("No records found"));
        setupColumns();

        txtSearch.textProperty().addListener((o, a, b) -> { page = 0; applyFilter(); });
        table.heightProperty().addListener((o, a, b) -> recalcPageSize());

        clock = new Timeline(new KeyFrame(Duration.ZERO, e -> tick()), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        autoRefresh = new Timeline(new KeyFrame(Duration.seconds(REFRESH_SECONDS), e -> refresh()));
        autoRefresh.setCycleCount(Animation.INDEFINITE);
        autoRefresh.play();

        // stop the timers when the dashboard swaps this page out
        table.sceneProperty().addListener((o, oldScene, newScene) -> {
            if (newScene == null) { clock.stop(); autoRefresh.stop(); }
        });

        refresh();
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        lblDate.setText(now.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)));
        lblTime.setText(now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)));
    }

    // ===================== DATA (live) =====================
    protected void refresh() {
        Task<List<T>> task = new Task<>() {
            @Override protected List<T> call() throws Exception {
                try (Connection c = conn()) { return fetchRows(c); }
            }
        };
        task.setOnSucceeded(e -> {
            all.clear();
            all.addAll(task.getValue());
            table.setPlaceholder(new Label("No records found"));
            applyFilter();
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            table.setPlaceholder(new Label("⚠ Cannot load data – check the database connection"));
        });
        Thread t = new Thread(task, "page-refresh");
        t.setDaemon(true);
        t.start();
    }

    protected Connection conn() throws SQLException { return DBConnection.getInstance().getConnection(); }

    private void applyFilter() {
        String q = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        filtered = q.isEmpty() ? new ArrayList<>(all)
                : all.stream().filter(r -> matches(r, q)).collect(Collectors.toList());
        page = Math.min(page, maxPage());
        renderPage();
    }

    // ===================== PAGINATION =====================
    private int maxPage() { return Math.max(0, (filtered.size() - 1) / pageSize); }

    private void recalcPageSize() {
        int rows = Math.max(3, (int) Math.floor((table.getHeight() - 42) / ROW_HEIGHT));
        if (rows != pageSize) {
            pageSize = rows;
            page = Math.min(page, maxPage());
            renderPage();
        }
    }

    private void renderPage() {
        int from = page * pageSize;
        int to = Math.min(filtered.size(), from + pageSize);
        table.getItems().setAll(from < to ? filtered.subList(from, to) : new ArrayList<T>());
        lblCount.setText(filtered.isEmpty() ? "No records" : "Showing " + (from + 1) + "–" + to + " of " + filtered.size());
        buildPagination();
    }

    private void buildPagination() {
        int pages = maxPage() + 1;
        pagination.getChildren().clear();
        pagination.getChildren().add(pageBtn("‹", page > 0, () -> goTo(page - 1), false));

        int start = Math.max(0, Math.min(page - 2, pages - 5));
        int end = Math.min(pages, start + 5);
        for (int i = start; i < end; i++) {
            final int idx = i;
            pagination.getChildren().add(pageBtn(String.valueOf(i + 1), true, () -> goTo(idx), i == page));
        }
        if (end < pages) {
            Label dots = new Label("…");
            dots.getStyleClass().add("pg-muted");
            pagination.getChildren().add(dots);
        }
        pagination.getChildren().add(pageBtn("›", page < pages - 1, () -> goTo(page + 1), false));
    }

    private Button pageBtn(String text, boolean enabled, Runnable action, boolean active) {
        Button b = new Button(text);
        b.getStyleClass().add("pg-page-btn");
        if (active) b.getStyleClass().add("active");
        b.setDisable(!enabled);
        b.setOnAction(e -> action.run());
        return b;
    }

    private void goTo(int p) { page = p; renderPage(); }

    // ===================== COLUMN HELPERS =====================
    protected void textCol(String title, double width, Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        table.getColumns().add(c);
    }

    /** coloured status pill (green / red / gold decided by the text) */
    protected void badgeCol(String title, double width, Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                Label badge = new Label(s);
                badge.getStyleClass().addAll("pg-badge", badgeClass(s));
                setGraphic(badge);
            }
        });
        table.getColumns().add(c);
    }

    private static String badgeClass(String s) {
        String t = s.toLowerCase();
        if (t.contains("low") || t.contains("out") || t.contains("inactive")) return "pg-badge-red";
        if (t.contains("in stock") || t.equals("active")) return "pg-badge-green";
        return "pg-badge-gold";
    }

    /** ✎ and 🗑 buttons column. Pass null to hide a button. */
    protected void actionsCol(double width, Consumer<T> onEdit, Consumer<T> onDelete) {
        TableColumn<T, Void> c = new TableColumn<>("Actions");
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(10);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                if (onEdit != null) box.getChildren().add(iconBtn("✎", "pg-icon-edit", () -> run(onEdit)));
                if (onDelete != null) box.getChildren().add(iconBtn("🗑", "pg-icon-delete", () -> run(onDelete)));
            }
            private void run(Consumer<T> action) {
                T item = getTableRow() == null ? null : getTableRow().getItem();
                if (item != null) action.accept(item);
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
        table.getColumns().add(c);
    }

    private Button iconBtn(String text, String extraClass, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("pg-icon-btn", extraClass);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ===================== TOP BUTTONS =====================
    @FXML protected void handleAdd() { onAdd(); }

    @FXML
    protected void handleExport() {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(exportName() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File f = fc.showSaveDialog(table.getScene().getWindow());
        if (f == null) return;
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) {
            w.print('\uFEFF');                                   // so Excel reads UTF-8 correctly
            w.println(csvLine(csvHeader()));
            for (T row : filtered) w.println(csvLine(csvRow(row)));
            message("Export", "Saved " + filtered.size() + " rows to:\n" + f.getAbsolutePath());
        } catch (Exception ex) {
            message("Export failed", ex.getMessage());
        }
    }

    private static String csvLine(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(cells[i] == null ? "" : cells[i].replace("\"", "\"\"")).append('"');
        }
        return sb.toString();
    }

    // ===================== DIALOG HELPERS =====================
    protected GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);
        ColumnConstraints a = new ColumnConstraints(115);
        ColumnConstraints b = new ColumnConstraints(270);
        b.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(a, b);
        return g;
    }

    protected void row(GridPane g, int r, String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("dlg-label");
        g.add(l, 0, r);
        g.add(input, 1, r);
        GridPane.setHgrow(input, Priority.ALWAYS);
    }

    protected TextField input(String prompt, String value) {
        TextField t = new TextField(value == null ? "" : value);
        t.setPromptText(prompt);
        t.getStyleClass().add("dlg-field");
        return t;
    }

    protected <C> ComboBox<C> combo(List<C> items, String prompt) {
        ComboBox<C> c = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(items));
        c.setPromptText(prompt);
        c.setMaxWidth(Double.MAX_VALUE);
        c.getStyleClass().add("dlg-field");
        return c;
    }

    /** Opens a styled dialog. Returns true when the user pressed the OK button and the validator passed. */
    protected boolean showForm(String title, Node content, Supplier<String> validator, String okText, boolean withCancel) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(table.getScene().getWindow());
        dlg.initStyle(StageStyle.UNDECORATED);

        DialogPane pane = dlg.getDialogPane();
        pane.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
        pane.getStyleClass().add("gold-dialog");

        Label head = new Label(title);
        head.getStyleClass().add("dlg-title");
        Label err = new Label();
        err.getStyleClass().add("dlg-error");
        err.setWrapText(true);
        err.setManaged(false);
        err.setVisible(false);

        VBox box = new VBox(16, head, content, err);
        box.setPadding(new Insets(8, 10, 0, 10));
        pane.setContent(box);

        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().add(ok);
        if (withCancel) pane.getButtonTypes().add(ButtonType.CANCEL);

        Button okBtn = (Button) pane.lookupButton(ok);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String problem = validator.get();
            if (problem != null) {
                err.setText(problem);
                err.setManaged(true);
                err.setVisible(true);
                ev.consume();                    // keep the dialog open
            }
        });
        return dlg.showAndWait().filter(b -> b == ok).isPresent();
    }

    protected void message(String title, String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(340);
        l.getStyleClass().add("dlg-text");
        showForm(title, l, () -> null, "OK", false);
    }

    protected boolean confirm(String title, String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(340);
        l.getStyleClass().add("dlg-text");
        return showForm(title, l, () -> null, "Delete", true);
    }

    protected void dbError(SQLException e) {
        e.printStackTrace();
        String m;
        if (e.getErrorCode() == 1451) m = "This record is used by other data (sales, purchases, stock...) so it cannot be deleted.";
        else if (e.getErrorCode() == 1062) m = "A record with the same value already exists.";
        else m = e.getMessage();
        message("Database Error", m);
    }

    // ===================== SMALL UTILITIES =====================
    protected List<Item> loadItems(String sql) {
        List<Item> list = new ArrayList<>();
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(new Item(rs.getInt(1), rs.getString(2)));
        } catch (SQLException e) { dbError(e); }
        return list;
    }

    protected static int exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    protected static String code(String prefix, int id) { return prefix + String.format("%03d", id); }

    protected static String money(double v) {
        return v == Math.rint(v) ? String.format("%,.0f", v) : String.format("%,.2f", v);
    }

    protected static String plain(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    protected static Double num(String s) {
        try { double d = Double.parseDouble(s.trim().replace(",", "")); return d >= 0 ? d : null; }
        catch (Exception e) { return null; }
    }

    protected static Integer whole(String s) {
        try { int i = Integer.parseInt(s.trim()); return i >= 0 ? i : null; }
        catch (Exception e) { return null; }
    }

    protected static String stockStatus(int qty, int reorder) {
        if (qty <= 0) return "Out of Stock";
        if (qty <= reorder) return "Low Stock";
        return "In Stock";
    }

    protected static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    protected static String text(String s) { return s == null ? "" : s; }
}