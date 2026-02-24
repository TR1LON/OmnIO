# OmnIO

**Tiered Universal Conduit System for Minecraft 1.21.1**

OmnIO is a multi-loader (NeoForge + Fabric) Minecraft mod providing a tiered, universal item/fluid/energy/redstone conduit system. Multiple conduit types can coexist in a single block, forming intelligent networks that transfer resources between machines.

## Features

- **Bundle Architecture** — Up to 9 conduits in a single block — energy, fluid, item, and redstone side by side
- **Multi-Channel Routing** — Same-type conduits on different color channels coexist in one bundle, each independently connecting to adjacent machines (even on the same face)
- **Tiered System** — Basic, Advanced, Elite, Ultimate, and Creative tiers with increasing throughput
- **Graph-Based Networks** — Efficient persistent networks with automatic merge/split via BFS
- **Priority & Filtering** — Per-connection insert/extract modes, priorities, redstone control, and item filters
- **Facades** — Cosmetic block overlays to hide conduits (planned)
- **Extensible API** — Third-party mods can register custom conduit types

## Conduit Types

| Type | Description | Tiered |
|------|-------------|--------|
| Energy Conduit | Transfers Forge Energy (FE) | ✅ Basic → Ultimate + Creative |
| Fluid Conduit | Transfers fluids (mB) with fluid locking | ✅ Basic → Ultimate + Creative |
| Item Conduit | Transfers items with round-robin & filtering | ✅ Basic → Ultimate + Creative |
| Redstone Conduit | 16-channel signal propagation | ❌ Single tier |

---

## Project Status

### Overall Progress: ~75%

| Area | Status | Notes |
|------|--------|-------|
| Build System | ✅ Done | Gradle multi-loader, MC 1.21.1, Java 21 |
| Tier System | ✅ Done | 5 tiers, per-conduit-type rates |
| Bundle Block | ✅ Done | Placement, removal, neighbor evaluation |
| Network Graph | ✅ Done | BFS merge/split, chunk load/unload |
| Energy Transfer | ✅ Done | Pool-and-distribute, NeoForge IEnergyStorage bridge |
| Fluid Transfer | ✅ Done | Pool-and-distribute, fluid locking, NeoForge IFluidHandler bridge |
| Item Transfer | ✅ Done | Round-robin extract, priority insert, simulate-then-commit |
| Redstone Signals | ✅ Done | 16-channel propagation, DyeColor mapping |
| Network Persistence | ✅ Done | SavedData serialization to disk |
| Multi-Channel Rework | ✅ Done | ConduitSlot(typeId, channel) keying throughout |
| Rendering | ✅ Done | BER, VoxelShape, tier/channel tinting, resource files |
| GUI / Menu | � In Progress | Connection config, channel picker, filters |
| Fabric Real Impls | 🔲 Todo | Team Reborn Energy, Fabric Transfer API v2 |
| API Docs | 🔲 Todo | Javadoc, addon guide |
| Facades | 🔲 Todo | Cosmetic block overlays (low priority) |

---

## Roadmap

### Completed (Phases 1–8)

Server-side logic is fully functional on NeoForge. All four conduit types (energy, fluid, item, redstone) work with proper network formation, transfer, merge/split, and redstone control.

**Files implemented:** 47 Java source files across `common/`, `neoforge/`, `fabric/` modules.

### Next Steps (in order)

#### Step 1: Network Persistence (`ConduitNetworkSavedData`) ✅
Save and restore conduit networks across world reloads using Minecraft's `SavedData` API.

- [x] `ConduitNetworkSavedData.java` — serialize all networks + node data to NBT
- [x] Integrate with `ConduitNetworkManager` load/save hooks
- [x] Handle missing conduit types gracefully (mod removal scenario)

#### Step 2: Multi-Channel Bundle Rework ✅
Introduce `ConduitSlot(typeId, channel)` keying so the same conduit type can exist multiple times per bundle on different color channels.

- [x] `ConduitSlot.java` — record with `ResourceLocation typeId` + `int channel`
- [x] `OmniConduitBlockEntity` — change `Map<ResourceLocation, ...>` to `Map<ConduitSlot, ...>`
- [x] `ConduitNetworkManager` — key networks by `ConduitSlot` instead of `ResourceLocation`
- [x] `ConduitItem` — store channel in item data component (default = white/0)
- [x] Enforce 9-conduit max per bundle
- [x] Per-`(slot, face)` connection config (multiple connections on same face)

#### Step 3: Rendering ✅
BlockEntityRenderer-based rendering for conduit bundles with per-conduit cores, connectors, and tier/channel-based tinting.

