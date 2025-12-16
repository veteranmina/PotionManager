# PotionManager

A powerful and flexible Minecraft potion effect management plugin for Paper/Spigot servers running 1.21.10+

[![Version](https://img.shields.io/badge/version-4.1.1-blue.svg)](https://github.com/veteranmina/PotionManager)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21.10-green.svg)](https://www.spigotmc.org/)

## Features

### 🎯 Core Features
- **Apply/Remove Potion Effects** - Toggle any potion effect on yourself or other players
- **31 Supported Effects** - All positive and negative Minecraft potion effects
- **Custom Duration & Power** - Configure effect duration (in seconds) and amplifier levels
- **Console Support** - Apply effects from console, bypassing all restrictions

### 🏆 Advanced Tier System
- **4-Tier Permission System** - Basic, VIP, Premium, and Ultimate tiers
- **Per-Tier Limits** - Each tier has configurable max duration and power levels
- **Infinite Duration** - Tier 4 supports infinite duration effects (`-1` config value)
- **Global Tier Naming** - Customize tier display names globally across all effects
- **Tier-Based Cooldowns** - Different cooldown times per tier per effect

### ⏱️ Cooldown System
- **Per-Effect Cooldowns** - Prevent effect spam with configurable cooldowns
- **Per-Tier Cooldowns** - Higher tiers can have shorter or no cooldowns
- **Persistent Storage** - Cooldowns saved to disk, survive server restarts
- **Memory Efficient** - In-memory tracking with automatic cleanup

### 🎨 Modern UI
- **Color-Coded Messages** - Beautiful, consistent color scheme throughout
- **Unicode Symbols** - Modern ✓/✗ symbols and box drawing characters
- **Tier Display** - Shows your tier and available effects with `/potion list`
- **Helpful Error Messages** - Clear feedback when limits are exceeded

## Installation

1. Download the latest `PotionManager.jar` from [Releases](https://github.com/veteranmina/PotionManager/releases)
2. Place the JAR file in your server's `plugins/` folder
3. Restart your server or use a plugin manager to load it
4. Configure `plugins/PotionManager/config.yml` to your liking
5. Reload with `/potionmanager reload`

## Quick Start

### Basic Commands

```bash
# Apply speed effect to yourself (30 seconds, level 1)
/potion speed

# Apply speed for 60 seconds
/potion speed 60

# Apply speed for 60 seconds at level 5 power
/potion speed 60 4

# Apply speed to another player
/potion speed 60 4 PlayerName

# Apply infinite duration (if your tier allows it)
/potion speed -1

# List all available effects
/potion list

# Reload configuration
/potionmanager reload
```

### Command Aliases

- `/potion` = `/pot` = `/peffect`
- `/potionmanager` = `/pm` = `/potmgr`

## Permissions

### Base Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `potionmanager.self` | Use potion effects on yourself | `true` |
| `potionmanager.other` | Use potion effects on other players | `op` |
| `potionmanager.reload` | Reload plugin configuration | `op` |

### Tier Permissions

| Permission | Tier | Description | Default |
|------------|------|-------------|---------|
| _(none)_ | Tier 1 - Basic | Default tier for all players | `true` |
| `potionmanager.tier.vip` | Tier 2 - VIP | Unlocks VIP limits for all effects | `false` |
| `potionmanager.tier.premium` | Tier 3 - Premium | Unlocks Premium limits for all effects | `false` |
| `potionmanager.tier.unlimited` | Tier 4 - Ultimate | Infinite duration, no cooldowns | `false` |

### Wildcard Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `potionmanager.tier.*` | Grants all tier permissions (Tier 4) | `op` |
| `potionmanager.*` | Grants all PotionManager permissions | `op` |

### Effect Permissions

Individual effect permissions are configured in `config.yml` under each effect's `permission` field. For example:
- `potionmanager.effect.speed`
- `potionmanager.effect.regeneration`
- `potionmanager.effect.night_vision`

## Configuration

### Global Tier Display Names

Customize tier names shown to players:

```yaml
tier_display_names:
  tier1: "Basic"
  tier2: "VIP"
  tier3: "Premium"
  tier4: "Ultimate"
```

### Effect Configuration

Each effect can be individually configured:

```yaml
effects:
  speed:
    enabled: true
    permission: "potionmanager.effect.speed"
    tiers:
      tier1:
        permission: ""                      # No extra permission for tier1
        max_duration: 180                   # 3 minutes max
        max_power: 1                        # Level II max (0=Level I, 1=Level II)
        cooldown_seconds: 300               # 5 minute cooldown
      tier2:
        permission: "potionmanager.tier.vip"
        max_duration: 600                   # 10 minutes max
        max_power: 3                        # Level IV max
        cooldown_seconds: 180               # 3 minute cooldown
      tier3:
        permission: "potionmanager.tier.premium"
        max_duration: 1200                  # 20 minutes max
        max_power: 5                        # Level VI max
        cooldown_seconds: 60                # 1 minute cooldown
      tier4:
        permission: "potionmanager.tier.unlimited"
        max_duration: -1                    # INFINITE DURATION!
        max_power: 10                       # Level XI max
        cooldown_seconds: 0                 # No cooldown
```

### Supported Effects

**Positive Effects:**
- Speed, Regeneration, Fire Resistance, Strength, Jump Boost
- Night Vision, Invisibility, Water Breathing, Haste, Resistance
- Slow Falling, Absorption, Saturation, Health Boost, Luck
- Dolphins Grace, Conduit Power, Hero of the Village, Glowing

**Negative Effects:**
- Slowness, Mining Fatigue, Weakness, Poison, Wither
- Nausea, Blindness, Hunger, Levitation, Unluck
- Bad Omen, Darkness

## Examples

### Example 1: Setting Up Ranks

```yaml
# In your permissions plugin (LuckPerms, etc.)
# Basic players - Tier 1 (default, no permission needed)

# VIP rank - Tier 2
group.vip:
  permissions:
    - potionmanager.tier.vip

# Premium rank - Tier 3
group.premium:
  permissions:
    - potionmanager.tier.premium

# Ultimate rank - Tier 4
group.ultimate:
  permissions:
    - potionmanager.tier.unlimited
```

### Example 2: Restricting Specific Effects

```yaml
# Allow only staff to use invisibility
effects:
  invisibility:
    enabled: true
    permission: "potionmanager.effect.invisibility"  # Only those with this permission
    # ... tier config
```

### Example 3: Infinite Duration Setup

```yaml
# Tier 4 with infinite duration
tier4:
  permission: "potionmanager.tier.unlimited"
  max_duration: -1        # -1 enables infinite duration
  max_power: 10
  cooldown_seconds: 0

# Players use: /potion speed -1
# This gives permanent speed effect (until removed)
```

### Example 4: Console Usage

```bash
# Console can apply effects to any player, bypassing all restrictions
# From console or command blocks:
/potion speed 999999 10 PlayerName    # 999999 seconds, level 11, to PlayerName
/potion regeneration -1 5 PlayerName  # Infinite duration, level 6
```

## Cooldown System

The cooldown system prevents players from spamming effects:

- **In-Memory Tracking** - Fast, efficient cooldown checking
- **Disk Persistence** - Cooldowns saved to `cooldowns.yml` on shutdown
- **Auto-Loaded** - Cooldowns restored on server startup
- **Auto-Cleanup** - Expired cooldowns automatically removed
- **Per-Player, Per-Effect, Per-Tier** - Separate cooldowns for each combination

**How it works:**
1. Player applies an effect → cooldown starts
2. Cooldown time based on player's tier for that effect
3. Cooldown persists across server restarts
4. Tier 4 can have 0-second cooldowns (no cooldown)

## Data Storage

### cooldowns.yml

Stores active cooldowns in YAML format:

```yaml
cooldowns:
  player-uuid:
    effect_name:
      tier1: 1701234567890  # Unix timestamp in milliseconds
      tier2: 1701234567890
```

This file is automatically managed - no manual editing needed.

## Troubleshooting

### Effects not working?

1. Check if the effect is `enabled: true` in config.yml
2. Verify player has effect permission (e.g., `potionmanager.effect.speed`)
3. Verify player has `potionmanager.self` permission
4. Check if player is on cooldown (wait or remove from cooldowns.yml)

### Duration/Power limits not applying?

1. Check player's tier permissions
2. Higher tier permissions override lower ones (tier4 > tier3 > tier2 > tier1)
3. Console bypasses all limits

### Cooldowns not persisting?

1. Ensure `cooldowns.yml` is being created in plugin folder
2. Check server logs for errors during save/load
3. Verify file permissions (server must be able to read/write)

## Development

### Building from Source

```bash
git clone https://github.com/veteranmina/PotionManager.git
cd PotionManager
mvn clean package
# Output: target/PotionManager-4.1.1.jar
```

### Requirements
- Java 21+
- Maven 3.6+
- Paper API 1.21.10

## Credits

- **Original Author:** H!DD3N!NJA
- **Recoded for 1.21.10:** VeritasLuxMea
- **Contributors:** [View Contributors](https://github.com/veteranmina/PotionManager/graphs/contributors)

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

- **Issues:** [GitHub Issues](https://github.com/veteranmina/PotionManager/issues)

---

**Made with ❤️ for the Minecraft community**
