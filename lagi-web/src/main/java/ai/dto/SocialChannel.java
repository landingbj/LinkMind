package ai.dto;

import lombok.Data;

import java.util.Date;

@Data
public class SocialChannel {
    private Long id;
    private String name;
    private String description;
    private String ownerUserId;
    private Boolean isPublic;
    private Date createdAt;
}
