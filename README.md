# Mado's Nucleus Utilities

- Preset waypoints for jungle etherwarp + cheese
- Custom waypoints that reset per lobby that you can set with a click of a button!
- Bal timer that tracks when bal is able to respawn again!
- Speedrun timer with splits with tons and tons of customizibility features! Track your best runs!
- Gambling animation for when you get a rare drop!
- For fun achievements to keep you on the grind!
- Tells you the lobby day!
- Wrong pet/Low on tools alerts!
- Tools/hr tracker
- More!
- And even more to come!
A client-side Hypixel SkyBlock mod for **Crystal Hollows** players (Fabric, Minecraft 26.1.2).
Config GUI via `/mado` (works anywhere; features work in the Crystal Hollows) with five tabs:
**Features, Waypoints, Sounds, Speedruns, Achievements.**

## Trust first: is this safe?

Yes — verify it yourself instead of trusting me:

- **100% open source.** Every line of the mod is in this repository (`src/`). No obfuscation, no hidden modules.
- **No internet access.** The mod makes zero network requests: no update checks, no analytics, no Discord RPC, no API calls. It only reads your game (chat, scoreboard, entities) and draws overlays.
- **No account access.** It never touches your session, tokens, or credentials. Files: only its own tiny configs in `.minecraft/config/nucleus/` — plus a sound file **you** pick yourself via the Browse button (a plain `.wav`, read once to play it, never uploaded anywhere).
- **Reproducible CI builds.** Every push is compiled from source by GitHub Actions (`.github/workflows/build.yml`). Every release is built from its tag by `.github/workflows/release.yml` with the jars attached on the **Releases page** — download from there instead of any random file, and compare its SHA-256 with your own `./gradlew build`.
- **Small, readable codebase.** The whole mod is a handful of classes under `src/*/java/com/nucleus/` — start with `NucleusClient.java`.

## Features

- **Waypoints tab** — Jungle Temple highlights: **Automatic** (finds both Door Guardians, places from the higher-X one's feet) or **Manual** (hotkey capture); custom waypoints (up to 100) anywhere in the Hollows.
- **Features tab** — Bal respawn timer, gambling animation for Divan's Alloy / Quick Claw / Jade Dye (detected the way SkyHanni does), lobby-day + scavenger trackers, wrong-pet / low-tools alerts, NPC highlights (Yolkar, Robot, Keepers, both Door Guardians; cleared per lobby).
- **Sounds tab** — objective sounds (any Minecraft sound id or your own `.wav`, volume 0–200%, optional OS-mixer bypass, per-trigger toggles, gambling volume).
- **Speedruns tab** — crystal-run timer with auto splits, bests + trimmed averages in `config/nucleus/speedrun.json`.
- **Achievements tab** — tiered (Nucleus Runner, Speedrunner, Pro Gambler) and hidden fun achievements, all earned locally.

## Requirements

- Minecraft **26.1.2**, Fabric Loader **0.19.5+**, Fabric API, Java **25** (the build fails on older Java — Loom requires 21+).

## Build from source

```sh
./gradlew build
```

The jar lands in `build/libs/` as e.g. `MadoNucUtilities-1.3.1.jar`. Compare its SHA-256 with the release artifact:

```sh
# Windows
certutil -hashfile build\libs\MadoNucUtilities-1.3.1.jar SHA256
```

## Commands

- `/mado` — config GUI (all tabs)
- `/mado times` | `/mado runs` — run history in chat
- `/mado stop` — reset the speedrun timer
- `/mado delete last` | `/mado delete all` — custom waypoints
- `/mado debug` — diagnostics dump
- `/mado notabwarn` — permanently hide the `/tab` widget warning

## License

CC0-1.0 — do whatever you want with it. See [LICENSE](LICENSE).
