package com.earthpol.epmcapi.endpoints.chat;

import java.time.Instant;
import java.util.UUID;

public class ChatEntry {
    private final int entryId;
    private final UUID userUuid;
    private final String username;
    private final String nickname;
    private final String message;
    private final String channel;
    private final Instant timestamp;

    public ChatEntry(int entryId, UUID userUuid, String username, String nickname,
                     String message, String channel, Instant timestamp) {
        this.entryId = entryId;
        this.userUuid = userUuid;
        this.username = username;
        this.nickname = nickname;
        this.message = message;
        this.channel = channel;
        this.timestamp = timestamp;
    }

    public int getEntryId() { return entryId; }
    public UUID getUserUuid() { return userUuid; }
    public String getUsername() { return username; }
    public String getNickname() { return nickname; }
    public String getMessage() { return message; }
    public String getChannel() { return channel; }
    public Instant getTimestamp() { return timestamp; }
}
