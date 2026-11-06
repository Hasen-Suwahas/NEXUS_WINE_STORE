package lk.ijse.first_sem_final_project;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lk.ijse.first_sem_final_project.db.DBConnection;

public class AppInitializer extends Application {
    public static void main(String[] args) {
        launch(args);

    }
    @Override
    public void start(Stage stage) throws Exception {
        Parent load = FXMLLoader.load(getClass().getResource("/view/SupplierPage.fxml"));
        stage.setTitle("Nexus_Wine_Store");
        stage.setScene(new Scene(load));
        stage.setMaximized(true);
        stage.show();


    }
}