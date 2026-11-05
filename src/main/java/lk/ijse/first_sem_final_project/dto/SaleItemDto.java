package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SaleItemDto {
    private int saleItemId;
    private int saleId;
    private int productId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}