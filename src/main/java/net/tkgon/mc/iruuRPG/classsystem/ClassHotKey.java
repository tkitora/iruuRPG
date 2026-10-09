package net.tkgon.mc.iruuRPG.classsystem;

import java.util.Locale;

public enum ClassHotKey {
    F("F", "アイテムスワップ"),
    Q("Q", "アイテムドロップ"),
    L("L", "左クリック"),
    R("R", "右クリック"),
    SL("SL", "シフト左クリック"),
    SR("SR", "シフト右クリック");

    private final String key;
    private final String displayName;

    ClassHotKey(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static ClassHotKey fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String normalized = raw.trim().replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        for (ClassHotKey hotKey : values()) {
            if (hotKey.name().equals(normalized) || hotKey.key.equalsIgnoreCase(raw.trim())) {
                return hotKey;
            }
        }
        return null;
    }
}
