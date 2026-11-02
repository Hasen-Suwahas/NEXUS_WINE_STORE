package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.CategoryDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class CategoryModel {

    public boolean saveCategory(CategoryDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO categories VALUES (?, ?, ?)",
                dto.getCategoryId(),
                dto.getCategoryName(),
                dto.getDescription());
    }

    public boolean updateCategory(CategoryDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE categories SET category_name = ?, description = ? WHERE category_id = ?",
                dto.getCategoryName(),
                dto.getDescription(),
                dto.getCategoryId());
    }

    public boolean deleteCategory(String categoryId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM categories WHERE category_id = ?", categoryId);
    }

    public CategoryDto searchCategory(String categoryId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM categories WHERE category_id = ?", categoryId);
        if (rs.next()) {
            return new CategoryDto(
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("description")
            );
        }
        return null;
    }

    public ArrayList<CategoryDto> getAllCategories() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM categories");
        ArrayList<CategoryDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new CategoryDto(
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("description")
            ));
        }
        return list;
    }

    public String getNextCategoryId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT category_id FROM categories ORDER BY category_id DESC LIMIT 1");
        if (rs.next()) {
            String lastId = rs.getString("category_id");
            int nextId = Integer.parseInt(lastId.replaceAll("\\D+", "")) + 1;
            return String.format("CAT%03d", nextId);
        }
        return "CAT001";
    }
}