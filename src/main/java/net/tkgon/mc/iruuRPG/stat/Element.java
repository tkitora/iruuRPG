package net.tkgon.mc.iruuRPG.stat;

import java.util.Locale;

public enum Element {
    RED("red", "赤"),
    BLUE("blue", "青"),
    WHITE("white", "白"),
    GREEN("green", "緑"),
    ORANGE("orange", "橙");

    private final String key;
    private final String displayName;

    Element(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Element fromConfig(String raw, Element fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);

        for (Element element : values()) {
            if (element.name().equals(normalized) || element.key.equalsIgnoreCase(raw.trim())) {
                return element;
            }
        }

        return fallback;
    }

    public static Element fromLegacyIndex(int index) {
        return switch (index) {
            case 0 -> RED;
            case 1 -> BLUE;
            case 2 -> WHITE;
            case 3 -> GREEN;
            case 4 -> ORANGE;
            default -> throw new IllegalArgumentException("Unknown element index: " + index);
        };
    }
}
