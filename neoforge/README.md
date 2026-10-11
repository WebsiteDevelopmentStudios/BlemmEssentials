# BlemmEssentials for NeoForge

Native NeoForge server mod targeting Minecraft 26.3 and Java 25.

## Commands currently implemented

- `/balance` and `/money` — show the executing player's balance.
- `/pay <player> <amount>` — transfer money to an online player.
- `/money <player> <amount>` — operator-only balance adjustment.

Balances persist in `blemmessentials/balances.tsv` inside the server directory. Install this mod on the server; it is intended for dedicated servers.

This is the first porting milestone, not full parity with the Paper plugin yet. Homes, teleports, warps, ranks, shops, auctions, and optional Paper integrations remain to be ported.

Build from the repository root with Java 25: `gradle -p neoforge build`.
