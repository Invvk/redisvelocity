package io.github.invvk.redisvelocity;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

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
        if (identifier.toLowerCase(Locale.ROOT).startsWith("players_")) {
            String server = identifier.toLowerCase(Locale.ROOT).replace("players_", "");
            if (server.equalsIgnoreCase("total")) {

            }
        }
        return null;
    }
}
