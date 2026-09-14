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

## 🔨 In progress (this batch)

- [ ] **Zoom** (`zoom`, Utility) — hold-key FOV zoom, smooth lerp, scroll-adjust, lower sensitivity while zoomed. Tick-based, no mixins.
- [ ] **FreeLook** (`freelook`, Utility) — hold-key 360° camera, player keeps walking direction. Requires the project's first mixin (camera detach).

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
