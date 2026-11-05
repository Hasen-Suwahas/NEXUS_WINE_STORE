package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseDto {
    private String purchaseId;
    private String supplierId;
    private String userId;
    private LocalDate purchaseDate;
    private BigDecimal totalAmount;
}