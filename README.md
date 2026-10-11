# BlemmEssentials

Economy, teleports, homes, warps, ranks, categorised shop, selling and auction house.

Targets Paper/Purpur 26.3 (Java 25). **Recommended** (optional): Geyser + Floodgate, DiscordSRV, Multiverse-Core, PlaceholderAPI.

## Discord + Bedrock skins
1. Install DiscordSRV and PlaceholderAPI.
2. In DiscordSRV `config.yml` set `AvatarUrl: "%blemm_discord_avatar%"`.
Java players use their normal avatar; Bedrock players (Floodgate UUIDs) use the skin Geyser uploaded, looked up from the GeyserMC skin API.

## Per-world disabling
`disabled-commands` in config.yml, e.g. `world_the_end: [rtp, home, compass]`.

## Build
`mvn -f paper/pom.xml package` (Java 25). CI uploads `BlemmEssentials-all-platforms.zip` with `paper/ fabric/ neoforge/ forge/`.
