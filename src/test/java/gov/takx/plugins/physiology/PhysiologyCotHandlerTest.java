package gov.takx.plugins.physiology;

import gov.tak.api.cot.event.CotEvent;
import gov.tak.api.cot.importer.ImportResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class PhysiologyCotHandlerTest {
    @Test
    void acceptsCurrentAndLegacyBatteryAndAlerts() {
        List<BiometricReading> readings = new ArrayList<>();
        List<DeviceSnapshot.Alert> alerts = new ArrayList<>();
        PhysiologyCotHandler handler = new PhysiologyCotHandler(readings::add, (uid, alert) -> alerts.add(alert));
        PhysiologyCotHandler legacyHandler = PhysiologyCotHandler.legacy(readings::add,
                (uid, alert) -> alerts.add(alert));
        CotEvent current = event("<contact callsign=\"Medic 1\"/><status battery=\"93\"/>"
                + "<physiology isFalsePositive=\"true\" hssi=\"1\" cadence=\"2\" caloricBurnRate=\"3\" "
                + "hrlv=\"4\" vo2=\"5\" hrv=\"6\"><device><uid>band-1</uid><model>BIOSTRAP</model>"
                + "<hr>72</hr><skt>37</skt><exert>85</exert></device></physiology>");
        assertThat(handler.toItemMetadata(null, current,
                current.getDetail().getFirstChildByName("physiology"))).isEqualTo(ImportResult.SUCCESS);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).callsign()).isEqualTo("Medic 1");
        assertThat(readings.get(0).battery()).isEqualTo(93d);
        assertThat(readings.get(0).exertion()).isEqualTo(85d);
        assertThat(readings.get(0).heartRate()).isEqualTo(72d);
        assertThat(readings.get(0).skinTempC()).isEqualTo(37d);
        assertThat(readings.get(0).serial()).isEqualTo("band-1");
        assertThat(readings.get(0).model()).isEqualTo("BIOSTRAP");
        assertThat(readings.get(0).falsePositive()).isTrue();
        assertThat(readings.get(0).hssi()).isEqualTo(1d);
        assertThat(readings.get(0).cadence()).isEqualTo(2d);
        assertThat(readings.get(0).calories()).isEqualTo(3d);
        assertThat(readings.get(0).hrlv()).isEqualTo(4d);
        assertThat(readings.get(0).vo2()).isEqualTo(5d);
        assertThat(readings.get(0).hrv()).isEqualTo(6d);
        assertThat(readings.get(0).staleAt()).isEqualTo(java.time.Instant.parse("2026-10-01T12:01:00Z"));

        CotEvent legacy = event("<biometrics><device uid=\"band-1\" model=\"BIOSTRAP\" hr=\"N/A\">"
                + "<bat>42</bat></device></biometrics>");
        assertThat(legacyHandler.toItemMetadata(null, legacy,
                legacy.getDetail().getFirstChildByName("biometrics"))).isEqualTo(ImportResult.SUCCESS);
        assertThat(readings.get(1).heartRate()).isNull();
        assertThat(readings.get(1).battery()).isEqualTo(42d);

        CotEvent alert = event("<biometrics alertState=\"ACTIVE\" alertPriority=\"1\" "
                + "alertCategory=\"CORE_TEMP\"><device uid=\"band-1\"/></biometrics>");
        legacyHandler.toItemMetadata(null, alert, alert.getDetail().getFirstChildByName("biometrics"));
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).severity()).isEqualTo(DeviceSnapshot.Severity.HIGH);
    }

    @Test
    void prefersPhysiologyRegardlessOfHandlerOrder() {
        for (boolean legacyFirst : new boolean[]{true, false}) {
            List<BiometricReading> readings = new ArrayList<>();
            PhysiologyCotHandler handler = new PhysiologyCotHandler(readings::add, (uid, alert) -> {});
            PhysiologyCotHandler legacyHandler = PhysiologyCotHandler.legacy(readings::add, (uid, alert) -> {});
            CotEvent both = event("<physiology><device uid=\"new\" model=\"BIOSTRAP\" hr=\"72\"/>"
                    + "</physiology><biometrics><device uid=\"old\" model=\"BIOSTRAP\" hr=\"80\"/>"
                    + "</biometrics>");
            PhysiologyCotHandler first = legacyFirst ? legacyHandler : handler;
            PhysiologyCotHandler second = legacyFirst ? handler : legacyHandler;
            String firstTag = legacyFirst ? "biometrics" : "physiology";
            String secondTag = legacyFirst ? "physiology" : "biometrics";
            assertThat(first.toItemMetadata(null, both,
                    both.getDetail().getFirstChildByName(firstTag))).isEqualTo(ImportResult.SUCCESS);
            assertThat(second.toItemMetadata(null, both,
                    both.getDetail().getFirstChildByName(secondTag))).isEqualTo(ImportResult.SUCCESS);
            assertThat(readings).hasSize(1);
            assertThat(readings.get(0).serial()).isEqualTo("new");
            assertThat(readings.get(0).heartRate()).isEqualTo(72d);
        }
    }

    @Test
    void acceptsPhysiologyAndLegacyAlertsAndCancelsWithoutProducingReadings() {
        for (String tag : new String[]{"physiology", "biometrics"}) {
            List<BiometricReading> readings = new ArrayList<>();
            List<String> uids = new ArrayList<>();
            List<DeviceSnapshot.Alert> alerts = new ArrayList<>();
            BiConsumer<String, DeviceSnapshot.Alert> onAlert = (uid, alert) -> {
                uids.add(uid);
                alerts.add(alert);
            };
            PhysiologyCotHandler handler = "physiology".equals(tag)
                    ? new PhysiologyCotHandler(readings::add, onAlert)
                    : PhysiologyCotHandler.legacy(readings::add, onAlert);
            for (String state : new String[]{"ACTIVE", "CANCEL"}) {
                CotEvent alert = event("<" + tag + " alertState=\"" + state + "\" alertPriority=\"1\" "
                        + "alertCategory=\"CORE_TEMP\"><device uid=\"band-1\" model=\"BIOSTRAP\" "
                        + "hr=\"72\"/></" + tag + ">");
                assertThat(handler.toItemMetadata(null, alert,
                        alert.getDetail().getFirstChildByName(tag))).isEqualTo(ImportResult.SUCCESS);
            }
            assertThat(readings).isEmpty();
            assertThat(uids).containsExactly("band-1", "band-1");
            assertThat(alerts).hasSize(2);
            assertThat(alerts.get(0).active()).isTrue();
            assertThat(alerts.get(1).active()).isFalse();
            assertThat(alerts.get(0).severity()).isEqualTo(DeviceSnapshot.Severity.HIGH);
            assertThat(alerts.get(0).category()).isEqualTo("CORE_TEMP");
        }
    }

    @Test
    void prefersPhysiologyAlertsAndCancelsRegardlessOfHandlerOrder() {
        for (boolean legacyFirst : new boolean[]{true, false}) {
            for (String state : new String[]{"ACTIVE", "CANCEL"}) {
                List<BiometricReading> readings = new ArrayList<>();
                List<DeviceSnapshot.Alert> alerts = new ArrayList<>();
                PhysiologyCotHandler handler = new PhysiologyCotHandler(readings::add,
                        (uid, alert) -> alerts.add(alert));
                PhysiologyCotHandler legacyHandler = PhysiologyCotHandler.legacy(readings::add,
                        (uid, alert) -> alerts.add(alert));
                CotEvent both = event("<physiology alertState=\"" + state + "\" alertPriority=\"1\" "
                        + "alertCategory=\"CORE_TEMP\"><device uid=\"band-1\"/></physiology>"
                        + "<biometrics alertState=\"" + state + "\" alertPriority=\"2\" "
                        + "alertCategory=\"LEGACY\"><device uid=\"band-1\"/></biometrics>");
                PhysiologyCotHandler first = legacyFirst ? legacyHandler : handler;
                PhysiologyCotHandler second = legacyFirst ? handler : legacyHandler;
                String firstTag = legacyFirst ? "biometrics" : "physiology";
                String secondTag = legacyFirst ? "physiology" : "biometrics";
                assertThat(first.toItemMetadata(null, both,
                        both.getDetail().getFirstChildByName(firstTag))).isEqualTo(ImportResult.SUCCESS);
                assertThat(second.toItemMetadata(null, both,
                        both.getDetail().getFirstChildByName(secondTag))).isEqualTo(ImportResult.SUCCESS);
                assertThat(readings).isEmpty();
                assertThat(alerts).hasSize(1);
                assertThat(alerts.get(0).active()).isEqualTo("ACTIVE".equals(state));
                assertThat(alerts.get(0).severity()).isEqualTo(DeviceSnapshot.Severity.HIGH);
                assertThat(alerts.get(0).category()).isEqualTo("CORE_TEMP");
            }
        }
    }

    @Test
    void preservesLegacyAlertAndCancelAlongsidePhysiology() {
        List<BiometricReading> readings = new ArrayList<>();
        List<String> uids = new ArrayList<>();
        List<DeviceSnapshot.Alert> alerts = new ArrayList<>();
        BiConsumer<String, DeviceSnapshot.Alert> onAlert = (uid, alert) -> {
            uids.add(uid);
            alerts.add(alert);
        };
        PhysiologyCotHandler handler = PhysiologyCotHandler.legacy(readings::add, onAlert);
        PhysiologyCotHandler primaryHandler = new PhysiologyCotHandler(readings::add, onAlert);
        for (String state : new String[]{"ACTIVE", "CANCEL"}) {
            CotEvent alert = event("<physiology><device uid=\"band-1\" model=\"BIOSTRAP\" hr=\"72\"/>"
                    + "</physiology><biometrics alertState=\"" + state + "\" alertPriority=\"1\" "
                    + "alertDescription=\"CORE_TEMP\"><device><uid>band-1</uid></device></biometrics>");
            assertThat(handler.toItemMetadata(null, alert,
                    alert.getDetail().getFirstChildByName("biometrics"))).isEqualTo(ImportResult.SUCCESS);
            assertThat(primaryHandler.toItemMetadata(null, alert,
                    alert.getDetail().getFirstChildByName("physiology"))).isEqualTo(ImportResult.SUCCESS);
        }
        assertThat(readings).hasSize(2);
        assertThat(uids).containsExactly("band-1", "band-1");
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).active()).isTrue();
        assertThat(alerts.get(1).active()).isFalse();
        assertThat(alerts.get(0).category()).isEqualTo("CORE_TEMP");
    }

    @Test
    void rejectsIncompletePhysiologyWithoutFallingBackToLegacy() {
        List<BiometricReading> readings = new ArrayList<>();
        PhysiologyCotHandler handler = new PhysiologyCotHandler(readings::add, (uid, alert) -> {});
        PhysiologyCotHandler legacyHandler = PhysiologyCotHandler.legacy(readings::add, (uid, alert) -> {});
        CotEvent invalid = event("<physiology/><biometrics>"
                + "<device uid=\"old\" model=\"BIOSTRAP\" hr=\"80\"/></biometrics>");
        assertThat(handler.toItemMetadata(null, invalid,
                invalid.getDetail().getFirstChildByName("physiology"))).isEqualTo(ImportResult.FAILURE);
        assertThat(legacyHandler.toItemMetadata(null, invalid,
                invalid.getDetail().getFirstChildByName("biometrics"))).isEqualTo(ImportResult.SUCCESS);
        assertThat(readings).isEmpty();
    }

    private static CotEvent event(String detail) {
        return CotEvent.parse("<event version=\"2.0\" uid=\"operator-1\" type=\"a-f-G\" how=\"m-g\" "
                + "time=\"2026-10-01T12:00:00Z\" start=\"2026-10-01T12:00:00Z\" "
                + "stale=\"2026-10-01T12:01:00Z\"><point lat=\"0\" lon=\"0\" hae=\"0\" ce=\"1\" "
                + "le=\"1\"/><detail>" + detail + "</detail></event>");
    }
}
