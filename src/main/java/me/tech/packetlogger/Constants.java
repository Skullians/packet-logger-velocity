package me.tech.packetlogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Constants {
    /** The time the plugin started. */
    public static final Instant NOW = Instant.now();

    /** The format for the folder the SQLite DB is contained in. */
    public static final String DB_FOLDER_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
        .format(NOW);

    /** The format for the SQLite file name. */
    public static final String SQLITE_FILE_NAME = "packets_%s.sqlite".formatted(NOW.toEpochMilli());
}
