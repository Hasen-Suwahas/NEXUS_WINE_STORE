package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.PaymentDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class PaymentModel {

    public boolean savePayment(PaymentDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO payments VALUES (?, ?, ?, ?, ?, ?)",
                dto.getPaymentId(),
                dto.getSaleId(),
                dto.getAmountPaid(),
                dto.getPaymentDate(),
                dto.getPaymentMethod(),
                dto.getPaymentStatus());
    }

    public boolean updatePayment(PaymentDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE payments SET sale_id = ?, amount_paid = ?, payment_date = ?, payment_method = ?, payment_status = ? WHERE payment_id = ?",
                dto.getSaleId(),
                dto.getAmountPaid(),
                dto.getPaymentDate(),
                dto.getPaymentMethod(),
                dto.getPaymentStatus(),
                dto.getPaymentId());
    }

    public boolean deletePayment(String paymentId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM payments WHERE payment_id = ?", paymentId);
    }

    public PaymentDto searchPayment(String paymentId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM payments WHERE payment_id = ?", paymentId);
        if (rs.next()) {
            return new PaymentDto(
                    rs.getLong("payment_id"),
                    rs.getLong("sale_id"),
                    rs.getBigDecimal("amount_paid"),
                    rs.getTimestamp("payment_date").toLocalDateTime(),
                    rs.getString("payment_method"),
                    rs.getString("payment_status")
            );
        }
        return null;
    }

    public ArrayList<PaymentDto> getAllPayments() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM payments");
        ArrayList<PaymentDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new PaymentDto(
                    rs.getLong("payment_id"),
                    rs.getLong("sale_id"),
                    rs.getBigDecimal("amount_paid"),
                    rs.getTimestamp("payment_date").toLocalDateTime(),
                    rs.getString("payment_method"),
                    rs.getString("payment_status")
            ));
        }
        return list;
    }

    public String getNextPaymentId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT payment_id FROM payments ORDER BY payment_id DESC LIMIT 1");
        if (rs.next()) {
            String lastId = rs.getString("payment_id");
            int nextId = Integer.parseInt(lastId.replaceAll("\\D+", "")) + 1;
            return String.format("PAY%03d", nextId);
        }
        return "PAY001";
    }
}
