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

The loader port is command-based rather than GUI-based and is not yet identical to Paper. It does not include the Paper shop GUI, integrations, rank chat/tab prefixes, or custom item metadata in auction listings.

Build with Java 25: `gradle -p fabric build`.
