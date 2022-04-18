package io.github.invvk.redisvelocity;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class PlaceholderHook extends PlaceholderExpansion {

    private final RedisVelocitySpigot plugin;

    @Override
    public @NotNull String getIdentifier() {
        return "redisvelocity";
    }

    @Override
    public @NotNull String getAuthor() {
        return "UltimisMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(@NotNull Player player, @NotNull String identifier) {
        if (identifier.startsWith("players_")) {
            String server = identifier.replace("players_", "");
            int online = plugin.getOnlinePlayers(
                    player,
                    server.equalsIgnoreCase("total")
                    ? "ALL" : server
            );
            return Integer.toString(online);
        }
        if (identifier.equalsIgnoreCase("proxy")) {
            String currentProxy = plugin.getProxyFor(player);
            return currentProxy == null ? "N/A" : currentProxy;
        }
        return null;
    }
}
