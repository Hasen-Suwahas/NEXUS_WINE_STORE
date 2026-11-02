package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SaleDto {
    private Long saleId;
    private LocalDateTime saleDate;
    private BigDecimal totalAmount;
    private String orderStatus;
    private Long customerId;
    private String customerName;
    private Long userId;
    private List<SaleItemDto> saleItems;
    private List<PaymentDto> payments;
}