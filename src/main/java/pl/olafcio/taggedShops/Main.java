package pl.olafcio.taggedShops;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    @Override
    public void onLoad() {
        saveDefaultConfig();
        Instance.config = getConfig();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(
                new TSListener(this),
                PacketListenerPriority.HIGH
        );
    }

    @Override
    public void onDisable() {}
}
