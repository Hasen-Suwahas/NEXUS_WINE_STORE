package lk.ijse.first_sem_final_project.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockTm {
    private Long stockId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime lastUpdated;

}