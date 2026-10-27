# Helio

A personal Android app for the Amazfit Helio Strap, designed for the Galaxy Z Fold 8.

- **Closed (cover screen):** recovery, strain and sleep rings, weekly strain goal, activities, stress.
- **Open:** the same column, plus the stress monitor and the 7-day strain & recovery chart popping in from the right.
- **Half-folded:** live mode, with a stress gauge and live line above the hinge and breathing guide + controls below.
- **Widget:** recovery, strain and stress; refreshed every 30 s while the live link is on.

## Data — direct from the strap (recommended)
Helio talks to the Helio Strap directly using Ridge's protocol module (github.com/TheCommishDeuce/ridge-helio-strap,
AGPL-3.0, vendored in `strap-protocol/` and `strap-science/`). One authenticated Bluetooth link carries both the live
heart rate and a history sync every 10 minutes: heart rate per minute, HRV all day, stress, skin temperature, SpO2,
breathing rate, sleep stages, workouts, PAI and battery. Recovery, strain, sleep need/debt/regularity, Charge and the
illness flag come from Ridge's science; Helio adds live stress, the weekly strain goal, VO2 max blend + running test,
estimated age and the fold-aware UI.

Setup, once:
1. Pair the strap with the Zepp app and install any firmware update.
2. On a computer: `uv run tools/keyfetch/keyfetch.py you@example.com` from the Ridge repo → the strap's MAC and auth key
   (signing in logs the Zepp app out; the key stays valid until the strap is paired again).
3. Keep the Zepp app from reconnecting to the strap (force-stop it / turn off its Bluetooth permission): the strap
   talks to one app at a time.
4. In Helio: Profile → Helio Strap · direct → MAC and key → Save. The link starts and the first sync pulls ~30 days.

### Older history from Zepp
The strap holds about 30 days. To bring in earlier months from the Zepp cloud (heart rate,
steps, stress, sleep), run `tools/zepp-history` on a computer and import the file in
Profile → Import Zepp history file. See tools/zepp-history/README.md.

## Data — fallbacks
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
