package net.tkgon.mc.iruuRPG.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Quest rules. Main quests are fixed and one-time (unlocked in order). Sub quests are generated from templates
 * once per day (same for everybody and every town) and are cleared when the day rolls over. Rewards are currency.
 */
public final class QuestService {

    public record Entry(QuestDefinition quest, QuestState state, int progress) {
    }

    private static final long EXPLANATION_LINE_TICKS = 50L;
    private static final String SUB_PREFIX = "sub_";

    private final JavaPlugin plugin;
    private final QuestRegistry registry;
    private final PlayerProfileManager profileManager;
    private final ItemIdentifier itemIdentifier;

    private String cachedDay = "";
    private List<QuestDefinition> cachedSubQuests = List.of();

    public QuestService(JavaPlugin plugin, QuestRegistry registry, PlayerProfileManager profileManager, ItemIdentifier itemIdentifier) {
        this.plugin = plugin;
        this.registry = registry;
        this.profileManager = profileManager;
        this.itemIdentifier = itemIdentifier;
    }

    public String currencyName() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    // ---- daily rotation ---------------------------------------------------------------------------------

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("quest.time-zone", "Asia/Tokyo"));
        } catch (Exception e) {
            return ZoneId.of("Asia/Tokyo");
        }
    }

    private int resetHour() {
        return Math.max(0, Math.min(23, plugin.getConfig().getInt("quest.sub-reset-hour", 4)));
    }

    /** The "day" the sub quests belong to: it changes every day at the reset hour. */
    public String dayKey() {
        LocalDate day = ZonedDateTime.now(zone()).minusHours(resetHour()).toLocalDate();
        return day.toString();
    }

    /** Minutes until the sub quests are re-rolled. */
    public long minutesUntilRefresh() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime next = now.toLocalDate().atStartOfDay(zone()).plusHours(resetHour());
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Math.max(1L, java.time.Duration.between(now, next).toMinutes());
    }

    /** Today's sub quests: a random pick of templates, targets, amounts and rewards, fixed for the whole day. */
    public List<QuestDefinition> subQuestsToday() {
        String day = dayKey();
        if (day.equals(cachedDay)) return cachedSubQuests;

        List<QuestRegistry.SubTemplate> templates = registry.subTemplates();
        List<QuestDefinition> result = new ArrayList<>();
        if (!templates.isEmpty()) {
            Random random = new Random(day.hashCode() * 31L + 7L);
            int count = Math.max(1, plugin.getConfig().getInt("quest.sub-count", 4));
            Set<String> used = new HashSet<>();
            for (int attempt = 0; attempt < count * 20 && result.size() < count; attempt++) {
                QuestRegistry.SubTemplate template = templates.get(random.nextInt(templates.size()));
                QuestRegistry.Target target = template.targets().get(random.nextInt(template.targets().size()));
                if (!used.add(template.id() + ":" + target.id())) continue;

                int amount = template.minAmount() + random.nextInt(template.maxAmount() - template.minAmount() + 1);
                double jitter = 0.9 + random.nextDouble() * 0.2;
                long reward = Math.max(5L, Math.round(amount * template.rewardPerUnit() * jitter / 5.0) * 5L);
                result.add(new QuestDefinition(
                        SUB_PREFIX + day + "_" + result.size(),
                        QuestCategory.SUB,
                        template.type(),
                        fill(template.name(), target, amount),
                        template.description().stream().map(line -> fill(line, target, amount)).toList(),
                        target.id(),
                        target.name(),
                        amount,
                        reward,
                        Set.of(),
                        List.of(),
                        List.of()
                ));
            }
        }
        cachedDay = day;
        cachedSubQuests = result;
        return result;
    }

    private static String fill(String text, QuestRegistry.Target target, int amount) {
        return text.replace("{target}", target.name()).replace("{amount}", String.valueOf(amount)).replace("{unit}", target.unit());
    }

    /** Clears yesterday's sub quests from the player's record when the day changed. */
    public void refresh(PlayerProfile profile) {
        String day = dayKey();
        if (day.equals(profile.subQuestDay())) return;

        profile.questProgress().keySet().removeIf(id -> id.startsWith(SUB_PREFIX));
        profile.completedSubQuests().clear();
        profile.setSubQuestDay(day);
    }

    // ---- what the board shows --------------------------------------------------------------------------------

    public Optional<QuestDefinition> find(String id) {
        Optional<QuestDefinition> main = registry.findMain(id);
        if (main.isPresent()) return main;
        return subQuestsToday().stream().filter(quest -> quest.id().equals(id)).findFirst();
    }

    public List<Entry> mainEntries(Player player, String town) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        refresh(profile);
        List<Entry> entries = new ArrayList<>();
        for (QuestDefinition quest : registry.mainQuests()) {
            if (!quest.availableIn(town)) continue;

            QuestState state = stateOf(profile, quest);
            boolean unlocked = profile.completedQuests().containsAll(quest.requires());
            if (state == QuestState.AVAILABLE && !unlocked) continue; // not unlocked yet: hidden
            entries.add(new Entry(quest, state, profile.questProgress().getOrDefault(quest.id(), 0)));
        }
        return entries;
    }

    public List<Entry> subEntries(Player player, String town) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        refresh(profile);
        List<Entry> entries = new ArrayList<>();
        for (QuestDefinition quest : subQuestsToday()) {
            if (!quest.availableIn(town)) continue;
            entries.add(new Entry(quest, stateOf(profile, quest), profile.questProgress().getOrDefault(quest.id(), 0)));
        }
        return entries;
    }

    public QuestState stateOf(PlayerProfile profile, QuestDefinition quest) {
        boolean done = quest.category() == QuestCategory.MAIN
                ? profile.completedQuests().contains(quest.id())
                : profile.completedSubQuests().contains(quest.id());
        if (done) return QuestState.DONE;

        Integer progress = profile.questProgress().get(quest.id());
        if (progress == null) return QuestState.AVAILABLE;
        return progress >= quest.amount() ? QuestState.READY : QuestState.IN_PROGRESS;
    }

    // ---- actions ---------------------------------------------------------------------------------------------

    public void accept(Player player, QuestDefinition quest) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        refresh(profile);
        if (stateOf(profile, quest) != QuestState.AVAILABLE) return;

        profile.questProgress().put(quest.id(), 0);
        player.sendMessage(Component.text("[依頼] 「" + quest.name() + "」を受けた。", NamedTextColor.YELLOW));
        if (quest.type() == QuestType.INFO) {
            playExplanation(player, quest);
        }
        profileManager.save(player);
    }

    /** Reads the explanation line by line; the quest is done when the last line has been said. */
    public void playExplanation(Player player, QuestDefinition quest) {
        List<String> lines = quest.explanation();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            boolean last = index == lines.size() - 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Component.text("受付 ", NamedTextColor.GOLD).append(Component.text("「" + line + "」", NamedTextColor.WHITE)));
                if (last) {
                    PlayerProfile profile = profileManager.getOrCreate(player);
                    if (profile.questProgress().containsKey(quest.id())) {
                        profile.questProgress().put(quest.id(), quest.amount());
                        player.sendMessage(Component.text("[依頼] 「" + quest.name() + "」を達成した。ボードで報酬を受け取ろう。", NamedTextColor.GREEN));
                        profileManager.save(player);
                    }
                }
            }, EXPLANATION_LINE_TICKS * (index + 1));
        }
        if (lines.isEmpty()) {
            profileManager.getOrCreate(player).questProgress().put(quest.id(), quest.amount());
        }
    }

    /** A mob of this id was killed by the player (called for everyone who dealt enough damage). */
    public void onMobKilled(Player player, String mobId) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        refresh(profile);
        for (String id : new ArrayList<>(profile.questProgress().keySet())) {
            QuestDefinition quest = find(id).orElse(null);
            if (quest == null || quest.type() != QuestType.KILL || !quest.target().equals(mobId)) continue;

            int progress = profile.questProgress().getOrDefault(id, 0);
            if (progress >= quest.amount()) continue;

            progress++;
            profile.questProgress().put(id, progress);
            if (progress >= quest.amount()) {
                player.sendMessage(Component.text("[依頼] 「" + quest.name() + "」を達成した。ボードで報酬を受け取ろう。", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("[依頼] " + quest.name() + " " + progress + "/" + quest.amount(), NamedTextColor.GRAY));
            }
        }
    }

    /** Hands in the items. Returns true if the quest is now ready. */
    public boolean deliver(Player player, QuestDefinition quest) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (stateOf(profile, quest) != QuestState.IN_PROGRESS || quest.type() != QuestType.DELIVER) return false;

        if (countItems(player, quest.target()) < quest.amount()) {
            player.sendMessage(Component.text("[依頼] " + quest.targetName() + "が足りない。(" + countItems(player, quest.target()) + "/" + quest.amount() + ")", NamedTextColor.RED));
            return false;
        }
        removeItems(player, quest.target(), quest.amount());
        profile.questProgress().put(quest.id(), quest.amount());
        player.sendMessage(Component.text("[依頼] " + quest.targetName() + "を納品した。報酬を受け取ろう。", NamedTextColor.GREEN));
        profileManager.save(player);
        return true;
    }

    public void claim(Player player, QuestDefinition quest) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (stateOf(profile, quest) != QuestState.READY) return;

        profile.questProgress().remove(quest.id());
        if (quest.category() == QuestCategory.MAIN) {
            profile.completedQuests().add(quest.id());
        } else {
            profile.completedSubQuests().add(quest.id());
        }
        profile.setCurrency(profile.currency() + quest.reward());
        player.sendMessage(Component.text("[依頼] 報酬 " + quest.reward() + " " + currencyName() + " を受け取った。(所持: " + profile.currency() + ")", NamedTextColor.GOLD));
        profileManager.save(player);
    }

    public PlayerProfile profileOf(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        refresh(profile);
        return profile;
    }

    // ---- debug helpers -----------------------------------------------------------------------------------------

    public void addCurrency(Player player, long amount) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.setCurrency(profile.currency() + amount);
        profileManager.save(player);
    }

    public long currency(Player player) {
        return profileManager.getOrCreate(player).currency();
    }

    /** Wipes the player's quest record (not the currency). */
    public void reset(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.questProgress().clear();
        profile.completedQuests().clear();
        profile.completedSubQuests().clear();
        profile.setSubQuestDay("");
        profileManager.save(player);
    }

    /** Forces a new random sub quest set right now (testing). */
    public void rerollNow() {
        cachedDay = "";
        cachedSubQuests = List.of();
    }

    // ---- inventory --------------------------------------------------------------------------------------------

    private int countItems(Player player, String itemId) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (itemIdentifier.itemId(stack).filter(itemId::equals).isPresent()) total += stack.getAmount();
        }
        return total;
    }

    private void removeItems(Player player, String itemId, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || itemIdentifier.itemId(stack).filter(itemId::equals).isEmpty()) continue;

            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
    }
}
