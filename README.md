# OmnIO

**Tiered Universal Conduit System for Minecraft 1.21.1**

OmnIO is a multi-loader (NeoForge + Fabric) Minecraft mod providing a tiered, universal item/fluid/energy/redstone conduit system. Multiple conduit types can coexist in a single block, forming intelligent networks that transfer resources between machines.

## Features

- **Bundle Architecture** — Place energy, fluid, item, and redstone conduits in a single block
- **Tiered System** — Basic, Advanced, Elite, Ultimate, and Creative tiers with increasing throughput
- **Graph-Based Networks** — Efficient persistent networks with automatic merge/split
- **Priority & Filtering** — Per-connection insert/extract modes, priorities, redstone control, and item filters
- **Facades** — Cosmetic block overlays to hide conduits
- **Extensible API** — Third-party mods can register custom conduit types

## Conduit Types

| Type | Description | Tiered |
|------|-------------|--------|
| Energy Conduit | Transfers Forge Energy (FE) | ✅ Basic → Ultimate |
| Fluid Conduit | Transfers fluids (mB) | ✅ Basic → Ultimate |
| Item Conduit | Transfers items with filtering | ✅ Basic → Ultimate |
| Redstone Conduit | 16-channel redstone signals | ❌ Single tier |

---

## 🚧 Work-In-Progress — Implementation Status

### Phase 1: Project Skeleton + Build System ✅
- [x] Multi-loader Gradle structure (`common/`, `fabric/`, `neoforge/`)
- [x] `Constants.java` — MOD_ID, mod name, logger
- [x] `OmnIOCommon.java` — shared init logic
- [x] `IPlatformHelper.java` — ServiceLoader SPI interface
- [x] `NeoForgePlatformHelper.java` — NeoForge SPI implementation
- [x] `FabricPlatformHelper.java` — Fabric SPI implementation
- [x] `OmnIONeoForge.java` — `@Mod` entry point
- [x] `OmnIOFabric.java` — `ModInitializer` entry point
- [x] `gradle.properties` — MC 1.21.1, Java 21, NeoForge 21.1.77, Fabric 0.16.9
- [x] `.gitignore`, `LICENSE` (MIT), `README.md`
- [x] Git repository initialized

### Phase 2: Tier System + Conduit Type Registry ✅
- [x] `BaseTier.java` — Enum (BASIC, ADVANCED, ELITE, ULTIMATE, CREATIVE) with `StringRepresentable` codec
- [x] `ITier.java` — Tier interface (`getBaseTier()`)
- [x] `EnergyConduitTier.java` — Tiered capacity & transfer rates
- [x] `FluidConduitTier.java` — Tiered capacity & transfer rates
- [x] `ItemConduitTier.java` — Tiered stack size & speed
- [x] `IConduitType.java` — Registry type key, codec, capability ID
- [x] `OmnIORegistries.java` — Custom DeferredRegister for CONDUIT_TYPES
- [x] `ConduitTypes.java` — Registers ENERGY, FLUID, ITEM, REDSTONE
- [x] `ConduitTypeRegistry.java` — Runtime conduit type lookup

### Phase 3: Bundle Block + Block Entity ✅
- [x] `OmniConduitBlock.java` — Single block for all conduit types, VoxelShape
- [x] `OmniConduitBlockEntity.java` — Holds conduits, connections, nodes; evaluateConnection with status-change guard
- [x] `ConduitItem.java` — Item for placing conduits
- [x] `ConnectionContainer.java` — Per-direction status/config/filter
- [x] `ConnectionConfig.java` — Per-side connection settings
- [x] `ConnectionStatus.java` — DISCONNECTED, CONNECTED_CONDUIT, CONNECTED_BLOCK, DISABLED
- [x] `IConnectionConfig.java` — Connection config interface
- [x] `OmnIOBlocks.java` — Block registration
- [x] `OmnIOBlockEntities.java` — Block entity registration
- [x] `OmnIOItems.java` — Item registration
- [x] `OmnIOCreativeTabs.java` — Creative tab registration
- [x] `NeoForgeRegistration.java` — NeoForge deferred register binding
- [x] `FabricRegistration.java` — Fabric registry binding

### Phase 4: Network Graph Layer ✅
- [x] `IConduitNetwork.java` — Graph accessors: nodes, edges, context
- [x] `IConduitNetworkContext.java` — Per-network mutable state interface
- [x] `IConduitNode.java` — Position + connections + per-side config interface
- [x] `ConduitNetwork.java` — Persistent graph (nodes + edges), AtomicLong IDs, single-pass cache rebuild
- [x] `ConduitNodeImpl.java` — Position, connections, per-side config, defensive copy
- [x] `ConduitNetworkContext.java` — Base network context implementation
- [x] `ConduitNetworkManager.java` — Spatial index, optimized BFS merge/split, stale edge cleanup
- [x] `StubConduitType.java` — Test/stub conduit type
- [ ] `ConduitNetworkSavedData.java` — Persistence to disk via `SavedData`

