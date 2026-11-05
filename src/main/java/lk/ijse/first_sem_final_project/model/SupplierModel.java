package lk.ijse.first_sem_final_project.model;

import lk.ijse.first_sem_final_project.dto.SupplierDto;
import lk.ijse.first_sem_final_project.util.CrudUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class SupplierModel {

    public boolean saveSupplier(SupplierDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("INSERT INTO suppliers VALUES (?, ?, ?, ?, ?, ?)",
                dto.getSupplierId(),
                dto.getSupplierName(),
                dto.getContactPerson(),
                dto.getPhone(),
                dto.getEmail(),
                dto.getAddress());
    }

    public boolean updateSupplier(SupplierDto dto) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("UPDATE suppliers SET supplier_name = ?, contact_person = ?, phone = ?, email = ?, address = ? WHERE supplier_id = ?",
                dto.getSupplierName(),
                dto.getContactPerson(),
                dto.getPhone(),
                dto.getEmail(),
                dto.getAddress(),
                dto.getSupplierId());
    }

    public boolean deleteSupplier(String supplierId) throws SQLException, ClassNotFoundException {
        return CrudUtil.execute("DELETE FROM suppliers WHERE supplier_id = ?", supplierId);
    }

    public SupplierDto searchSupplier(String supplierId) throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM suppliers WHERE supplier_id = ?", supplierId);
        if (rs.next()) {
            return new SupplierDto(
                    rs.getLong("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("contact_person"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("address")
            );
        }
        return null;
    }

    public ArrayList<SupplierDto> getAllSuppliers() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT * FROM suppliers");
        ArrayList<SupplierDto> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new SupplierDto(
                    rs.getLong("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("contact_person"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("address")
            ));
        }
        return list;
    }

    public String getNextSupplierId() throws SQLException, ClassNotFoundException {
        ResultSet rs = CrudUtil.execute("SELECT supplier_id FROM suppliers ORDER BY supplier_id DESC LIMIT 1");
        char prefix = 'S';
        if (rs.next()) {
            String lastId = rs.getString("supplier_id");
            int nextId = Integer.parseInt(lastId.substring(1)) + 1;
            return String.format(prefix + "%03d", nextId);
        }
        return prefix + "001";
    }
}