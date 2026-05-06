package ai.sevice;

import ai.config.ContextLoader;
import ai.dto.SocialChannel;
import ai.dto.SocialChannelMessage;
import ai.dto.SocialUser;
import ai.llm.service.CompletionsService;
import ai.migrate.dao.SocialChannelDao;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.I18nFieldUtil;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SocialChannelService {
    private final SocialChannelDao socialChannelDao = new SocialChannelDao();
    private final String translateModel = resolveTranslateModel();

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public boolean registerUser(String userId, String username) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        if (isBlank(username)) {
            throw new IOException("username is required");
        }
        try {
            return socialChannelDao.registerUser(userId, username);
        } catch (Exception e) {
            throw new IOException("register user failed: " + e.getMessage(), e);
        }
    }

    public SocialUser getUser(String userId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            return socialChannelDao.findUserById(userId);
        } catch (Exception e) {
            throw new IOException("get user failed: " + e.getMessage(), e);
        }
    }

    public void saveLastLoginUser(String userId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            socialChannelDao.saveLastLoginUser(userId);
        } catch (Exception e) {
            throw new IOException("save last login user failed: " + e.getMessage(), e);
        }
    }

    public long createChannel(String userId, String name, String description, Boolean isPublic) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        if (isBlank(name)) {
            throw new IOException("name is required");
        }
        try {
            if (!socialChannelDao.userExists(userId)) {
                throw new IOException("user not registered");
            }
            return socialChannelDao.createChannelWithOwnerSubscription(userId, name, description);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("create channel failed: " + e.getMessage(), e);
        }
    }

    public void subscribe(String userId, long channelId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            if (!socialChannelDao.userExists(userId)) {
                throw new IOException("user not registered");
            }
            SocialChannel ch = socialChannelDao.findChannelById(channelId);
            if (ch == null) {
                throw new IOException("channel not found");
            }
            if (!Boolean.TRUE.equals(ch.getEnabled())) {
                throw new IOException("channel is disabled");
            }
            socialChannelDao.addSubscription(userId, channelId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("subscribe failed: " + e.getMessage(), e);
        }
    }

    public void unsubscribe(String userId, long channelId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            if (socialChannelDao.isOwner(userId, channelId)) {
                throw new IOException("channel owner cannot unsubscribe");
            }
            socialChannelDao.removeSubscription(userId, channelId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("unsubscribe failed: " + e.getMessage(), e);
        }
    }

    public List<SocialChannel> listMyChannels(String userId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            return socialChannelDao.listSubscribedChannels(userId);
        } catch (Exception e) {
            throw new IOException("list channels failed: " + e.getMessage(), e);
        }
    }

    public List<SocialChannel> listOwnedChannels(String userId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            return socialChannelDao.listOwnerChannels(userId);
        } catch (Exception e) {
            throw new IOException("list owned channels failed: " + e.getMessage(), e);
        }
    }

    public List<SocialChannel> listPublicChannels(int limit) throws IOException {
        return listPublicChannels(limit, null);
    }

    public List<SocialChannel> listPublicChannels(int limit, String preferLang) throws IOException {
        try {
            return socialChannelDao.listPublicChannels(limit, preferLang);
        } catch (Exception e) {
            throw new IOException("list public channels failed: " + e.getMessage(), e);
        }
    }

    /**
     * Translates the channel's name and description into the target language
     * via the configured LLM and persists the result alongside any existing
     * translations. Returns the resolved values for the caller to display.
     */
    public Map<String, String> translateChannel(long channelId, String targetLang) throws IOException {
        String normalized = I18nFieldUtil.normalizeLang(targetLang);
        if (normalized == null) {
            throw new IOException("lang is required");
        }
        try {
            Map<String, String> raw = socialChannelDao.findChannelRawI18n(channelId);
            if (raw == null) {
                throw new IOException("channel not found");
            }
            I18nFieldUtil.I18nValue nameValue = I18nFieldUtil.parse(raw.get("name"));
            I18nFieldUtil.I18nValue descValue = I18nFieldUtil.parse(raw.get("description"));
            String nameToPersist = nameValue.has(normalized)
                    ? null
                    : translateText(nameValue.getDefaultValue(), normalized);
            String descToPersist = descValue.has(normalized)
                    ? null
                    : translateText(descValue.getDefaultValue(), normalized);
            if (nameToPersist != null || descToPersist != null) {
                socialChannelDao.updateChannelTranslation(channelId, normalized, nameToPersist, descToPersist);
            }
            String translatedName = nameValue.has(normalized)
                    ? nameValue.resolve(normalized)
                    : (nameToPersist != null ? nameToPersist : nameValue.getDefaultValue());
            String translatedDesc = descValue.has(normalized)
                    ? descValue.resolve(normalized)
                    : (descToPersist != null ? descToPersist : descValue.getDefaultValue());
            Map<String, String> result = new LinkedHashMap<String, String>();
            result.put("name", translatedName);
            result.put("description", translatedDesc);
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("translate channel failed: " + e.getMessage(), e);
        }
    }

    private static String resolveTranslateModel() {
        try {
            if (ContextLoader.configuration == null
                    || ContextLoader.configuration.getFunctions() == null
                    || ContextLoader.configuration.getFunctions().getTranslate() == null
                    || ContextLoader.configuration.getFunctions().getTranslate().isEmpty()) {
                return null;
            }
            return ContextLoader.configuration.getFunctions().getTranslate().get(0).getModel();
        } catch (Exception e) {
            return null;
        }
    }

    private String translateText(String source, String targetLang) {
        if (source == null || source.trim().isEmpty()) {
            return "";
        }
        String langName = "en-US".equalsIgnoreCase(targetLang) ? "English" : "Simplified Chinese";
        String prompt = "Translate the following text into " + langName + "."
                + " Return only the translation, without any quotes, explanations, or extra punctuation."
                + " If the text is already in " + langName + ", return it unchanged.\n\nText:\n" + source;
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setTemperature(0.2);
        request.setMax_tokens(512);
        request.setStream(false);
        request.setModel(translateModel);
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(prompt);
        request.setMessages(Lists.newArrayList(message));
        try {
            CompletionsService completionsService = new CompletionsService();
            ChatCompletionResult result = completionsService.completions(request);
            if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
                return null;
            }
            String content = result.getChoices().get(0).getMessage().getContent();
            if (content == null) {
                return null;
            }
            String trimmed = content.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    public SocialChannel getChannel(String userId, long channelId) throws IOException {
        try {
            SocialChannel ch = socialChannelDao.findChannelById(channelId);
            if (ch == null) {
                throw new IOException("channel not found");
            }
            if (!Boolean.TRUE.equals(ch.getEnabled())) {
                throw new IOException("channel is disabled");
            }
            return ch;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("get channel failed: " + e.getMessage(), e);
        }
    }

    public List<SocialChannelMessage> listMessages(String userId, long channelId, int limit, Long beforeMessageId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            if (!socialChannelDao.isSubscribed(userId, channelId)) {
                throw new IOException("not subscribed to this channel");
            }
            return socialChannelDao.listMessages(channelId, limit, beforeMessageId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("list messages failed: " + e.getMessage(), e);
        }
    }

    public long sendMessage(String userId, long channelId, String content) throws IOException {
        return sendMessage(userId, Long.valueOf(channelId), null, content);
    }

    public long sendMessage(String userId, Long channelId, String channelName, String content) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        if (isBlank(content)) {
            throw new IOException("content is required");
        }
        try {
            Long resolvedChannelId = channelId;
            if (resolvedChannelId == null || resolvedChannelId <= 0) {
                if (isBlank(channelName)) {
                    throw new IOException("channelId or channelName is required");
                }
                List<SocialChannel> channels = socialChannelDao.findSubscribedChannelsByName(userId, channelName);
                if (channels.isEmpty()) {
                    throw new IOException("channel not found or not subscribed");
                }
                if (channels.size() > 1) {
                    throw new IOException("multiple subscribed channels found with same name");
                }
                resolvedChannelId = channels.get(0).getId();
            }
            SocialChannel ch = socialChannelDao.findChannelById(resolvedChannelId);
            if (ch == null) {
                throw new IOException("channel not found");
            }
            if (!Boolean.TRUE.equals(ch.getEnabled())) {
                throw new IOException("channel is disabled");
            }
            if (!socialChannelDao.isSubscribed(userId, resolvedChannelId)) {
                throw new IOException("not subscribed to this channel");
            }
            return socialChannelDao.insertMessage(resolvedChannelId, userId, content);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("send message failed: " + e.getMessage(), e);
        }
    }

    public void toggleChannel(String userId, long channelId, boolean enabled) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            SocialChannel ch = socialChannelDao.findChannelById(channelId);
            if (ch == null) {
                throw new IOException("channel not found");
            }
            if (!userId.trim().equals(ch.getOwnerUserId())) {
                throw new IOException("only owner can toggle channel");
            }
            socialChannelDao.updateChannelStatus(channelId, enabled);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("toggle channel failed: " + e.getMessage(), e);
        }
    }

    public void deleteChannel(String userId, long channelId) throws IOException {
        if (isBlank(userId)) {
            throw new IOException("userId is required");
        }
        try {
            SocialChannel ch = socialChannelDao.findChannelById(channelId);
            if (ch == null) {
                throw new IOException("channel not found");
            }
            if (!userId.trim().equals(ch.getOwnerUserId())) {
                throw new IOException("only owner can delete channel");
            }
            socialChannelDao.deleteChannel(channelId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("delete channel failed: " + e.getMessage(), e);
        }
    }
}
