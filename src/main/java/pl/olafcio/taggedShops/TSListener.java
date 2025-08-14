package pl.olafcio.taggedShops;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Optional;

public class TSListener implements PacketListener {
    public static final String nlName = "TaggedShops.NameListeningNow";
    public static final String iePersistent = "TaggedShops.InteractedEntity";
    public static final String emSending = "TaggedShops.EntityMetadataSending";

    @NotNull JavaPlugin plugin;
    MetadataValue metaTrue;

    public TSListener(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.metaTrue = new FixedMetadataValue(plugin, true);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            var wrapper = new WrapperPlayServerSystemChatMessage(event);
            var text = PlainTextComponentSerializer.plainText().serialize(wrapper.getMessage());
            if (text.startsWith("Please enter the shop's new name in chat."))
                ((Player) event.getPlayer()).setMetadata(nlName, metaTrue);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            var player = (Player) event.getPlayer();
            if (player.hasMetadata(emSending)) {
                player.removeMetadata(emSending, plugin);
                return;
            }

            event.setCancelled(true);

            var wrapper = new WrapperPlayServerEntityMetadata(event);
            var user = event.getUser();

            var metadata = new ArrayList<>(wrapper.getEntityMetadata());
            var entityId = wrapper.getEntityId();
            var spoofed = new WrapperPlayServerEntityMetadata(entityId, metadata);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity target = null;

                for (var entity : player.getWorld().getEntities())
                    if (entity.getEntityId() == entityId)
                        target = entity;

                if (target != null) {
                    var pos = encodePos(target.getLocation());

                    if (Instance.config.contains(pos)) {
                        var newName = Instance.config.getString(pos);

                        if (metadata.get(0).getValue() instanceof Optional)
                            ((EntityData<Optional<Component>>) metadata.get(0)).setValue(Optional.of(Component.text(newName)));
                        else if (metadata.size() >= 2 && metadata.get(2).getValue() instanceof Optional)
                            ((EntityData<Optional<Component>>) metadata.get(2)).setValue(Optional.of(Component.text(newName)));
                    }
                }

                player.setMetadata(emSending, metaTrue);
                user.sendPacket(spoofed);
            });
        }
    }

    String encodePos(Location pos) {
        return pos.getBlockX() + " " +
               pos.getBlockY() + " " +
               pos.getBlockZ() + " ";
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
            var plr = (Player) event.getPlayer();
            if (plr.hasMetadata(nlName) && plr.hasMetadata(iePersistent)) {
                var pos = (Location) plr.getMetadata(iePersistent).getFirst().value();
                assert pos != null;

                plr.removeMetadata(nlName, plugin);
                plr.removeMetadata(iePersistent, plugin);

                var wrapper = new WrapperPlayClientChatMessage(event);
                var msg = wrapper.getMessage();

                if (msg.equals("-")) {
                    var encoded = encodePos(pos);
                    if (Instance.config.contains(encoded)) {
                        Instance.config.set(encoded, null);
                        plugin.saveConfig();
                        plr.sendMessage("§e[TaggedShops]§a Removed spoofed name.");
                    } else plr.sendMessage("§e[TaggedShops]§a No spoofed name found, proceeding.");
                } else {
                    msg = MatrixColorAPI.process(msg);

                    var vanilla = msg.replaceAll("§x(§.){6}", "").replaceAll("§.", "").split("");
                    var vanillaOutput = new StringBuilder();
                    for (var ch : vanilla)
                        if ("QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm ".contains(ch))
                            vanillaOutput.append(ch);
                    wrapper.setMessage(vanillaOutput.toString());
                    wrapper.write();

                    Instance.config.set(encodePos(pos), msg);
                    plugin.saveConfig();
                    plr.sendMessage("§e[TaggedShops]§a Spoofed to: " + msg);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            var wrapper = new WrapperPlayClientInteractEntity(event);
            var player = (Player) event.getPlayer();
            var entityId = wrapper.getEntityId();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity target = null;

                for (var entity : player.getWorld().getEntities())
                    if (entity.getEntityId() == entityId)
                        target = entity;

                if (target == null)
                    return;

                var pos = target.getLocation();
                player.setMetadata(iePersistent, new FixedMetadataValue(plugin, pos));
            });
        }
    }
}
