# Wireless Network Channels

Each FTB team (or an overridden player identity) owns one wireless network. A
network now contains one or more named channels rather than a single balance.
Every channel stores energy as an unbounded `BigInteger`, has independent
throughput statistics, wireless-charging settings, and light-weight endpoint
records.

## Channel Operations

The PSS wireless controller is the management terminal. Its UI selects a
channel, edits its name, creates a channel, deletes the selected channel, and
sends 1,000,000 EU to the next channel. The final channel cannot be deleted.
Deleting any other channel divides its remaining energy evenly over all
remaining channels; the remainder is distributed by stable channel order.

Wireless energy hatches and the wireless energy covers persist their selected
channel. A missing channel automatically falls back to channel `0` (`Main`),
which keeps old worlds and deleted-channel devices usable.

## Devices

Input hatches and covers pull from a channel; output variants deposit into it.
All transfers use the device tier and amperage limits. The cover items already
registered from ULV through MAX are now active. Use a screwdriver on a cover to
cycle its channel.

Wireless charging is configured on the controller per channel. It can charge
hand slots, hands plus armor, or all main-inventory slots for online members of
the same resolved team. Charging is capped at 1,000,000 EU per player/channel
per tick to bound work and instantaneous loss.

## Data And Optimization

Channel NBT stores a compact endpoint key, type, dimension, block position,
last-seen tick, and loaded/forced-loaded flags. Endpoints update in batches with
their existing 20-tick transfer cadence.

The implementation borrows Flux Networks principles: queue structural changes
before a tick, use bounded per-device transfers, record rolling statistics, and
send compact state instead of a global live graph. Further work should add
round-robin scheduling for equal-priority endpoints and packetized GUI list
refreshing rather than polling full network state.
