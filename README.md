# UnitedCraft — Nations & Governance Mod
### Fabric 1.21.1 | Server-Side Only

A full nations, governance, and warfare mod for Minecraft Fabric servers.

---

## Features
- **Nations** — Found and manage nations with custom government types
- **Government Types** — Monarchy, Democracy, or Republic
- **Roles** — Sovereign, Chancellor, Minister, Knight, Citizen, Exile
- **Elections & Voting** — Hold anonymous elections for government roles
- **Land Claiming** — Chunk-based territory claiming with limits tied to member count
- **Build Protection** — Claimed chunks protect against outsider griefing
- **War System** — Declare war, earn war score from PvP, with cooldowns
- **Rebellion & Coup** — Citizens accumulate discontentment; high enough triggers a coup window
- **Nation Rules** — Each nation controls PvP, visitor permissions, tax rate, recruitment

---

## Commands

### `/nation`
| Command | Description |
|---|---|
| `/nation create <name> <MONARCHY\|DEMOCRACY\|REPUBLIC>` | Found a nation |
| `/nation info [name]` | View nation info |
| `/nation list` | List all nations |
| `/nation join <name>` | Join a nation |
| `/nation leave` | Leave your nation |
| `/nation invite <player>` | Invite a player (leadership) |
| `/nation kick <player>` | Kick a member (leadership) |
| `/nation setrole <player> <role>` | Set a member's role (Sovereign only) |
| `/nation transfer <player>` | Transfer sovereignty |
| `/nation disband` | Disband the nation (Sovereign only) |
| `/nation coup` | Attempt a coup during a rebellion |

### `/land`
| Command | Description |
|---|---|
| `/land claim [type]` | Claim current chunk (STANDARD/CAPITAL/BORDER/OUTPOST) |
| `/land unclaim` | Unclaim current chunk |
| `/land info` | View who owns the current chunk |
| `/land map` | View a 5x5 text map of surrounding chunks |

### `/gov`
| Command | Description |
|---|---|
| `/gov election start <role>` | Start an election for a role |
| `/gov vote <role> <player>` | Cast an anonymous vote |
| `/gov results <role>` | View current vote counts |
| `/gov close <role>` | Close election and appoint winner |
| `/gov rules` | View nation rules |
| `/gov set <rule> <value>` | Change a nation rule (pvp/visitors/recruitment/tax) |

### `/war`
| Command | Description |
|---|---|
| `/war declare <nation>` | Declare war on another nation |
| `/war status` | Check if your nation is at war |

---

## Building

### Prerequisites
- Java 21
- Gradle (use the included wrapper)

### Build
```bash
./gradlew build
```
Output JAR: `build/libs/unitedcraft-1.0.0.jar`

---

## Installation
1. Build the mod or download the JAR
2. Place in your server's `mods/` folder
3. Restart the server
4. Data is stored at `config/unitedcraft/data.db`

---

## Discontentment System
Citizens accumulate discontentment when:
- Nation loses a war (+15)
- Leadership is inactive
- High tax rates persist

When discontentment reaches **75%**, a rebellion brews and a 2-hour coup window opens.
Coup success chance = 40% + (discontentment - 75), capped at 85%.

---

## Roadmap
- [ ] Peace treaties
- [ ] Nation treasury & economy
- [ ] Alliance system
- [ ] Discord webhook integration
- [ ] Nation chat channel
- [ ] Scoreboard integration
- [ ] Map dynmap/bluemap integration
