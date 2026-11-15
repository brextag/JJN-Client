# JJN Friends Relay Server (`:friends-relay`)

Standalone lightweight TCP relay for **JJN Client** providing:
- Cross-internet **Friends list, live Online/Playing status & Cosmetic/Emote sync**
- **Friend requests & live World Invites**
- **NAT-traversal TCP World Tunnel** (`TUNNEL_JOIN` / `TUNNEL_BIND`) so friends can join your Singleplayer world even behind CGNAT / strict routers
- **`PING` healthcheck probe** (`PING\n` -> `PONG jjn-relay <online> <tunnels>\n`)

Requires **Java 21+** (uses ~32–192 MB RAM) and **1 TCP port** (default `25580`).

---

## Option 1: Docker / Docker Compose (VPS, Home Server, Oracle Cloud Free Tier)

From the `friends-relay/` folder:

```bash
docker compose up -d --build
```

- Opens TCP port `25580` (remember to allow TCP `25580` in your VPS firewall / cloud security group).
- Persists friends & requests in the `jjn_relay_data` Docker volume (`/data/relay-data.json`).

Check logs:
```bash
docker compose logs -f
```

---

## Option 2: Railway / Fly.io / Render (Cloud Container PaaS)

1. Point the platform at `friends-relay/Dockerfile` (Root Directory: `friends-relay`).
2. Expose **TCP** port `25580` (or let the platform pass `PORT` — `JjnRelayServer` reads `JJN_RELAY_PORT`, cloud `PORT`, and `--port`).
3. Attach a persistent volume at `/data` (when `/data` exists, `relay-data.json` is stored there automatically).
4. Copy the public TCP host and port (for example `monorail.proxy.rlwy.net:12345` or `my-relay.fly.dev:25580`) and paste it into:
   - **Launcher -> Friends tab -> Network & Relay Server**, OR
   - **In-Game -> JJN Friends -> Relay Setup** -> click **Test Relay** -> **Save & Connect**.

---

## Option 3: Run Directly with Java / Gradle

Build distribution from repository root:
```bash
./gradlew :friends-relay:installDist
```

Run:
```bash
./friends-relay/build/install/friends-relay/bin/friends-relay --port 25580 --data relay-data.json
```

Environment variables:
- `JJN_RELAY_PORT` or `PORT`: TCP port to listen on (default `25580`)
- `JJN_RELAY_DATA`: path to JSON persistence file (default `/data/relay-data.json` if `/data` exists, else `relay-data.json`)
- Optional client default override: set `JJN_RELAY_URL=host:port` in your environment so JJN Launcher / Client mod connects automatically when no custom Relay URL is saved.
