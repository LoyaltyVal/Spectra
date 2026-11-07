package com.spectra.log;

import com.spectra.Spectra;
import com.spectra.player.PlayerData;
import com.spectra.violation.Violation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Appends flags and punishments to daily log files on a background thread. */
public final class ViolationLogger {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Spectra plugin;
    private final File directory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Spectra-Logger");
        thread.setDaemon(true);
        return thread;
    });

    public ViolationLogger(Spectra plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "logs");
    }

    public void logFlag(PlayerData data, Violation v) {
        write(String.format(Locale.ROOT, "FLAG %s (%s) %s vl=%.2f level=%s ping=%dms tps=%.1f%s | %s",
                data.name(), data.uuid(), v.checkName(), v.vl(), v.level(), v.ping(), v.tps(),
                v.experimental() ? " [experimental]" : "", v.info()));
    }

    public void logPunishment(PlayerData data, Violation v, List<String> commands) {
        write(String.format(Locale.ROOT, "PUNISH %s (%s) %s vl=%.2f commands=%s",
                data.name(), data.uuid(), v.checkName(), v.vl(), commands));
    }

    private void write(String message) {
        if (!plugin.spectraConfig().loggingEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        String line = "[" + now.format(TIME) + "] " + message;
        LocalDate day = now.toLocalDate();
        executor.execute(() -> {
            if (!directory.exists() && !directory.mkdirs()) return;
            File file = new File(directory, "violations-" + day.format(DAY) + ".log");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(line);
                writer.write(System.lineSeparator());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write violation log", e);
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
