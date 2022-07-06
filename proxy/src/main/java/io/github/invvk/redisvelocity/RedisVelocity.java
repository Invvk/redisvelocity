package io.github.invvk.redisvelocity;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.squareup.okhttp.Dispatcher;
import com.squareup.okhttp.OkHttpClient;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import io.github.invvk.redisvelocity.config.ProxyConfigProperties;
import io.github.invvk.redisvelocity.config.ProxyConfiguration;
import io.github.invvk.redisvelocity.events.PubSubMessageEvent;
import io.github.invvk.redisvelocity.util.IOUtil;
import io.github.invvk.redisvelocity.util.LuaManager;
import io.github.invvk.redisvelocity.util.uuid.NameFetcher;
import io.github.invvk.redisvelocity.util.uuid.UUIDFetcher;
import io.github.invvk.redisvelocity.util.uuid.UUIDTranslator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.slf4j.Logger;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The RedisVelocity plugin.
 * <p>
 * The only function of interest is {@link #getApi()}, which deprecated now,
 * Please check {@link RedisVelocityAPI#getRedisVelocityApi()},
 *
 * which exposes some functions in this class.
 * but if you want old version support,
 * then you can use old method {@link #getApi()}
 *
 */
public final class RedisVelocity {
    @Getter
    private static final Gson gson = new Gson();
    private static RedisVelocityAPI api;
    @Getter(AccessLevel.PACKAGE)
    private static PubSubListener psl = null;
    @Getter
    private JedisPool pool;
    @Getter
    private UUIDTranslator uuidTranslator;
    @Getter(AccessLevel.PACKAGE)
    private static RedisVelocityConfiguration configuration;
    @Getter
    private DataManager dataManager;
    @Getter
    private static OkHttpClient httpClient;
    private volatile List<String> serverIds;
    private final AtomicInteger nagAboutServers = new AtomicInteger();
    private final AtomicInteger globalPlayerCount = new AtomicInteger();
    private Future<?> integrityCheck;
    private Future<?> heartbeatTask;
    private LuaManager.Script serverToPlayersScript;
    private LuaManager.Script getPlayerCountScript;

    private static final Object SERVER_TO_PLAYERS_KEY = new Object();
    private final Cache<Object, Multimap<String, UUID>> serverToPlayersCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    private final ProxyConfiguration pconfig;

    /**
     * Fetch the {@link RedisVelocityAPI} object created on plugin start.
     *
     * @deprecated Please use {@link RedisVelocityAPI#getRedisVelocityApi()}
     *
     * @return the {@link RedisVelocityAPI} object instance.
     */
    @Deprecated
    public static RedisVelocityAPI getApi() {
        return api;
    }

    static PubSubListener getPubSubListener() {
        return psl;
    }

    final List<String> getServerIds() {
        return serverIds;
    }

    private final ProxyServer server;
    private final Logger logger;

    private final ScheduledExecutorService executor;

    @Getter private final Path dataFolder;

    @Inject
    public RedisVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataFolder) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.executor = new ScheduledThreadPoolExecutor(24, new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("redisvelocity-scheduler")
                .build()
        );
        this.pconfig = new ProxyConfiguration();
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getServer() {
        return server;
    }

    private List<String> getCurrentServerIds(boolean nag, boolean lagged) {
        try (Jedis jedis = pool.getResource()) {
            long time = getRedisTime(jedis.time());
            int nagTime = 0;
            if (nag) {
                nagTime = nagAboutServers.decrementAndGet();
                if (nagTime <= 0) {
                    nagAboutServers.set(10);
                }
            }
            ImmutableList.Builder<String> servers = ImmutableList.builder();
            Map<String, String> heartbeats = jedis.hgetAll("heartbeats");
            for (Map.Entry<String, String> entry : heartbeats.entrySet()) {
                try {
                    long stamp = Long.parseLong(entry.getValue());
                    if (lagged ? time >= stamp + 30 : time <= stamp + 30)
                        servers.add(entry.getKey());
                    else if (nag && nagTime <= 0) {
                        getLogger().warn(entry.getKey() + " is " + (time - stamp) + " seconds behind! (Time not synchronized or server down?) and was removed from heartbeat.");
                        jedis.hdel("heartbeats", entry.getKey());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return servers.build();
        } catch (JedisConnectionException e) {
            getLogger().error("Unable to fetch server IDs", e);
            return Collections.singletonList(configuration.getServerId());
        }
    }

    public Set<UUID> getPlayersOnProxy(String server) {
        Preconditions.checkArgument(getServerIds().contains(server), server + " is not a valid proxy ID");
        try (Jedis jedis = pool.getResource()) {
            Set<String> users = jedis.smembers("proxy:" + server + ":usersOnline");
            ImmutableSet.Builder<UUID> builder = ImmutableSet.builder();
            for (String user : users) {
                builder.add(UUID.fromString(user));
            }
            return builder.build();
        }
    }

    final Multimap<String, UUID> serversToPlayers() {
        try {
            return serverToPlayersCache.get(SERVER_TO_PLAYERS_KEY, () -> {
                Collection<String> data = (Collection<String>) serverToPlayersScript.eval(ImmutableList.of(), getServerIds());

                ImmutableMultimap.Builder<String, UUID> builder = ImmutableMultimap.builder();
                String key = null;
                for (String s : data) {
                    if (key == null) {
                        key = s;
                        continue;
                    }

                    builder.put(key, UUID.fromString(s));
                    key = null;
                }

                return builder.build();
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    final int getCount() {
        return globalPlayerCount.get();
    }

    final int getCurrentCount() {
        Long count = (Long) getPlayerCountScript.eval(ImmutableList.of(), ImmutableList.of());
        return count.intValue();
    }

    private Set<String> getLocalPlayersAsUuidStrings() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Player player : getServer().getAllPlayers()) {
            builder.add(player.getUniqueId().toString());
        }
        return builder.build();
    }

    final Set<UUID> getPlayers() {
        ImmutableSet.Builder<UUID> setBuilder = ImmutableSet.builder();
        if (pool != null) {
            try (Jedis rsc = pool.getResource()) {
                List<String> keys = new ArrayList<>();
                for (String i : getServerIds()) {
                    keys.add("proxy:" + i + ":usersOnline");
                }
                if (!keys.isEmpty()) {
                    Set<String> users = rsc.sunion(keys.toArray(new String[0]));
                    if (users != null && !users.isEmpty()) {
                        for (String user : users) {
                            try {
                                setBuilder = setBuilder.add(UUID.fromString(user));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                getLogger().error("Unable to get connection from pool - did your Redis server go away?", e);
                throw new RuntimeException("Unable to get all players online", e);
            }
        }
        return setBuilder.build();
    }

    final void sendProxyCommand(@NonNull String proxyId, @NonNull String command) {
        Preconditions.checkArgument(getServerIds().contains(proxyId) || proxyId.equals("allservers"), "proxyId is invalid");
        sendChannelMessage("redisvelocity-" + proxyId, command);
    }

    final void sendChannelMessage(String channel, String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message);
        } catch (JedisConnectionException e) {
            // Redis server has disappeared!
            getLogger().error("Unable to get connection from pool - did your Redis server go away?", e);
            throw new RuntimeException("Unable to publish channel message", e);
        }
    }

    private long getRedisTime(List<String> timeRes) {
        return Long.parseLong(timeRes.get(0));
    }

    public InputStream getResource(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        } else {
            try {
                URL url = this.getClass().getClassLoader().getResource(filename);
                if (url == null) {
                    return null;
                } else {
                    URLConnection connection = url.openConnection();
                    connection.setUseCaches(false);
                    return connection.getInputStream();
                }
            } catch (IOException var4) {
                return null;
            }
        }
    }

    @Subscribe
    public void onInitial(ProxyInitializeEvent event) {
        try {
            loadConfig();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load/save config", e);
        } catch (JedisConnectionException e) {
            throw new RuntimeException("Unable to connect to your Redis server!", e);
        }
        if (pool != null) {
            try (Jedis tmpRsc = pool.getResource()) {
                // This is more portable than INFO <section>
                String info = tmpRsc.info();
                for (String s : info.split("\r\n")) {
                    if (s.startsWith("redis_version:")) {
                        String version = s.split(":")[1];
                        getLogger().info(version + " <- redis version");
                        if (!RedisUtil.isRedisVersionRight(version)) {
                            getLogger().warn("Your version of Redis (" + version + ") is not at least version 6.0 RedisVelocity requires a newer version of Redis.");
                            throw new RuntimeException("Unsupported Redis version detected");
                        } else {
                            LuaManager manager = new LuaManager(this);
                            serverToPlayersScript = manager.createScript(IOUtil.readInputStreamAsString(getResource("lua/server_to_players.lua")));
                            getPlayerCountScript = manager.createScript(IOUtil.readInputStreamAsString(getResource("lua/get_player_count.lua")));
                        }

                        break;
                    }
                }

                tmpRsc.hset("heartbeats", configuration.getServerId(), tmpRsc.time().get(0));

                long uuidCacheSize = tmpRsc.hlen("uuid-cache");
                if (uuidCacheSize > 750000) {
                    getLogger().info("Looks like you have a really big UUID cache! Run https://www.spigotmc.org/resources/redisbungeecleaner.8505/ as soon as possible.");
                }
            }
            serverIds = getCurrentServerIds(true, false);
            uuidTranslator = new UUIDTranslator(this);
            heartbeatTask = executor.scheduleAtFixedRate(() -> {
                try (Jedis rsc = pool.getResource()) {
                    long redisTime = getRedisTime(rsc.time());
                    rsc.hset("heartbeats", configuration.getServerId(), String.valueOf(redisTime));
                } catch (JedisConnectionException e) {
                    // Redis server has disappeared!
                    getLogger().error("Unable to update heartbeat - did your Redis server go away?", e);
                    return;
                }
                try {
                    serverIds = getCurrentServerIds(true, false);
                    globalPlayerCount.set(getCurrentCount());
                } catch (Throwable e) {
                    getLogger().error("Unable to update data - did your Redis server go away?", e);
                }
            }, 0, 3, TimeUnit.SECONDS);
            dataManager = new DataManager(this);
            CommandManager manager = getServer().getCommandManager();
            if (configuration.isRegisterBungeeCommands()) {
                manager.register(manager.metaBuilder("glist")
                        .aliases("redisvelocity", "rglist").build(),
                        new RedisVelocityCommands.GlistCommand(this));

                manager.register(manager.metaBuilder("find")
                        .aliases("rfind").build(),
                        new RedisVelocityCommands.FindCommand(this));

                manager.register(manager.metaBuilder("lastseen")
                        .aliases("rlastseen")
                        .build(), new RedisVelocityCommands.LastSeenCommand(this));

                manager.register(manager.metaBuilder("ip")
                        .aliases("playerip", "rip", "rplayerip").build(),
                        new RedisVelocityCommands.IpCommand(this));
            }
            manager.register(manager.metaBuilder("sendtoall")
                    .aliases("rsendtoall").build(),
                    new RedisVelocityCommands.SendToAll(this));

            manager.register(manager.metaBuilder("serverid")
                    .aliases("rserverid").build(),
                    new RedisVelocityCommands.ServerId(this));

            manager.register(manager.metaBuilder("serverids")
                    .aliases("rserverids").build(),
                    new RedisVelocityCommands.ServerIds());

            manager.register(manager.metaBuilder("pproxy")
                    .aliases("rpproxy").build(),
                    new RedisVelocityCommands.PlayerProxyCommand(this));

            manager.register(manager.metaBuilder("plist")
                    .aliases("rplist").build(),
                    new RedisVelocityCommands.PlistCommand(this));

            manager.register(manager.metaBuilder("rdebug").build(),
                    new RedisVelocityCommands.DebugCommand(this));

            manager.register(manager.metaBuilder("goto").build(),
                    new RedisVelocityCommands.GotoCommand(this));

            api = new RedisVelocityAPI(this);
            getServer().getEventManager().register(this, new RedisVelocityListener(this, configuration.getExemptAddresses()));
            getServer().getEventManager().register(this, dataManager);
            psl = new PubSubListener();
            getServer().getScheduler().buildTask(this, psl).schedule();
            integrityCheck = executor.scheduleAtFixedRate(() -> {
                try (Jedis tmpRsc = pool.getResource()) {
                    Set<String> players = getLocalPlayersAsUuidStrings();
                    Set<String> playersInRedis = tmpRsc.smembers("proxy:" + configuration.getServerId() + ":usersOnline");
                    List<String> lagged = getCurrentServerIds(false, true);

                    // Clean up lagged players.
                    for (String s : lagged) {
                        Set<String> laggedPlayers = tmpRsc.smembers("proxy:" + s + ":usersOnline");
                        tmpRsc.del("proxy:" + s + ":usersOnline");
                        if (!laggedPlayers.isEmpty()) {
                            getLogger().info("Cleaning up lagged proxy " + s + " (" + laggedPlayers.size() + " players)...");
                            for (String laggedPlayer : laggedPlayers) {
                                RedisUtil.cleanUpPlayer(laggedPlayer, tmpRsc);
                            }
                        }
                    }

                    Set<String> absentLocally = new HashSet<>(playersInRedis);
                    absentLocally.removeAll(players);
                    Set<String> absentInRedis = new HashSet<>(players);
                    absentInRedis.removeAll(playersInRedis);

                    for (String member : absentLocally) {
                        boolean found = false;
                        for (String proxyId : getServerIds()) {
                            if (proxyId.equals(configuration.getServerId())) continue;
                            if (tmpRsc.sismember("proxy:" + proxyId + ":usersOnline", member)) {
                                // Just clean up the set.
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            RedisUtil.cleanUpPlayer(member, tmpRsc);
                            getLogger().warn("Player found in set that was not found locally and globally: " + member);
                        } else {
                            tmpRsc.srem("proxy:" + configuration.getServerId() + ":usersOnline", member);
                            getLogger().warn("Player found in set that was not found locally, but is on another proxy: " + member);
                        }
                    }

                    Pipeline pipeline = tmpRsc.pipelined();

                    for (String player : absentInRedis) {
                        // Player not online according to Redis but not BungeeCord.
                        getLogger().warn("Player " + player + " is on the proxy but not in Redis.");

                        Optional<Player> pl = getServer().getPlayer(UUID.fromString(player));
                        if (pl.isEmpty())
                            continue;

                        Player proxiedPlayer = pl.get();

                        RedisUtil.createPlayer(proxiedPlayer, pipeline, true);
                    }

                    pipeline.sync();
                } catch (Throwable e) {
                    getLogger().error("Unable to fix up stored player data", e);
                }
            }, 0, 1, TimeUnit.MINUTES);
        }
        getServer().getChannelRegistrar().register(new LegacyChannelIdentifier("legacy:redisvelocity"), new LegacyChannelIdentifier("RedisVelocity"));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (pool != null) {
            // Poison the PubSub listener
            psl.poison();
            integrityCheck.cancel(true);
            heartbeatTask.cancel(true);

            try (Jedis tmpRsc = pool.getResource()) {
                tmpRsc.hdel("heartbeats", configuration.getServerId());
                if (tmpRsc.scard("proxy:" + configuration.getServerId() + ":usersOnline") > 0) {
                    Set<String> players = tmpRsc.smembers("proxy:" + configuration.getServerId() + ":usersOnline");
                    for (String member : players)
                        RedisUtil.cleanUpPlayer(member, tmpRsc);
                }
            }

            pool.destroy();
        }
    }

    private void loadConfig() throws IOException, JedisConnectionException {

        final String redisServer = pconfig.getConfig().getProperty(ProxyConfigProperties.SERVER);
        final int redisPort = pconfig.getConfig().getProperty(ProxyConfigProperties.PORT);
        final boolean useSSL = pconfig.getConfig().getProperty(ProxyConfigProperties.SSL);
        String redisPassword = pconfig.getConfig().getProperty(ProxyConfigProperties.PASSWORD);
        String serverId = pconfig.getConfig().getProperty(ProxyConfigProperties.SERVER_ID);
        final String randomUUID = UUID.randomUUID().toString();

        if (redisPassword != null && (redisPassword.isEmpty() || redisPassword.equals("none"))) {
            redisPassword = null;
        }

        // Configuration sanity checks.
        getLogger().info("Loaded server id " + serverId + '.');


        if (pconfig.getConfig().getProperty(ProxyConfigProperties.USE_RANDOM_ID)) {
            serverId = pconfig.getConfig().getProperty(ProxyConfigProperties.SERVER_ID) + "-" + randomUUID;
        }

        if (redisServer != null && !redisServer.isEmpty()) {
            final String finalRedisPassword = redisPassword;
            FutureTask<JedisPool> task = new FutureTask<>(() -> {
                // Create the pool...
                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxTotal(pconfig.getConfig().getProperty(ProxyConfigProperties.MAXIMUM_CONNECTIONS));
                if (finalRedisPassword == null)
                        return new JedisPool(config, redisServer, redisPort, 0, useSSL);
                return new JedisPool(config, redisServer, redisPort, 0, finalRedisPassword, useSSL);
            });

            getServer().getScheduler().buildTask(this, task).schedule();

            try {
                pool = task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Unable to create Redis pool", e);
            }

            // Test the connection
            try (Jedis rsc = pool.getResource()) {
                rsc.ping();
                // If that worked, now we can check for an existing, alive Bungee:
                File crashFile = new File(getDataFolder().toFile(), "restarted_from_crash.txt");
                if (crashFile.exists()) {
                    crashFile.delete();
                } else if (rsc.hexists("heartbeats", serverId)) {
                    try {
                        long value = Long.parseLong(rsc.hget("heartbeats", serverId));
                        long redisTime = getRedisTime(rsc.time());
                        if (redisTime < value + 20) {
                            getLogger().error("You have launched a possible impostor Velocity instance. Another instance is already running.");
                            getLogger().error("For data consistency reasons, RedisVelocity will now disable itself.");
                            getLogger().error("If this instance is coming up from a crash, create a file in your RedisVelocity plugins directory with the name 'restarted_from_crash.txt' and RedisVelocity will not perform this check.");
                            throw new RuntimeException("Possible impostor instance!");
                        }
                    } catch (NumberFormatException ignored) {}
                }

                FutureTask<Void> task2 = new FutureTask<>(() -> {
                    httpClient = new OkHttpClient();
                    Dispatcher dispatcher = new Dispatcher(getExecutor());
                    httpClient.setDispatcher(dispatcher);
                    NameFetcher.setHttpClient(httpClient);
                    UUIDFetcher.setHttpClient(httpClient);
                    RedisVelocity.configuration = new RedisVelocityConfiguration(RedisVelocity.this.getPool(), pconfig, randomUUID);
                    return null;
                });

                getServer().getScheduler().buildTask(this, task2).schedule();

                try {
                    task2.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException("Unable to create HTTP client", e);
                }

                getLogger().info("Successfully connected to Redis.");
            } catch (JedisConnectionException e) {
                pool.destroy();
                pool = null;
                throw e;
            }
        } else {
            throw new RuntimeException("No redis server specified!");
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class PubSubListener implements Runnable {
        private JedisPubSubHandler jpsh;

        private Set<String> addedChannels = new HashSet<>();

        @Override
        public void run() {
            boolean broken = false;
            try (Jedis rsc = pool.getResource()) {
                try {
                    jpsh = new JedisPubSubHandler();
                    addedChannels.add("redisvelocity-" + configuration.getServerId());
                    addedChannels.add("redisvelocity-allservers");
                    addedChannels.add("redisvelocity-data");
                    rsc.subscribe(jpsh, addedChannels.toArray(new String[0]));
                } catch (Exception e) {
                    // FIXME: Extremely ugly hack
                    // Attempt to unsubscribe this instance and try again.
                    getLogger().info("PubSub error, attempting to recover.", e);
                    try {
                        jpsh.unsubscribe();
                    } catch (Exception e1) {
                        /* This may fail with
                        - java.net.SocketException: Broken pipe
                        - redis.clients.jedis.exceptions.JedisConnectionException: JedisPubSub was not subscribed to a Jedis instance
                        */
                    }
                    broken = true;
                }
            } catch (JedisConnectionException e) {
                getLogger().info("PubSub error, attempting to recover in 5 secs.");
                getServer().getScheduler().buildTask(RedisVelocity.this, PubSubListener.this)
                        .repeat(5, TimeUnit.SECONDS)
                        .schedule();
            }

            if (broken) {
                run();
            }
        }

        public void addChannel(String... channel) {
            addedChannels.addAll(Arrays.asList(channel));
            jpsh.subscribe(channel);
        }

        public void removeChannel(String... channel) {
            Arrays.asList(channel).forEach(addedChannels::remove);
            jpsh.unsubscribe(channel);
        }

        public void poison() {
            addedChannels.clear();
            jpsh.unsubscribe();
        }
    }

    private class JedisPubSubHandler extends JedisPubSub {
        @Override
        public void onMessage(final String s, final String s2) {
            if (s2.trim().length() == 0) return;
            getServer().getScheduler().buildTask(RedisVelocity.this, () ->
                    getServer().getEventManager().fire(new PubSubMessageEvent(s, s2))).schedule();
        }
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

}
