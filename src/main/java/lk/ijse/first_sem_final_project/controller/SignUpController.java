package lk.ijse.first_sem_final_project.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/*
 *   users(user_id, full_name, username, password_hash, role, phone, status)
 * Password is saved as SHA-256 hex (LoginController already accepts this).
 * All JDBC code is inside this class
 */
public class SignUpController {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";
    private static final String LOGIN_FXML = "/view/LoginPage.fxml";
    private static final String DEFAULT_ROLE = "Cashier";   // role given to every new account
    private static final String FIRST_USER_ROLE = "Admin";  // the very first account becomes Admin

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{4,20}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{9,12}$");

    @FXML private StackPane root;
    @FXML private HBox nameBox, userBox, phoneBox, passBox, confirmBox;
    @FXML private TextField txtFullName, txtUsername, txtPhone, txtPasswordVisible, txtConfirmVisible;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private Button btnEye, btnSignup;
    @FXML private Label lblMessage;

    private double dragX, dragY;

    @FXML
    public void initialize() {
        // the hidden/visible fields share the same text
        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        txtConfirmVisible.textProperty().bindBidirectional(txtConfirm.textProperty());

        bindFocus(txtFullName, nameBox);
        bindFocus(txtUsername, userBox);
        bindFocus(txtPhone, phoneBox);
        bindFocus(txtPassword, passBox);
        bindFocus(txtPasswordVisible, passBox);
        bindFocus(txtConfirm, confirmBox);
        bindFocus(txtConfirmVisible, confirmBox);

        Platform.runLater(() -> txtFullName.requestFocus());
    }

    private void bindFocus(TextField field, HBox box) {
        field.focusedProperty().addListener((o, was, is) -> {
            if (is) { if (!box.getStyleClass().contains("focused")) box.getStyleClass().add("focused"); }
            else box.getStyleClass().remove("focused");
        });
    }

    //  SIGN UP
    @FXML
    private void onSignUp() {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String phone    = txtPhone.getText().trim();
        String password = txtPassword.getText();
        String confirm  = txtConfirm.getText();

        String problem = validate(fullName, username, phone, password, confirm);
        if (problem != null) { showMessage(problem, false); return; }

        hideMessage();
        btnSignup.setDisable(true);
        btnSignup.setText("CREATING...");

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception { return register(fullName, username, phone, password); }
        };
        task.setOnSucceeded(e -> {
            btnSignup.setDisable(false);
            btnSignup.setText("SIGN UP");
            String error = task.getValue();
            if (error != null) { showMessage(error, false); return; }

            showMessage("Account created successfully! Redirecting to login...", true);
            btnSignup.setDisable(true);
            PauseTransition wait = new PauseTransition(Duration.seconds(1.6));
            wait.setOnFinished(ev -> onBackToLogin());
            wait.play();
        });
        task.setOnFailed(e -> {
            btnSignup.setDisable(false);
            btnSignup.setText("SIGN UP");
            task.getException().printStackTrace();
            showMessage("Database error: " + task.getException().getMessage(), false);
        });
        Thread t = new Thread(task, "signup-task");
        t.setDaemon(true);
        t.start();
    }

    private String validate(String fullName, String username, String phone, String password, String confirm) {
        if (fullName.length() < 3)                return "Please enter your full name.";
        if (!USERNAME.matcher(username).matches()) return "Username must be 4-20 characters (letters, numbers, _ or .).";
        if (!PHONE.matcher(phone).matches())       return "Please enter a valid phone number (9-12 digits).";
        if (password.length() < 6)                 return "Password must be at least 6 characters.";
        if (!password.equals(confirm))             return "Passwords do not match.";
        return null;
    }

    /** returns null when the account was created, otherwise an error message */
    private String register(String fullName, String username, String phone, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            // 1) username must be unique
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return "That username is already taken. Please choose another.";
                }
            }

            // 2) first account in an empty system becomes Admin
            String role = DEFAULT_ROLE;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) role = FIRST_USER_ROLE;
            }

            // 3) insert
            String sql = "INSERT INTO users (full_name, username, password_hash, role, phone, status) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, fullName);
                ps.setString(2, username);
                ps.setString(3, sha256(password));
                ps.setString(4, role);
                ps.setString(5, phone);
                ps.setString(6, "ACTIVE");
                ps.executeUpdate();
            }
            return null;
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

    // NAVIGATION
    @FXML
    private void onBackToLogin() {
        try {
            Parent login = FXMLLoader.load(getClass().getResource(LOGIN_FXML));
            root.getScene().setRoot(login);          // same (transparent, undecorated) window
        } catch (Exception ex) {
            ex.printStackTrace();
            showMessage("Could not open the login page: " + ex.getMessage(), false);
        }
    }

    // UI HELPERS
    @FXML
    private void togglePassword() {
        boolean show = !txtPasswordVisible.isVisible();
        swap(txtPassword, txtPasswordVisible, show);
        swap(txtConfirm, txtConfirmVisible, show);
        btnEye.setText(show ? "🙈" : "👁");
    }

    private void swap(PasswordField hidden, TextField shown, boolean showText) {
        shown.setVisible(showText);
        shown.setManaged(showText);
        hidden.setVisible(!showText);
        hidden.setManaged(!showText);
    }

    private void showMessage(String msg, boolean success) {
        lblMessage.setText(msg);
        lblMessage.setStyle(success ? "-fx-text-fill: #7ee787;" : "");
        lblMessage.setVisible(true);
        lblMessage.setManaged(true);
    }

    private void hideMessage() {
        lblMessage.setVisible(false);
        lblMessage.setManaged(false);
    }

    @FXML private void onMinimize() { ((Stage) root.getScene().getWindow()).setIconified(true); }
    @FXML private void onClose()    { Platform.exit(); }

    @FXML private void onWindowPress(MouseEvent e) { dragX = e.getSceneX(); dragY = e.getSceneY(); }
    @FXML private void onWindowDrag(MouseEvent e) {
        Stage s = (Stage) root.getScene().getWindow();
        s.setX(e.getScreenX() - dragX);
        s.setY(e.getScreenY() - dragY);
    }
}