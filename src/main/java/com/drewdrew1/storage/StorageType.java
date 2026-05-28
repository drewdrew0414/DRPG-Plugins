package com.drewdrew1.storage;

public enum StorageType {
    PERSONAL("player"),
    GUILD("guild");

    private final String prefix;

    StorageType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
