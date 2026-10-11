# BlemmEssentials for Fabric

Native Fabric server mod targeting Minecraft 26.3 and Java 25.

## Commands

- Economy: `/balance`, `/money`, `/pay <player> <amount>`.
- Shop and selling: `/shop <item> <amount>`, `/sell` (sells the held stack).
- Auction house: `/ah list`, `/ah sell <price>`, `/ah buy <id>`, `/ah cancel <id>`.
- Homes: `/sethome [name]`, `/home [name]`, `/delhome <name>` (three homes by default).
- Warps/lobby: `/setwarp <name>`, `/warp <name>`, `/delwarp <name>`, `/setlobby`, `/lobby`.
- Teleporting: `/rtp`, `/tpa <player>`, `/tpahere <player>`, `/tpaccept`, `/tpdeny`.
- Friends: `/friend add <player>`, `/friend remove <player>`, `/friend list`, `/homefriends`, `/homevisit <player> <home>`.
- Ranks: `/rank`, `/rank <player>`, operator-only `/rank set <player> <member|vip|mvp|admin>`.
- Help: `/help-`.

Operator-only commands require operator permission. Home, warp, lobby, economy, auction, rank, and friend settings are persisted under `blemmessentials/` in the server directory.

The loader port is command-based rather than GUI-based and is not yet identical to Paper. It does not include the Paper shop GUI, integrations, or full item stack metadata in auction listings.

Build with Java 25: `gradle -p fabric build`.


## Configurable shop pricing

After first start, edit `blemmessentials/shop-prices.properties` in the server directory. Use `/shopprice <item>` to inspect prices and `/shopprices` to see configuration keys. Category defaults use `default.<category>.buy` / `default.<category>.sell`; item overrides use `item.<namespace:id>.buy` / `item.<namespace:id>.sell`. Set an item's sell price to `0` to disable selling it.


### Discord auction announcements on mod loaders

The mod ports can announce new auctions without DiscordSRV by using a Discord webhook. After the first server start, open `blemmessentials/discord.properties` and set `webhook-url` to a Discord webhook URL. Leave it empty to disable announcements. Webhook requests run asynchronously; Paper continues to support its existing DiscordSRV integration.


### Per-dimension command disabling on mod loaders

After first start, edit `blemmessentials/disabled-commands.properties`. Use dimension IDs as keys, such as `minecraft_the_end=rtp,home,sethome`, or `*=rtp` to disable a command in every dimension. List command names separated by commas; leave a value empty to allow all BlemmEssentials commands in that dimension.


`/shop` opens the category-based vanilla chest GUI. Left-click buys one item; right-click buys a stack. The command form `/shop <item> <amount>` remains available.


Auction listings now serialize the complete item stack through Minecraft's registry-aware item codec, preserving custom names, lore, enchantments, and other item components across server restarts. Existing listings from older versions remain readable and fall back to plain item stacks.
