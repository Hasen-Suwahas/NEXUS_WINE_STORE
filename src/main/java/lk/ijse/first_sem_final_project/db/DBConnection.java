package lk.ijse.first_sem_final_project.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static DBConnection dbConnection;
    private Connection connection;

    private String URL = "jdbc:mysql://localhost:3306/nexus_wine_store";
    private String USER = "root";
    private String PASSWORD = "ijse@1234";

    private DBConnection() throws SQLException {
        this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static synchronized DBConnection getInstance() throws SQLException {
        if (dbConnection == null || dbConnection.getConnection() == null || dbConnection.getConnection().isClosed()) {
            dbConnection = new DBConnection();
        }
        return dbConnection;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return connection;
    }
}