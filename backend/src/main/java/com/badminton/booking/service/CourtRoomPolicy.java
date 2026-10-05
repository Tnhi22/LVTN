package com.badminton.booking.service;

import java.util.Locale;

/** Capacities supplied by the owner. Never derive a room from its database ID. */
public final class CourtRoomPolicy {
    private CourtRoomPolicy() {}
    private static String key(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
    public static String group(String type, String room) {
        String value = key(type) + key(room);
        if (value.contains("MISTFAN") || value.contains("DAILYVISITOR")) return "DAILY_VISITOR";
        if (value.contains("PREMIUM") && value.contains("PRIVATE")) return "PREMIUM_PRIVATE";
        if (value.contains("PREMIUM") && value.contains("SHARED")) return "PREMIUM_SHARED";
        if (value.contains("GOLD")) return "GOLD";
        if (value.contains("BASIC")) return "BASIC";
        return "UNCONFIGURED";
    }
    public static int capacity(String group) {
        return "PREMIUM_PRIVATE".equals(group) ? 1 : 0; // 0 means no configured upper limit.
    }
    public static boolean accepts(String type, String group) {
        String value = key(type);
        if (value.contains("PREMIUM")) {
            if (value.contains("PRIVATE")) return "PREMIUM_PRIVATE".equals(group);
            if (value.contains("SHARED")) return "PREMIUM_SHARED".equals(group);
            return "PREMIUM_PRIVATE".equals(group) || "PREMIUM_SHARED".equals(group);
        }
        if (value.contains("GOLD")) return "GOLD".equals(group);
        if (value.contains("BASIC")) return "BASIC".equals(group);
        return (value.contains("MISTFAN") || value.contains("DAILYVISITOR")) && "DAILY_VISITOR".equals(group);
    }
}
