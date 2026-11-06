package lk.ijse.first_sem_final_project.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashBoardPageController {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";
    private static final String STORE_NAME = "Nexus Wine Store";
    private static final String PAGE_FOLDER = "/view/";          // where your Page.fxml files are (resources/view)

    private static final int REFRESH_SECONDS = 10;
    private static final String RANGE_7 = "Last 7 Days";
    private static final String RANGE_30 = "Last 30 Days";
    private static final String RANGE_YEAR = "This Year";
    private static final String[] COLORS = {"#e0a93b", "#b87333", "#f0cf7a", "#8a8a8a", "#555555"};
    private static final String NOT_CANCELLED = " UPPER(COALESCE(order_status,'')) <> 'CANCELLED' ";

    // small holders for one refresh of data
    private record CategoryShare(String name, long units) {}
    private record Snapshot(int totalProducts, double totalSalesValue, int todayOrders, double todayRevenue,
                            int lowStock, List<String> labels, List<Double> values, List<CategoryShare> categories) {}

    // fxml
    @FXML private ToggleGroup navGroup;
    @FXML private StackPane contentHost;
    @FXML private VBox dashboardPane;
    @FXML private Label lblStoreName, lblBadge, lblAvatar, lblUserName, lblUserRole;
    @FXML private Label lblGreeting, lblDate, lblTime, lblLastUpdated;
    @FXML private Label lblTotalProducts, lblSalesValue, lblTodayOrders, lblTodayRevenue, lblLowStock;
    @FXML private AreaChart<String, Number> areaChart;
    @FXML private NumberAxis yAxis;
    @FXML private ComboBox<String> rangeCombo;
    @FXML private PieChart pieChart;
    @FXML private Label lblDonutTotal;
    @FXML private VBox legendBox;

    private ScheduledService<Snapshot> liveService;
    private Timeline clock;
    private volatile String selectedRange = RANGE_7;
    private String userName = "Admin";

    @FXML
    public void initialize() {
        lblStoreName.setText(STORE_NAME);
        lblBadge.setText(STORE_NAME);
        setCurrentUser("Admin", "Admin");

        setupChart();
        rangeCombo.getItems().addAll(RANGE_7, RANGE_30, RANGE_YEAR);
        rangeCombo.getSelectionModel().selectFirst();
        rangeCombo.valueProperty().addListener((o, a, v) -> {
            if (v != null) { selectedRange = v; refreshNow(); }
        });

        startClock();
        startLiveUpdates();
    }
    public void setCurrentUser(String fullName, String role) {
        userName = (fullName == null || fullName.isBlank()) ? "Admin" : fullName;
        lblUserName.setText(userName);
        lblUserRole.setText(role == null ? "" : role);
        lblAvatar.setText(userName.substring(0, 1).toUpperCase());
        tick();
    }
    private void startClock() {
        clock = new Timeline(new KeyFrame(Duration.ZERO, e -> tick()), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        lblDate.setText(now.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)));
        lblTime.setText(now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)));
        int h = now.getHour();
        String g = h < 12 ? "Good Morning" : h < 17 ? "Good Afternoon" : "Good Evening";
        lblGreeting.setText(g + ", " + userName + "!");
    }

    private void startLiveUpdates() {
        liveService = new ScheduledService<>() {
            @Override protected Task<Snapshot> createTask() {
                final String range = selectedRange;
                return new Task<>() {
                    @Override protected Snapshot call() throws Exception { return loadSnapshot(range); }
                };
            }
        };
        liveService.setPeriod(Duration.seconds(REFRESH_SECONDS));
        liveService.setRestartOnFailure(true);
        liveService.setMaximumFailureCount(Integer.MAX_VALUE);
        liveService.setBackoffStrategy(ScheduledService.LINEAR_BACKOFF_STRATEGY);
        liveService.setOnSucceeded(e -> render(liveService.getValue()));
        liveService.setOnFailed(e -> {
            lblLastUpdated.setText("⚠ Database connection failed");
            liveService.getException().printStackTrace();
        });
        liveService.start();
    }

    private void refreshNow() {
        if (liveService != null) liveService.restart();
    }

    /* Call from stage.setOnCloseRequest*/
    public void shutdown() {
        if (liveService != null) liveService.cancel();
        if (clock != null) clock.stop();
    }

    // DATABASE (plain JDBC)
    // If you already have a DBConnection class, replace the body with:
    //   return DBConnection.getInstance().getConnection();
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private Snapshot loadSnapshot(String range) throws SQLException {
        try (Connection c = getConnection()) {
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            loadSalesTrend(c, range, labels, values);

            return new Snapshot(
                    (int) scalar(c, "SELECT COUNT(*) FROM products"),
                    scalar(c, "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE" + NOT_CANCELLED),
                    (int) scalar(c, "SELECT COUNT(*) FROM sales WHERE DATE(sale_date) = CURDATE() AND" + NOT_CANCELLED),
                    scalar(c, "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE DATE(sale_date) = CURDATE() AND" + NOT_CANCELLED),
                    (int) scalar(c, "SELECT COUNT(*) FROM stock s JOIN products p ON p.product_id = s.product_id "
                            + "WHERE s.quantity <= p.reorder_level"),
                    labels, values, loadCategoryShares(c));
        }
    }

    private double scalar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private void loadSalesTrend(Connection c, String range, List<String> labels, List<Double> values) throws SQLException {
        if (RANGE_YEAR.equals(range)) {
            double[] m = new double[12];
            String sql = "SELECT MONTH(sale_date) m, COALESCE(SUM(total_amount),0) t FROM sales "
                    + "WHERE YEAR(sale_date) = YEAR(CURDATE()) AND" + NOT_CANCELLED + "GROUP BY MONTH(sale_date)";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) m[rs.getInt("m") - 1] = rs.getDouble("t");
            }
            for (int i = 1; i <= 12; i++) {
                labels.add(Month.of(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                values.add(m[i - 1]);
            }
            return;
        }

        int days = RANGE_30.equals(range) ? 30 : 7;
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        Map<LocalDate, Double> map = new HashMap<>();
        String sql = "SELECT DATE(sale_date) d, COALESCE(SUM(total_amount),0) t FROM sales "
                + "WHERE DATE(sale_date) >= ? AND" + NOT_CANCELLED + "GROUP BY DATE(sale_date)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getDate("d").toLocalDate(), rs.getDouble("t"));
            }
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            labels.add(f.format(d));
            values.add(map.getOrDefault(d, 0.0));
        }
    }

    /** units in stock per category: top 4 + "Other" */
    private List<CategoryShare> loadCategoryShares(Connection c) throws SQLException {
        List<CategoryShare> all = new ArrayList<>();
        String sql = "SELECT cat.category_name, COALESCE(SUM(s.quantity),0) units "
                + "FROM categories cat "
                + "LEFT JOIN products p ON p.category_id = cat.category_id "
                + "LEFT JOIN stock s ON s.product_id = p.product_id "
                + "GROUP BY cat.category_id, cat.category_name ORDER BY units DESC";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                long u = rs.getLong("units");
                if (u > 0) all.add(new CategoryShare(rs.getString(1), u));
            }
        }
        if (all.size() <= 5) return all;
        List<CategoryShare> out = new ArrayList<>(all.subList(0, 4));
        long other = all.subList(4, all.size()).stream().mapToLong(CategoryShare::units).sum();
        out.add(new CategoryShare("Other", other));
        return out;
    }

    // RENDER
    private void render(Snapshot d) {
        if (d == null) return;
        lblTotalProducts.setText(String.format("%,d", d.totalProducts()));
        lblSalesValue.setText("LKR " + String.format("%,.0f", d.totalSalesValue()));
        lblTodayOrders.setText(String.format("%,d", d.todayOrders()));
        lblTodayRevenue.setText("LKR " + String.format("%,.0f", d.todayRevenue()) + " today");
        lblLowStock.setText(String.format("%,d", d.lowStock()));
        lblLastUpdated.setText("Live • updated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        renderArea(d);
        renderDonut(d);
    }

    private void setupChart() {
        areaChart.setAnimated(false);
        pieChart.setAnimated(false);
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number n) {
                double v = n.doubleValue();
                if (v >= 1_000_000) return trim(v / 1_000_000) + "M";
                if (v >= 1_000) return trim(v / 1_000) + "K";
                return String.valueOf((long) v);
            }
            @Override public Number fromString(String s) { return 0; }
        });
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private void renderArea(Snapshot d) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Sales");
        for (int i = 0; i < d.labels().size(); i++)
            s.getData().add(new XYChart.Data<>(d.labels().get(i), d.values().get(i)));
        areaChart.getData().setAll(s);

        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> p : s.getData())
                if (p.getNode() != null)
                    Tooltip.install(p.getNode(), new Tooltip(
                            p.getXValue() + ": LKR " + String.format("%,.2f", p.getYValue().doubleValue())));
        });
    }

    private void renderDonut(Snapshot d) {
        List<CategoryShare> cats = d.categories();
        long total = cats.stream().mapToLong(CategoryShare::units).sum();
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        legendBox.getChildren().clear();

        for (int i = 0; i < cats.size(); i++) {
            CategoryShare c = cats.get(i);
            data.add(new PieChart.Data(c.name(), c.units()));

            Circle dot = new Circle(6, Color.web(COLORS[i % COLORS.length]));
            Label name = new Label(c.name());
            name.getStyleClass().add("legend-name");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label pct = new Label(Math.round(100.0 * c.units() / total) + "%");
            pct.getStyleClass().add("legend-pct");
            HBox row = new HBox(10, dot, name, sp, pct);
            row.setAlignment(Pos.CENTER_LEFT);
            legendBox.getChildren().add(row);
        }
        if (cats.isEmpty()) {
            Label none = new Label("No stock data yet");
            none.getStyleClass().add("legend-name");
            legendBox.getChildren().add(none);
        }

        pieChart.setData(data);
        lblDonutTotal.setText(String.format("%,d", total));

        Platform.runLater(() -> {
            for (int i = 0; i < data.size(); i++) {
                Node n = data.get(i).getNode();
                if (n != null) n.setStyle("-fx-pie-color: " + COLORS[i % COLORS.length] + ";");
            }
        });
    }

    //  NAVIGATION
    @FXML
    private void navigate(ActionEvent e) {
        goTo((String) ((Node) e.getSource()).getUserData());
    }

    @FXML
    private void quickAction(ActionEvent e) {
        goTo((String) ((Node) e.getSource()).getUserData());
    }

    private void goTo(String page) {
        for (Toggle t : navGroup.getToggles())
            if (page.equals(t.getUserData())) { navGroup.selectToggle(t); break; }

        boolean dash = "DashBoard".equals(page);
        Node view = dash ? dashboardPane : loadPage(page);

        contentHost.getChildren().removeIf(n -> n != dashboardPane);
        dashboardPane.setVisible(dash);
        dashboardPane.setManaged(dash);
        if (!dash) contentHost.getChildren().add(view);
    }

    /* "Payment" -> /view/PaymentPage.fxml */
    private Node loadPage(String page) {
        URL url = getClass().getResource(PAGE_FOLDER + page + "Page.fxml");
        if (url != null) {
            try { return FXMLLoader.load(url); }
            catch (Exception ex) { ex.printStackTrace(); }
        }
        Label l = new Label(page + " page – coming soon");
        l.getStyleClass().add("h1");
        return new StackPane(l);
    }
}