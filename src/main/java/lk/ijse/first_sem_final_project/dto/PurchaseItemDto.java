package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseItemDto {
    private int purchaseItemId;
    private int purchaseId;
    private int productId;
    private int quantity;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
}