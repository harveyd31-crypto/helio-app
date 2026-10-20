# Helio

A personal Android app for the Amazfit Helio Strap, designed for the Galaxy Z Fold 8.

- **Closed (cover screen):** recovery, strain and sleep rings, weekly strain goal, activities, stress.
- **Open:** the same column, plus the stress monitor and the 7-day strain & recovery chart popping in from the right.
- **Half-folded:** live mode, with a stress gauge and live line above the hinge and breathing guide + controls below.
- **Widget:** recovery, strain and stress; refreshed every 30 s while the live link is on.

## Data
- **Live:** the Helio's standard Bluetooth heart-rate broadcast (0x180D / 0x2A37), kept open by a foreground service.
  Live stress = heart rate vs your resting baseline, every 5 s (the Helio does not broadcast beat-to-beat intervals).
  Notification when stress stays ≥ 2.5 for 2 minutes (at most every 30 min).
- **Daily:** Health Connect, from any app (Zepp writes there). Sample data is shown until real data arrives.
- **Planned:** Gadgetbridge sync of the Helio's stored HRV / stress / temperature every ~5 min; port of Pulse's scoring engine.

## Build
Every push to `main` builds the APK on GitHub Actions and publishes it under **Releases** (`helio-<n>.apk`).
Open the latest release on the phone, tap the APK, allow "Install unknown apps" once.

## Credits
- Stress curve follows Pulse Mobile's Stress Monitor (github.com/adityaongit/pulse-mobile), PolyForm Noncommercial 1.0.0.
  This project is for personal, non-commercial use.
- Fonts: Inter and Inter Tight (SIL Open Font License), see FONTS-OFL-LICENSE.txt.
