package net.tkgon.mc.iruuRPG.questline;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one player has of the quest lines (saved in players/&lt;uuid&gt;.yml under questlines: and flags:): the state of
 * every line they ever started, and the shared flags.
 */
public final class QuestlineData {

    public enum Status {
        ACTIVE, PAUSED, COMPLETED, FAILED;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Status of(String text) {
            if (text == null) return null;
            for (Status status : values()) {
                if (status.key().equalsIgnoreCase(text.trim())) return status;
            }
            return null;
        }
    }

    /** One line of one player: where it is, since when, its own flags, and how many times it was completed. */
    public static final class LineState {
        public Status status;
        public String step;
        public long stepSince;
        public final Map<String, Object> flags = new LinkedHashMap<>();
        public int runs;
        /** The cutscene the line waits for (not saved: after a restart the line simply goes on). */
        public transient String waitingCutscene;

        public LineState(Status status, String step, long stepSince) {
            this.status = status;
            this.step = step;
            this.stepSince = stepSince;
        }
    }

    private final Map<String, LineState> lines = new LinkedHashMap<>();
    private final Map<String, Object> flags = new LinkedHashMap<>();

    public Map<String, LineState> lines() {
        return lines;
    }

    public LineState line(String id) {
        return lines.get(id);
    }

    /** The shared flags (flags/flags.yml). */
    public Map<String, Object> flags() {
        return flags;
    }

    /** Writes questlines: and flags: into the player's file. */
    public void writeTo(ConfigurationSection root) {
        for (var entry : lines.entrySet()) {
            LineState state = entry.getValue();
            String at = "questlines." + entry.getKey();
            root.set(at + ".status", state.status.key());
            root.set(at + ".step", state.step);
            root.set(at + ".step-since", state.stepSince);
            root.set(at + ".runs", state.runs);
            for (var flag : state.flags.entrySet()) root.set(at + ".flags." + flag.getKey(), flag.getValue());
        }
        for (var flag : flags.entrySet()) root.set("flags." + flag.getKey(), flag.getValue());
    }

    /** Reads what {@link #writeTo} wrote (a line with an unknown status is skipped). */
    public void readFrom(ConfigurationSection root) {
        lines.clear();
        flags.clear();
        ConfigurationSection section = root.getConfigurationSection("questlines");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection line = section.getConfigurationSection(id);
                Status status = line == null ? null : Status.of(line.getString("status"));
                if (status == null) continue;
                LineState state = new LineState(status, line.getString("step", ""), line.getLong("step-since", 0L));
                state.runs = line.getInt("runs", 0);
                ConfigurationSection own = line.getConfigurationSection("flags");
                if (own != null) {
                    for (String key : own.getKeys(false)) state.flags.put(key, own.get(key));
                }
                lines.put(id, state);
            }
        }
        ConfigurationSection shared = root.getConfigurationSection("flags");
        if (shared != null) {
            for (String key : shared.getKeys(false)) flags.put(key, shared.get(key));
        }
    }
}
