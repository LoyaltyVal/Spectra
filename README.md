# Spectra AntiCheat

A modular anticheat for Paper with a buffered detection engine, per-check configuration, violation tracking and staff tooling.

**Status:** Phase 1 (core engine). The detection, violation and administration framework is in place, with nine starter checks. Automatic punishments are **off** by default.

## Requirements

- Paper 1.20.4 or newer
- Java 17 or newer
- [PacketEvents](https://modrinth.com/plugin/packetevents) 2.x installed as a plugin

## Building

```bash
./gradlew build          # Windows: gradlew.bat build
```

The jar is written to `build/libs/Spectra-<version>.jar`. Put it in `plugins/` next to PacketEvents.

To compile against a newer Paper API, change the `paper-api` version in `build.gradle.kts`. The code only uses API that exists in 1.20.4.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/spectra` | `spectra.command` | Help |
| `/spectra alerts [level]` | `spectra.alerts` | Toggle alerts, or set your minimum level (LOW/MEDIUM/HIGH/CRITICAL) |
| `/spectra notify` | `spectra.notify` | Toggle punishment notifications |
| `/spectra info <player>` | `spectra.info` | Investigate a player: client, ping, VL per check, buffers, exemptions |
| `/spectra logs <player> [page]` | `spectra.logs` | This session's violation history |
| `/spectra checks [toggle <id>]` | `spectra.checks` | List checks by category; click one to toggle it |
| `/spectra exempt <player> [check\|all]` | `spectra.exempt` | Toggle a persistent exemption (works for offline players) |
| `/spectra reset <player> [check]` | `spectra.reset` | Reset violation levels |
| `/spectra debug <player> [check]` | `spectra.debug` | Stream live check internals for one player |
| `/spectra stats` | `spectra.stats` | Flags, punishments, packets, processing time |
| `/spectra reload` | `spectra.reload` | Reload config.yml, checks.yml, messages.yml |

`spectra.admin` grants all of the above. `spectra.bypass` (default: false) exempts a player from every check.

Alias: `/spec`.

## Checks

| ID | Name | What it detects |
|---|---|---|
| `fly-a` | Fly (A) | Airborne vertical motion that breaks vanilla gravity: `(last − 0.08) × 0.98` |
| `speed-a` | Speed (A) | Airborne horizontal speed above `last × 0.91 + 0.026` |
| `nofall-a` | NoFall (A) | Claiming to be on the ground with no ground nearby (ground spoof) |
| `timer-a` | Timer (A) | Movement packets faster than 20/s (balance with a lag floor) |
| `noswing-a` | NoSwing (A) | Attacking without an arm swing in the same tick |
| `autoclicker-a` | AutoClicker (A) | CPS above a limit (mining and placing ignored) |
| `reach-a` | Reach (A) | Hitting from too far away. **Experimental:** alerts only |
| `badpackets-a` | BadPackets (A) | Pitch outside ±90° |
| `badpackets-b` | BadPackets (B) | Attacking your own entity |

The movement checks are deliberately conservative. They skip any sample near a block, liquid or entity, and any sample after teleports, knockback, flight, elytra, vehicles, riptide, levitation or slow falling. They will miss some cheats, but they should not flag legitimate players. Ground movement, liquids and climbing get prediction-based checks in Phase 2.

## Violation model

- **Buffer:** a check flags only after `max-buffer` suspicious samples. Each clean sample removes `buffer-decay`. With `max-buffer: 0`, the check flags on the first sample, which is reserved for packets the vanilla client can never send.
- **VL:** each flag adds 1 VL to that check. VL decays by `vl-decay-per-minute`.
- **Alert level:** set by VL relative to `punish.vl`: LOW (<25%), MEDIUM (<60%), HIGH, CRITICAL. A flag is also CRITICAL when `critical-distinct-checks` different checks flagged the same player within `critical-window-seconds`.
- **Alerts:** sent once VL ≥ `alert-vl`, rate-limited per player and check. Hover over an alert for ping, TPS and evidence; click it to run `/spectra info`.
- **Punishments:** run only if `punishments.enabled` (config.yml) **and** the check's `punish.enabled` are true, and never for experimental checks. Placeholders: `%player% %uuid% %check% %check_id% %vl%`.
- **Logs:** every flag and punishment is written to `plugins/Spectra/logs/violations-YYYY-MM-DD.log`.

## Architecture

```
com.spectra
├── Spectra                  plugin entry point, wiring, reload
├── api/                     SpectraAPI (ServicesManager), SpectraFlagEvent, SpectraPunishEvent
├── check/                   Check base class, @CheckInfo, CheckRegistry (checks.yml), DefaultChecks
│   └── impl/{movement,combat,packet}/
├── packet/                  PacketEvents listener -> IncomingPacket records -> main-thread PacketProcessor
├── player/                  PlayerData (state, exemptions, history, debug), MovementTracker, MoveUpdate
├── violation/               ViolationManager (VL, decay, levels), Violation, AlertLevel
├── alert/                   AlertManager (staff preferences, formatted clickable alerts)
├── punishment/              PunishmentManager
├── exempt/                  ExemptionStore (exemptions.yml)
├── log/                     ViolationLogger (async daily files)
├── command/                 /spectra
├── config/                  SpectraConfig, Messages (MiniMessage)
├── listener/                teleports, knockback, join/quit
└── util/                    BlockUtil, MathUtil
```

**Threading.** PacketEvents delivers packets on Netty threads. The listener only copies the fields checks need into immutable `IncomingPacket` records, each with a receive timestamp, and queues them per player. `PacketProcessor` drains the queues on the main thread every tick. Checks therefore run in packet order and can safely read Bukkit state (blocks, effects, game mode). Timing checks use the receive timestamps, so server lag doesn't skew them. Queues are bounded (`processing.max-queue-size`) to protect against packet floods.

### Writing a check

```java
@CheckInfo(id = "example-a", name = "Example (A)", category = CheckCategory.MOVEMENT,
        description = "What it detects.")
public final class ExampleA extends Check {
    public ExampleA(PlayerData data) { super(data); }

    @Override
    public void onMove(MoveUpdate move) {
        if (data.isMovementExempt()) return;
        if (move.deltaY() > option("max", 0.42)) {
            if (increaseBuffer()) flag("dy=" + move.deltaY());
        } else {
            decreaseBuffer();
        }
    }
}
```

Register it in `DefaultChecks`. A default section is added to `checks.yml` automatically on the next start.

## Roadmap

1. **Core engine** ✅ this release
2. **Detection:** transaction-based latency tracking, a packet-driven world/entity cache, prediction-based movement (ground, liquids, climbing), KillAura, Aim, Velocity, Scaffold, FastBreak, more BadPackets, setbacks
3. **Staff administration:** in-game GUI, freeze/teleport, investigation notes, staff audit log
4. **Database & integrations:** MongoDB, Discord webhooks, cross-server sync
5. **Web dashboard:** React + TypeScript (separate project)
6. **Optimization & release**
