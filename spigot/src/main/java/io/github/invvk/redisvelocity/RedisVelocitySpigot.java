package io.github.invvk.redisvelocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.HashMap;
import java.util.Map;

public class RedisVelocitySpigot extends JavaPlugin implements PluginMessageListener, Listener {

    @Getter private final Map<Player, String> playerProxyMap = new HashMap<>();
    @Getter private final Map<String, Integer> onlinePlayersMap = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, "RedisVelocity", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "legacy:redisvelocity", this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "RedisVelocity");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "legacy:redisvelocity");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!channel.equalsIgnoreCase("RedisVelocity") || !channel.equalsIgnoreCase("legacy:redisvelocity"))
            return;
        ByteArrayDataInput dataInput = ByteStreams.newDataInput(bytes);
        String subChannel = dataInput.readUTF();
        if (subChannel.equalsIgnoreCase("proxy")) {
            final String currentProxy = dataInput.readUTF();
            if (playerProxyMap.containsKey(player))
                playerProxyMap.replace(player, currentProxy);
            else
                playerProxyMap.put(player, currentProxy);
        }
        if (subChannel.equalsIgnoreCase("playercount")) {
            final String server = dataInput.readUTF();
            final int onlinePlayers = dataInput.readInt();
            if (onlinePlayersMap.containsKey(server))
                onlinePlayersMap.replace(server, onlinePlayers);
            else
                onlinePlayersMap.put(server, onlinePlayers);
        }
    }

    public int getOnlinePlayers(Player player, String server) {
        int amount = onlinePlayersMap.getOrDefault(server, -1);
        if (amount == -1) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            dataOutput.writeUTF("PlayerCount");
            dataOutput.writeUTF(server);
            player.sendPluginMessage(this, "RedisVelocity", dataOutput.toByteArray());
        }
        return amount;
    }

    public String getProxyFor(Player player) {
        String proxy = playerProxyMap.getOrDefault(player, null);
        if (proxy == null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            dataOutput.writeUTF("Proxy");
            player.sendPluginMessage(this, "RedisVelocity", dataOutput.toByteArray());
        }
        return proxy;
    }

    @EventHandler
    public void onJoin(PlayerQuitEvent event) {
        onlinePlayersMap.remove(event.getPlayer());
    }
}