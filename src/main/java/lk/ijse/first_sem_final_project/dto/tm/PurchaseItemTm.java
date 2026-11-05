package lk.ijse.first_sem_final_project.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseItemTm {
    private int purchaseItemId;
    private int purchaseId;
    private int productId;
    private int quantity;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
}