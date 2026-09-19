# Mado's Nucleus Utilities

A client-side Hypixel SkyBlock mod for Nucleus runners. 
-Temple waypoints, Bal timer, jackpot celebrations, a crystal-run speedrun timer, and achievements.

## Features

- **Temple waypoints** — hotkey captures 3 through-wall highlights at fixed offsets from the Jungle Temple to etherwarp to; custom waypoints (up to 100) anywhere in the Hollows.
- **Bal timer** — 60s respawn countdown triggered by the Bal kill message, movable HUD, Crystal Hollows only.
- **Rare drop animation** — casino wheel + MAX WIN celebration for Divan's Alloy / Quick Claw / Jade Dye, detected the way SkyHanni does (nucleus bundle chat + rare-drop lines).
- **Speedrun timer** — timer with auto splits (box, Yolkar egg, crystals, Bal area, Topaz, first place, box return), bests + trimmed averages in `config/nucleus/speedrun.json`.
- **Achievements** — tiered (Nucleus Runner, Speedrunner, Pro Gambler) and hidden fun achievements, all earned locally.
- Config GUI via `/madobrick` (works anywhere; features work in the Crystal Hollows).

## Requirements

- Minecraft **26.1.2**, Fabric Loader **0.19.5+**, Fabric API, Java **25**.

## Build from source

```sh
./gradlew build
```

The jar lands in `build/libs/`. Compare its SHA-256 with the release artifact:

```sh
# Windows
certutil -hashfile build\libs\nucleus-0.1.0.jar SHA256
```

## Commands

- `/madobrick` — config GUI (all tabs)
- `/madobrick set` / `/madobrick clear` — temple waypoints
- `/madobrick alloy test` — preview the jackpot animation
- `/madobrick hollows` — location diagnostics
- `/madobrick setRuns|setBest|setSplit` — manual speedrun records
- `/nucleus status` — diagnostics

## License

CC0-1.0 — See [LICENSE](LICENSE).
