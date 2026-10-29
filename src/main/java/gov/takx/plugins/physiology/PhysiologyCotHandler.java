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

final class PhysiologyCotHandler extends CotDetailHandler {
    private static final Logger logger = LoggerFactory.getLogger(PhysiologyCotHandler.class);
    private final boolean legacy;
    private final Consumer<BiometricReading> onReading;
    private final BiConsumer<String, DeviceSnapshot.Alert> onAlert;

    PhysiologyCotHandler(Consumer<BiometricReading> onReading,
                         BiConsumer<String, DeviceSnapshot.Alert> onAlert) {
        this("physiology", onReading, onAlert);
    }

    static PhysiologyCotHandler legacy(Consumer<BiometricReading> onReading,
                                       BiConsumer<String, DeviceSnapshot.Alert> onAlert) {
        return new PhysiologyCotHandler("biometrics", onReading, onAlert);
    }

    private PhysiologyCotHandler(String detailName, Consumer<BiometricReading> onReading,
                                 BiConsumer<String, DeviceSnapshot.Alert> onAlert) {
        super(detailName);
        legacy = "biometrics".equals(detailName);
        this.onReading = onReading;
        this.onAlert = onAlert;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event, CotDetail physiology) {
        CotDetail primary = legacy && event != null && event.getDetail() != null
                ? event.getDetail().getFirstChildByName("physiology") : null;
        if (physiology.getAttribute("alertState") != null) {
            if (primary != null && primary.getAttribute("alertState") != null) {
                return ImportResult.SUCCESS;
            }
            CotDetail device = physiology.getFirstChildByName("device");
            String uid = childValue(device, "uid");
            if (blank(uid)) {
                logger.warn("Ignoring physiological alert without a device UID");
                return ImportResult.FAILURE;
            }
            String state = physiology.getAttribute("alertState");
            boolean active = !"CANCEL".equalsIgnoreCase(state);
            String category = physiology.getAttribute("alertCategory");
            if (blank(category)) {
                category = physiology.getAttribute("alertDescription");
            }
            String priority = physiology.getAttribute("alertPriority");
            DeviceSnapshot.Severity severity = "1".equals(priority) ? DeviceSnapshot.Severity.HIGH
                    : "2".equals(priority) || "3".equals(priority) ? DeviceSnapshot.Severity.LOW
                    : DeviceSnapshot.Severity.NONE;
            onAlert.accept(uid, new DeviceSnapshot.Alert(Instant.now(), active, severity, category));
            return ImportResult.SUCCESS;
        }

        // Alert handling stays independent of legacy reading suppression.
        if (primary != null) {
            return ImportResult.SUCCESS;
        }

        CotDetail device = physiology.getFirstChildByName("device");
        if (device == null || event == null || blank(event.getUID())) {
            logger.warn("Ignoring incomplete physiology CoT event");
            return ImportResult.FAILURE;
        }
        String serial = childValue(device, "uid");
        String model = childValue(device, "model");
        String heartRate = childValue(device, "hr");
        if (blank(serial) || blank(model) || blank(heartRate)) {
            logger.warn("Ignoring physiology event without device UID, model or heart rate");
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
                Boolean.parseBoolean(physiology.getAttribute("isFalsePositive")),
                number(heartRate, "hr"), number(childValue(device, "skt"), "skt"),
                number(childValue(device, "exert"), "exert"), number(battery, "battery"),
                number(physiology.getAttribute("hssi"), "hssi"),
                number(physiology.getAttribute("cadence"), "cadence"),
                number(physiology.getAttribute("caloricBurnRate"), "caloricBurnRate"),
                number(physiology.getAttribute("hrlv"), "hrlv"),
                number(physiology.getAttribute("vo2"), "vo2"),
                number(physiology.getAttribute("hrv"), "hrv"), staleAt, receivedAt));
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
