# BlemmEssentials

BlemmEssentials targets Minecraft 26.3 and Java 25. Paper/Purpur remains the reference implementation; Fabric, Forge, and NeoForge use native server mods because the Paper plugin JAR cannot run on mod loaders.

## Platform status

| Platform | Project | Current scope |
| --- | --- | --- |
| Paper/Purpur | `paper/` | Reference implementation: economy, teleports, homes, warps, ranks, shop, selling, auction house, integrations, and GUI flows |
| Fabric | `fabric/` | Economy, command-based shop/selling, auctions, ranks, homes, warps, lobby, random teleport, teleport requests, friends and friend-only home visits |
| Forge | `forge/` | Economy, command-based shop/selling, auctions, ranks, homes, warps, lobby, random teleport, teleport requests, friends and friend-only home visits |
| NeoForge | `neoforge/` | Economy, command-based shop/selling, auctions, ranks, homes, warps, lobby, random teleport, teleport requests, friends and friend-only home visits |

The mod ports are a functional first pass, **not exact feature parity**. The Paper inventory GUI, Paper's exact selling rules and category assignments, DiscordSRV announcements, PlaceholderAPI, Geyser/Floodgate avatar integration, Multiverse integration, and Paper-specific menu/compass behavior are not ported to the mod loaders. The mod ports now support configurable category defaults and per-item buy/sell prices, but they do not yet duplicate every Paper shop rule. The loader shop and auction house are command-based, rank prefixes use vanilla scoreboard teams (which may conflict with another mod that manages the same teams), and auction listings preserve item type and stack count but not custom item metadata.

## Commands in the mod ports

### Economy and trading
- `/balance` or `/money` — view your balance.
- `/pay <player> <amount>` — pay an online player.
- `/money <player> <amount>` — operator-only balance adjustment.
- `/shop <item> <amount>` — buy up to one stack of an item by registry ID (for example, `diamond` or `minecraft:iron_ingot`).
- `/shopprice <item>` — inspect an item's current buy/sell prices.
- `/shopprices` — show price configuration keys and supported categories.

Mod-loader shop prices are editable in `blemmessentials/shop-prices.properties` after the first server start. Configure category defaults with `default.<category>.buy` and `default.<category>.sell`, or override an item with `item.<namespace:id>.buy` and `item.<namespace:id>.sell`. A sell price of `0` disables selling that item.

### Per-dimension command disabling on mod loaders

After first start, edit `blemmessentials/disabled-commands.properties`. Use dimension IDs as keys, such as `minecraft_the_end=rtp,home,sethome`, or `*=rtp` to disable a command in every dimension. List command names separated by commas; leave a value empty to allow all BlemmEssentials commands in that dimension.


### Discord auction announcements on mod loaders

The mod ports can announce new auctions without DiscordSRV by using a Discord webhook. After the first server start, open `blemmessentials/discord.properties` and set `webhook-url` to a Discord webhook URL. Leave it empty to disable announcements. Webhook requests run asynchronously; Paper continues to support its existing DiscordSRV integration.

- `/sell` — sell the stack in your main hand.
- `/ah list` — list auctions.
- `/ah sell <price>` — list the held stack.
- `/ah buy <id>` — purchase a listing.
- `/ah cancel <id>` — cancel your listing (seller must be online).

### Homes, warps, and teleporting
- `/sethome [name]`, `/home [name]`, `/delhome <name>` — manage up to three homes.
- `/setwarp <name>`, `/warp <name>`, `/delwarp <name>` — manage operator warps.
- `/setlobby`, `/lobby` — set and visit the lobby.
- `/rtp` — attempt a safe random teleport within 100–5,000 blocks of your current position.
- `/tpa <player>`, `/tpahere <player>`, `/tpaccept`, `/tpdeny` — teleport requests expire after 60 seconds.

### Friends and ranks
- `/friend add <player>`, `/friend remove <player>`, `/friend list` — manage friends (both players must be online to add/remove).
- `/homefriends` — toggle whether friends can visit your homes.
- `/homevisit <player> <home>` — visit a friend's home when they have enabled visits.
- `/rank`, `/rank <player>`, `/rank set <player> <member|vip|mvp|admin>` — view ranks; operators can set them. VIP, MVP, and Admin ranks get colored chat/tab prefixes.
- `/help-` — show the command summary.

Mod-port balances, locations, auctions, ranks, and friend preferences persist under the server directory in `blemmessentials/`. Paper uses its own data files; data is not automatically shared across platforms. New mod-port players start with $100.

## Paper integrations

Optional Paper integrations include Geyser + Floodgate, DiscordSRV, Multiverse-Core, and PlaceholderAPI.

### Discord + Bedrock skins
1. Install DiscordSRV and PlaceholderAPI.
2. In DiscordSRV `config.yml`, set `AvatarUrl: "%blemm_discord_avatar%"`.
3. Java players use their normal avatar; Bedrock players use the skin uploaded by Geyser, looked up from the GeyserMC skin API.

### Per-world disabling
Paper supports `disabled-commands` in `config.yml`, e.g. `world_the_end: [rtp, home, compass]`.

## Build

- Paper: `mvn -f paper/pom.xml package` (Java 25).
- Fabric: `gradle -p fabric build` (Java 25).
- Forge: `gradle -p forge build` (Java 25; ForgeGradle uses a JDK 21 runtime with a Java 25 toolchain in CI).
- NeoForge: `gradle -p neoforge build` (Java 25).

GitHub Actions assembles `BlemmEssentials-all-platforms.zip` only after all four platform builds succeed. Run dedicated-server smoke tests before using the new mod ports on a production server.
