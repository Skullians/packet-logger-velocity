package me.tech.packetlogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BatchedPacketsService {
    private static final long NOW_MS = Instant.now().toEpochMilli();
    private static final Logger log = LoggerFactory.getLogger(BatchedPacketsService.class);

    private final PacketLoggerPlugin plugin;
    private final Path dataFolderPath;
    private final ExecutorService executor;

    private final Map<String, Boolean> boundCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> packetQueue = new ConcurrentHashMap<>();

    public BatchedPacketsService(
        final PacketLoggerPlugin plugin
    ) {
        this.plugin = plugin;
        this.dataFolderPath = plugin.getDataFolder().toPath();
        this.executor = Executors.newSingleThreadExecutor();

        createSqlite();
    }

    public void add(String packetName, boolean outgoing) {
        if(!boundCache.containsKey(packetName)) {
            boundCache.put(packetName, outgoing);
        }

        packetQueue.computeIfAbsent(packetName, (k) -> new AtomicInteger(0))
            .getAndIncrement();
    }

    public void startPublish() {
        final var scheduler = plugin.getServer().getAsyncScheduler();
        scheduler.runAtFixedRate(plugin, (task) -> {
            executor.submit(this::flush);
        }, 0L, 3L, TimeUnit.SECONDS);

    }

    public void flush() {
        try(final var conn = getConnection()) {
            conn.setAutoCommit(false);
            final String sql = "INSERT INTO batched_packets (packet_name, amount, outgoing, collected_at) VALUES (?, ?, ?, ?)";

            final var counter = new AtomicInteger();

            try(final var statement = conn.prepareStatement(sql)) {
                for(final var packet : packetQueue.keySet()) {
                    final var amount = packetQueue.get(packet).get();

                    log.info("There have been {} of {} sent in the last 3 seconds.", amount, packet);

                    statement.setObject(1, packet);
                    statement.setObject(2, amount);
                    statement.setObject(3, boundCache.getOrDefault(packet, false));
                    statement.setObject(4, Instant.now().toEpochMilli());
                    statement.addBatch();

                    if(counter.getAndIncrement() % 15 == 0) {
                        statement.executeBatch();
                        conn.commit();
                    }
                }

                // flush final batch
                statement.executeBatch();
                conn.commit();
            } catch(SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch(SQLException ex) {
            ex.printStackTrace();
        }

        packetQueue.clear();
    }

    private void createSqlite() {
        final var dbFilePath = dataFolderPath.resolve(getDBFileName());

        if(Files.exists(dbFilePath)) {
            log.warn("{} already exists.", getDBFileName());
            return;
        }

        try {
            Files.createFile(dbFilePath);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        try (var conn = getConnection()) {
            conn.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS batched_packets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "packet_name TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL, " +
                    "outgoing INTEGER NOT NULL, " +
                    "collected_at INTEGER NOT NULL" +
                    ")");
            log.info("Created batched_packets table in SQLite DB.");
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            "jdbc:sqlite:%s".formatted(dataFolderPath.resolve(getDBFileName()).toAbsolutePath())
        );
    }

    private String getDBFileName() {
        return "packets_%s.db".formatted(NOW_MS);
    }
}
