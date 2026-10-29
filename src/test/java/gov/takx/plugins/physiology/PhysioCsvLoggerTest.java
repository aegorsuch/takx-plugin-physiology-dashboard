package gov.takx.plugins.physiology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhysioCsvLoggerTest {
    @TempDir Path temporary;

    @Test
    void writesOrderedSamplesAndEscapesCallsign() throws Exception {
        PhysioCsvLogger logger = new PhysioCsvLogger(temporary.resolve("logs"));
        try {
            Path started = logger.start();
            logger.record(new BiometricReading("uid", "Team, \"One\"", "BIOSTRAP", "device", false,
                    72d, 37d, 25d, null, null, null, null, null, null, null,
                    null, Instant.EPOCH));
            logger.record(new BiometricReading("uid", "Another", "BIOSTRAP", "device", false,
                    null, null, null, null, null, null, null, null, null, null,
                    null, Instant.EPOCH.plusSeconds(1)));
            Path completed = logger.stop().join();
            assertThat(completed).isEqualTo(started);
            assertThat(Files.readAllLines(completed)).isEqualTo(List.of(
                    "collected_at_utc,callsign,heart_rate_bpm,skin_temp_f,exertion_percent",
                    "1970-01-01T00:00:00.000Z,\"Team, \"\"One\"\"\",72.0,98.6,25.0",
                    "1970-01-01T00:00:01.000Z,Another,,,"));
        } finally {
            logger.shutdown().join();
        }
    }

    @Test
    void preventsSpreadsheetFormulaInCallsign() {
        assertThat(PhysioCsvLogger.formatRow(Instant.EPOCH, "=1+1", null, null, null))
                .startsWith("1970-01-01T00:00:00.000Z,'=1+1,");
    }
}
