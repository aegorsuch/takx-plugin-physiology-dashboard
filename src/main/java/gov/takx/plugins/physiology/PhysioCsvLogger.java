package gov.takx.plugins.physiology;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class PhysioCsvLogger {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final String HEADER = "collected_at_utc,callsign,heart_rate_bpm,skin_temp_f,exertion_percent";

    private final Path directory;
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "physiology-csv-writer");
        thread.setDaemon(true);
        return thread;
    });
    private BufferedWriter writer;
    private Path file;
    private boolean recording;
    private boolean closed;
    private IOException failure;
    private CompletableFuture<Path> pendingStop;
    private volatile Consumer<IOException> failureListener = ignored -> {};

    PhysioCsvLogger(Path directory) {
        this.directory = directory;
    }

    synchronized void setFailureListener(Consumer<IOException> listener) {
        failureListener = listener;
    }

    synchronized Path start() throws IOException {
        if (closed || writer != null) {
            throw new IOException(closed ? "Logger has been shut down" : "Previous log is still open");
        }
        Files.createDirectories(directory);
        String name = "physio-log-" + FILE_DATE.format(Instant.now());
        Path candidate = directory.resolve(name + ".csv");
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(name + "-" + suffix++ + ".csv");
        }
        try {
            BufferedWriter opened = Files.newBufferedWriter(candidate, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                opened.write(HEADER);
                opened.newLine();
                opened.flush();
            } catch (IOException e) {
                try {
                    opened.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                Files.deleteIfExists(candidate);
                throw e;
            }
            writer = opened;
        } catch (IOException e) {
            throw new IOException("Cannot start physiological log in " + directory, e);
        }
        file = candidate;
        failure = null;
        recording = true;
        return candidate;
    }

    synchronized boolean isRecording() {
        return recording;
    }

    synchronized void record(BiometricReading reading) {
        if (!recording || failure != null) {
            return;
        }
        String row = formatRow(reading.receivedAt(), reading.callsign(), reading.heartRate(),
                reading.skinTempC(), reading.exertion());
        queue.execute(() -> {
            IOException error = null;
            synchronized (PhysioCsvLogger.this) {
                if (failure != null) {
                    return;
                }
                try {
                    writer.write(row);
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    failure = e;
                    error = e;
                }
            }
            if (error != null) {
                failureListener.accept(error);
            }
        });
    }

    synchronized CompletableFuture<Path> stop() {
        if (pendingStop != null) {
            return pendingStop;
        }
        if (writer == null) {
            return CompletableFuture.failedFuture(new IOException("No physiological log is open"));
        }
        recording = false;
        Path completed = file;
        pendingStop = new CompletableFuture<>();
        CompletableFuture<Path> result = pendingStop;
        queue.execute(() -> {
            IOException error;
            synchronized (PhysioCsvLogger.this) {
                error = failure;
                try {
                    writer.close();
                } catch (IOException e) {
                    if (error == null) {
                        error = e;
                    } else {
                        error.addSuppressed(e);
                    }
                }
                writer = null;
                file = null;
                failure = null;
                pendingStop = null;
            }
            if (error == null) {
                result.complete(completed);
            } else {
                result.completeExceptionally(new IOException("Cannot finish " + completed, error));
            }
        });
        return result;
    }

    synchronized CompletableFuture<Path> shutdown() {
        closed = true;
        CompletableFuture<Path> result = writer == null
                ? CompletableFuture.completedFuture(null) : stop();
        queue.shutdown();
        return result;
    }

    static String formatRow(Instant receivedAt, String callsign, Double heartRate,
                            Double skinTempC, Double exertion) {
        return ROW_DATE.format(receivedAt) + "," + csv(callsign) + ","
                + number(heartRate) + ","
                + number(skinTempC == null ? null : skinTempC * 9 / 5 + 32) + ","
                + number(exertion);
    }

    private static String number(Double value) {
        return value == null || !Double.isFinite(value) ? "" : Double.toString(value);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        // Spreadsheet tools interpret untrusted callsigns starting with formula characters as commands.
        String safe = value.matches("^[\\s]*[=+@\\-].*") ? "'" + value : value;
        return safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
