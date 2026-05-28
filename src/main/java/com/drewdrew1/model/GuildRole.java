package com.drewdrew1.model;

public enum GuildRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageStorage() {
        return this == OWNER || this == ADMIN;
    }

    public boolean isHigherThan(GuildRole other) {
        return rank() > other.rank();
    }

    private int rank() {
        return switch (this) {
            case OWNER -> 3;
            case ADMIN -> 2;
            case MEMBER -> 1;
        };
    }
}
