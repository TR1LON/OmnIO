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
