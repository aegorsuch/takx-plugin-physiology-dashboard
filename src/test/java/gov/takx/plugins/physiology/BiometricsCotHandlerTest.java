package gov.takx.plugins.physiology;

import gov.tak.api.cot.event.CotEvent;
import gov.tak.api.cot.importer.ImportResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BiometricsCotHandlerTest {
    @Test
    void acceptsCurrentAndLegacyBatteryAndAlerts() {
        List<BiometricReading> readings = new ArrayList<>();
        List<DeviceSnapshot.Alert> alerts = new ArrayList<>();
        BiometricsCotHandler handler = new BiometricsCotHandler(readings::add, (uid, alert) -> alerts.add(alert));
        CotEvent current = event("<contact callsign=\"Medic 1\"/><status battery=\"93\"/>"
                + "<biometrics><device><uid>band-1</uid><model>BIOSTRAP</model>"
                + "<hr>72</hr><skt>37</skt><exert>85</exert></device></biometrics>");
        assertThat(handler.toItemMetadata(null, current,
                current.getDetail().getFirstChildByName("biometrics"))).isEqualTo(ImportResult.SUCCESS);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).callsign()).isEqualTo("Medic 1");
        assertThat(readings.get(0).battery()).isEqualTo(93d);
        assertThat(readings.get(0).exertion()).isEqualTo(85d);

        CotEvent legacy = event("<biometrics><device uid=\"band-1\" model=\"BIOSTRAP\" hr=\"N/A\">"
                + "<bat>42</bat></device></biometrics>");
        handler.toItemMetadata(null, legacy, legacy.getDetail().getFirstChildByName("biometrics"));
        assertThat(readings.get(1).heartRate()).isNull();
        assertThat(readings.get(1).battery()).isEqualTo(42d);

        CotEvent alert = event("<biometrics alertState=\"ACTIVE\" alertPriority=\"1\" "
                + "alertCategory=\"CORE_TEMP\"><device uid=\"band-1\"/></biometrics>");
        handler.toItemMetadata(null, alert, alert.getDetail().getFirstChildByName("biometrics"));
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).severity()).isEqualTo(DeviceSnapshot.Severity.HIGH);
    }

    private static CotEvent event(String detail) {
        return CotEvent.parse("<event version=\"2.0\" uid=\"operator-1\" type=\"a-f-G\" how=\"m-g\" "
                + "time=\"2026-10-01T12:00:00Z\" start=\"2026-10-01T12:00:00Z\" "
                + "stale=\"2026-10-01T12:01:00Z\"><point lat=\"0\" lon=\"0\" hae=\"0\" ce=\"1\" "
                + "le=\"1\"/><detail>" + detail + "</detail></event>");
    }
}
