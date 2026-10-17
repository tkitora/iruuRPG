package net.tkgon.mc.iruuRPG.skill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * {@code /skilltest <id> [self|dummy] [element]} (admins): plays a skill so you can look at it, without hurting
 * anybody. {@code self}: a stand-in casts it from 8 blocks in front of you, aimed at you (you see the warning and
 * the hit come, and are told whether it would have hit you). {@code dummy}: you are the caster and the skill goes
 * the way you look. The id may also be a template name (strike, sweep, ...) for its defaults.
 */
public final class SkillTestCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final SkillRegistry registry;
    private final SkillRunner runner;

    public SkillTestCommand(JavaPlugin plugin, SkillRegistry registry, SkillRunner runner) {
        this.plugin = plugin;
        this.registry = registry;
        this.runner = runner;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        if (!player.hasPermission("iruurpg.admin")) {
            player.sendMessage(Component.text("[skilltest] 管理者のみです。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("[skilltest] /skilltest <id> [self|dummy] [red|blue|white|green|orange]  (id: skills/*.yml のスキル、または "
                    + String.join(" / ", templateNames()) + ")", NamedTextColor.YELLOW));
            return true;
        }
        SkillSpec spec = registry.find(args[0]).orElse(null);
        if (spec == null) {
            player.sendMessage(Component.text("[skilltest] そのスキルはありません: " + args[0], NamedTextColor.RED));
            return true;
        }
        boolean dummy = args.length > 1 && args[1].equalsIgnoreCase("dummy");
        Element element = Element.WHITE;
        if (args.length > 2) {
            try {
                element = Element.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("[skilltest] 属性は red / blue / white / green / orange です (white で再生します)。", NamedTextColor.YELLOW));
            }
        }

        TestCaster caster = new TestCaster(player, dummy, element);
        Location aimPoint = dummy
                ? player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(Math.max(spec.range(), 6.0)))
                : null;
        player.sendMessage(Component.text("[skilltest] " + (spec.id().isEmpty() ? spec.template().id() : spec.id())
                + " (" + spec.template().id() + ") を再生します" + (dummy ? " (あなたから前へ)" : " (前方8ブロックから、あなたに向けて)"), NamedTextColor.GRAY));
        runner.start(caster, spec,
                () -> dummy ? aimPoint : (player.isOnline() ? player.getLocation().add(0.0, player.getHeight() * 0.6, 0.0) : null),
                () -> {
                    caster.stop();
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("[skilltest] 終わり。" + (dummy ? "" : (caster.hits() > 0
                                ? " あなたに当たりました (" + caster.hits() + "回。テストなのでダメージ・効果はなし)"
                                : " あなたには当たりませんでした")), NamedTextColor.GRAY));
                    }
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(registry.all().keySet());
            result.addAll(templateNames());
        } else if (args.length == 2) {
            result.add("self");
            result.add("dummy");
        } else if (args.length == 3) {
            for (Element element : Element.values()) result.add(element.name().toLowerCase(Locale.ROOT));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        result.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(prefix));
        return result;
    }

    private static List<String> templateNames() {
        List<String> names = new ArrayList<>();
        for (SkillTemplate template : SkillTemplate.values()) names.add(template.id());
        return names;
    }

    /** The stand-in that casts the test skill: nothing is hurt, hits are only counted. */
    private final class TestCaster implements SkillCaster {
        private final Player admin;
        private final boolean dummy;
        private final Element element;
        private final Location location;
        private int hits;
        private boolean stopped;

        private TestCaster(Player admin, boolean dummy, Element element) {
            this.admin = admin;
            this.dummy = dummy;
            this.element = element;
            Location base = admin.getLocation().clone();
            if (dummy) {
                this.location = base;
            } else {
                Vector ahead = base.getDirection().setY(0.0);
                if (ahead.lengthSquared() < 1.0E-6) ahead = new Vector(0.0, 0.0, 1.0);
                this.location = base.add(ahead.normalize().multiply(8.0));
                this.location.setDirection(base.toVector().subtract(this.location.toVector()).setY(0.0));
            }
        }

        private int hits() {
            return hits;
        }

        private void stop() {
            stopped = true;
        }

        @Override
        public org.bukkit.World world() {
            return location.getWorld();
        }

        @Override
        public Location location() {
            return location.clone();
        }

        @Override
        public Location eye() {
            return location.clone().add(0.0, 1.6, 0.0);
        }

        @Override
        public boolean valid() {
            return !stopped && admin.isOnline() && admin.getWorld().equals(location.getWorld());
        }

        @Override
        public Element element() {
            return element;
        }

        @Override
        public void freeze() {
        }

        @Override
        public void release() {
        }

        @Override
        public void face(Location at) {
            Location look = location.clone();
            look.setDirection(at.toVector().subtract(eye().toVector()));
            location.setYaw(look.getYaw());
            location.setPitch(look.getPitch());
        }

        @Override
        public void swing() {
        }

        @Override
        public void lunge(Vector velocity) {
            // moves the stand-in the way a real push would, over a few ticks
            Vector step = velocity.clone().setY(0.0).multiply(0.9);
            new BukkitRunnable() {
                private int age;

                @Override
                public void run() {
                    if (stopped || age++ >= 8) {
                        cancel();
                        return;
                    }
                    location.add(step);
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        @Override
        public List<Player> candidates() {
            return dummy ? List.of() : List.of(admin);
        }

        @Override
        public boolean applyEffects() {
            return false;
        }

        @Override
        public void damage(Player victim, double rate) {
            hits++;
        }
    }
}
