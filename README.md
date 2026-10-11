# BlemmEssentials

BlemmEssentials is a server-essentials project targeting Minecraft 26.3 and Java 25.

The Paper/Purpur plugin is the reference implementation and includes economy, teleports, homes, warps, ranks, categorised shop, selling, and auction house features. The Fabric, Forge, and NeoForge ports are separate native mods; they cannot load the Paper plugin JAR.

## Platform status

| Platform | Build project | Currently ported |
| --- | --- | --- |
| Paper/Purpur | `paper/` | Full reference feature set |
| Fabric | `fabric/` | Initial economy commands: `/balance`, `/money`, `/pay` |
| Forge | `forge/` | Initial economy commands: `/balance`, `/money`, `/pay` |
| NeoForge | `neoforge/` | Initial economy commands: `/balance`, `/money`, `/pay` |

The three mod ports are an initial compatibility layer, **not yet feature-parity replacements** for the Paper plugin. Homes, teleports, warps, ranks, shops, auctions, optional integrations, and the Paper GUI flows still need to be ported and tested independently on each loader.

## Economy commands on the mod ports

- `/balance` or `/money` — show your balance.
- `/pay <player> <amount>` — transfer money to an online player.
- `/money <player> <amount>` — operator-only balance adjustment.

Mod-port balances are stored in `blemmessentials/balances.tsv` in the server directory. Paper continues to use its own configuration and storage; data is not automatically shared between platforms.

## Paper integrations

Optional integrations include Geyser + Floodgate, DiscordSRV, Multiverse-Core, and PlaceholderAPI.

### Discord + Bedrock skins

1. Install DiscordSRV and PlaceholderAPI.
2. In DiscordSRV `config.yml`, set `AvatarUrl: "%blemm_discord_avatar%"`.
3. Java players use their normal avatar; Bedrock players (Floodgate UUIDs) use the skin Geyser uploaded, looked up from the GeyserMC skin API.

### Per-world disabling

Paper supports `disabled-commands` in `config.yml`, e.g. `world_the_end: [rtp, home, compass]`.

## Build

- Paper: `mvn -f paper/pom.xml package` (Java 25).
- Fabric: `gradle -p fabric build` (Java 25).
- Forge: `gradle -p forge build` (Java 25).
- NeoForge: `gradle -p neoforge build` (Java 25).

GitHub Actions builds each platform independently and assembles `BlemmEssentials-all-platforms.zip` only if all four builds succeed.
