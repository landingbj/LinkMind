package ai.agent.util;

import ai.config.ContextLoader;
import ai.config.pojo.SkillsConfig;
import ai.pnps.skills.pojo.SkillEntry;

import java.util.List;

public class AgentSocialUtil {

    public static final String SOCIAL_CHANNEL_SKILL = "social-channel";

    private static final boolean SOCIAL_CHANNEL_SKILL_ENABLED;

    static {
        boolean enabled = false;
        if (ContextLoader.configuration != null) {
            SkillsConfig skills = ContextLoader.configuration.getSkills();
            if (skills != null && skills.isEnable()) {
                List<SkillEntry> items = skills.getSkills();
                if (items != null) {
                    for (SkillEntry entry : items) {
                        if (entry == null || entry.getName() == null) {
                            continue;
                        }
                        if (SOCIAL_CHANNEL_SKILL.equalsIgnoreCase(entry.getName().trim())) {
                            enabled = true;
                            break;
                        }
                    }
                }
            }
        }
        SOCIAL_CHANNEL_SKILL_ENABLED = enabled;
    }

    private AgentSocialUtil() {
    }

    /**
     * Returns true when {@code skills.enable} is true and {@code skills.items} contains
     * an entry named {@link #SOCIAL_CHANNEL_SKILL}.
     */
    public static boolean isSocialChannelSkillEnabled() {
        return SOCIAL_CHANNEL_SKILL_ENABLED;
    }
}
