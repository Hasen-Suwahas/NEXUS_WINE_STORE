package lk.ijse.first_sem_final_project.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;


public class ScanController {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private static final String OK_COLOR  = "#e0a93b";
    private static final String ERR_COLOR = "#e5604d";
    private static final int MAX_RECENT = 5;

    private record Bottle(String name, String category, String barcode, double price, int stock) {}

    @FXML private TextField txtScan;
    @FXML private Label lblStatus, lblName, lblMeta, lblPrice, lblStock;
    @FXML private VBox recentBox;

    private final Deque<String> recent = new ArrayDeque<>();

    @FXML
    public void initialize() {
        clearCard();
        renderRecent();
        setStatus("Ready – scan a bottle", OK_COLOR);
        Platform.runLater(txtScan::requestFocus);
    }

    /** Fired when the scanner sends ENTER (or you type a code and press ENTER). */
    @FXML
    private void onScan(ActionEvent e) {
        String code = txtScan.getText() == null ? "" : txtScan.getText().trim();
        txtScan.clear();
        if (code.isEmpty()) return;

        Task<Bottle> task = new Task<>() {
            @Override protected Bottle call() throws Exception { return findBottle(code); }
        };
        task.setOnSucceeded(ev -> {
            Bottle b = task.getValue();
            if (b == null) {
                clearCard();
                setStatus("✖ Not found: " + code, ERR_COLOR);
            } else {
                showBottle(b);
                addRecent(b);
            }
            txtScan.requestFocus();
        });
        task.setOnFailed(ev -> {
            task.getException().printStackTrace();
            setStatus("⚠ Database error", ERR_COLOR);
            txtScan.requestFocus();
        });
        Thread t = new Thread(task, "quick-scan-lookup");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void clearRecent(ActionEvent e) {
        recent.clear();
        renderRecent();
        clearCard();
        setStatus("Ready – scan a bottle", OK_COLOR);
        txtScan.requestFocus();
    }

    // ---------------- database ----------------

    private Connection getConnection() throws SQLException {
        // If you already have a DBConnection class use: return DBConnection.getInstance().getConnection();
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private Bottle findBottle(String code) throws SQLException {
        String sql = "SELECT p.product_name, p.unit_price, p.barcode, cat.category_name, "
                + "COALESCE(SUM(s.quantity),0) qty "
                + "FROM products p "
                + "LEFT JOIN categories cat ON cat.category_id = p.category_id "
                + "LEFT JOIN stock s ON s.product_id = p.product_id "
                + "WHERE p.barcode = ? "
                + "GROUP BY p.product_id, p.product_name, p.unit_price, p.barcode, cat.category_name";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Bottle(rs.getString("product_name"), rs.getString("category_name"),
                        rs.getString("barcode"), rs.getDouble("unit_price"), rs.getInt("qty"));
            }
        }
    }

    // ---------------- ui ----------------

    private void showBottle(Bottle b) {
        lblName.setText(b.name());
        lblMeta.setText((b.category() == null ? "No category" : b.category()) + "  •  " + b.barcode());
        lblPrice.setText("LKR " + String.format("%,.2f", b.price()));
        boolean out = b.stock() <= 0;
        lblStock.setText(out ? "Out of stock" : b.stock() + " in stock");
        lblStock.setStyle("-fx-text-fill: " + (out ? ERR_COLOR : OK_COLOR) + ";");
        setStatus(out ? "⚠ " + b.name() + " is out of stock" : "✔ Found", out ? ERR_COLOR : OK_COLOR);
    }

    private void clearCard() {
        lblName.setText("—");
        lblMeta.setText("");
        lblPrice.setText("LKR 0.00");
        lblStock.setText("");
    }

    private void addRecent(Bottle b) {
        recent.addFirst(b.name() + "  –  LKR " + String.format("%,.2f", b.price()));
        while (recent.size() > MAX_RECENT) recent.removeLast();
        renderRecent();
    }

    private void renderRecent() {
        recentBox.getChildren().clear();
        if (recent.isEmpty()) {
            Label none = new Label("No scans yet");
            none.getStyleClass().add("muted");
            recentBox.getChildren().add(none);
            return;
        }
        for (String line : recent) {
            Label l = new Label(line);
            l.getStyleClass().add("qs-recent");
            l.setWrapText(true);
            recentBox.getChildren().add(l);
        }
    }

    private void setStatus(String msg, String color) {
        lblStatus.setText(msg);
        lblStatus.setStyle("-fx-text-fill: " + color + ";");
    }
}