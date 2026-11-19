package lk.ijse.first_sem_final_project.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ForgotPasswordController {

    private static final String LOGIN_FXML = "view/Login.fxml";   // <- change if your login fxml has another name
    private static final int MAX_ATTEMPTS = 5;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private static final String ERR_STYLE = "-fx-text-fill: #e0786b;";
    private static final String OK_STYLE  = "-fx-text-fill: #7fc98f;";

    @FXML private VBox step1, step2;
    @FXML private Label lblTitle, lblHint, lblMessage;
    @FXML private TextField txtUsername, txtPhone;
    @FXML private PasswordField txtNewPassword, txtConfirmPassword;
    @FXML private Button btnVerify, btnReset;

    private int verifiedUserId = -1;
    private int failedAttempts = 0;

    @FXML
    public void initialize() {
        Platform.runLater(() -> txtUsername.requestFocus());
    }

    // ---------------- STEP 1 : verify ----------------

    @FXML
    private void onVerify() {
        if (failedAttempts >= MAX_ATTEMPTS) {
            showMessage("Too many wrong attempts. Please contact your administrator.", true);
            return;
        }
        String username = txtUsername.getText().trim();
        String phone = txtPhone.getText().trim();
        if (username.isEmpty() || phone.isEmpty()) {
            showMessage("Please enter your username and phone number.", true);
            return;
        }

        hideMessage();
        btnVerify.setDisable(true);
        btnVerify.setText("CHECKING...");

        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws Exception { return findUser(username, phone); }
        };
        task.setOnSucceeded(e -> {
            btnVerify.setDisable(false);
            btnVerify.setText("VERIFY");
            int id = task.getValue();
            if (id > 0) {
                verifiedUserId = id;
                showStep2();
            } else if (id == -2) {
                showMessage("This account is inactive. Please contact the admin.", true);
            } else {
                failedAttempts++;
                showMessage("Username and phone number do not match.", true);
            }
        });
        task.setOnFailed(e -> {
            btnVerify.setDisable(false);
            btnVerify.setText("VERIFY");
            task.getException().printStackTrace();
            showMessage("Cannot connect to the database. Please try again.", true);
        });
        startThread(task, "forgot-verify");
    }

    /** returns user_id when username + phone match, -1 when not, -2 when the account is inactive */
    private int findUser(String username, String phone) throws SQLException {
        String sql = "SELECT user_id, phone, status FROM users WHERE username = ?";
        try (Connection c = lk.ijse.first_sem_final_project.db.DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1;
                if (!samePhone(phone, rs.getString("phone"))) return -1;

                String status = rs.getString("status");
                if (status != null && !status.equalsIgnoreCase("ACTIVE") && !status.equals("1")) return -2;
                return rs.getInt("user_id");
            }
        }
    }

    /** compares the last 9 digits, so 0771234567 and +94771234567 count as the same number */
    private static boolean samePhone(String typed, String stored) {
        String a = digits(typed), b = digits(stored);
        if (a.length() < 9 || b.length() < 9) return false;
        return a.substring(a.length() - 9).equals(b.substring(b.length() - 9));
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private void showStep2() {
        hideMessage();
        step1.setVisible(false);  step1.setManaged(false);
        step2.setVisible(true);   step2.setManaged(true);
        lblTitle.setText("CREATE NEW PASSWORD");
        lblHint.setText("Choose a new password with at least " + MIN_PASSWORD_LENGTH + " characters.");
        Platform.runLater(() -> txtNewPassword.requestFocus());
    }

    // ---------------- STEP 2 : save new password ----------------

    @FXML
    private void onReset() {
        if (verifiedUserId <= 0) return;

        String p1 = txtNewPassword.getText();
        String p2 = txtConfirmPassword.getText();
        if (p1.length() < MIN_PASSWORD_LENGTH) {
            showMessage("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.", true);
            return;
        }
        if (!p1.equals(p2)) {
            showMessage("The two passwords do not match.", true);
            return;
        }

        hideMessage();
        btnReset.setDisable(true);
        btnReset.setText("SAVING...");
        final int userId = verifiedUserId;

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception { savePassword(userId, p1); return null; }
        };
        task.setOnSucceeded(e -> {
            verifiedUserId = -1;
            btnReset.setText("DONE");
            showMessage("Password changed. Taking you to the login page...", false);
            PauseTransition wait = new PauseTransition(Duration.seconds(1.5));
            wait.setOnFinished(ev -> onBackToLogin());
            wait.play();
        });
        task.setOnFailed(e -> {
            btnReset.setDisable(false);
            btnReset.setText("RESET PASSWORD");
            task.getException().printStackTrace();
            showMessage("Could not save the new password. Please try again.", true);
        });
        startThread(task, "forgot-reset");
    }

    private void savePassword(int userId, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection c = lk.ijse.first_sem_final_project.db.DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sha256(newPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    // ---------------- back to login ----------------

    @FXML
    private void onBackToLogin() {
        try {
            Stage current = (Stage) lblTitle.getScene().getWindow();
            Parent login = FXMLLoader.load(getClass().getResource("/view/Login.fxml"));

            Stage stage = new Stage();
            stage.initStyle(StageStyle.UNDECORATED);   // the login page has its own window buttons
            stage.setTitle("Nexus Wine Store - Login");
            stage.setScene(new Scene(login, 1000, 640));
            stage.show();
            stage.centerOnScreen();
            current.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            showMessage("Could not open the login page: " + ex.getMessage(), true);
        }
    }

    // ---------------- helpers ----------------

    private void showMessage(String msg, boolean error) {
        lblMessage.setText(msg);
        lblMessage.setStyle(error ? ERR_STYLE : OK_STYLE);
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
    }

    private void hideMessage() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
    }

    private void startThread(Task<?> task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }
}
