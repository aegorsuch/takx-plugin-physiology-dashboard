package gov.takx.plugins.physiology;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DeviceStore {
    enum Sort {
        CALLSIGN_ASC, CALLSIGN_DESC, HEART_RATE_ASC, HEART_RATE_DESC,
        BATTERY_ASC, BATTERY_DESC, EXERTION_ASC, EXERTION_DESC
    }

    private final Map<String, DeviceSnapshot> devices = new HashMap<>();

    synchronized void update(BiometricReading reading) {
        DeviceSnapshot device = devices.computeIfAbsent(reading.uid(), DeviceSnapshot::new);
        device.callsign = reading.callsign();
        device.model = reading.model();
        device.serial = reading.serial();
        device.falsePositive = reading.falsePositive();
        device.heartRate = reading.heartRate();
        device.skinTempC = reading.skinTempC();
        device.exertion = reading.exertion();
        device.battery = reading.battery();
        device.hssi = reading.hssi();
        device.cadence = reading.cadence();
        device.calories = reading.calories();
        device.hrlv = reading.hrlv();
        device.vo2 = reading.vo2();
        device.hrv = reading.hrv();
        device.staleAt = reading.staleAt();
        device.lastSeen = reading.receivedAt();
    }

    synchronized boolean addAlert(String uid, DeviceSnapshot.Alert alert) {
        DeviceSnapshot device = devices.get(uid);
        if (device == null) {
            return false;
        }
        device.addAlert(alert);
        return true;
    }

    synchronized List<DeviceSnapshot> list(Sort sort, Instant now) {
        devices.values().removeIf(device -> device.expired(now));
        List<DeviceSnapshot> result = new ArrayList<>();
        devices.values().forEach(device -> result.add(device.copy()));
        Comparator<DeviceSnapshot> byName = Comparator.comparing(
                device -> device.callsign == null ? "" : device.callsign,
                String.CASE_INSENSITIVE_ORDER);
        Comparator<DeviceSnapshot> byMetric = switch (sort) {
            case HEART_RATE_ASC, HEART_RATE_DESC -> Comparator.comparing(
                    device -> device.heartRate, Comparator.nullsLast(Double::compare));
            case BATTERY_ASC, BATTERY_DESC -> Comparator.comparing(
                    device -> device.battery, Comparator.nullsLast(Double::compare));
            case EXERTION_ASC, EXERTION_DESC -> Comparator.comparing(
                    device -> device.exertion, Comparator.nullsLast(Double::compare));
            default -> byName;
        };
        if (sort == Sort.CALLSIGN_DESC) {
            result.sort(byName.reversed());
        } else if (sort == Sort.HEART_RATE_DESC || sort == Sort.BATTERY_DESC || sort == Sort.EXERTION_DESC) {
            result.sort((a, b) -> {
                Double left = metric(a, sort);
                Double right = metric(b, sort);
                int comparison = Comparator.nullsLast(Comparator.<Double>reverseOrder()).compare(left, right);
                return comparison == 0 ? byName.compare(a, b) : comparison;
            });
        } else {
            result.sort(byMetric.thenComparing(byName));
        }
        return result;
    }

    private static Double metric(DeviceSnapshot device, Sort sort) {
        return switch (sort) {
            case HEART_RATE_DESC -> device.heartRate;
            case BATTERY_DESC -> device.battery;
            case EXERTION_DESC -> device.exertion;
            default -> throw new IllegalArgumentException("Not a descending metric sort: " + sort);
        };
    }
}
