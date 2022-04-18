package io.github.invvk.redisvelocity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.invvk.redisvelocity.events.PlayerChangedServerNetworkEvent;
import io.github.invvk.redisvelocity.events.PlayerJoinedNetworkEvent;
import io.github.invvk.redisvelocity.events.PlayerLeftNetworkEvent;
import io.github.invvk.redisvelocity.events.PubSubMessageEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import redis.clients.jedis.Jedis;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * This class manages all the data that RedisVelocity fetches from Redis, along with updates to that data.
 *
 * @since 0.3.3
 */
public class DataManager  {
    private final RedisVelocity plugin;
    private final Cache<UUID, String> serverCache = createCache();
    private final Cache<UUID, String> proxyCache = createCache();
    private final Cache<UUID, InetAddress> ipCache = createCache();
    private final Cache<UUID, Long> lastOnlineCache = createCache();

    public DataManager(RedisVelocity plugin) {
        this.plugin = plugin;
    }

    private static <K, V> Cache<K, V> createCache() {
        // TODO: Allow customization via cache specification, ala ServerListPlus
        return CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    private final JsonParser parser = new JsonParser();

    public String getServer(final UUID uuid) {
        Optional<Player> optional = plugin.getServer().getPlayer(uuid);

        if (optional.isPresent()) {
            Player player = optional.get();
            return player.getCurrentServer().isPresent() ? player.getCurrentServer().get().getServerInfo().getName() : null;
        }

        try {
            return serverCache.get(uuid, () -> {
                try (Jedis tmpRsc = plugin.getPool().getResource()) {
                    return Objects.requireNonNull(tmpRsc.hget("player:" + uuid, "server"), "user not found");
                }
            });
        } catch (ExecutionException | UncheckedExecutionException e) {
            if (e.getCause() instanceof NullPointerException && e.getCause().getMessage().equals("user not found"))
                return null; // HACK
            plugin.getLogger().error("Unable to get server", e);
            throw new RuntimeException("Unable to get server for " + uuid, e);
        }
    }

    public String getProxy(final UUID uuid) {
        Optional<Player> optional = plugin.getServer().getPlayer(uuid);

        if (optional.isPresent()) {
            Player player = optional.get();
            return RedisVelocity.getConfiguration().getServerId();
        }

        try {
            return proxyCache.get(uuid, () -> {
                try (Jedis tmpRsc = plugin.getPool().getResource()) {
                    return Objects.requireNonNull(tmpRsc.hget("player:" + uuid, "proxy"), "user not found");
                }
            });
        } catch (ExecutionException | UncheckedExecutionException e) {
            if (e.getCause() instanceof NullPointerException && e.getCause().getMessage().equals("user not found"))
                return null; // HACK
            plugin.getLogger().error( "Unable to get proxy", e);
            throw new RuntimeException("Unable to get proxy for " + uuid, e);
        }
    }

    public InetAddress getIp(final UUID uuid) {
        Optional<Player> optional = plugin.getServer().getPlayer(uuid);

        if (optional.isPresent()) {
            Player player = optional.get();
            return player.getRemoteAddress().getAddress();
        }

        try {
            return ipCache.get(uuid, () -> {
                try (Jedis tmpRsc = plugin.getPool().getResource()) {
                    String result = tmpRsc.hget("player:" + uuid, "ip");
                    if (result == null)
                        throw new NullPointerException("user not found");
                    return InetAddresses.forString(result);
                }
            });
        } catch (ExecutionException | UncheckedExecutionException e) {
            if (e.getCause() instanceof NullPointerException && e.getCause().getMessage().equals("user not found"))
                return null; // HACK
            plugin.getLogger().error("Unable to get IP", e);
            throw new RuntimeException("Unable to get IP for " + uuid, e);
        }
    }

    public long getLastOnline(final UUID uuid) {
        Optional<Player> optional = plugin.getServer().getPlayer(uuid);

        if (optional.isPresent()) {
            return 0;
        }

        try {
            return lastOnlineCache.get(uuid, new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    try (Jedis tmpRsc = plugin.getPool().getResource()) {
                        String result = tmpRsc.hget("player:" + uuid, "online");
                        return result == null ? -1 : Long.parseLong(result);
                    }
                }
            });
        } catch (ExecutionException e) {
            plugin.getLogger().error("Unable to get last time online", e);
            throw new RuntimeException("Unable to get last time online for " + uuid, e);
        }
    }

    private void invalidate(UUID uuid) {
        ipCache.invalidate(uuid);
        lastOnlineCache.invalidate(uuid);
        serverCache.invalidate(uuid);
        proxyCache.invalidate(uuid);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        // Invalidate all entries related to this player, since they now lie.
        invalidate(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        // Invalidate all entries related to this player, since they now lie.
        invalidate(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onPubSubMessage(PubSubMessageEvent event) {
        if (!event.getChannel().equals("redisvelocity-data"))
            return;

        // Partially deserialize the message so we can look at the action
        JsonObject jsonObject = parser.parse(event.getMessage()).getAsJsonObject();

        String source = jsonObject.get("source").getAsString();

        if (source.equals(RedisVelocity.getConfiguration().getServerId()))
            return;

        DataManagerMessage.Action action = DataManagerMessage.Action.valueOf(jsonObject.get("action").getAsString());

        switch (action) {
            case JOIN:
                final DataManagerMessage<LoginPayload> message1 = RedisVelocity.getGson().fromJson(jsonObject, new TypeToken<DataManagerMessage<LoginPayload>>() {
                }.getType());
                proxyCache.put(message1.getTarget(), message1.getSource());
                lastOnlineCache.put(message1.getTarget(), (long) 0);
                ipCache.put(message1.getTarget(), message1.getPayload().getAddress());
                plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        plugin.getServer().getEventManager().fire(new PlayerJoinedNetworkEvent(message1.getTarget()));
                    }
                }).schedule();
                break;
            case LEAVE:
                final DataManagerMessage<LogoutPayload> message2 = RedisVelocity.getGson().fromJson(jsonObject, new TypeToken<DataManagerMessage<LogoutPayload>>() {
                }.getType());
                invalidate(message2.getTarget());
                lastOnlineCache.put(message2.getTarget(), message2.getPayload().getTimestamp());
                plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        plugin.getServer().getEventManager()
                                .fire(new PlayerLeftNetworkEvent(message2.getTarget()));
                    }
                }).schedule();
                break;
            case SERVER_CHANGE:
                final DataManagerMessage<ServerChangePayload> message3 = RedisVelocity.getGson().fromJson(jsonObject, new TypeToken<DataManagerMessage<ServerChangePayload>>() {
                }.getType());
                serverCache.put(message3.getTarget(), message3.getPayload().getServer());
                plugin.getServer().getScheduler().buildTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        plugin.getServer().getEventManager().fire(new PlayerChangedServerNetworkEvent(message3.getTarget(), message3.getPayload().getOldServer(), message3.getPayload().getServer()));
                    }
                }).schedule();
                break;
        }
    }

    @Getter
    @RequiredArgsConstructor
    static class DataManagerMessage<T> {
        private final UUID target;
        private final String source = RedisVelocityAPI.getRedisVelocityApi().getServerId();
        private final Action action; // for future use!
        private final T payload;

        enum Action {
            JOIN,
            LEAVE,
            SERVER_CHANGE
        }
    }

    @Getter
    @RequiredArgsConstructor
    static class LoginPayload {
        private final InetAddress address;
    }

    @Getter
    @RequiredArgsConstructor
    static class ServerChangePayload {
        private final String server;
        private final String oldServer;
    }

    @Getter
    @RequiredArgsConstructor
    static class LogoutPayload {
        private final long timestamp;
    }
}
