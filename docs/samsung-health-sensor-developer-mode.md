# Samsung Health Sensor Service developer mode

## Scope

This note records the procedure verified on the project's physical test device. It is development setup, not a distribution-policy workaround.

Verified baseline:

- device: Samsung Galaxy Watch9, model SM-L355F;
- service: Health Sensor Service 1.8.00.07;
- result: developer mode enabled;
- Samsung Health Monitor is a separate consumer application and must not be used as evidence that the SDK service is configured.

Do not store the watch IP address, transient pairing or debugging ports, pairing code, ADB keys, serial number, IMEI, or health samples in this repository.

## Why the documented menu path was insufficient

On the verified service version, opening the ordinary application card from the watch's **Settings > Apps** list did not show a developer-mode control. Searching in Galaxy Wearable on the phone also did not expose it.

The working entry point was the internal watch activity:

```text
com.samsung.android.service.health/.wear.settings.home.SettingsMainActivity
```

This component name is an observed implementation detail and may change after a watch or service update. Always retain runtime capability and error checks in the application.

## Verified ADB procedure

Prerequisites:

1. Enable developer options and wireless debugging on the watch.
2. Keep the watch awake and on the same local network as the development computer.
3. Read the current pairing port, debugging port, and one-time pairing code from the watch. Ports can change.

Use placeholders rather than copying local connection details into scripts or documentation:

```powershell
adb pair <WATCH_IP>:<PAIRING_PORT>
adb connect <WATCH_IP>:<DEBUG_PORT>
adb devices -l
adb -s <WATCH_IP>:<DEBUG_PORT> shell am start -n com.samsung.android.service.health/.wear.settings.home.SettingsMainActivity
```

The `-s` selector is required when both the Wear OS emulator and physical watch are present.

Confirm the intended screen visually on the watch. A successful activity-start command or `mCurrentFocus` value alone does not prove that the interactive service screen is visible: the charging overlay can still cover it. Exit the charging screen before tapping the service title and enabling developer mode.

After testing, wireless debugging can be disabled on the watch. If the endpoint is still connected, it can first be removed from ADB with:

```powershell
adb disconnect <WATCH_IP>:<DEBUG_PORT>
```

## Observed connection failures

| Symptom | Likely condition observed during setup | First checks |
| --- | --- | --- |
| `protocol fault` during pairing | stale code or pairing port; incompatible or confused ADB session | Generate a fresh code, verify the pairing port, and restart the ADB server before retrying |
| Windows socket error `10013` | firewall or Windows networking policy blocked the connection | Check the local-network profile, firewall permission, and WinNAT state |
| Windows socket error `10060` | old port, sleeping watch, or suspended Wi-Fi | Wake the watch and read the current debugging port again |
| `error: closed` | unstable wireless ADB session | Keep the watch awake, reconnect, and repeat the command with the exact device selector |

These are troubleshooting observations, not universal diagnoses. Recheck the current watch screen and endpoint before changing firewall settings.

## SDK consequence and boundary

Developer mode allows local testing with an application signature that has not yet been registered with Samsung, where the selected tracker is supported. Without the required development or production authorization, tracker startup can return `SDK_POLICY_ERROR`.

Developer mode does not authorize public distribution. Before a release that uses Samsung Health Sensor SDK, the project still requires the applicable Samsung partner process and registration of the final application package and signing-certificate SHA-256 value. The provisional application ID must therefore be replaced before SDK integration, signing, or distribution.

## References

- Samsung developer mode: https://developer.samsung.com/health/sensor/guide/developer-mode.html
- Samsung app verification: https://developer.samsung.com/health/sensor/guide/app-verification.html
