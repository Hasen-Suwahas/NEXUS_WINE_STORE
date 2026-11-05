package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.PurchaseItemDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class PurchaseItemModel {

    public boolean savePurchaseItem(PurchaseItemDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO purchase_items (purchase_id, product_id, quantity, unit_cost, subtotal) VALUES (?, ?, ?, ?, ?)",
                dto.getPurchaseId(),
                dto.getProductId(),
                dto.getQuantity(),
                dto.getUnitCost(),
                dto.getSubtotal());
    }

    public ArrayList<PurchaseItemDto> getPurchaseItemsByPurchaseId(int purchaseId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM purchase_items WHERE purchase_id = ?", purchaseId);
        ArrayList<PurchaseItemDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new PurchaseItemDto(
                    rs.getInt("purchase_item_id"),
                    rs.getInt("purchase_id"),
                    rs.getInt("product_id"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_cost"),
                    rs.getBigDecimal("subtotal")
            ));
        }
        return list;
    }
}