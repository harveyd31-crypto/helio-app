# Zepp history → Helio

The strap keeps about a month. Older months exist only in the Zepp cloud, from when the Zepp
app synced them. This tool (Ridge's `zepp-backfill`, AGPL-3.0, with an `export` step added
for Helio) pulls them once into one file that Helio imports. Helio only uses it to **fill
gaps**: any minute, night or day the strap delivered itself is kept.

| Comes across | |
|---|---|
| Heart rate, per minute | yes |
| Steps, per minute and day totals (distance, calories) | yes |
| Stress (the strap's own values) | yes |
| Sleep and naps with stages | yes |
| HRV, SpO₂, skin temperature, breathing rate, workouts | no, the cloud doesn't keep them |

## On the Mac (Terminal), once

```sh
brew install uv                      # or: curl -LsSf https://astral.sh/uv/install.sh | sh
cd ~/path/to/helio-app               # the folder Android Studio opened
git pull

uv run tools/zepp-history/backfill.py fetch you@example.com --from 2026-01-01 --to 2026-10-31
uv run tools/zepp-history/backfill.py inspect --tz Africa/Casablanca
uv run tools/zepp-history/backfill.py export  --tz Africa/Casablanca
```

1. **fetch** asks for your Zepp password (typed in the Terminal, not shown, never stored) and
   saves the cloud's raw replies to `~/zepp-backfill`. Signing in logs the Zepp app out.
2. **inspect** prints one line per day so you can see what came back.
3. **export** writes `~/Desktop/helio-history.json.gz`.

`--tz` is the time zone you lived in then. This is your health data: delete
`~/zepp-backfill` and the file when you're done.

## Into Helio

Get the file onto the phone (emulator: drag it onto the emulator window, it lands in
Downloads; Fold: Quick Share / Google Drive), then **Profile → Import Zepp history file**.
Helio works every day out again from the longer history (up to six months are shown).
