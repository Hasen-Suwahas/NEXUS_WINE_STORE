package lk.ijse.first_sem_final_project.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseTm {
    private String purchaseId;
    private String supplierId;
    private String userId;
    private LocalDate purchaseDate;
    private BigDecimal totalAmount;
}