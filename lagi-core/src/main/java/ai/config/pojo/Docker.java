package ai.config.pojo;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Docker {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private List<ModelMapper> models;
}
