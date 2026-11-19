# JJN Client

A custom **Minecraft Launcher & Built-In Fabric Client Mod Suite** built with Java 25 and JavaFX 27, featuring dual bundled Fabric client mods for **Minecraft `1.21.4`** and **Minecraft `26.2`**, a **Dockerized Friends & World Hosting Relay**, **Profiles with 3D Skin/Cape/Cosmetic Previews**, **Named Waypoints**, **Quests & JJN Coins (`◈`)**, a **3D Cosmetics Shop**, and **Synced 3D Emotes**.

---

## Features

### Launcher (`JJNLauncher`)
- **Blue, White & Black Theme, Minecraft Font & UI Customizer** — Tactile 3D button UI powered by the bundled official **Minecraft font** (`Minecraft-Regular.ttf` / `Minecraft-Bold.ttf`), with live customization in **Settings** (theme presets, custom accent/background/surface/text color pickers, UI scale, font toggle, and 3D button depth).
- **Microsoft Account Sign-In & Switcher** — Persistent Microsoft OAuth + Xbox Live/Minecraft token caching and multi-account switching stored in `~/.jjnclient/accounts.json`.
- **Instance & Loader Management** — Create and launch **Fabric**, **Quilt**, **Forge**, or Vanilla instances, inspect/install mods from Modrinth, and auto-bundle the matching built-in `jjn-client` mod jar on Fabric `1.21.4` and `26.2`.
- **Keybinds & Controls Editor (`⌨ Keybinds`)** — Edit all standard Minecraft and discovered/custom modded `key_*` keybinds plus mouse sensitivity, Toggle Sneak/Sprint, Auto-Jump, Invert Y, and Raw Mouse Input per instance (`options.txt`), with live keyboard/mouse capture, conflict detection, and **Apply to All Instances**.
- **Profiles & 3D Preview** — Create custom profiles with Skin/Slim variant, custom Capes, and live interactive 3D preview (`SkinPreviewCanvas`), mirrored on the in-game Title Screen.
- **Friends Relay & Diagnostics** — Add friends, view what world/server they are playing, configure a Friends Relay (or default via `JJN_RELAY_URL` / `-Djjn.relay.url`), and run live **Test Relay** checks (latency ms, online players, active tunnels).
- **Shop, Quests, Creator Codes & Admin Panel** — Earn **JJN Coins (`◈`)** from Daily & Lifetime Quests, unlock Capes, 3D Wings/Aero-Packs, 3D Headgear, Particle Auras, and 3D Emotes, bind emotes to your 8-slot wheel, redeem **Creator Codes** (`JJN`, `BLUE`, `VIP`, `WELCOME`, or custom codes in `creator-codes.json`) for shop discounts & featured deals, and manage coins/items/quests/codes via the secret **Admin Panel** (code configurable in `EconomyStore.ADMIN_ACCESS_CODE`).
- **Standalone Windows `.exe` Packaging** — Packages via `jpackage` into `JJN Client.exe` with custom icon and Desktop shortcut support.

