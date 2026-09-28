package com.earthpol.epmcapi.endpoints.discord;


public class DiscordLink {
    private final String discord;
    private final String uuid;

    public DiscordLink(String discord, String uuid) {
        this.discord = discord;
        this.uuid = uuid;
    }

    public String getDiscord() {
        return discord;
    }

    public String getUuid() {
        return uuid;
    }
}
