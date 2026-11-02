package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseDto {
    private Long purchaseId;
    private LocalDate purchaseDate;
    private BigDecimal totalAmount;
    private Long supplierId;
    private String supplierName;
    private Long userId;
    private List<PurchaseItemDto> purchaseItems;
}