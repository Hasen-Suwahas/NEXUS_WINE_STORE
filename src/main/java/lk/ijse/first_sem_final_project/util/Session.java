package lk.ijse.first_sem_final_project.util;

/** Remembers who is logged in so every page's sidebar can show the user. */
public final class Session {
    private static Session instance;

    private int userId;
    private String name = "Admin";
    private String role = "Admin";

    private Session(int userId, String fullName, String userRole) {
        this.userId = userId;
        this.name = (fullName == null || fullName.isBlank()) ? "Admin" : fullName;
        this.role = userRole == null ? "" : userRole;
    }

    public static synchronized void set(int userId, String fullName, String userRole) {
        instance = new Session(userId, fullName, userRole);
    }

    // Overload in case other parts of your code still call the old 2-parameter version
    public static synchronized void set(String fullName, String userRole) {
        if (instance == null) {
            instance = new Session(1, fullName, userRole);
        } else {
            instance.name = (fullName == null || fullName.isBlank()) ? "Admin" : fullName;
            instance.role = userRole == null ? "" : userRole;
        }
    }

    public static synchronized Session getInstance() {
        if (instance == null) {
            instance = new Session(1, "Admin", "Admin"); // Default fallback session
        }
        return instance;
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getRole() { return role; }
}