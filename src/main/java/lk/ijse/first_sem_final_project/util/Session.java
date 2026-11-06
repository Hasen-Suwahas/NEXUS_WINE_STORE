package lk.ijse.first_sem_final_project.util;

/** Remembers who is logged in so every page's sidebar can show the user. */
public final class Session {
    private static String name = "Admin";
    private static String role = "Admin";

    private Session() {}

    public static void set(String fullName, String userRole) {
        name = (fullName == null || fullName.isBlank()) ? "Admin" : fullName;
        role = userRole == null ? "" : userRole;
    }

    public static String getName() { return name; }
    public static String getRole() { return role; }
}