package io.github.invvk.redisvelocity.events;

import lombok.ToString;

import java.util.UUID;

/**
 * This event is sent when a player disconnects. RedisVelocity sends the event only when
 * the proxy the player has been connected to is different from the local proxy.
 * <p>
 *
 * @since 0.3.4
 */
@ToString
public class PlayerLeftNetworkEvent {
    private final UUID uuid;

    public PlayerLeftNetworkEvent(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }
}
