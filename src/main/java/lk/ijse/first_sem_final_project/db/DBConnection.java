package lk.ijse.first_sem_final_project.db;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static DBConnection dbConnection;
    private final Connection connection;

    private String url = "jdbc:mysql://localhost:3306/nexus_vain_store?";
    private String user ="root";
    private String password = "ijse@1234";

    private DBConnection() throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        connection = DriverManager.getConnection(url, user, password);

    }
    public static DBConnection getInstance() throws SQLException {
        return dbConnection == null ? dbConnection = new DBConnection() : dbConnection;

    }
    public Connection getConnection() {
        return connection;

    }
}