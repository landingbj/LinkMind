package ai.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class LagiAgentExpenseListResponse {
    private String status;
    private Integer totalRow;
    private Integer totalPage;
    private Integer pageNumber;
    private Integer pageSize;
    private List<PaidLagiAgent> data;
}
