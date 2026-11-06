package lk.ijse.first_sem_final_project.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/*
-   users(user_id, full_name, username, password_hash, role, phone, status)
- All JDBC code is inside this class
 */
public class LoginController {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/nexus_vain_store";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";
    private static final String DASHBOARD_FXML = "/view/DashBoardPage.fxml";
    private static final String SIGNUP_FXML    = "/view/SignUpPage.fxml"; // <- Sign Up FXML path එක (අවශ්‍ය නම් වෙනස් කරගන්න)
    private static final String SIDE_IMAGE = "/image/login_side.jpg";   // optional bottle/wine photo

    @FXML private StackPane root;
    @FXML private Region photo;
    @FXML private HBox userBox, passBox;
    @FXML private TextField txtUsername, txtPasswordVisible;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin, btnSignUp, btnEye;
    @FXML private Label lblError;

    private double dragX, dragY;

    private record LoginResult(boolean ok, String message, int userId, String fullName, String role) {}

    @FXML
    public void initialize() {
        // photo on the left
        URL img = getClass().getResource(SIDE_IMAGE);
        if (img != null) {
            photo.setStyle("-fx-background-image: url('" + img.toExternalForm() + "');"
                    + "-fx-background-size: cover; -fx-background-position: center;");
        }

        // password and visible-password fields share one text
        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());

        // gold glow on the focused input
        bindFocus(txtUsername, userBox);
        bindFocus(txtPassword, passBox);
        bindFocus(txtPasswordVisible, passBox);

        Platform.runLater(() -> txtUsername.requestFocus());
    }

    private void bindFocus(TextField field, HBox box) {
        field.focusedProperty().addListener((o, was, is) -> {
            if (is) { if (!box.getStyleClass().contains("focused")) box.getStyleClass().add("focused"); }
            else box.getStyleClass().remove("focused");
        });
    }

    // LOGIN
    @FXML
    private void onLogin() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter your username and password.");
            return;
        }
        hideError();
        btnLogin.setDisable(true);
        btnLogin.setText("SIGNING IN...");

        Task<LoginResult> task = new Task<>() {
            @Override protected LoginResult call() throws Exception { return authenticate(username, password); }
        };
        task.setOnSucceeded(e -> {
            btnLogin.setDisable(false);
            btnLogin.setText("LOGIN");
            LoginResult r = task.getValue();
            if (r.ok()) openDashboard(r);
            else { showError(r.message()); txtPassword.clear(); txtPassword.requestFocus(); }
        });
        task.setOnFailed(e -> {
            btnLogin.setDisable(false);
            btnLogin.setText("LOGIN");
            task.getException().printStackTrace();
            showError("Cannot connect to the database. Please check the connection settings.");
        });
        Thread t = new Thread(task, "login-task");
        t.setDaemon(true);
        t.start();
    }

    private LoginResult authenticate(String username, String password) throws SQLException {
        String sql = "SELECT user_id, full_name, role, password_hash, status FROM users WHERE username = ?";

        try (Connection c = lk.ijse.first_sem_final_project.db.DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return fail("Invalid username or password.");

                String status = rs.getString("status");
                if (status != null && !status.equalsIgnoreCase("ACTIVE") && !status.equals("1")) {
                    return fail("This account is inactive. Please contact the admin.");
                }
                if (!passwordMatches(password, rs.getString("password_hash"))) {
                    return fail("Invalid username or password.");
                }
                return new LoginResult(true, "", rs.getInt("user_id"), rs.getString("full_name"), rs.getString("role"));
            }
        }
    }
    private LoginResult fail(String msg) { return new LoginResult(false, msg, 0, null, null); }

    private boolean passwordMatches(String input, String stored) {
        if (stored == null) return false;
        return stored.equals(input) || stored.equalsIgnoreCase(sha256(input));
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    // OPEN DASHBOARD
    private void openDashboard(LoginResult user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(DASHBOARD_FXML));
            Parent dash = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Nexus Wine Store");
            stage.setScene(new Scene(dash, 1280, 860));
            stage.setMinWidth(1100);
            stage.setMinHeight(760);

            Object c = loader.getController();
            if (c instanceof DashBoardPageController dc) {
                dc.setCurrentUser(user.fullName(), user.role());
                stage.setOnCloseRequest(e -> dc.shutdown());
            }
            stage.show();
            stage.centerOnScreen();

            ((Stage) root.getScene().getWindow()).close();      // close login window
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Could not open the dashboard: " + ex.getMessage());
        }
    }

    // ===================== SIGN UP ACTION =====================
    @FXML
    private void onSignUp() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(SIGNUP_FXML));
            Parent signup = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Nexus Wine Store - Sign Up");
            stage.setScene(new Scene(signup, 1000, 640));
            stage.show();
            stage.centerOnScreen();

            ((Stage) root.getScene().getWindow()).close();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Could not open the Sign Up window: " + ex.getMessage());
        }
    }

    // UI HELPERS
    @FXML
    private void togglePassword() {
        boolean show = !txtPasswordVisible.isVisible();
        txtPasswordVisible.setVisible(show);
        txtPasswordVisible.setManaged(show);
        txtPassword.setVisible(!show);
        txtPassword.setManaged(!show);
        btnEye.setText(show ? "🙈" : "👁");
        (show ? txtPasswordVisible : txtPassword).requestFocus();
        (show ? txtPasswordVisible : txtPassword).end();
    }

    @FXML
    private void onContactAdmin() {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Please contact your system administrator to create an account.");
        a.setHeaderText("Contact Admin");
        a.show();
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    //  WINDOW (undecorated stage)
    @FXML private void onMinimize() { ((Stage) root.getScene().getWindow()).setIconified(true); }
    @FXML private void onClose()    { Platform.exit(); }

    @FXML private void onWindowPress(MouseEvent e) { dragX = e.getSceneX(); dragY = e.getSceneY(); }
    @FXML private void onWindowDrag(MouseEvent e) {
        Stage s = (Stage) root.getScene().getWindow();
        s.setX(e.getScreenX() - dragX);
        s.setY(e.getScreenY() - dragY);
    }
}