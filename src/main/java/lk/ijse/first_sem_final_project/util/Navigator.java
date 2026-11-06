package lk.ijse.first_sem_final_project.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;

import java.net.URL;
import java.util.Map;

/**
 * Opens a page by replacing the root of the current scene (same window).
 * Page keys are the userData values used by the sidebar buttons.
 */
public final class Navigator {

    private static final Map<String, String> PAGES = Map.ofEntries(
            Map.entry("DashBoard", "/view/DashBoardPage.fxml"),
            Map.entry("Product",   "/view/ProductPage.fxml"),
            Map.entry("Category",  "/view/CategoryPage.fxml"),
            Map.entry("Stock",     "/view/StockPage.fxml"),
            Map.entry("Purchase",  "/view/PurchasePage.fxml"),
            Map.entry("Sale",      "/view/SalePage.fxml"),
            Map.entry("Customer",  "/view/CustomerPage.fxml"),
            Map.entry("Supplier",  "/view/SupplierPage.fxml"),
            Map.entry("Payment",   "/view/PaymentPage.fxml"),
            Map.entry("User",      "/view/UserPage.fxml"),
            Map.entry("Setting",   "/view/SettingPage.fxml"));

    private Navigator() {}

    /** @return true when the page was opened */
    public static boolean go(Node from, String page) {
        if (from == null || from.getScene() == null) return false;

        String path = PAGES.get(page);
        URL url = path == null ? null : Navigator.class.getResource(path);
        if (url == null) {
            alert(from, "Coming soon", "The \"" + page + "\" page has not been created yet.");
            return false;
        }
        try {
            Parent root = FXMLLoader.load(url);
            from.getScene().setRoot(root);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            alert(from, "Could not open page", String.valueOf(ex.getMessage()));
            return false;
        }
    }

    private static void alert(Node from, String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text);
        a.setHeaderText(header);
        if (from.getScene().getWindow() != null) a.initOwner(from.getScene().getWindow());
        a.show();
    }
}