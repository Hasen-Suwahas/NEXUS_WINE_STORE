package lk.ijse.first_sem_final_project.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupplierTm {
    private Long supplierId;
    private String supplierName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
}
