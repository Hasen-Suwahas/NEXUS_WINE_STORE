package lk.ijse.first_sem_final_project.controller;

import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.Alert;

public class ReportController {

    @FXML
    private void onSalesReport(MouseEvent event) {
        showAlert("Sales Report", "Opening Sales Report summary...");
    }

    @FXML
    private void onPurchaseReport(MouseEvent event) {
        showAlert("Purchase Report", "Opening Purchase Report summary...");
    }

    @FXML
    private void onStockReport(MouseEvent event) {
        showAlert("Stock Report", "Opening Stock Report details...");
    }

    @FXML
    private void onCustomerReport(MouseEvent event) {
        showAlert("Customer Report", "Opening Customer Report details...");
    }

    @FXML
    private void onSupplierReport(MouseEvent event) {
        showAlert("Supplier Report", "Opening Supplier Report details...");
    }

    @FXML
    private void onProductReport(MouseEvent event) {
        showAlert("Product Report", "Opening Product Report details...");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}