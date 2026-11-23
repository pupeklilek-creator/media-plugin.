package cz.media;

import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MediaIcon extends JavaPlugin implements Listener {

    private static final String ICON = "\uE000";

    private String rankText;
    private byte[] zip;
    private byte[] sha1;
    private String url;
    private boolean force;
    private HttpServer http;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this,
                () -> getServer().getOnlinePlayers().forEach(this::updateTab), 100L, 100L);
    }

    @Override
    public void onDisable() {
        stopServer();
    }

    private void load() {
        reloadConfig();
        rankText = getConfig().getString("rank-text", "Media");
        force = getConfig().getBoolean("force-pack", true);
        try {
            buildPack();
            startServer();
        } catch (Exception ex) {
            getLogger().severe("Nepodarilo se vytvorit/spustit resource pack: " + ex);
        }
    }

    // ---------- resource pack ----------

    private byte[] iconPng() throws Exception {
        File custom = new File(getDataFolder(), "camera.png");
        if (custom.exists()) return Files.readAllBytes(custom.toPath());
        try (InputStream in = getResource("camera.png")) {
            return in.readAllBytes();
        }
    }

    private void buildPack() throws Exception {
        int format = getConfig().getInt("pack-format", 22);
        String mcmeta = "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Media icon\"}}";
        // \\uE000 v Java = text \uE000 v JSONu
        String font = "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"minecraft:font/camera.png\","
                + "\"ascent\":8,\"height\":9,\"chars\":[\"\\uE000\"]}]}";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            put(z, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
            put(z, "assets/minecraft/font/default.json", font.getBytes(StandardCharsets.UTF_8));
            put(z, "assets/minecraft/textures/font/camera.png", iconPng());
        }
        zip = bos.toByteArray();
        sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
    }

    private void put(ZipOutputStream z, String name, byte[] data) throws Exception {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L); // stejny obsah = stejny hash
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    private void startServer() throws Exception {
        stopServer();
        int port = getConfig().getInt("http-port", 8765);
        String host = getConfig().getString("public-host", "127.0.0.1");
        url = "http://" + host + ":" + port + "/pack.zip";

        http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/pack.zip", ex -> {
            ex.getResponseHeaders().add("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, zip.length);
            try (var out = ex.getResponseBody()) {
                out.write(zip);
            }
        });
        http.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
        http.start();
        getLogger().info("Resource pack bezi na " + url);
    }

    private void stopServer() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    // ---------- jmeno s kamerkou ----------

    private Component prefix() {
        return Component.text(ICON + " ", NamedTextColor.WHITE)
                .append(Component.text(rankText + " ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
    }

    private void updateTab(Player p) {
        if (p.hasPermission("mediaicon.use")) {
            p.playerListName(prefix().append(Component.text(p.getName(), NamedTextColor.WHITE)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        updateTab(p);
        if (url != null) {
            getServer().getScheduler().runTaskLater(this, () ->
                    p.setResourcePack(url, sha1,
                            Component.text("Pack s ikonkou Media"), force), 20L);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!e.getPlayer().hasPermission("mediaicon.use")) return;
        e.renderer((source, name, msg, viewer) ->
                prefix().append(name).append(Component.text(": ")).append(msg));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        load();
        getServer().getOnlinePlayers().forEach(this::updateTab);
        s.sendMessage("MediaIcon reloaded.");
        return true;
    }
}
