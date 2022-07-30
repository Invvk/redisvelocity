package io.github.invvk.redisvelocity;

import com.google.common.annotations.VisibleForTesting;
import com.velocitypowered.api.proxy.Player;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@VisibleForTesting
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisUtil {
    protected static void createPlayer(Player player, Pipeline pipeline, boolean fireEvent) {
        createPlayerA(player, pipeline, fireEvent);
        if (player.getCurrentServer().isPresent())
            pipeline.hset("player:" + player.getUniqueId().toString(), "server", player.getCurrentServer().get().getServer().getServerInfo().getName());
    }

    protected static void createPlayerA(Player connection, Pipeline pipeline, boolean fireEvent) {
        Map<String, String> playerData = new HashMap<>(4);
        playerData.put("online", "0");
        playerData.put("ip", connection.getRemoteAddress().getAddress().getHostAddress());
        playerData.put("proxy", RedisVelocity.getConfiguration().getServerId());

        pipeline.sadd("proxy:" + RedisVelocityAPI.getRedisVelocityApi().getServerId() + ":usersOnline", connection.getUniqueId().toString());
        pipeline.hmset("player:" + connection.getUniqueId().toString(), playerData);

        if (fireEvent) {
            pipeline.publish("redisvelocity-data", RedisVelocity.getGson().toJson(new DataManager.DataManagerMessage<>(
                    connection.getUniqueId(), DataManager.DataManagerMessage.Action.JOIN,
                    new DataManager.LoginPayload(connection.getRemoteAddress().getAddress()))));
        }
    }

    public static void cleanUpPlayer(String player, Jedis rsc) {
        rsc.srem("proxy:" + RedisVelocityAPI.getRedisVelocityApi().getServerId() + ":usersOnline", player);
        rsc.hdel("player:" + player, "server", "ip", "proxy");
        long timestamp = System.currentTimeMillis();
        rsc.hset("player:" + player, "online", String.valueOf(timestamp));
        rsc.publish("redisvelocity-data", RedisVelocity.getGson().toJson(new DataManager.DataManagerMessage<>(
                UUID.fromString(player), DataManager.DataManagerMessage.Action.LEAVE,
                new DataManager.LogoutPayload(timestamp))));
    }

    public static void cleanUpPlayer(String player, Pipeline rsc) {
        rsc.srem("proxy:" + RedisVelocityAPI.getRedisVelocityApi().getServerId() + ":usersOnline", player);
        rsc.hdel("player:" + player, "server", "ip", "proxy");
        long timestamp = System.currentTimeMillis();
        rsc.hset("player:" + player, "online", String.valueOf(timestamp));
        rsc.publish("redisvelocity-data", RedisVelocity.getGson().toJson(new DataManager.DataManagerMessage<>(
                UUID.fromString(player), DataManager.DataManagerMessage.Action.LEAVE,
                new DataManager.LogoutPayload(timestamp))));
    }

    public static boolean isRedisVersionRight(String redisVersion) {
        // Need to use >=6.2 to use Lua optimizations.
        String[] args = redisVersion.split("\\.");
        if (args.length < 2) {
            return false;
        }
        return Integer.parseInt(args[0]) >= 6 && Integer.parseInt(args[1]) >= 0;
    }

    // Ham1255: i am keeping this if some plugin uses this *IF*
    @Deprecated
    public static boolean canUseLua(String redisVersion) {
        return isRedisVersionRight(redisVersion);
    }
}
