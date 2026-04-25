package ai.dto;

import lombok.Data;

import java.util.Date;

@Data
public class SocialChannelMessage {
    private Long id;
    private Long channelId;
    private String channelName;
    private String userId;
    private String userName;
    private String content;
    private Date createdAt;
}
