module lk.ijse.first_sem_final_project {
    requires javafx.controls;
    requires static lombok;
    requires javafx.fxml;
    requires java.sql;

    opens lk.ijse.first_sem_final_project to javafx.fxml;
    opens lk.ijse.first_sem_final_project.controller to javafx.fxml;

    exports lk.ijse.first_sem_final_project;
    exports lk.ijse.first_sem_final_project.controller;
}