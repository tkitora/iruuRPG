package net.tkgon.mc.iruuRPG.item;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RpgItemFactory {

    private static final NamedTextColor STAT_VALUE_COLOR = NamedTextColor.GRAY;

    private final JavaPlugin plugin;
    private final ItemIdentifier identifier;
    private final ItemSkillRegistry skillRegistry;

    public RpgItemFactory(JavaPlugin plugin, ItemIdentifier identifier, ItemSkillRegistry skillRegistry) {
        this.plugin = plugin;
        this.identifier = identifier;
        this.skillRegistry = skillRegistry;
    }

    public ItemStack create(RpgItemDefinition definition) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(displayName(definition));
            meta.lore(lore(definition));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            applyVisualOptions(definition, meta);
            item.setItemMeta(meta);
        }

        identifier.setItemId(item, definition.id());
        return item;
    }

    private void applyVisualOptions(RpgItemDefinition definition, ItemMeta meta) {
        ItemVisualOptions options = definition.visualOptions();

        if (options.glint()) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        if (options.hasSkinTexture() && meta instanceof SkullMeta skullMeta) {
            UUID textureId = UUID.nameUUIDFromBytes(("iruurpg:item:" + definition.id()).getBytes(StandardCharsets.UTF_8));
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(textureId);
            profile.setProperty(new ProfileProperty("textures", options.skinTexture()));
            skullMeta.setPlayerProfile(profile);
        }

        if (options.hasLeatherColor() && meta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(options.leatherColor());
        }
    }

    private Component displayName(RpgItemDefinition definition) {
        Component name = Component.text(definition.name(), color(definition.element()));
        if (definition.rarity().tier() <= 0) {
            return name.decoration(TextDecoration.ITALIC, false);
        }

        Component rarity = rarityMarks(definition.rarity());
        if (rarity.equals(Component.empty())) {
            return name.decoration(TextDecoration.ITALIC, false);
        }

        return name.append(Component.space())
                .append(rarity)
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component rarityMarks(Rarity rarity) {
        if (rarity.tier() <= 0) return Component.empty();

        String mark = plugin.getConfig().getString("rarity.mark", "*");
        if (mark == null || mark.isBlank()) return Component.empty();

        return Component.text("[", NamedTextColor.AQUA)
                .append(Component.text(mark.repeat(rarity.tier()), NamedTextColor.YELLOW))
                .append(Component.text("]", NamedTextColor.AQUA));
    }

    private List<Component> lore(RpgItemDefinition definition) {
        List<Component> lore = new ArrayList<>();

        lore.add(Component.text("Lv", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(definition.requiredLevel(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("  " + definition.damageKind().displayName(), NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                .append(Component.text("  " + definition.attackType().displayName(), NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        for (StatType type : StatType.values()) {
            double value = definition.stats().get(type);
            if (Math.abs(value) < 1.0E-9) continue;

            lore.add(statLine(statDisplay(type), value));
        }

        for (Element element : Element.values()) {
            double damage = definition.elementStats().damage(element);
            double damagePercent = definition.elementStats().damagePercent(element);
            double resist = definition.elementStats().resist(element);

            if (Math.abs(damage) >= 1.0E-9) {
                lore.add(elementStatLine("◆", element.displayName() + "属性ダメージ", element, damage, false));
            }
            if (Math.abs(damagePercent) >= 1.0E-9) {
                lore.add(elementStatLine("◆", element.displayName() + "属性ダメージ", element, damagePercent, true));
            }
            if (Math.abs(resist) >= 1.0E-9) {
                lore.add(elementStatLine("◇", element.displayName() + "属性防御", element, resist, true));
            }
        }

        addSkillLore(lore, definition);

        if (!definition.description().isEmpty()) {
            lore.add(Component.empty());
            for (String line : definition.description()) {
                lore.add(Component.text(line, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }

        return lore;
    }

    private void addSkillLore(List<Component> lore, RpgItemDefinition definition) {
        if (!definition.skill().enabled()) return;

        ItemSkillDefinition skill = skillRegistry.definition(definition.skill());
        ItemSkillVariant variant = skill.variant(definition.attackType());

        lore.add(Component.empty());
        lore.add(Component.text("スキル: ", NamedTextColor.GOLD)
                .append(Component.text(skill.displayName(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));

        if (!variant.enabled() || !variant.hasText()) {
            lore.add(skillLine("発動不可", "この攻撃タイプでは発動しない。"));
            return;
        }

        List<String> description = variant.description();
        if (!variant.trigger().isBlank()) {
            if (description.isEmpty()) {
                lore.add(skillLine("発動", variant.trigger()));
            } else {
                lore.add(skillLine(variant.trigger(), description.get(0)));
            }
        } else if (!description.isEmpty()) {
            lore.add(skillText(description.get(0)));
        }

        int descriptionStart = variant.trigger().isBlank() ? 1 : 1;
        for (int index = descriptionStart; index < description.size(); index++) {
            lore.add(skillText(description.get(index)));
        }

        if (variant.hasCost() || variant.hasCooldown()) {
            lore.add(skillLine("Cost", costText(variant)));
        }
        if (variant.hasDuration()) {
            lore.add(skillLine("効果時間", formatTicks(variant.durationTicks(0))));
        }
        for (String line : variant.lore()) {
            lore.add(skillText(line));
        }
    }

    private Component skillLine(String label, String value) {
        return Component.text(label, NamedTextColor.DARK_AQUA)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component skillText(String value) {
        return Component.text(value, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    private String costText(ItemSkillVariant variant) {
        List<String> parts = new ArrayList<>();
        if (variant.hasCost()) {
            parts.add(format(variant.cost(0.0)) + " MP");
        }
        if (variant.hasCooldown()) {
            parts.add("CD " + formatTicks(variant.cooldownTicks(0)));
        }
        return String.join(" / ", parts);
    }

    private String formatTicks(int ticks) {
        double seconds = ticks / 20.0;
        return format(seconds) + "秒";
    }

    private Component statLine(StatDisplay display, double value) {
        String sign = value > 0 ? "+" : "";
        String suffix = display.percent() ? "%" : "";

        return Component.text(display.label(), display.color())
                .append(Component.space())
                .append(Component.text(sign + format(value) + suffix, STAT_VALUE_COLOR))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component elementStatLine(String icon, String label, Element element, double value, boolean percent) {
        String sign = value > 0 ? "+" : "";
        String suffix = percent ? "%" : "";

        return Component.text(icon, color(element))
                .append(Component.space())
                .append(Component.text(label, NamedTextColor.GRAY))
                .append(Component.space())
                .append(Component.text(sign + format(value) + suffix, STAT_VALUE_COLOR))
                .decoration(TextDecoration.ITALIC, false);
    }

    private StatDisplay statDisplay(StatType type) {
        return switch (type) {
            case MAX_HP -> new StatDisplay("最大HP", NamedTextColor.RED, false);
            case HP_REGEN -> new StatDisplay("HP回復", NamedTextColor.RED, false);
            case MAX_MP -> new StatDisplay("最大MP", NamedTextColor.AQUA, false);
            case MP_REGEN -> new StatDisplay("MP回復", NamedTextColor.AQUA, false);
            case WEAPON_DAMAGE -> new StatDisplay("武器ダメージ", NamedTextColor.DARK_RED, false);
            case STRENGTH -> new StatDisplay("筋力", NamedTextColor.RED, false);
            case MAGIC -> new StatDisplay("魔力", NamedTextColor.DARK_AQUA, false);
            case ADD_DAMAGE -> new StatDisplay("追加ダメージ", NamedTextColor.YELLOW, false);
            case MELEE_DAMAGE -> new StatDisplay("近接ダメージ", NamedTextColor.RED, false);
            case MELEE_DAMAGE_PERCENT -> new StatDisplay("近接ダメージ", NamedTextColor.RED, true);
            case RANGE_DAMAGE -> new StatDisplay("遠距離ダメージ", NamedTextColor.DARK_AQUA, false);
            case RANGE_DAMAGE_PERCENT -> new StatDisplay("遠距離ダメージ", NamedTextColor.DARK_AQUA, true);
            case CRIT_DAMAGE -> new StatDisplay("会心ダメージ", NamedTextColor.YELLOW, true);
            case CRIT_CHANCE -> new StatDisplay("会心率", NamedTextColor.YELLOW, true);
            case ADRENALINE -> new StatDisplay("アドレナリン", NamedTextColor.YELLOW, true);
            case MAGIC_OVERLOAD -> new StatDisplay("魔力増幅", NamedTextColor.AQUA, true);
            case STABILITY -> new StatDisplay("安定性", NamedTextColor.GREEN, false);
            case DURATION -> new StatDisplay("効果時間", NamedTextColor.GREEN, false);
            case ATTACK_SPEED -> new StatDisplay("攻撃速度", NamedTextColor.YELLOW, true);
            case MOVE_SPEED -> new StatDisplay("移動速度", NamedTextColor.AQUA, true);
            case DEFENSE -> new StatDisplay("防御", NamedTextColor.YELLOW, false);
            case PROTECTION -> new StatDisplay("保護", NamedTextColor.BLUE, true);
            case PHYSICAL_RESIST -> new StatDisplay("物理耐性", NamedTextColor.DARK_RED, true);
            case MAGIC_RESIST -> new StatDisplay("魔法耐性", NamedTextColor.AQUA, true);
            case DAMAGE_REDUCTION -> new StatDisplay("ダメージ軽減", NamedTextColor.YELLOW, true);
            case SPECIAL_DAMAGE -> new StatDisplay("特殊ダメージ", NamedTextColor.LIGHT_PURPLE, false);
            case BLEED -> new StatDisplay("出血", NamedTextColor.RED, false);
            case ABSORB_PERCENT -> new StatDisplay("吸血", NamedTextColor.GREEN, true);
            case SLOW_PERCENT -> new StatDisplay("鈍足", NamedTextColor.BLUE, true);
            case CORROSION -> new StatDisplay("腐食", NamedTextColor.DARK_GREEN, false);
            case DECAY -> new StatDisplay("腐敗", NamedTextColor.GRAY, false);
            case LACERATION -> new StatDisplay("裂傷", NamedTextColor.DARK_RED, false);
            case CONFUSION -> new StatDisplay("混乱", NamedTextColor.LIGHT_PURPLE, false);
            case EXPLOSION -> new StatDisplay("爆破", NamedTextColor.GOLD, false);
        };
    }

    private NamedTextColor color(Element element) {
        return switch (element) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case WHITE -> NamedTextColor.WHITE;
            case GREEN -> NamedTextColor.GREEN;
            case ORANGE -> NamedTextColor.GOLD;
        };
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return String.valueOf((long) Math.rint(value));
        }

        return String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private record StatDisplay(String label, NamedTextColor color, boolean percent) {
    }
}
