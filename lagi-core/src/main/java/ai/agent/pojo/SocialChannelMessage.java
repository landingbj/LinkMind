package ai.agent.pojo;

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
    /**
     * In-memory only: {@code true} when the message was sent by the social agent
     * on behalf of a user ({@link ai.agent.service.AgentSocialService}), not persisted.
     */
    private boolean agentAutoSent;
}
