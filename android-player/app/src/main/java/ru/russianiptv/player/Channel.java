package ru.russianiptv.player;

public final class Channel {
    public final String id, name, url, logo, group;
    public Channel(String id, String name, String url, String logo, String group) {
        this.id = id; this.name = name; this.url = url; this.logo = logo; this.group = group;
    }
}
