package de.lamamc.sift.pack;

import de.lamamc.sift.core.PackSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends the Sift resource pack (hosted on GitHub Releases or any URL) to every player on join. The
 * SHA-1 comes from config.yml or, if empty there, from downloading the pack once at startup.
 */
public final class ResourcePackService implements Listener {

    private final JavaPlugin plugin;
    private Settings settings;
    /** SHA-1 of the pack at settings.url, null until known */
    private volatile String sha1;
    private final Set<UUID> waiting = new HashSet<>();

    private record Settings(boolean enabled, String url, String configuredSha1, boolean required, Component prompt,
                            UUID id, Component kickMessage, Component failedMessage) {
    }

    public ResourcePackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reads the resource-pack section and (re)determines the SHA-1. */
    public void load(ConfigurationSection config) {
        MiniMessage mm = MiniMessage.miniMessage();
        String version = plugin.getPluginMeta().getVersion();
        settings = new Settings(
                config.getBoolean("enabled", true),
                PackSource.resolveUrl(config.getString("url", ""), version),
                PackSource.normalizeSha1(config.getString("sha1", "")),
                config.getBoolean("required", true),
                mm.deserialize(config.getString("prompt", "")),
                UUID.fromString(config.getString("uuid", "5f1a7c2e-3b84-4d6e-9a10-7c3e2b9d4f61")),
                mm.deserialize(config.getString("kick-on-decline", "")),
                mm.deserialize(config.getString("failed-message", "")));
        sha1 = null;
        if (!settings.enabled()) {
            plugin.getLogger().info("Resource pack is disabled (resource-pack.enabled)");
            return;
        }
        if (settings.configuredSha1() != null) {
            sha1 = settings.configuredSha1();
            plugin.getLogger().info("Resource pack " + settings.url() + " (SHA-1 from config.yml: " + sha1 + ")");
            sendToWaiting();
            return;
        }
        Settings current = settings;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> computeSha1(current));
    }

    private void computeSha1(Settings current) {
        try {
            String hash = download(current.url());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (settings != current) {
                    return; // reloaded in the meantime
                }
                sha1 = hash;
                plugin.getLogger().info("Resource pack " + current.url() + " (SHA-1 " + hash + ")");
                sendToWaiting();
            });
        } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not download the resource pack from " + current.url()
                    + " to compute its SHA-1; players will not get it until /sift pack reload", e);
        }
    }

    private static String download(String url) throws IOException, InterruptedException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build()) {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return PackSource.hex(digest.digest());
    }

    private void sendToWaiting() {
        for (UUID id : Set.copyOf(waiting)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                send(player);
            }
        }
        waiting.clear();
    }

    /** Sends the pack now, or as soon as its SHA-1 is known; false if the pack is disabled. */
    public boolean send(Player player) {
        if (settings == null || !settings.enabled()) {
            return false;
        }
        String hash = sha1;
        if (hash == null) {
            waiting.add(player.getUniqueId());
            return true;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(settings.id(), URI.create(settings.url()), hash);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(settings.required())
                .prompt(settings.prompt())
                .replace(false)
                .build());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (settings == null || !settings.id().equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case DECLINED -> {
                if (settings.required()) {
                    player.kick(settings.kickMessage());
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                plugin.getLogger().warning("Resource pack for " + player.getName() + ": " + event.getStatus());
                player.sendMessage(settings.failedMessage());
            }
            default -> {
            }
        }
    }

    /** For /sift pack info. */
    public String describe() {
        if (settings == null || !settings.enabled()) {
            return "aus";
        }
        return settings.url() + " | SHA-1 " + (sha1 == null ? "(wird ermittelt)" : sha1)
                + " | Pflicht " + settings.required();
    }
}
