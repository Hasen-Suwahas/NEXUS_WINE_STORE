package lk.ijse.first_sem_final_project.model;

import java.time.LocalDate;
import lk.ijse.first_sem_final_project.dto.PurchaseDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class PurchaseModel {

    public boolean savePurchase(PurchaseDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO purchases VALUES (?, ?, ?, ?, ?)",
                dto.getPurchaseId(),
                dto.getSupplierId(),
                dto.getUserId(),
                dto.getPurchaseDate(),
                dto.getTotalAmount());
    }

    public boolean updatePurchase(PurchaseDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE purchases SET supplier_id = ?, user_id = ?, purchase_date = ?, total_amount = ? WHERE purchase_id = ?",
                dto.getSupplierId(),
                dto.getUserId(),
                dto.getPurchaseDate(),
                dto.getTotalAmount(),
                dto.getPurchaseId());
    }

    public boolean deletePurchase(String purchaseId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM purchases WHERE purchase_id = ?", purchaseId);
    }

    public PurchaseDto searchPurchase(String purchaseId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM purchases WHERE purchase_id = ?", purchaseId);
        if (rs.next()) {
            return new PurchaseDto(
                    rs.getString("purchase_id"),
                    rs.getString("supplier_id"),
                    rs.getString("user_id"),
                    rs.getDate("purchase_date").toLocalDate(),
                    rs.getBigDecimal("total_amount")
            );
        }
        return null;
    }

    public ArrayList<PurchaseDto> getAllPurchases() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM purchases");
        ArrayList<PurchaseDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new PurchaseDto(
                    rs.getString("purchase_id"),
                    rs.getString("supplier_id"),
                    rs.getString("user_id"),
                    rs.getDate("purchase_date").toLocalDate(),
                    rs.getBigDecimal("total_amount")
            ));
        }
        return list;
    }

    public String getNextPurchaseId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT purchase_id FROM purchases ORDER BY purchase_id DESC LIMIT 1");
        if (rs.next()) {
            String lastId = rs.getString("purchase_id");
            int nextId = Integer.parseInt(lastId.replaceAll("\\D+", "")) + 1;
            return String.format("PUR%03d", nextId);
        }
        return "PUR001";
    }
}
