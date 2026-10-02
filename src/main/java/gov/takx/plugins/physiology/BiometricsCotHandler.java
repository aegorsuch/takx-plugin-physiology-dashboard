package gov.takx.plugins.physiology;

import gov.tak.api.cot.detail.CotDetailHandler;
import gov.tak.api.cot.event.CotDetail;
import gov.tak.api.cot.event.CotEvent;
import gov.tak.api.cot.importer.ImportResult;
import gov.tak.api.maps.MapItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class BiometricsCotHandler extends CotDetailHandler {
    private static final Logger logger = LoggerFactory.getLogger(BiometricsCotHandler.class);
    private final Consumer<BiometricReading> onReading;
    private final BiConsumer<String, DeviceSnapshot.Alert> onAlert;

    BiometricsCotHandler(Consumer<BiometricReading> onReading,
                         BiConsumer<String, DeviceSnapshot.Alert> onAlert) {
        super("biometrics");
        this.onReading = onReading;
        this.onAlert = onAlert;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event, CotDetail biometrics) {
        if (biometrics.getAttribute("alertState") != null) {
            CotDetail device = biometrics.getFirstChildByName("device");
            String uid = childValue(device, "uid");
            if (blank(uid)) {
                logger.warn("Ignoring physiological alert without a device UID");
                return ImportResult.FAILURE;
            }
            String state = biometrics.getAttribute("alertState");
            boolean active = !"CANCEL".equalsIgnoreCase(state);
            String category = biometrics.getAttribute("alertCategory");
            if (blank(category)) {
                category = biometrics.getAttribute("alertDescription");
            }
            String priority = biometrics.getAttribute("alertPriority");
            DeviceSnapshot.Severity severity = "1".equals(priority) ? DeviceSnapshot.Severity.HIGH
                    : "2".equals(priority) || "3".equals(priority) ? DeviceSnapshot.Severity.LOW
                    : DeviceSnapshot.Severity.NONE;
            onAlert.accept(uid, new DeviceSnapshot.Alert(Instant.now(), active, severity, category));
            return ImportResult.SUCCESS;
        }

        CotDetail device = biometrics.getFirstChildByName("device");
        if (device == null || event == null || blank(event.getUID())) {
            logger.warn("Ignoring incomplete biometrics CoT event");
            return ImportResult.FAILURE;
        }
        String serial = childValue(device, "uid");
        String model = childValue(device, "model");
        String heartRate = childValue(device, "hr");
        if (blank(serial) || blank(model) || blank(heartRate)) {
            logger.warn("Ignoring biometrics event without device UID, model or heart rate");
            return ImportResult.FAILURE;
        }
        CotDetail root = event.getDetail();
        CotDetail contact = root == null ? null : root.getFirstChildByName("contact");
        CotDetail status = root == null ? null : root.getFirstChildByName("status");
        String callsign = contact == null ? event.getUID() : contact.getAttribute("callsign");
        if (blank(callsign)) {
            callsign = event.getUID();
        }
        String battery = status == null ? null : status.getAttribute("battery");
        if (blank(battery)) {
            battery = childValue(device, "bat");
        }

        Instant receivedAt = Instant.now();
        Instant staleAt = event.getStale() == null ? null
                : Instant.ofEpochMilli(event.getStale().getMilliseconds());
        onReading.accept(new BiometricReading(
                event.getUID(), callsign, model, serial,
                Boolean.parseBoolean(biometrics.getAttribute("isFalsePositive")),
                number(heartRate, "hr"), number(childValue(device, "skt"), "skt"),
                number(childValue(device, "exert"), "exert"), number(battery, "battery"),
                number(biometrics.getAttribute("hssi"), "hssi"),
                number(biometrics.getAttribute("cadence"), "cadence"),
                number(biometrics.getAttribute("caloricBurnRate"), "caloricBurnRate"),
                number(biometrics.getAttribute("hrlv"), "hrlv"),
                number(biometrics.getAttribute("vo2"), "vo2"),
                number(biometrics.getAttribute("hrv"), "hrv"), staleAt, receivedAt));
        return ImportResult.SUCCESS;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail root) {
        return false;
    }

    private static String childValue(CotDetail detail, String name) {
        if (detail == null) {
            return null;
        }
        CotDetail child = detail.getFirstChildByName(name);
        return child == null ? detail.getAttribute(name) : child.getInnerText();
    }

    private static Double number(String text, String field) {
        if (blank(text) || "N/A".equalsIgnoreCase(text) || "None".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid {} biometric value: {}", field, text);
            return null;
        }
        logger.warn("Non-finite {} biometric value: {}", field, text);
        return null;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
