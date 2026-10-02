package gov.takx.plugins.physiology;

import java.time.Instant;

record BiometricReading(
        String uid, String callsign, String model, String serial, boolean falsePositive,
        Double heartRate, Double skinTempC, Double exertion, Double battery,
        Double hssi, Double cadence, Double calories, Double hrlv, Double vo2, Double hrv,
        Instant staleAt, Instant receivedAt
) {}
