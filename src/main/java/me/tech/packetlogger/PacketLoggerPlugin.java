package me.tech.packetlogger;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PacketLoggerPlugin extends JavaPlugin implements Listener {
    private static final Logger log = LoggerFactory.getLogger(PacketLoggerPlugin.class);
    private BatchedPacketsService batchedPacketsService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.batchedPacketsService = new BatchedPacketsService(this);
        batchedPacketsService.startPublish();

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.values()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                batchedPacketsService.add(event.getPacketType().name(), false);
            }

            @Override
            public void onPacketSending(PacketEvent event) {
                batchedPacketsService.add(event.getPacketType().name(), true);
            }
        });
    }

    @Override
    public void onDisable() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);

        batchedPacketsService.flush();
    }
}
