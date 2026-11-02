package lk.ijse.first_sem_final_project.dto.tm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTm {
    private Long userId;
    private String username;
    private String fullName;
    private String phone;
    private String role;
    private String status;
}