### In-Game Client Mods (`:client-mod` for `1.21.4` & `:client-mod-26` for `26.2`)
- **Custom Title Screen & Badges** — JJN-themed front screen with left-side interactive 3D player showcase rendering your skin, cape, and equipped **3D Shop Headgear & Wings/Aero-Packs**, plus the JJN Creeper emblem in chat, nametags, and the tab list.
- **Customizable HUD & Modules (`Right Shift` → `MODULES` / `HUD`)** — Paginated module control center featuring **FPS Counter**, **CPS Counter (LMB | RMB)**, **Opti-Zoom (`[C]` hold)** with smooth camera toggle, **Fullbright**, **Armor Status HUD** (equipped slots & durability %), **Potion Status HUD** (active effects & timers), **Coordinates & Direction**, **Toggle Sprint**, **Ping & Server**, **Biome**, and **Keystrokes HUD** with drag-to-position editor, opacity, scale, and accent presets.
- **Named Waypoints (`U`)** — Create, edit, and color-code named waypoints with world markers, beacon beams, distance labels, and off-screen edge arrows.
- **Emotes (`B`) & Multiplayer Cosmetics Sync** — Trigger 8 animated 3D emotes (*Friendly Wave*, *Commander Salute*, *Applause*, *Ascended T-Pose*, *Facepalm*, *Classic Dab*, *Starlight Spin*, *Breakdance Groove*); equipped Capes, 3D Wings/Aero-Packs, 3D Headgear, Particle Auras, and active Emotes sync live across the **Friends Relay** and **LAN** so friends see your cosmetics and animations in-game.
- **Friends & Pause Menu World Hosting** — Host Singleplayer worlds to friends (`HOST TO FRIENDS` in Pause Menu) with UPnP / Friends Relay TCP tunneling, test Relay reachability in-game, and join friend invites with `[J]`.

---

## Default In-Game Keybinds

| Key | Action |
| --- | --- |
| `Right Shift` | Open **JJN Control Center** (Modules, HUD, Emotes, Shop & Quests, Installed Mods) |
| `C` *(Hold)* | **Opti-Zoom** 4x smooth optical zoom |
| `U` | Quick-add a **Named Waypoint** at your current coordinates |
| `B` | Open the **8-Slot Emote Wheel** (`1`–`8` or click to perform) |
| `J` | Accept & join the latest incoming **Friend World Invite** toast |

---

## Repository Structure

```text
JJNLauncher/
├── src/                # JavaFX Launcher, Auth, Installers, Themes, Keybinds, Profiles, Friends, Shop & Emotes UI
├── client-mod/         # Bundled Fabric client mod for Minecraft 1.21.4 (Yarn mappings)
├── client-mod-26/      # Bundled Fabric client mod for Minecraft 26.2 (Mojang mappings)
├── friends-relay/      # Java 21 TCP & Friends presence/cosmetics/world-tunnel relay + Docker setup
└── packaging/          # Windows .ico & PowerShell install / Desktop shortcut script
```

---

## Build & Run

### Prerequisites
- **JDK 25** (configured via Gradle Java toolchain; `:client-mod` and `:friends-relay` target Java 21 bytecode).

### Run Launcher from Source
```powershell
.\gradlew.bat run
```

### Build Everything & Package `JJN Client.exe` (Windows)
```powershell
.\gradlew.bat clean build packageExe :friends-relay:installDist --console=plain
```
Install the packaged app image to `%LOCALAPPDATA%\JJN Client\JJN Client.exe` and create the Desktop shortcut:
```powershell
powershell -ExecutionPolicy Bypass -File "packaging\create-desktop-shortcut.ps1"
```

### Run / Host the Friends Relay Server
Local run:
```powershell
.\friends-relay\build\install\friends-relay\bin\friends-relay.bat
```
Or host online with Docker (see `friends-relay/README.md` for VPS / Railway / Fly.io setup):
```powershell
docker compose -f friends-relay/docker-compose.yml up -d --build
```
*(Main class: `com.jjn.relay.JjnRelayServer`, TCP port `25580`)*

---

## Local Data Locations
All persistent data is stored outside the repository under `%USERPROFILE%\.jjnclient\`:
- `instances/<instance>/saves/` — Singleplayer worlds
- `instances/<instance>/game/options.txt` — Per-instance Minecraft & modded keybinds and controls
- `settings.properties` — Launcher RAM, resolution, UI theme presets/colors, font, and scale settings
- `accounts.json` — Saved Microsoft launcher sessions
- `profiles.json` — Launcher profiles & custom skin/cape selections
- `friends.json` — Friends list & relay configuration
- `economy.json` — JJN Coins (`◈`), unlocked cosmetics/emotes, wheel slots, and quest progress
- `creator-codes.json` & `creator-state.json` — Custom Shop Creator Codes and active/redeemed code state
