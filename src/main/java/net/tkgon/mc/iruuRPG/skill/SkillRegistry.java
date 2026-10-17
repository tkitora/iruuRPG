package net.tkgon.mc.iruuRPG.skill;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The skills of {@code plugins/iruuRPG/skills/*.yml} and the reader that turns any skill entry into a
 * {@link SkillSpec}. Problems are logged as warnings (the reload report collects them) and the value is fixed to the
 * nearest allowed one, so a bad number never breaks the server.
 *
 * <pre>
 * skills:
 *   fire_slam:                     # id: lower case letters, digits and _
 *     name: "..."
 *     template: strike             # strike | sweep | barrage | dash | wave | nova | beam
 *     look:   { colors: ["#ff5a1e", "#ffd24a", "#ffffff"], particle: FLAME, size: 1.0, width: 1.0, sound: heavy }
 *     shape:  { radius: 3.5, range: 6, angle: 130, orientation: horizontal }
 *     timing: { windup-ticks: 24, cooldown-ticks: 160, count: 1, interval-ticks: 4, speed: 12 }
 *     effects: { damage-rate: 1.8, status: [{type: slow, percent: 40, seconds: 3}], heal-rate: 0, knockback: 0 }
 *     target: enemies
 * </pre>
 * The same keys can also be written flat (the old {@code {type: strike, radius: 3}} form). A mob uses a skill with
 * {@code {skill: fire_slam}} and may override any key ({@code {skill: fire_slam, radius: 5}}).
 */
public final class SkillRegistry {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Set<String> STATUS_TYPES = Set.of("slow", "bleed", "corrosion", "decay", "confusion", "explosion", "magic_amplify", "protection");

    /** The sparkle particles a skill may use (DUST means colored dust only). */
    private static final Map<String, Particle> PARTICLES = new LinkedHashMap<>();

    static {
        PARTICLES.put("DUST", Particle.REDSTONE);
        PARTICLES.put("FLAME", Particle.FLAME);
        PARTICLES.put("SOUL_FIRE_FLAME", Particle.SOUL_FIRE_FLAME);
        PARTICLES.put("END_ROD", Particle.END_ROD);
        PARTICLES.put("ELECTRIC_SPARK", Particle.ELECTRIC_SPARK);
        PARTICLES.put("CRIT", Particle.CRIT);
        PARTICLES.put("CRIT_MAGIC", Particle.CRIT_MAGIC);
        PARTICLES.put("SPELL_WITCH", Particle.SPELL_WITCH);
        PARTICLES.put("CLOUD", Particle.CLOUD);
        PARTICLES.put("VILLAGER_HAPPY", Particle.VILLAGER_HAPPY);
        PARTICLES.put("SNOWFLAKE", Particle.SNOWFLAKE);
        PARTICLES.put("WAX_ON", Particle.WAX_ON);
        PARTICLES.put("SMOKE_NORMAL", Particle.SMOKE_NORMAL);
    }

    /** Ids of the item skills (iskill): a weapon's `skill:` with one of these keeps meaning the old skill. */
    private static final Set<String> RESERVED_IDS = Set.of("sweep", "heal", "dash", "strike", "slash", "none");

    private final JavaPlugin plugin;
    private final Map<String, SkillSpec> skills = new LinkedHashMap<>();

    public SkillRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static Set<String> particleNames() {
        return Collections.unmodifiableSet(PARTICLES.keySet());
    }

    public int size() {
        return skills.size();
    }

    public Map<String, SkillSpec> all() {
        return Collections.unmodifiableMap(skills);
    }

    /** A skill by id; a bare template name ("strike") gives that template's defaults. */
    public Optional<SkillSpec> find(String id) {
        if (id == null) return Optional.empty();
        SkillSpec skill = skills.get(id);
        if (skill != null) return Optional.of(skill);
        SkillTemplate template = SkillTemplate.parse(id);
        return template == null || template == SkillTemplate.COMBO ? Optional.empty() : Optional.of(SkillSpec.defaults(template));
    }

    /** The skill a weapon uses: skill `id` with the item's `skill-overrides` applied; null when there is no such skill. */
    public SkillSpec forItem(String owner, String id, Map<String, Object> overrides) {
        SkillSpec base = skills.get(id);
        if (base == null) return null;
        if (overrides == null || overrides.isEmpty()) return base;
        SkillSpec changed = parse(owner, new LinkedHashMap<>(overrides), base, id);
        return changed == null ? base : changed;
    }

    /** Reloads every skill file; if loading throws, the previous skills stay. */
    public void reload() {
        Map<String, SkillSpec> previous = new LinkedHashMap<>(skills);
        try {
            load();
        } catch (RuntimeException e) {
            skills.clear();
            skills.putAll(previous);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "skills: reload failed, keeping the previous skills", e);
        }
    }

    private void load() {
        skills.clear();
        File folder = new File(plugin.getDataFolder(), "skills");
        if (!folder.isDirectory() && !folder.mkdirs()) return;
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files);
        List<String[]> comboOwners = new ArrayList<>();
        List<Map<String, Object>> comboMaps = new ArrayList<>();
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("skills");
            if (section == null) continue;
            for (String id : section.getKeys(false)) {
                String owner = file.getName() + ": " + id;
                if (!ID.matcher(id).matches()) {
                    plugin.getLogger().warning("skills: " + owner + ": the id must be lower case letters, digits and _ (skipped)");
                    continue;
                }
                if (RESERVED_IDS.contains(id) || SkillTemplate.parse(id) != null) {
                    plugin.getLogger().warning("skills: " + owner + ": the id is taken by a built-in skill or a template name (skipped)");
                    continue;
                }
                if (skills.containsKey(id)) {
                    plugin.getLogger().warning("skills: " + owner + ": duplicate skill id (the first one is kept)");
                    continue;
                }
                Object raw = section.get(id);
                Map<String, Object> map = toMap(raw);
                if (map == null) {
                    plugin.getLogger().warning("skills: " + owner + ": must be a section");
                    continue;
                }
                Map<String, Object> flatCheck = flatten(map);
                Object templateName = flatCheck.containsKey("template") ? flatCheck.get("template") : flatCheck.get("type");
                if (SkillTemplate.parse(templateName == null ? null : templateName.toString()) == SkillTemplate.COMBO) {
                    // combos refer to other skills: read them after all the others
                    comboOwners.add(new String[]{owner, id});
                    comboMaps.add(map);
                    continue;
                }
                SkillSpec spec = parse(owner, map, null, id);
                if (spec != null) skills.put(id, spec);
            }
        }
        pendingComboIds.clear();
        for (String[] owner : comboOwners) pendingComboIds.add(owner[1]);
        for (int index = 0; index < comboMaps.size(); index++) {
            SkillSpec combo = parseCombo(comboOwners.get(index)[0], comboMaps.get(index), comboOwners.get(index)[1]);
            if (combo != null) skills.put(comboOwners.get(index)[1], combo);
        }
        if (!skills.isEmpty()) plugin.getLogger().info("Loaded " + skills.size() + " skills from " + files.length + " files.");
    }

    // ---- reading one entry -------------------------------------------------------------------------------------------

    /**
     * Turns an entry of a mob's {@code skills:} list into a skill: {@code {skill: id, ...overrides}} or the old
     * {@code {type: strike, ...}}. Returns null (and warns) when it cannot be read.
     */
    public SkillSpec resolve(String owner, Map<?, ?> raw) {
        Map<String, Object> map = toMap(raw);
        if (map == null) return null;
        Object ref = map.get("skill");
        if (ref != null) {
            String id = ref.toString().trim();
            SkillSpec base = skills.get(id);
            if (base == null) {
                plugin.getLogger().warning("skills: " + owner + ": unknown skill '" + id + "' (skipped)");
                return null;
            }
            return parse(owner, map, base, id);
        }
        return parse(owner, map, null, "");
    }

    /**
     * Builds a skill from a map. With a {@code base}, only the keys present change; without one, the template's
     * defaults are the base. Values out of range are fixed (with a warning).
     */
    public SkillSpec parse(String owner, Map<String, Object> raw, SkillSpec base, String id) {
        Map<String, Object> flat = flatten(raw);

        SkillSpec start = base;
        if (start == null) {
            Object templateName = flat.containsKey("template") ? flat.get("template") : flat.get("type");
            SkillTemplate template = SkillTemplate.parse(templateName == null ? null : templateName.toString());
            if (template == null) {
                plugin.getLogger().warning("skills: " + owner + ": unknown template '" + templateName
                        + "' (strike / sweep / barrage / dash / wave / nova / beam) (skipped)");
                return null;
            }
            if (template == SkillTemplate.COMBO) {
                plugin.getLogger().warning("skills: " + owner + ": a combo must be a skill of skills/*.yml (with steps), not written inline (skipped)");
                return null;
            }
            start = SkillSpec.defaults(template);
        }

        String name = start.name();
        if (flat.get("name") != null) name = flat.get("name").toString();

        // look
        SkillLook oldLook = start.look();
        List<Color> colors = oldLook.colors();
        if (flat.containsKey("colors") || flat.containsKey("color")) {
            colors = colors(owner, flat.containsKey("colors") ? flat.get("colors") : flat.get("color"));
        }
        Particle particle = oldLook.particle();
        if (flat.get("particle") != null) {
            String particleName = flat.get("particle").toString().trim().toUpperCase(Locale.ROOT);
            Particle found = PARTICLES.get(particleName);
            if (found == null) {
                plugin.getLogger().warning("skills: " + owner + ": particle '" + particleName + "' is not allowed (" + String.join(" / ", PARTICLES.keySet()) + "); the element's own is used");
            } else {
                particle = found;
            }
        }
        double size = number(owner, flat, "size", oldLook.size(), SkillLook.MIN_SIZE, SkillLook.MAX_SIZE);
        double width = number(owner, flat, "width", oldLook.width(), SkillLook.MIN_WIDTH, SkillLook.MAX_WIDTH);
        SoundStyle sound = oldLook.sound();
        if (flat.get("sound") != null) {
            SoundStyle parsed = SoundStyle.parse(flat.get("sound").toString());
            if (parsed == null) {
                plugin.getLogger().warning("skills: " + owner + ": sound '" + flat.get("sound") + "' is unknown (heavy / sharp / magic / none)");
            } else {
                sound = parsed;
            }
        }

        // shape
        double radius = number(owner, flat, "radius", start.radius(), 1.0, 20.0);
        double range = number(owner, flat, "range", start.range(), 1.0, 40.0);
        double angle = number(owner, flat, "angle", start.angle(), 10.0, 360.0);
        SkillSpec.Orientation orientation = start.orientation();
        if (flat.get("orientation") != null) {
            try {
                orientation = SkillSpec.Orientation.valueOf(flat.get("orientation").toString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("skills: " + owner + ": orientation '" + flat.get("orientation") + "' is unknown (horizontal / vertical / diagonal)");
            }
        }

        // timing
        int windup = (int) Math.round(number(owner, flat, "windup-ticks", start.windupTicks(), 4, 200));
        int cooldown = (int) Math.round(number(owner, flat, "cooldown-ticks", start.cooldownTicks(), 20, 2400));
        int count = (int) Math.round(number(owner, flat, "count", start.count(), 1, 20));
        int interval = (int) Math.round(number(owner, flat, "interval-ticks", start.intervalTicks(), 2, 100));
        double speed = number(owner, flat, "speed", start.speed(), 2.0, 60.0);

        // effects
        double damageRate = number(owner, flat, "damage-rate", start.damageRate(), 0.0, 20.0);
        List<SkillSpec.StatusApply> statuses = start.statuses();
        if (flat.containsKey("status")) statuses = statuses(owner, flat.get("status"));
        double heal = number(owner, flat, "heal-rate", start.healRate(), 0.0, 1.0);
        double knockback = number(owner, flat, "knockback", start.knockback(), 0.0, 3.0);
        SkillSpec.Target target = start.target();
        if (flat.get("target") != null) {
            try {
                target = SkillSpec.Target.valueOf(flat.get("target").toString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("skills: " + owner + ": target '" + flat.get("target") + "' is unknown (enemies / allies / both)");
            }
        }

        double cost = number(owner, flat, "cost", start.cost(), 0.0, 500.0);
        List<String> description = start.description();
        if (flat.get("description") != null) {
            Object text = flat.get("description");
            description = new ArrayList<>();
            if (text instanceof List<?> lines) {
                for (Object line : lines) description.add(String.valueOf(line));
            } else {
                description.add(String.valueOf(text));
            }
        }

        SkillTemplate template = start.template();
        if (template == SkillTemplate.WAVE && count > 5) {
            plugin.getLogger().warning("skills: " + owner + ": a wave skill can send at most 5 waves, used 5");
            count = 5;
        }
        return new SkillSpec(id.isEmpty() ? start.id() : id, name, template,
                new SkillLook(colors, particle, size, width, sound),
                radius, range, angle, orientation, windup, cooldown, count, interval, speed,
                damageRate, statuses, heal, knockback, target, cost, description, start.steps());
    }

    /**
     * A combo: {@code steps:} is a list of {@code {skill: id, delay-ticks, with-previous, ...overrides}} (or
     * {@code {template: strike, ...}}). At most 8 steps and 12 seconds in all; a combo cannot contain a combo; a step
     * whose skill does not exist is skipped. The combo's own cost, cooldown and description are the ones that count.
     */
    private SkillSpec parseCombo(String owner, Map<String, Object> raw, String id) {
        SkillSpec self = parse(owner, raw, SkillSpec.defaults(SkillTemplate.COMBO), id);
        if (self == null) return null;
        Object stepsRaw = flatten(raw).get("steps");
        if (!(stepsRaw instanceof List<?> list) || list.isEmpty()) {
            plugin.getLogger().warning("skills: " + owner + ": a combo needs a steps list (skipped)");
            return null;
        }
        List<SkillSpec.ComboStep> steps = new ArrayList<>();
        int number = 0;
        int ticks = 0;
        int previousStart = 0;
        for (Object item : list) {
            number++;
            String where = owner + " step " + number;
            if (steps.size() >= SkillSpec.MAX_COMBO_STEPS) {
                plugin.getLogger().warning("skills: " + where + ": a combo has at most " + SkillSpec.MAX_COMBO_STEPS + " steps (the rest are skipped)");
                break;
            }
            Map<String, Object> entry = toMap(item);
            if (entry == null) {
                plugin.getLogger().warning("skills: " + where + ": must be a section (skipped)");
                continue;
            }
            SkillSpec stepSpec;
            if (entry.get("skill") != null) {
                String ref = entry.get("skill").toString().trim();
                SkillSpec base = skills.get(ref);
                if (base == null) {
                    plugin.getLogger().warning("skills: " + where + ": unknown skill '" + ref + "'"
                            + (rawIsCombo(ref) ? " (a combo cannot contain a combo)" : "") + " (skipped)");
                    continue;
                }
                if (base.template() == SkillTemplate.COMBO) {
                    plugin.getLogger().warning("skills: " + where + ": '" + ref + "' is a combo; a combo cannot contain a combo (skipped)");
                    continue;
                }
                stepSpec = parse(where, entry, base, ref);
            } else {
                stepSpec = parse(where, entry, null, "");
            }
            if (stepSpec == null) continue;
            int delay = (int) Math.round(number(where, flatten(entry), "delay-ticks", 0, 0, 240));
            boolean with = Boolean.TRUE.equals(entry.get("with-previous")) && !steps.isEmpty();
            SkillSpec.ComboStep step = new SkillSpec.ComboStep(stepSpec, delay, with);
            int start = with ? previousStart + delay : ticks + delay;
            if (start >= SkillSpec.MAX_COMBO_TICKS) {
                plugin.getLogger().warning("skills: " + where + ": it would start after " + SkillSpec.MAX_COMBO_TICKS / 20 + " s; a combo is at most "
                        + SkillSpec.MAX_COMBO_TICKS / 20 + " s long (the rest are skipped)");
                break;
            }
            ticks = Math.max(ticks, start + stepSpec.estimatedTicks());
            previousStart = start;
            steps.add(step);
        }
        if (steps.isEmpty()) {
            plugin.getLogger().warning("skills: " + owner + ": a combo with no usable step (skipped)");
            return null;
        }
        if (ticks > SkillSpec.MAX_COMBO_TICKS) {
            plugin.getLogger().warning("skills: " + owner + ": the combo lasts about " + ticks / 20.0 + " s; it is cut at " + SkillSpec.MAX_COMBO_TICKS / 20 + " s");
        }
        return new SkillSpec(self.id(), self.name(), SkillTemplate.COMBO, self.look(), self.radius(), self.range(), self.angle(), self.orientation(),
                self.windupTicks(), self.cooldownTicks(), self.count(), self.intervalTicks(), self.speed(), self.damageRate(),
                self.statuses(), self.healRate(), self.knockback(), self.target(), self.cost(), self.description(), steps);
    }

    private final Set<String> pendingComboIds = new java.util.HashSet<>();

    private boolean rawIsCombo(String id) {
        return pendingComboIds.contains(id);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private double number(String owner, Map<String, Object> flat, String key, double fallback, double min, double max) {
        Object value = flat.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number)) {
            plugin.getLogger().warning("skills: " + owner + ": " + key + " must be a number (got '" + value + "'), used " + fallback);
            return fallback;
        }
        double v = number.doubleValue();
        if (v < min || v > max) {
            double fixed = Math.max(min, Math.min(max, v));
            plugin.getLogger().warning("skills: " + owner + ": " + key + " " + v + " is out of range (" + min + " - " + max + "), used " + fixed);
            return fixed;
        }
        return v;
    }

    private List<Color> colors(String owner, Object raw) {
        List<?> list = raw instanceof List<?> l ? l : List.of(raw);
        List<Color> result = new ArrayList<>();
        for (Object item : list) {
            String text = item == null ? "" : item.toString().trim();
            if (!HEX.matcher(text).matches()) {
                plugin.getLogger().warning("skills: " + owner + ": color '" + text + "' must look like #rrggbb (skipped)");
                continue;
            }
            result.add(Color.fromRGB(Integer.parseInt(text.substring(1), 16)));
            if (result.size() == 3) break;
        }
        return result.isEmpty() ? null : result;
    }

    private List<SkillSpec.StatusApply> statuses(String owner, Object raw) {
        List<SkillSpec.StatusApply> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return result;
        for (Object item : list) {
            Map<String, Object> entry = toMap(item);
            if (entry == null) continue;
            Object typeName = entry.get("type");
            String type = typeName == null ? "" : typeName.toString().trim().toLowerCase(Locale.ROOT);
            if (!STATUS_TYPES.contains(type)) {
                plugin.getLogger().warning("skills: " + owner + ": status '" + type + "' is unknown (" + String.join(" / ", new java.util.TreeSet<>(STATUS_TYPES)) + ")");
                continue;
            }
            double percent = number(owner, entry, "percent", 30.0, 0.0, 100.0);
            double seconds = number(owner, entry, "seconds", 3.0, 0.5, 30.0);
            double value = number(owner, entry, "value", 10.0, 1.0, 1000.0);
            result.add(new SkillSpec.StatusApply(type, percent, seconds, value));
        }
        return result;
    }

    /** Pulls the keys of the look / shape / timing / effects sections up next to the flat ones (flat wins). */
    private Map<String, Object> flatten(Map<String, Object> raw) {
        Map<String, Object> flat = new HashMap<>();
        for (String section : List.of("look", "shape", "timing", "effects")) {
            Map<String, Object> inner = toMap(raw.get(section));
            if (inner != null) flat.putAll(inner);
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!List.of("look", "shape", "timing", "effects").contains(entry.getKey())) flat.put(entry.getKey(), entry.getValue());
        }
        return flat;
    }

    /** A yaml section or map as a plain map (nested sections too); null for anything else. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                result.put(key, value instanceof ConfigurationSection inner ? toMap(inner) : value);
            }
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
            return result;
        }
        return null;
    }
}
