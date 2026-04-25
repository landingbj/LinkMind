package ai.dto;

import lombok.Data;

import java.util.Date;

@Data
public class SocialUser {
    private String userId;
    private String username;
    private Date createdAt;
}
