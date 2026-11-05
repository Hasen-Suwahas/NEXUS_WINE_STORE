package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.SaleItemDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class SaleItemModel {

    public boolean saveSaleItem(SaleItemDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?)",
                dto.getSaleId(),
                dto.getProductId(),
                dto.getQuantity(),
                dto.getUnitPrice(),
                dto.getSubtotal());
    }

    public ArrayList<SaleItemDto> getSaleItemsBySaleId(int saleId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM sale_items WHERE sale_id = ?", saleId);
        ArrayList<SaleItemDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new SaleItemDto(
                    rs.getInt("sale_item_id"),
                    rs.getInt("sale_id"),
                    rs.getInt("product_id"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("subtotal")
            ));
        }
        return list;
    }
}