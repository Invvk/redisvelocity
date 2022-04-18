package io.github.invvk.redisvelocity;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * This class contains subclasses that are used for the commands RedisVelocity overrides or includes: /glist, /find and /lastseen.
 * <p>
 * All classes use the {@link RedisVelocityAPI}.
 *
 * @author tuxed
 * @since 0.2.3
 */
class RedisVelocityCommands {
    private static final TextComponent NO_PLAYER_SPECIFIED =
            LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou must specify a player name.");
    private static final TextComponent PLAYER_NOT_FOUND =
            LegacyComponentSerializer.legacyAmpersand().deserialize("&cNo such player found.");
    private static final TextComponent NO_COMMAND_SPECIFIED =
            LegacyComponentSerializer.legacyAmpersand().deserialize("You must specify a command to be run.");

    private static String playerPlural(int num) {
        return num == 1 ? num + " player is" : num + " players are";
    }

    public static class GlistCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        GlistCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                @Override
                public void run() {
                    int count = RedisVelocityAPI.getRedisVelocityApi().getPlayerCount();
                    TextComponent playersOnline = LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&e" + playerPlural(count) + " currently online.");
                    if (args.length > 0 && args[0].equals("showall")) {
                        Multimap<String, UUID> serverToPlayers = RedisVelocityAPI.getRedisVelocityApi().getServerToPlayers();
                        Multimap<String, String> human = HashMultimap.create();
                        for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
                            human.put(entry.getKey(), plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
                        }
                        for (String server : new TreeSet<>(serverToPlayers.keySet())) {
                            TextComponent msg = LegacyComponentSerializer
                                    .legacyAmpersand().deserialize("&a[" + server +
                                            "] &e(" + serverToPlayers.get(server).size() + "): &f" 
                                            + Joiner.on(", ").join(human.get(server)));
                            sender.sendMessage(msg);
                        }
                        sender.sendMessage(playersOnline);
                    } else {
                        sender.sendMessage(playersOnline);
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&eTo see all players online, use /glist showall."));
                    }
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("velocity.command.list");
        }
    }

    public static class FindCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        FindCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            
            plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (args.length > 0) {
                        UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
                        if (uuid == null) {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                            return;
                        }
                        ServerInfo si = RedisVelocityAPI.getRedisVelocityApi().getServerFor(uuid);
                        if (si != null) {
                            TextComponent message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                    "&b" + args[0] + " is on " + si.getName() + ".");
                            sender.sendMessage(message);
                        } else {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                        }
                    } else {
                        sender.sendMessage(NO_PLAYER_SPECIFIED);
                    }
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("velocity.command.find");
        }
    }

    public static class LastSeenCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        LastSeenCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                if (args.length > 0) {
                    UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
                    if (uuid == null) {
                        sender.sendMessage(PLAYER_NOT_FOUND);
                        return;
                    }
                    long secs = RedisVelocityAPI.getRedisVelocityApi().getLastOnline(uuid);
                    TextComponent message;
                    if (secs == 0) {
                        message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&a" + args[0] + " is currently online."
                        );
                    } else if (secs != -1) {
                       message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&b" + args[0] + " was last online on " + new SimpleDateFormat().format(secs) + "."
                        );
                    } else {
                        message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&c" + args[0] + " has never been online."
                        );
                    }
                    sender.sendMessage(message);
                } else {
                    sender.sendMessage(NO_PLAYER_SPECIFIED);
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.lastseen");
        }
    }

    public static class IpCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        IpCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (args.length > 0) {
                        UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
                        if (uuid == null) {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                            return;
                        }
                        InetAddress ia = RedisVelocityAPI.getRedisVelocityApi().getPlayerIp(uuid);
                        if (ia != null) {

                            TextComponent message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                    "&a" + args[0] + " is connected from " + ia.toString() + ".");
                            sender.sendMessage(message);
                        } else {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                        }
                    } else {
                        sender.sendMessage(NO_PLAYER_SPECIFIED);
                    }
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.ip");
        }
    }

    public static class PlayerProxyCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        PlayerProxyCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (args.length > 0) {
                        UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
                        if (uuid == null) {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                            return;
                        }
                        String proxy = RedisVelocityAPI.getRedisVelocityApi().getProxy(uuid);
                        if (proxy != null) {
                            TextComponent message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                    "&a" + args[0] + " is connected to " + proxy + ".");
                            sender.sendMessage(message);
                        } else {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                        }
                    } else {
                        sender.sendMessage(NO_PLAYER_SPECIFIED);
                    }
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.pproxy");
        }
    }

    public static class SendToAll implements SimpleCommand {
        private final RedisVelocity plugin;

        SendToAll(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            if (args.length > 0) {
                String command = Joiner.on(" ").skipNulls().join(args);
                RedisVelocityAPI.getRedisVelocityApi().sendProxyCommand(command);
                TextComponent message = LegacyComponentSerializer.legacyAmpersand().deserialize("&aSent the command /" + command + " to all proxies.");
                sender.sendMessage(message);
            } else {
                sender.sendMessage(NO_COMMAND_SPECIFIED);
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.sendtoall");
        }
    }

    public static class ServerId implements SimpleCommand {
        private final RedisVelocity plugin;

        ServerId(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();

            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize("&eYou are on " + RedisVelocityAPI.getRedisVelocityApi().getServerId() + ".");
            sender.sendMessage(textComponent);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.serverid");
        }
    }

    public static class ServerIds implements SimpleCommand {

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();

            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(("&eAll server IDs: " + Joiner.on(", ").join(RedisVelocityAPI.getRedisVelocityApi().getAllServers())));
            sender.sendMessage(textComponent);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.serverids");
        }
    }

    public static class PlistCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        PlistCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();

            plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                @Override
                public void run() {
                    String proxy = args.length >= 1 ? args[0] : RedisVelocity.getConfiguration().getServerId();
                    if (!plugin.getServerIds().contains(proxy)) {
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(("&e" + proxy + " is not a valid proxy. See /serverids for valid proxies.")));
                        return;
                    }
                    Set<UUID> players = RedisVelocityAPI.getRedisVelocityApi().getPlayersOnProxy(proxy);
                    TextComponent playersOnline = LegacyComponentSerializer.legacyAmpersand().deserialize(("&e" + playerPlural(players.size()) + " currently on proxy " + proxy + "."));
                    if (args.length >= 2 && args[1].equals("showall")) {
                        Multimap<String, UUID> serverToPlayers = RedisVelocityAPI.getRedisVelocityApi().getServerToPlayers();
                        Multimap<String, String> human = HashMultimap.create();
                        for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
                            if (players.contains(entry.getValue())) {
                                human.put(entry.getKey(), plugin.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
                            }
                        }
                        for (String server : new TreeSet<>(human.keySet())) {
                            TextComponent text = LegacyComponentSerializer.legacyAmpersand().deserialize(
                                    "&c[" + server + "] &e(" + human.get(server).size() + "): &f" +
                                            Joiner.on(", ").join(human.get(server))
                            );
                            sender.sendMessage(text);
                        }
                        sender.sendMessage(playersOnline);
                    } else {
                        sender.sendMessage(playersOnline);
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(("&eTo see all players online, use /plist " + proxy + " showall.")));
                    }
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.plist");
        }
    }

    public static class DebugCommand implements SimpleCommand {
        private final RedisVelocity plugin;

        DebugCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            TextComponent poolActiveStat = LegacyComponentSerializer.legacyAmpersand().deserialize("Currently active pool objects: " + plugin.getPool().getNumActive());
            TextComponent poolIdleStat = LegacyComponentSerializer.legacyAmpersand().deserialize("Currently idle pool objects: " + plugin.getPool().getNumIdle());
            TextComponent poolWaitingStat = LegacyComponentSerializer.legacyAmpersand().deserialize("Waiting on free objects: " + plugin.getPool().getNumWaiters());
            sender.sendMessage(poolActiveStat);
            sender.sendMessage(poolIdleStat);
            sender.sendMessage(poolWaitingStat);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.debug");
        }
    }

    public static class GotoCommand implements SimpleCommand {

        private final RedisVelocity plugin;

        GotoCommand(RedisVelocity plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource sender = invocation.source();
            final String[] args = invocation.arguments();
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 0) {
                        UUID uuid = plugin.getUuidTranslator().getTranslatedUuid(args[0], true);
                        if (uuid == null) {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                            return;
                        }
                        ServerInfo si = RedisVelocityAPI.getRedisVelocityApi().getServerFor(uuid);
                        if (si != null) {
                            player.createConnectionRequest(
                                    plugin.getServer().getServer(si.getName()).get()
                            ).connect();
                        } else {
                            sender.sendMessage(PLAYER_NOT_FOUND);
                        }
                    } else {
                        sender.sendMessage(NO_PLAYER_SPECIFIED);
                    }
                }
            }).schedule();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("redisvelocity.command.goto");
        }

    }
}
