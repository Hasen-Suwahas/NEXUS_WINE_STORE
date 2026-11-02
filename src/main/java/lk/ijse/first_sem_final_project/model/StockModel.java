package lk.ijse.first_sem_final_project.model;

import java.time.LocalDateTime;

import lk.ijse.first_sem_final_project.dto.StockDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class StockModel {

    public boolean saveStock(StockDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO stock VALUES (?, ?, ?, ?)",
                dto.getStockId(),
                dto.getProductId(),
                dto.getQuantity(),
                dto.getLastUpdated());
    }

    public boolean updateStock(StockDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE stock SET product_id = ?, quantity = ?, last_updated = ? WHERE stock_id = ?",
                dto.getProductId(),
                dto.getQuantity(),
                dto.getLastUpdated(),
                dto.getStockId());
    }

    public boolean deleteStock(String stockId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM stock WHERE stock_id = ?", stockId);
    }

    public StockDto searchStock(String stockId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM stock WHERE stock_id = ?", stockId);
        if (rs.next()) {
            return new StockDto(
                    rs.getString("stock_id"),
                    rs.getString("product_id"),
                    rs.getInt("quantity"),
                    rs.getTimestamp("last_updated").toLocalDateTime()
            );
        }
        return null;
    }

    public ArrayList<StockDto> getAllStocks() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM stock");
        ArrayList<StockDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new StockDto(
                    rs.getString("stock_id"),
                    rs.getString("product_id"),
                    rs.getInt("quantity"),
                    rs.getTimestamp("last_updated").toLocalDateTime()
            ));
        }
        return list;
    }

    public String getNextStockId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT stock_id FROM stock ORDER BY stock_id DESC LIMIT 1");
        if (rs.next()) {
            String lastId = rs.getString("stock_id");
            int nextId = Integer.parseInt(lastId.replaceAll("\\D+", "")) + 1;
            return String.format("ST%03d", nextId);
        }
        return "ST001";
    }
}
