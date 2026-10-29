package gov.takx.plugins.physiology;

import gov.tak.api.cot.detail.ICotDetailManager;
import gov.takx.api.annotation.InvokeOnEDT;
import gov.takx.api.plugin.annotations.AppPlugin;
import gov.takx.api.plugin.annotations.PluginDescriptor;
import gov.takx.api.plugin.annotations.PluginSpecification;
import gov.takx.api.plugin.app.AAppPlugin;
import gov.takx.api.ui.ComponentPosition;
import gov.takx.platform.ui.ComponentDescriptor;
import gov.takx.platform.ui.ComponentDescriptorBuilder;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

@AppPlugin(
        spec = @PluginSpecification(
                family = "gov.takx.plugins.physiology",
                type = "Physiology Dashboard",
                version = "${project.rootVersion}",
                iconPath = "physiology.png"
        ),
        descriptor = @PluginDescriptor(
                name = "Physiology Dashboard",
                description = "Live biometric readings and physiological CSV logging",
                longDescription = "Shows incoming biometric CoT readings, alerts and physiological CSV logs."
        )
)
public class PhysiologyDashboardPlugin extends AAppPlugin {
    private static final Logger logger = LoggerFactory.getLogger(PhysiologyDashboardPlugin.class);

    private final ICotDetailManager cotDetails;
    private final DeviceStore devices = new DeviceStore();
    private final PhysioCsvLogger csv = new PhysioCsvLogger(Path.of(
            System.getProperty("user.home"), "TAKX", "physio-logs"));
    private final PhysiologyCotHandler handler = new PhysiologyCotHandler(this::onReading, this::onAlert);
    private final PhysiologyCotHandler legacyHandler = PhysiologyCotHandler.legacy(this::onReading, this::onAlert);
    private volatile boolean started;
    private PhysiologyPanel panel;

    @Inject
    public PhysiologyDashboardPlugin(ICotDetailManager cotDetails) {
        this.cotDetails = cotDetails;
    }

    @Override
    @InvokeOnEDT
    public void start() {
        panel = new PhysiologyPanel(devices, csv);
        ComponentDescriptor descriptor = new ComponentDescriptorBuilder(panel, "Physiology Dashboard")
                .setIcon(getClass().getClassLoader().getResource("physiology.png"))
                .setComponentPosition(ComponentPosition.RIGHT)
                .build();
        delegate.getUISpaceManager().addComponent(descriptor, panel);
        started = true;
        cotDetails.registerHandler(handler);
        cotDetails.registerHandler(legacyHandler);
        delegate.getUISpaceManager().showComponent(panel);
    }

    @Override
    @InvokeOnEDT
    public void stop() {
        started = false;
        cotDetails.unregisterHandler(handler);
        cotDetails.unregisterHandler(legacyHandler);
        if (csv.isRecording()) {
            csv.stop().whenComplete((file, error) -> {
                if (error == null) {
                    logger.info("Physiological log saved: {}", file);
                } else {
                    logger.error("Unable to finish physiological log", error);
                }
            });
        }
        panel.dispose();
        delegate.getUISpaceManager().removeComponent(panel);
        panel = null;
    }

    @Override
    public void shutdown() {
        csv.shutdown().whenComplete((file, error) -> {
            if (error != null) {
                logger.error("Unable to finish physiological log on shutdown", error);
            }
        });
    }

    private void onReading(BiometricReading reading) {
        if (!started) {
            return;
        }
        devices.update(reading);
        csv.record(reading);
        SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.refresh();
            }
        });
    }

    private void onAlert(String uid, DeviceSnapshot.Alert alert) {
        if (!started) {
            return;
        }
        if (!devices.addAlert(uid, alert)) {
            logger.warn("Received biometric alert for unknown device {}", uid);
        }
        SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.refresh();
            }
        });
    }
}
