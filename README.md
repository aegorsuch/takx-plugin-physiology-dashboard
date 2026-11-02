TAKX Physiology Dashboard Plugin
================================

TAKX application plugin for live wearable biometrics, alert history and physiological CSV logging.

**Readme Contents**
- [Purpose and Capabilities](#purpose-and-capabilities)
  - [Sample usage](#sample-usage)
- [Status](#status)
  - [Change log](#change-log)
- [Point of Contacts](#point-of-contacts)
  - [Contact](#contact)
- [Ports Required](#ports-required)
- [Equipment Required](#equipment-required)
- [Equipment Supported](#equipment-supported)
- [Compilation](#compilation)
  - [Build instructions](#build-instructions)
  - [Forge CI and personal GitHub publication](#forge-ci-and-personal-github-publication)
- [Developer Notes](#developer-notes)
  - [Adding external libraries](#adding-external-libraries)
  - [Using CDI](#using-cdi)
- [License](#license)

## Purpose and Capabilities

### Sample usage

Install and start the **Physiology Dashboard** application plugin in TAKX. Its panel opens on the right and listens for incoming CoT events with a `<physiology>` detail. The dashboard shows devices by callsign, model, heart rate, skin temperature (F), exertion, battery, calories, HRLV, VO2, HRV and sensor-fit warning. Select a device to see up to ten recent alert/clear events. Alerts are color-coded; devices appear disconnected after their CoT stale time and disappear 30 seconds later. Use the sort selector for callsign, heart rate, battery or exertion order.

WearTAK physiological PLI now uses `<detail><physiology><device>...</device></physiology></detail>`. Alert/cancel metadata also uses `<physiology alertState="..." ...>`. Only the tag changes; device children, alert attributes and values are unchanged. During transition, legacy `<biometrics><device>...</device></biometrics>` readings remain supported only when the event has no `<physiology>` detail, preventing duplicate dashboard updates and CSV rows. Legacy `<biometrics alertState="..." ...>` alerts/cancels remain supported unless the event also contains a `<physiology>` alert/cancel, which takes precedence regardless of handler order. A legacy alert alongside a primary physiology reading still updates alert history independently.

Click **Start logging** to record each received biometric sample. Click **Stop logging** to finish the CSV and display its full path in the field above the dashboard. Logs are stored in `~/TAKX/physio-logs` (on Windows, under the current user's home directory); stopping the plugin also finishes an active log. The CSV contains UTC collection time, callsign, heart rate (bpm), skin temperature (F) and exertion (%). There is no automatic mission-package send in this desktop port; share the saved CSV separately.

The plugin receives biometric device data from CoT; it does not connect to wearables directly or expose a public API to other plugins.


## Status

The repository declares plugin version `0.1.0-SNAPSHOT`, targeting TAKX `5.8.0`. Deployment readiness and a validated native distributable are not established; GitHub publication remains intentionally blocked as described below.

### Change log

- **0.1.0-SNAPSHOT**: TAKX dashboard for incoming biometric CoT data, alerts, sorting, stale-device status and CSV logging

## Point of Contacts

### Contact

takx@tak.gov

This is the existing repository contact. A plugin-specific maintainer is not identified in the repository.

## Ports Required

The plugin registers CoT detail handlers with TAKX and does not open its own network listener or directly connect to wearables. CoT transport and ports depend on the host TAKX configuration; deployment-specific port requirements are not documented here.

## Equipment Required

A desktop capable of running TAKX `5.8.0`, with the plugin installed and an incoming source of physiological CoT data. CSV logging requires writable storage under the current user's home directory. Development requires the Java and Gradle setup described in [Compilation](#compilation). Minimum desktop hardware specifications are not documented in this repository.

## Equipment Supported

The plugin consumes physiological CoT data rather than interfacing directly with wearable hardware. It supports the current `<physiology>` detail and the legacy `<biometrics>` fallback described in [Sample usage](#sample-usage). A validated wearable/device or desktop operating-system compatibility matrix is not provided in this repository.

## Compilation

### Build instructions

See [Building TAKX Plugins](https://wiki.tak.gov/display/DEV/Building+TAKX+Plugins) for detailed instructions on building this repo, including:

- Setting `takRepoUsername` and `takRepoPassword` in the Gradle credential store using the [GradleCredentialsSetup](https://git.tak.gov/raptor/takx-support/gradle-credentials-setup) repo
- Configuring the install location using the `ORG_GRADLE_PROJECT_pluginInstallDir` environment variable
- Building and installing with `.\gradlew.bat install` on Windows (or `./gradlew install` on Unix-like systems)

This project targets TAKX `5.8.0` (`platformVersion.txt`) and starts at plugin version `0.1.0-SNAPSHOT` (`projectVersion.txt`). It uses the Gradle 7.6.4 wrapper and requires Java 17. Reload the Gradle project in your IDE after changing the build configuration.

### Forge CI and personal GitHub publication

Canonical source is [Forge project 6850](https://git.tak.gov/raptor/takx-plugins/takx-plugin-physiology-dashboard), branch `main`. The personal mirror is [aegorsuch/takx-plugin-physiology-dashboard](https://github.com/aegorsuch/takx-plugin-physiology-dashboard). This is a desktop TAKX plugin, not an Android APK. Build-support `release-8.6.0` implements native installation by copying the `jar` task output into the TAKX plugins directory.

`.gitlab-ci.yml` runs for pushes or manually started pipelines on canonical Forge `main`, and for merge requests targeting canonical `main`. It tests the publication gate, then runs `clean check assemble` using Java 17 and retains `build/libs/` for seven days after success. These files are **unverified build outputs**, not approved GitHub release assets. No dependency credentials are provisioned by this pipeline: use the existing authorized runner/Gradle credential setup described above. Credential failures must remain failures.

Do not replace this command with `build` or `install` for verification: build-support finalizes `build` with native `publish`; `install` also depends on `build`. Existing Gradle repository and package-publishing configuration is unchanged. The new verification job neither invokes those tasks nor signs, installs or publishes packages.

**GitHub publication is intentionally blocked, not implemented.** After a successful `main` build, the blocking manual `personal-github-release` job runs `ci/personal_release_gate.py`, which always exits with status 2 and explains the missing evidence. The main pipeline remains blocked at that manual job; merge request pipelines cannot run it. No token, environment flag or destination override enables publication; no network writes occur. Do not configure a GitHub publishing token for this gate.

Before replacing the gate with a publisher, verify the exact native distributable, manifest, bundled dependencies and TAKX installation compatibility on a credentialed Forge build. Record its SHA-256 together with canonical commit, pipeline and producing job; recheck the downloaded bytes before upload. Implement the established fixed-tag rolling-release pattern only for the personal destination above, with managed-release ownership checks, protected-main validation, serialized updates, stale-pipeline rejection and refusal to overwrite unknown releases, tags or refs. Do not publish to a company repository or invent signing credentials.

Inspection on 2026-10-29 found no `.gitlab-ci.yml`, an empty project `ci_config_path`, no main pipeline history and no visible Maven packages. Variable access returned 403, mirror access 401 and policy endpoints 404; hidden or inherited policies could not be verified and require maintainer review. Local Gradle configuration stopped before task discovery/compilation with `No username configured for repo null/tak`; no distributable, hash or build provenance was validated.

Run gate tests without Gradle credentials:

```powershell
python -m unittest discover -s ci -p "test_*.py" -v
```


## Developer Notes

### Adding external libraries

Declaring an external dependency alone does not package it with the plugin. To bundle a library in the plugin JAR's `/lib` directory, configure `includeInJar` in `build.gradle` and add the dependency to that configuration:

```groovy
buildSupport.configureIncludeInJar(project)

dependencies {
    includeInJar group: 'org.jfree', name: 'jfreechart', version: '1.5.4'
}
```

The example library is not currently a dependency of this project. Add it only if the plugin uses it.

### Using CDI

Annotate a component with `@ApplicationScoped` to make it available for injection. Annotate a class that receives injected values with `@PseudoBean`:

```java
@ApplicationScoped
public class ManagerA {
    public void doAThing() {
        // Component behavior goes here.
    }
}

@PseudoBean
public class PanelB extends JPanel {
    @Inject
    private ManagerA manager;

    @PostConstruct
    private void postConstruct() {
        manager.doAThing();
    }
}
```

Injected fields are populated after construction, so use `@PostConstruct` rather than the constructor if initialization needs an injected value.

## License

No license file or explicit license declaration was found in the canonical repository at this revision. Licensing terms are unverified; confirm them with the maintainer before redistribution. This README reformat does not grant a license or change existing rights.
