package gov.takx.plugins.physiology;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceStoreTest {
    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void sortsReadingsAndExpiresStaleDevices() {
        DeviceStore store = new DeviceStore();
        store.update(reading("a", "Alpha", 70, now.plusSeconds(15)));
        store.update(reading("b", "Bravo", 110, now.plusSeconds(15)));
        assertThat(store.list(DeviceStore.Sort.HEART_RATE_DESC, now))
                .extracting(device -> device.uid).containsExactly("b", "a");
        assertThat(store.list(DeviceStore.Sort.CALLSIGN_ASC, now.plusSeconds(46))).isEmpty();
    }

    @Test
    void retainsTenAlertsAndClearsEventSeverity() {
        DeviceStore store = new DeviceStore();
        store.update(reading("a", "Alpha", 70, now.plusSeconds(15)));
        for (int i = 0; i < 12; i++) {
            assertThat(store.addAlert("a", new DeviceSnapshot.Alert(
                    now.plusSeconds(i), true, DeviceSnapshot.Severity.HIGH, "HEART_RATE"))).isTrue();
        }
        DeviceSnapshot snapshot = store.list(DeviceStore.Sort.CALLSIGN_ASC, now).get(0);
        assertThat(snapshot.history).hasSize(10);
        assertThat(snapshot.severity()).isEqualTo(DeviceSnapshot.Severity.HIGH);
        store.addAlert("a", new DeviceSnapshot.Alert(now, false, DeviceSnapshot.Severity.NONE, "HEART_RATE"));
        assertThat(store.list(DeviceStore.Sort.CALLSIGN_ASC, now).get(0).severity())
                .isEqualTo(DeviceSnapshot.Severity.NONE);
    }

    private BiometricReading reading(String uid, String callsign, double hr, Instant staleAt) {
        return new BiometricReading(uid, callsign, "BIOSTRAP", uid, false, hr, null,
                null, null, null, null, null, null, null, null, staleAt, now);
    }
}
