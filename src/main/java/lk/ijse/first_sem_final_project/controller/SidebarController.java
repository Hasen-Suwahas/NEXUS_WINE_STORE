package lk.ijse.first_sem_final_project.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import lk.ijse.first_sem_final_project.util.Navigator;
import lk.ijse.first_sem_final_project.util.Session;

/** Controller of Sidebar.fxml – shared by the dashboard and every Manage page. */
public class SidebarController {

    @FXML private ToggleGroup navGroup;
    @FXML private Label lblAvatar, lblUserName, lblUserRole;

    private String active = "DashBoard";

    @FXML
    public void initialize() { refreshUser(); }

    /** shows the logged-in user (name + role) at the bottom of the sidebar */
    public void refreshUser() {
        String name = Session.getName();
        lblUserName.setText(name);
        lblUserRole.setText(Session.getRole().isBlank() ? "Online" : Session.getRole());
        lblAvatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
    }

    /** highlights the button of the page that is open (called by each page controller) */
    public void setActive(String page) {
        active = page;
        for (Toggle t : navGroup.getToggles())
            if (page.equals(t.getUserData())) { navGroup.selectToggle(t); break; }
    }

    @FXML
    private void navigate(ActionEvent e) {
        ToggleButton btn = (ToggleButton) e.getSource();
        String page = (String) btn.getUserData();
        if (page.equals(active)) { btn.setSelected(true); return; }   // already here
        if (!Navigator.go(btn, page)) setActive(active);              // page missing -> keep old highlight
    }
}