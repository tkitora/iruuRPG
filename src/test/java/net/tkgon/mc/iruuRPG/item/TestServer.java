package net.tkgon.mc.iruuRPG.item;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_20_R3.CraftRegistry;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftMagicNumbers;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.mockito.Mockito;

import java.io.File;
import java.util.logging.Logger;

/** Just enough of a server for the item code: the dev bundle's own item factory, data and registries (no world, no network). */
final class TestServer {

    private static boolean started;

    private TestServer() {
    }

    static synchronized void start() {
        if (started) return;
        started = true;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        // a server that answers only what the item code asks (answering by name: stubbing with when() would load Registry before there is a server)
        Logger logger = Logger.getLogger("test");
        Server server = Mockito.mock(Server.class, call -> switch (call.getMethod().getName()) {
            case "getItemFactory" -> CraftItemFactory.instance();
            case "getUnsafe" -> CraftMagicNumbers.INSTANCE;
            case "getRegistry" -> registry(call.getArgument(0), access);
            case "getLogger" -> logger;
            case "createProfile" -> new com.destroystokyo.paper.profile.CraftPlayerProfile(call.getArgument(0), call.getArguments().length > 1 ? call.getArgument(1) : null);
            case "getName", "getVersion" -> "test";
            case "getBukkitVersion" -> "1.20.4-R0.1-SNAPSHOT";
            default -> Mockito.RETURNS_DEFAULTS.answer(call);
        });
        if (Bukkit.getServer() == null) Bukkit.setServer(server);
        CraftRegistry.setMinecraftRegistry(access);
    }

    /** The server's registry for the built-in kinds (enchantments, ...); an empty one for the kinds that come from data packs (not used here). */
    private static Object registry(Class<? extends org.bukkit.Keyed> kind, RegistryAccess access) {
        try {
            return CraftRegistry.createRegistry(kind, access);
        } catch (IllegalStateException missing) {
            return Mockito.mock(org.bukkit.Registry.class);
        }
    }

    /** The plugin, with its data folder somewhere else (config.yml comes from the resources). */
    static final class TestPlugin extends JavaPlugin {
        @SuppressWarnings("deprecation")
        TestPlugin(File folder) {
            super(new JavaPluginLoader(Bukkit.getServer()), new PluginDescriptionFile("iruuRPG", "1.0", "net.tkgon.mc.iruuRPG.Test"),
                    folder, new File(folder, "iruuRPG.jar"));
        }
    }
}
