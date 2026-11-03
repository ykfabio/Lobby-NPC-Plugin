# ykfabio Network Plugins

One Maven repository containing three separately built plugins:

1. **ykfabioLobby** — Paper lobby, classic-own navigator, glowing compass, lobby items, spawn commands and configurable lobby protection.
2. **ykfabioVelocityLobby** — Velocity-wide `/lobby` command; default destination is the server named `lobby` in `velocity.toml`.
3. **ykfabioNPC** — player NPC creation, skins, right-click actions, server switching and YAML-backed NPC settings.

## Target versions

- Paper API: Minecraft/Paper **26.3** (`[26.3.build,)` Maven range)
- Velocity API: **4.3.0**
- Java: **25**
- Build: Maven multi-module

## NPCs without external dependencies

`ykfabioNPC` does not require Citizens or any other NPC plugin. It creates persistent ArmorStand NPC statues and applies the selected Minecraft player skin to the NPC head using the Paper PlayerProfile API. This is a Citizens-free statue/head implementation, not a full animated player model. Skin lookup requires the server to be able to reach Mojang profile services.

## Build in GitHub Codespaces

1. Create one GitHub repository, e.g. `ykfabio/ykfabioNetworkPlugins`.
2. Upload the contents of this folder to the repository root (the root must contain this `pom.xml`).
3. Open the repository in **Codespaces**.
4. In the terminal run:

   ```bash
   java -version
   mvn -U clean package
   ```

5. JAR output:
   - `ykfabioLobby/target/ykfabioLobby-1.0.0.jar`
   - `ykfabioVelocityLobby/target/ykfabioVelocityLobby-1.0.0.jar`
   - `ykfabioNPC/target/ykfabioNPC-1.0.0.jar`

The included GitHub Actions workflow also builds the project using Java 25 and publishes the three JARs as a workflow artifact.

## Installation

- Copy `ykfabioLobby-1.0.0.jar` to the **lobby Paper server** `plugins/` folder.
- Copy `ykfabioVelocityLobby-1.0.0.jar` to the **Velocity proxy** `plugins/` folder.
- Copy `ykfabioNPC-1.0.0.jar` to the Paper server where the NPCs should exist. No Citizens installation is needed.
- Restart the servers/proxy after installing.

In `velocity.toml`, the backend server must be registered with the name `lobby`. Change `lobby-server` in the Velocity plugin's `plugins/ykfabioVelocityLobby/config.properties` to match your actual server name if it differs. Register CityBuild in `velocity.toml` using the name `citybuild` or change `network.citybuild-server` in the lobby config.

## Commands

### Lobby (Paper)

- `/lobby` — connect to the Velocity lobby server (Velocity plugin)
- `/lobby reload` — reload lobby config
- `/setlobby` — save current location as lobby location
- `/setspawn` — save current location as spawn
- `/spawn` — teleport to saved spawn

### NPC (Paper, Citizens-free)

- `/npc` — show help
- `/npc create <name> [skin]` — create an NPC; optional skin is a Minecraft player name
- `/npc set <id> skin <playername>` — change the NPC head skin
- `/npc remove <id>`
- `/npc list`
- `/npc tp <id>`
- `/npc action <id> <message|command|server>`
- `/npc set <id> <message|command|server> <value>`
- `/npc reload`

NPC data and per-NPC actions are stored in `plugins/ykfabioNPC/config.yml`. Set an NPC's `action` to `message`, `command`, or `server`; configure `message`, `command`, or `server` under the same NPC key. Optional custom hologram lines go in that NPC's `hologram` list.

## Permissions / Admin bypass

The Paper plugins recognize `ranksystem.admin`, `ykfabio.lobby.admin`, `ykfabio.lobby.bypass`, and operator status for administrative commands. The lobby protection bypass is configurable through `protection.admin-bypass`.

The Paper plugins look up `de.ykfabio.ranksystem.paper.RankSystemPaper` and call its `manager().hasPermission(UUID, String)` API reflectively to check `ranksystem.admin`. The Bukkit permission `ranksystem.admin` is also used as a fallback. This avoids adding a compile-time dependency on the RankSystem JAR.

## Notes

This is the source project, not prebuilt JARs. A successful build must be confirmed in Codespaces/GitHub Actions with Java 25. The current execution container only has Java 21 and no Maven, so it cannot truthfully certify a Paper 26.3 build here.

## Lobby defaults and holograms (v1.2.0)
- `settings.yml` → `players.default-hearts` and `players.default-level` are applied automatically on every join (including first join).
- `/lobby sethearts <amount>` and `/lobby setlevel <level>` change defaults and apply them immediately to online players.
- `/holo create|edit|addline|removeline|delete|list|tp|move|reload` manages persistent YAML-backed ArmorStand holograms. Supported text placeholders: `{online}`, `{max}`, `{name}`. Colors use `&` codes.
- Hologram permission: `ykfabio.lobby.hologram` (default: op).
