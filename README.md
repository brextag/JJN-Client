# JJN Client

**Premium Minecraft Java Edition Client**

![JJN Client](https://img.shields.io/badge/JJN-Client-blueviolet?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-Java%20Edition-green?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Foundation-orange?style=for-the-badge)

> Modern • Minimalistic • Futuristic  
> Liquid Glass UI • Modular Architecture • High Performance

---

## Design Philosophy

JJN Client is designed to feel like premium Apple software meeting the power of clients like Lunar and Badlion.

- **Liquid Glass-inspired UI**: Translucent panels, subtle blur, layered depth
- Smooth animations and transitions
- Rounded corners and elegant typography
- Clean dark theme by default (light theme available)
- No cyberpunk clutter or excessive neon
- Consistent spacing, alignment, and iconography

---

## Features

### Main Dashboard
- Large Minecraft character preview with animated 3D model and rotation controls
- JJN Client branding and logo
- Animated Play button
- Minecraft version selector
- Server / Multiplayer shortcuts
- Settings & Account management
- News & Updates section
- Current username + avatar

### Navigation
Sleek sidebar with:
- Home
- Multiplayer
- Cosmetics
- Mods
- Settings
- Accounts
- Performance

### In-Game HUD (Fully Customizable)
- FPS Counter
- CPS Counter (LMB / RMB)
- Coordinates
- Ping Display
- Keystrokes
- Armor Status
- Potion Effects
- Toggleable Scoreboard
- Custom Crosshair Editor
- Dynamic Health & Hunger

Every element is independently movable, resizable, and configurable.

### Modular Mods
- Toggle Sprint
- Motion Blur
- Zoom
- Freelook
- Keystrokes
- CPS / FPS Display
- Armor HUD
- Direction HUD
- Coordinates
- Ping Display
- Custom Crosshair
- Hit Color
- Item Physics

### Cosmetics
- Custom Capes
- Wings
- Hats
- Character Accessories
- Animated Cosmetics
- Live Skin Preview

### Settings & Performance
- Video / Audio / Controls
- Interface Customization
- Theme (Dark / Light)
- Language Selection
- Performance Dashboard (FPS, Memory, RAM, CPU)
- Configurable FPS limits & Graphics presets

---

## Technical Architecture

- **Target**: Minecraft Java Edition
- Modular architecture (UI separated from gameplay systems)
- Multi-version support (planned)
- Fabric-compatible core (recommended loader)
- Persistent settings
- Responsive interface
- Proper loading screens & error handling

---

## Project Structure

```
JJN-Client/
├── launcher/                 # Electron + React launcher (Liquid Glass UI)
├── client/                   # Fabric client-side mod suite
│   ├── src/main/java/com/jjnclient/
│   │   ├── mods/
│   │   ├── hud/
│   │   ├── cosmetics/
│   │   ├── settings/
│   │   └── ui/
├── assets/
├── docs/
└── README.md
```

---

## Getting Started

### Prerequisites
- Java 21+
- Node.js 20+
- Minecraft Java Edition
- Fabric Loader (for the mod suite)

### Launcher (UI)
```bash
cd launcher
npm install
npm run dev
```

### Client Mod (Fabric)
```bash
cd client
./gradlew build
```

---

## Development Roadmap

- [x] Project foundation & branding
- [ ] Liquid Glass UI implementation (launcher)
- [ ] Core mod framework
- [ ] HUD system with drag & drop
- [ ] Mod modules (Toggle Sprint, Zoom, etc.)
- [ ] Cosmetics system
- [ ] Settings persistence
- [ ] Performance dashboard
- [ ] Multi-version support
- [ ] Official website & CDN

---

## Contributing

This is the official foundation of **JJN Client**.  
Pull requests, ideas, and improvements are welcome.

---

## License

All rights reserved.  
JJN Client © 2026

---

**Built with precision. Designed for excellence.**
