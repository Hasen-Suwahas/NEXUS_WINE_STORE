package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {
    private String productId;
    private String categoryId;
    private String productName;
    private String description;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private int reorderLevel;
}