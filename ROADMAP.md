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
| Session Timer | HUD | `sessionTimer` |
| Potion Effects | HUD | `potionEffects` |
| Armor Status | HUD | `armorStatus` |
| Fullbright | Visuals | `fullbright` |
| Block Outline | Visuals | `blockOutline` |
| Perspective Skip | Utility | `perspectiveSkip` |
| Custom Hitboxes | Visuals | `hitboxes` |
| Zoom | Utility | `zoom` |
| FreeLook | Utility | `freelook` |

## 🔨 In progress (this batch)

- [x] **Zoom** (`zoom`, Utility) — hold/toggle, dashboard-rebindable key, level slider, per-frame exponential smooth in/out (toggleable) + speed, lower sensitivity. Camera mixin overwrites computed FOV; user option untouched.
- [x] **FreeLook** (`freelook`, Utility) — hold/toggle, dashboard-rebindable key, auto third-person-back on engage (restores after), sensitivity slider. The project's first mixin (camera detach + turn reroute).
- [x] **26.3 port** — Stonecutter `versions/26.3` node + SDL input layer (`NativeKeys`: `isKeyDown(int)`, KEYBOARD enum, GLFW↔SDL code translation, MouseHandler buttons). First `//?` blocks in `src/`. Verified in-game on 26.2 + 26.3 (26.1 smoke test still open).

## 🧱 Infra

- [x] **Stonecutter migration** — one shared `src/`, per-version nodes (`versions/26.1`, `versions/26.2`, `versions/26.3`), per-version jars (`Moid-Client-v1.3.0+26.x.jar`). Build one: `./gradlew :26.3:build`. Build all: `./gradlew :26.1:build :26.2:build :26.3:build`. Reset to VCS version before committing: `./gradlew "Reset active project"`.

## ⏭ Next up (agreed core gaps)

| Module | Category | Why |
|---|---|---|
| ToggleSprint | Utility | #1 client staple, servers expect it |
| ToggleSneak | Utility | pairs with ToggleSprint |
| Crosshair Customization | HUD | already planned in README |
| Direction / Compass | HUD | cheap, loved by builders/PvPers |
| Combo Counter | HUD | consecutive hits, PvP staple, needs attack hook |
| Autohide Hotbar/HUD | HUD | fade out when idle, Lunar-style QoL |
| Inventory HUD | HUD | see inventory contents without opening it |
| Mousestrokes | HUD | drag-direction indicator, two circles |

## 💡 Parked ideas (later)

- Auto GG / Auto GLHF (Utility, shines via web-UI lists)
- Time Changer / client Weather (Visuals, client-side only)
- Particles multiplier (Visuals)
- Nametag tweaks, subtle only (Visuals)
- Item counter / durability warnings (HUD)
- Memory usage (HUD, trivial via Runtime, debug flavor)
- Reach Display (HUD, last-attack distance, needs attack hook like Combo)
- Auto Reconnect (Utility, rejoin after disconnect, needs care on servers)
- Damage Indicator (HUD, floating damage numbers, needs attack hook like Combo)
- Zoom level reset (Utility, small: one-click reset for scroll-adjusted zoom)
- Discord RPC (Utility, rich presence, needs Discord IPC dependency)
- Item Physics (Visuals, drop animations client-side)
- Color Saturation (Visuals, saturation slider, fullbright family)
- Hit Color (Visuals, entity hurt tint)
- Motion Blur (Visuals, heavy: frame accumulation, invasive)
- Loot Beams (Visuals, pillars on rare drops, needs drop detection + world render)
- Screenshot Manager (Utility, capture key + gallery)
- NoFog / NoRender (Visuals, fog toggle or render filters)
- TNT Timer (HUD, primed-TNT fuse countdown display)

## 🚫 Out of scope

Cheats of any kind (kill aura, fly, speed, reach, ESP/X-ray, auto-clickers beyond CPS display).
Moid stays legit-server-safe: HUD, Visuals, Utility only.
