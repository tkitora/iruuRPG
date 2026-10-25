package net.tkgon.mc.iruuRPG.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.function.Predicate;

/**
 * A small web server that serves the resource pack files of the folder (GET only, and only the files the config switched on), for a server that has no web server of its own.
 * Off unless {@code resourcepack.host.enabled}; {@code resourcepack.host.port} must be open to the players, and {@code resourcepack.host.public-host} is the address they reach it by.
 */
final class PackHost {

    private final JavaPlugin plugin;
    private final HttpServer server;
    private final int port;

    private PackHost(JavaPlugin plugin, HttpServer server, int port) {
        this.plugin = plugin;
        this.server = server;
        this.port = port;
    }

    static PackHost start(JavaPlugin plugin, File folder, Predicate<String> servable) {
        int port = plugin.getConfig().getInt("resourcepack.host.port", 8765);
        String bind = plugin.getConfig().getString("resourcepack.host.bind", "0.0.0.0");
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext("/", exchange -> handle(exchange, folder, servable));
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "iruurpg-pack-host");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            plugin.getLogger().info("Resource pack host listening on " + bind + ":" + port);
            return new PackHost(plugin, server, port);
        } catch (IOException exception) {
            plugin.getLogger().warning("Resource pack host could not start on port " + port + ": " + exception.getMessage());
            return null;
        }
    }

    private static void handle(HttpExchange exchange, File folder, Predicate<String> servable) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String name = path.startsWith("/") ? path.substring(1) : path;
            File file = new File(folder, name);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) || !servable.test(name) || !file.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    /** The address players reach the host by: {@code http://<public-host>:<port>/}; null without a public host. */
    String publicUrl() {
        String host = plugin.getConfig().getString("resourcepack.host.public-host", "");
        return host == null || host.isBlank() ? null : "http://" + host + ":" + port + "/";
    }

    void stop() {
        server.stop(0);
    }
}
