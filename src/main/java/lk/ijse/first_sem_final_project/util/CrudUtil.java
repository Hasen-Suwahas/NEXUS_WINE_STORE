package lk.ijse.first_sem_final_project.util;

import lk.ijse.first_sem_final_project.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CrudUtil {
    public static  <T> T execute(String sql , Object... obj) throws ClassNotFoundException ,SQLException {
        Connection connection = DBConnection.getInstance().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < obj.length; i++) {
            statement.setObject(i + 1, obj[i]);
        }
        if(sql.startsWith("SELECT") || !sql.endsWith("select")) {
            ResultSet resultSet = statement.executeQuery();
            return (T) resultSet;
        }else {
            int i = statement.executeUpdate();
            boolean isSuccess = i < 0;
            return (T) (Boolean) isSuccess;
        }
    }
}