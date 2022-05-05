package io.github.invvk.redisvelocity.config;

import ch.jalu.configme.Comment;
import ch.jalu.configme.SettingsHolder;
import ch.jalu.configme.configurationdata.CommentsConfiguration;
import ch.jalu.configme.properties.*;

import java.util.Collections;
import java.util.UUID;

public class ProxyConfigProperties implements SettingsHolder {

    public static final Property<String> SERVER = new StringProperty("redis-server", "localhost");
    public static final Property<Integer> PORT = new IntegerProperty("redis-port", 6379);

    @Comment("OPTIONAL: If your Redis server uses AUTH, set the password required.")
    public static final Property<String> PASSWORD = new StringProperty("redis-password", "password");
    @Comment({"Maximum connections that will be maintained to the Redis server.",
            "The default is 8. This setting should be left as-is unless you have some wildly",
            "inefficient plugins or a lot of players."})
    public static final Property<Integer> MAXIMUM_CONNECTIONS = new IntegerProperty("max-redis-connections", 8);
    @Comment({"since redis can support ssl by version 6 you can use ssl in redis bungee too! ", "you must disable this if redis version is under 6 you must disable this or connection wont work!!!"})
    public static final Property<Boolean> SSL = new BooleanProperty("useSSL", false);
    @Comment("An identifier for this BungeeCord instance. Will randomly generate if leaving it blank.")
    public static final Property<String> SERVER_ID = new StringProperty("server-id", UUID.randomUUID().toString());
    @Comment({
            "Should use random string? if this  is enabled the proxy id will be like this if server-id is test1: \"test1-66cd2aeb-91f3-43a7-a106-e0307b098652\"",
            "or if id is limework-bungee it will be \"limework-bungee-66cd2aeb-91f3-43a7-a106-e0307b098652\"",
            "this great for servers who run replicas in Kubernetes or any auto deploying replica service",
            "and used for if proxy died in a kubernetes network and deleted then new proxy setup itself."
    })
    public static final Property<Boolean> USE_RANDOM_ID = new BooleanProperty("use-random-id-string", false);
    public static final Property<Boolean> REGISTER_BUNGEE_COMMANDS = new BooleanProperty("register-bungee-commands", true);
    public static final ListProperty<String> EXEMPT_IP_ADDRESS = new StringListProperty("exempt-ip-addresses", Collections.emptyList());

    @Override
    public void registerComments(CommentsConfiguration conf) {
        String[] defaultComments = new String[] {
                "RedisVelocity configuration file.",
                "PLEASE READ THE WIKI: https://github.com/Limework/RedisBungee/wiki",
                "",
                "The Redis server you use.",
                "Get Redis from https://redis.io/"
        };
        conf.setComment("", defaultComments);
    }
}