### Phase 5: Energy Conduit Ticker ✅
- [x] `IConduit.java` — Core conduit interface
- [x] `IConduitTicker.java` — `void tick(ServerLevel, conduit, network)`
- [x] `ITransferHelper.java` — Platform-neutral transfer abstraction
- [x] `EnergyConduitType.java` — Energy conduit type with overflow guard
- [x] `EnergyConduitTicker.java` — Pool-and-distribute with priority sort, redstone control
- [x] `EnergyConduitNetworkContext.java` — Energy buffer with merge/split
- [x] `NeoForgeEnergyTransferHelper.java` — Bridges to `IEnergyStorage` capability (fully functional)
- [x] `NoOpEnergyTransferHelper.java` — No-op fallback for tests
- [ ] `EnergyConduit.java` — Dedicated energy conduit class (logic currently in EnergyConduitType)
- [ ] `EnergyConduitConnectionConfig.java` — Energy-specific per-side connection config

### Phase 6: Fluid Conduit Ticker ❌
- [x] `FluidConduitTier.java` — Tier definitions only
- [ ] `FluidConduit.java` — Fluid conduit implementation
- [ ] `FluidConduitTicker.java` — Rate-limited, optional fluid locking, tier-scaled
- [ ] `FluidConduitConnectionConfig.java` — Fluid-specific per-side connection config
- [ ] `FluidConduitNetworkContext.java` — Fluid buffer with merge/split
- [ ] Platform fluid transfer helpers (NeoForge `IFluidHandler` / Fabric Transfer API)

### Phase 7: Item Conduit Ticker ❌
- [x] `ItemConduitTier.java` — Tier definitions only
- [ ] `ItemConduit.java` — Item conduit implementation
- [ ] `ItemConduitTicker.java` — Round-robin extraction, priority insertion, filters, tier-scaled
- [ ] `ItemConduitConnectionConfig.java` — Item-specific per-side connection config
- [ ] `ItemConduitNodeData.java` — Per-node item transfer state
- [ ] Platform item transfer helpers (NeoForge `IItemHandler` / Fabric Transfer API)

### Phase 8: Redstone Conduit Ticker ❌
- [ ] `RedstoneConduit.java` — Redstone conduit implementation (single tier)
- [ ] `RedstoneConduitTicker.java` — Signal propagation with 16 channel colors
- [ ] `RedstoneConduitConnectionConfig.java` — Channel/mode config per side
- [ ] `RedstoneConduitNetworkContext.java` — Per-network signal state

### Phase 9: Rendering ❌
- [ ] Custom `IUnbakedGeometry` (NeoForge) / `BakedModel` (Fabric) for conduit bundle
- [ ] `ConduitBundleRenderState.java` — Render state snapshot for model
- [ ] `ConduitModelParts.java` — Conduit core + connector model parts
- [ ] `ConduitShape.java` — Per-conduit VoxelShape for hit detection
- [ ] Tier-based color tinting
- [ ] Block Entity Renderer for animated overlays

### Phase 10: GUI / Menu ❌
- [x] `OmnIOMenuTypes.java` — Menu type registration (stub only)
- [ ] `ConduitMenu.java` — Server-side container for connection configuration
- [ ] `ConduitScreen.java` — Client-side GUI screen
- [ ] Per-side insert/extract toggle
- [ ] Priority slider
- [ ] Redstone control mode selector
- [ ] Item filter slots (item conduit)
- [ ] Fluid lock toggle (fluid conduit)

### Phase 11: Facades ❌
- [ ] Facade item + recipe
- [ ] Facade block state rendering (mimics target block appearance)
- [ ] Facade placement / removal interaction
- [ ] Facade data persistence in block entity

### Phase 12: Fabric Platform Implementation 🔄
- [x] `OmnIOFabric.java` — Entry point + mod initialization
- [x] `FabricPlatformHelper.java` — Platform SPI implementation
- [x] `FabricRegistration.java` — Registry binding
- [ ] `FabricEnergyTransferHelper.java` — Fabric Energy API integration (currently a stub)
- [ ] Fabric fluid transfer helper (Fabric Transfer API)
- [ ] Fabric item transfer helper (Fabric Transfer API)
- [ ] Fabric rendering integration (custom BakedModel)
- [ ] Fabric menu/screen registration

### Phase 13: API Stabilization + Documentation ❌
- [ ] Freeze public API interfaces (`api/` package)
- [ ] Javadoc on all public API types
- [ ] Third-party addon example / guide
- [ ] `ConnectionConfigType.java` — Typed config factory + codec
- [ ] `ConduitBundleData.java` — Bundle serialization data class
- [ ] `TierConfig.java` — Config-file overridable tier values

---

### Overall Progress: **~40%**

| Phase | Status |
|-------|--------|
| 1. Project Skeleton | ✅ Complete |
| 2. Tier System | ✅ Complete |
| 3. Bundle Block | ✅ Complete |
| 4. Network Graph | ✅ Complete (persistence pending) |
| 5. Energy Conduit | ✅ Complete (NeoForge-side) |
| 6. Fluid Conduit | ❌ Not started |
| 7. Item Conduit | ❌ Not started |
| 8. Redstone Conduit | ❌ Not started |
| 9. Rendering | ❌ Not started |
| 10. GUI / Menu | ❌ Not started |
| 11. Facades | ❌ Not started |
| 12. Fabric Platform | 🔄 Partial (stubs only) |
| 13. API Docs | ❌ Not started |

---

## Building

```bash
./gradlew build
```

### NeoForge
```bash
./gradlew :neoforge:build
```

### Fabric
```bash
./gradlew :fabric:build
```

## Requirements

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.x or Fabric Loader 0.16.x

## License

MIT — see [LICENSE](LICENSE)