- [x] `ConduitBundleRenderer.java` — BER with colored 3D geometry (RenderType.solid)
- [x] `ConduitShape.java` — per-conduit VoxelShape (3×3 grid layout, 9 slots)
- [x] `ConduitRenderHelper.java` — type/tier/channel color mapping
- [x] Conduit core + connector arm procedural geometry
- [x] Tier-based brightness tinting
- [x] Channel-based DyeColor tinting on conduit cores
- [x] NeoForge client registration (`OmnIONeoForgeClient`)
- [x] Fabric client registration (`OmnIOFabricClient`)
- [x] Blockstate, block model, 13 item models, en_us lang file

#### Step 4: GUI / Menu
In-world configuration screen for conduit connections.

- [ ] `ConduitMenu.java` — server-side container for connection configuration
- [ ] `ConduitScreen.java` — client-side GUI screen
- [ ] Bundle overview panel (slot grid showing up to 9 conduits)
- [ ] Per-connection: insert/extract toggle, priority slider, redstone mode selector
- [ ] Channel color picker (16-color DyeColor palette per slot)
- [ ] Item filter slots (item conduit)
- [ ] Fluid lock toggle (fluid conduit)

#### Step 5: Fabric Platform Implementation
Replace transfer helper stubs with real Fabric API bridges.

- [ ] `FabricEnergyTransferHelper` — Team Reborn Energy API bridge
- [ ] `FabricFluidTransferHelper` — Fabric Transfer API v2 bridge
- [ ] `FabricItemTransferHelper` — Fabric Transfer API v2 bridge
- [ ] Fabric rendering integration (custom BakedModel)
- [ ] Fabric menu/screen registration

#### Step 6: API Stabilization + Documentation
Freeze public API, document everything, provide addon examples.

- [ ] Freeze `api/` package interfaces
- [ ] Javadoc on all public API types
- [ ] `TierConfig.java` — config-file overridable tier values
- [ ] Third-party addon example / guide
- [ ] `ConduitBundleData.java` — bundle serialization data class

#### Step 7: Facades (Low Priority)
Cosmetic block overlays that hide conduits behind the appearance of other blocks.

- [ ] Facade item + crafting recipe
- [ ] Facade rendering (mimic target block appearance)
- [ ] Facade placement / removal interaction
- [ ] Facade data persistence in block entity NBT

---

## Architecture

### Multi-Channel Conduit Bundles

A single bundle block holds **up to 9 conduits** (hard limit). Conduits of the **same type** can coexist in one bundle as long as they are on **different color channels** (16 DyeColor-based channels). Each `(type, channel)` pair forms its own independent network.

**Key innovation:** every conduit in a bundle independently connects to adjacent blocks — including the **same face** of the same machine. Multiple item conduits on channels red/blue/green can all connect to the *back face* of a machine simultaneously, each routing to a different inventory slot.

```
  [Smelter] ──red──┐
 [Storage] ──blue──┤ Bundle (3 item conduits) ── back face ── [Machine]
[Coal Bin] ──green─┘
```

**Design rules:**
- **9 conduits max** per bundle — any mix of types and channels
- Bundle keying: `ConduitSlot(typeId, channel)` — two item conduits on different channels are separate entries
- Same type + same channel = same network; different channels = isolated networks
- Channel is a DyeColor (0–15), visible in rendering and GUI
- Default channel = 0 (white) when first placed

### Transfer Patterns

| Conduit | Pattern | Details |
|---------|---------|---------|
| Energy | Pool-and-distribute | Extract into shared buffer → push to targets by priority |
| Fluid | Pool-and-distribute | Same as energy, with single-fluid lock per network |
| Item | Instant pass-through | Round-robin extract → simulate-then-commit insert by priority |
| Redstone | Channel propagation | Clear → read from EXTRACT endpoints → write to INSERT endpoints |

### Platform Abstraction

```
common/         ← Vanilla-only code, ITransferHelper<T> SPI
├── api/        ← Public API interfaces (stable)
├── content/    ← Internal implementation
└── registry/   ← Registration helpers

neoforge/       ← IEnergyStorage, IFluidHandler, IItemHandler bridges
fabric/         ← Stubs (pending: Team Reborn Energy, Fabric Transfer API v2)
```

Transfer helpers use Java ServiceLoader (`IPlatformHelper`) so common code never imports platform-specific classes.

---

## Building

```bash
./gradlew build
```

### NeoForge only
```bash
./gradlew :neoforge:build
```

### Fabric only
```bash
./gradlew :fabric:build
```

## Requirements

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.x or Fabric Loader 0.16.x

## License

MIT — see [LICENSE](LICENSE)
