package gov.takx.plugins.physiology;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class DeviceSnapshot {
    enum Severity { NONE, LOW, HIGH }

    record Alert(Instant time, boolean active, Severity severity, String category) {}

    final String uid;
    String callsign;
    String model;
    String serial;
    boolean falsePositive;
    Double heartRate;
    Double skinTempC;
    Double exertion;
    Double battery;
    Double hssi;
    Double cadence;
    Double calories;
    Double hrlv;
    Double vo2;
    Double hrv;
    Instant staleAt;
    Instant lastSeen;
    Severity eventSeverity = Severity.NONE;
    final List<Alert> history = new ArrayList<>();

    DeviceSnapshot(String uid) {
        this.uid = uid;
    }

    DeviceSnapshot copy() {
        DeviceSnapshot copy = new DeviceSnapshot(uid);
        copy.callsign = callsign;
        copy.model = model;
        copy.serial = serial;
        copy.falsePositive = falsePositive;
        copy.heartRate = heartRate;
        copy.skinTempC = skinTempC;
        copy.exertion = exertion;
        copy.battery = battery;
        copy.hssi = hssi;
        copy.cadence = cadence;
        copy.calories = calories;
        copy.hrlv = hrlv;
        copy.vo2 = vo2;
        copy.hrv = hrv;
        copy.staleAt = staleAt;
        copy.lastSeen = lastSeen;
        copy.eventSeverity = eventSeverity;
        copy.history.addAll(history);
        return copy;
    }

    Severity severity() {
        if (eventSeverity == Severity.HIGH || exertion != null && exertion > 90) {
            return Severity.HIGH;
        }
        if (eventSeverity == Severity.LOW || exertion != null && exertion >= 81) {
            return Severity.LOW;
        }
        return Severity.NONE;
    }

    void addAlert(Alert alert) {
        eventSeverity = alert.active() ? alert.severity() : Severity.NONE;
        if (history.size() == 10) {
            history.remove(0);
        }
        history.add(alert);
    }

    boolean stale(Instant now) {
        return staleAt != null && now.isAfter(staleAt);
    }

    boolean expired(Instant now) {
        return staleAt != null && now.isAfter(staleAt.plusSeconds(30));
    }
}
