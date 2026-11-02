package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.ProductDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class ProductModel {

    public boolean saveProduct(ProductDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO products VALUES (?, ?, ?, ?, ?, ?, ?)",
                dto.getProductId(),
                dto.getCategoryId(),
                dto.getProductName(),
                dto.getDescription(),
                dto.getCostPrice(),
                dto.getSellingPrice(),
                dto.getReorderLevel());
    }

    public boolean updateProduct(ProductDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE products SET category_id = ?, product_name = ?, description = ?, cost_price = ?, selling_price = ?, reorder_level = ? WHERE product_id = ?",
                dto.getCategoryId(),
                dto.getProductName(),
                dto.getDescription(),
                dto.getCostPrice(),
                dto.getSellingPrice(),
                dto.getReorderLevel(),
                dto.getProductId());
    }

    public boolean deleteProduct(String productId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM products WHERE product_id = ?", productId);
    }

    public ProductDto searchProduct(String productId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM products WHERE product_id = ?", productId);
        if (rs.next()) {
            return new ProductDto(
                    rs.getString("product_id"),
                    rs.getString("category_id"),
                    rs.getString("product_name"),
                    rs.getString("description"),
                    rs.getBigDecimal("cost_price"),
                    rs.getBigDecimal("selling_price"),
                    rs.getInt("reorder_level")
            );
        }
        return null;
    }

    public ArrayList<ProductDto> getAllProducts() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM products");
        ArrayList<ProductDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new ProductDto(
                    rs.getString("product_id"),
                    rs.getString("category_id"),
                    rs.getString("product_name"),
                    rs.getString("description"),
                    rs.getBigDecimal("cost_price"),
                    rs.getBigDecimal("selling_price"),
                    rs.getInt("reorder_level")
            ));
        }
        return list;
    }

    public String getNextProductId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT product_id FROM products ORDER BY product_id DESC LIMIT 1");
        char prefix = 'P';
        if (rs.next()) {
            String lastId = rs.getString("product_id");
            int nextId = Integer.parseInt(lastId.substring(1)) + 1;
            return String.format(prefix + "%03d", nextId);
        }
        return prefix + "001";
    }
}
