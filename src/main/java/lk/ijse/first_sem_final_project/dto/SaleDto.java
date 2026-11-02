package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SaleDto {
    private String saleId;
    private String customerId;
    private String userId;
    private LocalDateTime saleDate;
    private BigDecimal totalAmount;
    private String orderStatus;
}