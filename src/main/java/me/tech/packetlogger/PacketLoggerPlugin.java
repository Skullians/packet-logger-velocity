package me.tech.packetlogger;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.dejvokep.boostedyaml.YamlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Plugin(
        id = "packetlogger-velocity",
        name = "packetlogger-velocity",
        version = BuildConstants.VERSION,
        authors = {
                "Skullians",
                "DebitCardz"
        },
        dependencies = {
                @Dependency(id = "packetevents")
        }
)
public class PacketLoggerPlugin {
    private static Logger log = LoggerFactory.getLogger(PacketLoggerPlugin.class);

    private static final int SERVICE_ID = 24008;
    private static final DateTimeFormatter FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BatchedPacketsService batchedPacketsService;
    private VelocityMetrics metrics;

    public ProxyServer server;
    private Logger logger;
    public Path dataDirectory;

    public final YamlDocument config;

    @Inject
    public PacketLoggerPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        try {
            config = YamlDocument.create(new File(dataDirectory.toFile(), "config.yml"),
                    getClass().getResourceAsStream("/config.yml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) { throw new RuntimeException(e); }

        //this.metrics = this.metricsFactory.make(this, SERVICE_ID);
        purge();

        this.batchedPacketsService = new BatchedPacketsService(this);
        batchedPacketsService.startPublish();

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                batchedPacketsService.add(event);
            }

            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                batchedPacketsService.add(event);
            }
        }, PacketListenerPriority.MONITOR);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        server.getScheduler().tasksByPlugin(this).forEach(ScheduledTask::cancel);
        //metrics.shutdown();

        batchedPacketsService.flush();
    }

    /**
     * Purge old packet logs.
     */
    private void purge() {
        final var purgeDays = config.getInt("purge-days", 14);
        log.info("Packet Logs will be purged after {} days.", purgeDays);

        final var now = LocalDateTime.now();
        final var files = dataDirectory.toFile().listFiles();
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

            server.getScheduler().buildTask(this, () -> {
                deleteRecursively(folder);
                folder.delete();
            }).schedule();

            log.info("Purged {}", folder.getName());
        }
    }

    /**
     * Recursively delete a folder
     * @param file the parent
     */
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
