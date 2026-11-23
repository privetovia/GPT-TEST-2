# Last Breath

Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.

## Included in this source revision

- Server-authoritative downed state with a 60-second timer.
- 50/50 self-recovery or final death when the timer expires.
- Hold-to-revive for 8 seconds, within 3 blocks.
- Configurable in Minecraft Controls: revive (`R`) and give-up (`K`) key mappings.
- `/kill_undeath` test command: puts the executing player into the downed state.
- Vanilla `/kill` is treated as final death, including when the player is already downed.
- Optional Sable Player Ragdoll bridge. The bridge uses Sable's `detachActive` API when leaving the downed state.
- Optional WakeUpIntro bridge on successful recovery.
- Top-center downed timer panel, hidden while a GUI/container screen is open.
- Brief blackout fading into a dim view while downed.

## Dependencies

Required: Minecraft 1.21.1 and NeoForge.

Optional: Sable + Sable Player Ragdoll, WakeUpIntro.

## Important scope note

This revision does **not** include TaCZ-specific weapon/headshot event handling, nonlethal head-hit camera effects, a real blur shader, or camera shake. Those are not represented as completed features. The source is provided for testing the downed-state, command, HUD, ragdoll-release, and wake-up flow.

## Build

Use the included Gradle wrapper or the repository's GitHub Actions workflow with Java 21.
