# Moid Client — Module Roadmap

Single source of truth for what's shipped, what's next, and what's parked.
Finished modules also live in the README table; this file tracks everything else.

## ✅ Shipped

| Module | Category | ID |
|---|---|---|
| FPS Counter | HUD | `fpsCounter` |
| TPS Counter | HUD | `tpsCounter` |
| Ping HUD | HUD | `ping` |
| CPS Counter | HUD | `cpsCounter` |
| Keystrokes | HUD | `keystrokes` |
| Coordinates | HUD | `coords` |
| Server IP | HUD | `server` |
| Clock | HUD | `clock` |
| Biome | HUD | `biome` |
| Fullbright | Visuals | `fullbright` |
| Block Outline | Visuals | `blockOutline` |
| Perspective Skip | Utility | `perspectiveSkip` |
| Custom Hitboxes | Visuals | `hitboxes` |
| Zoom | Utility | `zoom` |
| FreeLook | Utility | `freelook` |

## 🔨 In progress (this batch)

- [x] **Zoom** (`zoom`, Utility) — hold/toggle, dashboard-rebindable key, level slider, per-frame exponential smooth in/out (toggleable) + speed, lower sensitivity. Camera mixin overwrites computed FOV; user option untouched.
- [x] **FreeLook** (`freelook`, Utility) — hold/toggle, dashboard-rebindable key, auto third-person-back on engage (restores after), sensitivity slider. The project's first mixin (camera detach + turn reroute).
- [x] **26.3 port** — Stonecutter `versions/26.3` node + SDL input layer (`NativeKeys`: `isKeyDown(int)`, KEYBOARD enum, GLFW↔SDL code translation, MouseHandler buttons). First `//?` blocks in `src/`. In-game verification pending.

## 🧱 Infra

- [x] **Stonecutter migration** — one shared `src/`, per-version nodes (`versions/26.1`, `versions/26.2`, `versions/26.3`), per-version jars (`Moid-Client-v1.2.0+26.x.jar`). Build one: `./gradlew :26.3:build`. Build all: `./gradlew :26.1:build :26.2:build :26.3:build`. Reset to VCS version before committing: `./gradlew "Reset active project"`.

## ⏭ Next up (agreed core gaps)

| Module | Category | Why |
|---|---|---|
| ToggleSprint | Utility | #1 client staple, servers expect it |
| ToggleSneak | Utility | pairs with ToggleSprint |
| Armor Status | HUD | most-used HUD after FPS/CPS |
| Potion Effects | HUD | same tier as armor |
| Crosshair Customization | HUD | already planned in README |
| Direction / Compass | HUD | cheap, loved by builders/PvPers |
| Combo Counter | HUD | consecutive hits, PvP staple, needs attack hook |
| Session Timer | HUD | uptime clock, trivial, fits dashboard vibe |

## 💡 Parked ideas (later)

- Auto GG / Auto GLHF (Utility, shines via web-UI lists)
- Time Changer / client Weather (Visuals, client-side only)
- Particles multiplier (Visuals)
- Nametag tweaks, subtle only (Visuals)
- Item counter / durability warnings (HUD)
- Memory usage (HUD, trivial via Runtime, debug flavor)
- Reach Display (HUD, last-attack distance, needs attack hook like Combo)
- Auto Reconnect (Utility, rejoin after disconnect, needs care on servers)

## 🚫 Out of scope

Cheats of any kind (kill aura, fly, speed, reach, ESP/X-ray, auto-clickers beyond CPS display).
Moid stays legit-server-safe: HUD, Visuals, Utility only.
