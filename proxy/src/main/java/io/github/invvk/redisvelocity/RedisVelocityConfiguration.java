package io.github.invvk.redisvelocity;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import io.github.invvk.redisvelocity.config.ProxyConfigProperties;
import io.github.invvk.redisvelocity.config.ProxyConfiguration;
import lombok.Getter;
import redis.clients.jedis.JedisPool;

import java.net.InetAddress;
import java.util.List;

public class RedisVelocityConfiguration {
    @Getter
    private final JedisPool pool;
    @Getter
    private final String serverId;
    @Getter
    private final boolean registerBungeeCommands;
    @Getter
    private final List<InetAddress> exemptAddresses;


    public RedisVelocityConfiguration(JedisPool pool, ProxyConfiguration configuration, String randomUUID) {
        this.pool = pool;
        if (configuration.getConfig().getProperty(ProxyConfigProperties.USE_RANDOM_ID)) {
            this.serverId = configuration.getConfig().getProperty(ProxyConfigProperties.SERVER_ID) + "-" + randomUUID;
        } else {
            this.serverId = configuration.getConfig().getProperty(ProxyConfigProperties.SERVER_ID);
        }

        this.registerBungeeCommands = configuration.getConfig().getProperty(ProxyConfigProperties.REGISTER_BUNGEE_COMMANDS);

        List<String> stringified = configuration.getConfig().getProperty(ProxyConfigProperties.EXEMPT_IP_ADDRESS);
        ImmutableList.Builder<InetAddress> addressBuilder = ImmutableList.builder();

        for (String s : stringified) {
            addressBuilder.add(InetAddresses.forString(s));
        }

        this.exemptAddresses = addressBuilder.build();
    }

}
