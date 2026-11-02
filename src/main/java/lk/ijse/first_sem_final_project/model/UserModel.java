package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.UserDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class UserModel {

    public boolean saveUser(UserDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?)",
                dto.getUserId(),
                dto.getUsername(),
                dto.getPasswordHash(),
                dto.getFullName(),
                dto.getPhone(),
                dto.getRole(),
                dto.getStatus());
    }

    public boolean updateUser(UserDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE users SET username = ?, password_hash = ?, full_name = ?, phone = ?, role = ?, status = ? WHERE user_id = ?",
                dto.getUsername(),
                dto.getPasswordHash(),
                dto.getFullName(),
                dto.getPhone(),
                dto.getRole(),
                dto.getStatus(),
                dto.getUserId());
    }

    public boolean deleteUser(String userId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM users WHERE user_id = ?", userId);
    }

    public UserDto searchUser(String userId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM users WHERE user_id = ?", userId);
        if (rs.next()) {
            return new UserDto(
                    rs.getString("user_id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("role"),
                    rs.getString("status")
            );
        }
        return null;
    }

    public ArrayList<UserDto> getAllUsers() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM users");
        ArrayList<UserDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new UserDto(
                    rs.getString("user_id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("role"),
                    rs.getString("status")
            ));
        }
        return list;
    }

    public String getNextUserId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT user_id FROM users ORDER BY user_id DESC LIMIT 1");
        char prefix = 'U';
        if (rs.next()) {
            String lastId = rs.getString("user_id");
            int nextId = Integer.parseInt(lastId.substring(1)) + 1;
            return String.format(prefix + "%03d", nextId);
        }
        return prefix + "001";
    }
}

