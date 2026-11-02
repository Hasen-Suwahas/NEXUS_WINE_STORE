package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.CustomerDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class CustomerModel {

    public boolean saveCustomer(CustomerDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO customers VALUES (?, ?, ?, ?, ?)",
                dto.getCustomerId(),
                dto.getCustomerName(),
                dto.getPhone(),
                dto.getEmail(),
                dto.getAddress());
    }

    public boolean updateCustomer(CustomerDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE customers SET customer_name = ?, phone = ?, email = ?, address = ? WHERE customer_id = ?",
                dto.getCustomerName(),
                dto.getPhone(),
                dto.getEmail(),
                dto.getAddress(),
                dto.getCustomerId());
    }

    public boolean deleteCustomer(String customerId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM customers WHERE customer_id = ?", customerId);
    }

    public CustomerDto searchCustomer(String customerId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM customers WHERE customer_id = ?", customerId);
        if (rs.next()) {
            return new CustomerDto(
                    rs.getLong("customer_id"),
                    rs.getString("customer_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("address")
            );
        }
        return null;
    }

    public ArrayList<CustomerDto> getAllCustomers() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM customers");
        ArrayList<CustomerDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new CustomerDto(
                    rs.getLong("customer_id"),
                    rs.getString("customer_name"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("address")
            ));
        }
        return list;
    }

    public String getNextCustomerId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT customer_id FROM customers ORDER BY customer_id DESC LIMIT 1");
        char prefix = 'C';
        if (rs.next()) {
            String lastId = rs.getString("customer_id");
            int nextId = Integer.parseInt(lastId.substring(1)) + 1;
            return String.format(prefix + "%03d", nextId);
        }
        return prefix + "001";
    }
}

