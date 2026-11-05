package lk.ijse.first_sem_final_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockDto {
    private Long stockId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime lastUpdated;

}