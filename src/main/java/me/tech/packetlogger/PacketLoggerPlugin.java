package me.tech.packetlogger;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class PacketLoggerPlugin extends JavaPlugin implements Listener {
    private static final Logger log = LoggerFactory.getLogger(PacketLoggerPlugin.class);
    private static final DateTimeFormatter FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BatchedPacketsService batchedPacketsService;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PacketEvents.getAPI().init();

        purge();

        this.batchedPacketsService = new BatchedPacketsService(this);
        batchedPacketsService.startPublish();

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                batchedPacketsService.add(event.getPacketType().getName(), true);
            }

            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                batchedPacketsService.add(event.getPacketType().getName(), false);
            }
        }, PacketListenerPriority.MONITOR);
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        getServer().getAsyncScheduler().cancelTasks(this);

        batchedPacketsService.flush();
    }

    private void purge() {
        final var purgeDays = getConfig().getInt("purge-days", 14);
        log.info("Packet Logs will be purged after {} days.", purgeDays);

        final var now = LocalDateTime.now();
        final var files = getDataFolder().listFiles();
        if(files == null) {
            log.warn("No files were found in data folder to purge.");
            return;
        }

        for(final var folder : files) {
            if(!folder.isDirectory()) {
                continue;
            }

            final var date = LocalDate.parse(folder.getName(), FOLDER_FORMAT);
            final var daysBetween = ChronoUnit.DAYS.between(date, now);

            if(daysBetween < purgeDays) {
                continue;
            }

            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                deleteRecursively(folder);
                folder.delete();
            });
            log.info("Purged {}", folder.getName());
        }
    }

    private void deleteRecursively(final File file) {
        if(file.isDirectory()) {
            for(final var child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        if(!file.delete()) {
            log.error("Unable to delete {}", file.getName());
        }
    }
}
