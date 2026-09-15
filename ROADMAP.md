# Moid Client — Module Roadmap

Single source of truth for what's shipped, what's next, and what's parked.
Finished modules also live in the README table; this file tracks everything else.

## ✅ Shipped

| Module | Category | ID |
|---|---|---|
| FPS Counter | HUD | `fpsCounter` |
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

- [x] **Zoom** (`zoom`, Utility) — hold/toggle, dashboard-rebindable key, level slider, smooth + speed, lower sensitivity. Tick-based, no mixins. Writes FOV past slider-range validation via direct field write.
- [x] **FreeLook** (`freelook`, Utility) — hold/toggle, dashboard-rebindable key, auto third-person-back on engage (restores after), sensitivity slider. The project's first mixin (camera detach + turn reroute).
- [ ] **26.3 port** — Stonecutter `versions/26.3` node + SDL input layer (`isKeyDown(int)`, KEYBOARD enum, key-code translation). Range stays `<26.3` until this lands.

## 🧱 Infra

- [x] **Stonecutter migration** — one shared `src/`, per-version nodes (`versions/26.1`, `versions/26.2`), per-version jars (`Moid-Client-v1.1.0+26.x.jar`). Build one: `./gradlew :26.1:build`. Build all: `./gradlew :26.1:build :26.2:build`. Reset to VCS version before committing: `./gradlew "Reset active project"`.

## ⏭ Next up (agreed core gaps)

| Module | Category | Why |
|---|---|---|
| ToggleSprint | Utility | #1 client staple, servers expect it |
| ToggleSneak | Utility | pairs with ToggleSprint |
| Armor Status | HUD | most-used HUD after FPS/CPS |
| Potion Effects | HUD | same tier as armor |
| Crosshair Customization | HUD | already planned in README |
| Direction / Compass | HUD | cheap, loved by builders/PvPers |

## 💡 Parked ideas (later)

- Auto GG / Auto GLHF (Utility, shines via web-UI lists)
- Time Changer / client Weather (Visuals, client-side only)
- Particles multiplier (Visuals)
- Nametag tweaks, subtle only (Visuals)
- Item counter / durability warnings (HUD)

## 🚫 Out of scope

Cheats of any kind (kill aura, fly, speed, reach, ESP/X-ray, auto-clickers beyond CPS display).
Moid stays legit-server-safe: HUD, Visuals, Utility only.
