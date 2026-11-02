package lk.ijse.first_sem_final_project.model;

import java.time.LocalDate;

import lk.ijse.first_sem_final_project.dto.SaleDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class SaleModel {

    public boolean saveSale(SaleDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO sales VALUES (?, ?, ?, ?, ?, ?)",
                dto.getSaleId(),
                dto.getCustomerId(),
                dto.getUserId(),
                dto.getSaleDate(),
                dto.getTotalAmount(),
                dto.getOrderStatus());
    }

    public boolean updateSale(SaleDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE sales SET customer_id = ?, user_id = ?, sale_date = ?, total_amount = ?, order_status = ? WHERE sale_id = ?",
                dto.getCustomerId(),
                dto.getUserId(),
                dto.getSaleDate(),
                dto.getTotalAmount(),
                dto.getOrderStatus(),
                dto.getSaleId());
    }

    public boolean deleteSale(String saleId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM sales WHERE sale_id = ?", saleId);
    }

    public SaleDto searchSale(String saleId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM sales WHERE sale_id = ?", saleId);
        if (rs.next()) {
            return new SaleDto(
                    rs.getString("sale_id"),
                    rs.getString("customer_id"),
                    rs.getString("user_id"),
                    rs.getTimestamp("sale_date").toLocalDateTime(),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("order_status")

            );
        }
        return null;
    }
    public ArrayList<SaleDto> getAllSales() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM sales");
        ArrayList<SaleDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new SaleDto(
                    rs.getString("sale_id"),
                    rs.getString("customer_id"),
                    rs.getString("user_id"),
                    rs.getTimestamp("sale_date").toLocalDateTime(),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("order_status")
            ));
        }
        return list;
    }
    public String getNextSaleId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT sale_id FROM sales ORDER BY sale_id DESC LIMIT 1");
        if (rs.next()) {
            String lastId = rs.getString("sale_id");
            int nextId = Integer.parseInt(lastId.replaceAll("\\D+", "")) + 1;
            return String.format("S%03d", nextId);
        }
        return "S001";
    }
}