# Spreading Gravel — Mod Design Spec

**Date:** 2026-06-28  
**Status:** Approved

---

## Overview

A Minecraft mod that adds a single new block: **Spreading Gravel**. Like vanilla gravel it is subject to gravity, but when it can no longer fall straight down it checks horizontally for a direction it could fall further, moves one block in that direction, and continues falling. This makes it behave like a granular fluid, always seeking the deepest reachable point.

---

## Platforms

| Loader | Minecraft version |
|--------|-----------------|
| NeoForge | 1.21.1 |
| Fabric | 1.21.1 |

Project structure: manual multiloader — three Gradle subprojects (`common`, `neoforge`, `fabric`). No Architectury API runtime dependency.

---

## Project Layout

```
spreading_gravel/
├── settings.gradle
├── gradle.properties            # MC version, loader versions, mod metadata
├── common/
│   └── src/main/
│       ├── java/net/ethelred/spreading_gravel/
│       │   ├── SpreadingGravelBlock.java
│       │   └── ModContent.java
│       └── resources/
│           ├── assets/spreading_gravel/
│           │   ├── textures/block/spreading_gravel.png
│           │   ├── models/block/spreading_gravel.json
│           │   ├── models/item/spreading_gravel.json
│           │   ├── blockstates/spreading_gravel.json
│           │   └── lang/en_us.json
│           ├── pack.mcmeta
│           └── data/spreading_gravel/
│               ├── recipes/spreading_gravel.json
│               └── loot/blocks/spreading_gravel.json
├── neoforge/
│   └── src/main/
│       ├── java/net/ethelred/spreading_gravel/neoforge/
│       │   └── SpreadingGravelNeoForge.java
│       └── resources/META-INF/neoforge.mods.toml
└── fabric/
    └── src/main/
        ├── java/net/ethelred/spreading_gravel/fabric/
        │   └── SpreadingGravelFabric.java
        └── resources/fabric.mod.json
```

### Build wiring

Start from two separate scaffolds and merge them into one Gradle multi-project:

- **NeoForge**: download the [NeoForge MDK](https://github.com/neoforged/MDK) for 1.21.1; this covers `common` + `neoforge` subproject wiring out of the box
- **Fabric**: create the `fabric` subproject from the [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod) template for 1.21.1; it provides the Loom `minecraft`, `mappings`, and `fabric_loader` dependencies that the MDK does not

Key `build.gradle` points per subproject:

| Subproject | Key wiring |
|------------|-----------|
| `common` | applies `net.neoforged.gradle.userdev`; MC dependency `compileOnly` only |
| `neoforge` | standard MDK setup; adds `compileOnly project(':common')` + `sourceSets.main.compileClasspath += project(':common').sourceSets.main.output`; `processResources { from project(':common').sourceSets.main.resources }` |
| `fabric` | applies Fabric Loom; declares `minecraft`, `mappings loom.officialMojangMappings()`, `modImplementation "net.fabricmc:fabric-loader:…"`; same `processResources { from project(':common').sourceSets.main.resources }`. For the common code dependency, use `compileOnly(project(':common')) { transitive = false }` — because both NeoForge Gradle and Fabric Loom use Mojang official mappings in 1.21.1, no remapping is needed and a raw classpath reference is safe. Also add `sourceSets.main.compileClasspath += project(':common').sourceSets.main.output` so Loom picks up the compiled classes. |

### ModContent

`ModContent` holds two static fields, initialised to `null`:
- `public static Supplier<Block> SPREADING_GRAVEL`
- `public static Supplier<Item> SPREADING_GRAVEL_ITEM`

Each platform module assigns these during its registration phase (before any gameplay code runs). `common` code accesses the block/item via `ModContent.SPREADING_GRAVEL.get()` — this is only ever called during block ticks, which occur after the registration lifecycle has completed on both platforms.

---

## Block Behaviour

### Class

`SpreadingGravelBlock extends FallingBlock` (common module, no custom entity required).

Constructor properties: use `BlockBehaviour.Properties.ofFullCopy(Blocks.GRAVEL)` — inherits vanilla gravel's hardness (0.6), blast resistance (0.6), sound type, required tool (shovel), and drop behaviour.

### Scheduled Tick Algorithm

```
scheduledTick(state, level, pos, random):

  1. blockBelow = level.getBlockState(pos.below())
     // pos.below() is in the same chunk column; vanilla FallingBlock does not guard
     // this with isLoaded() so we match vanilla behaviour here.
     if blockBelow.canBeReplaced():
       call super.scheduledTick(...)   // vanilla fall — spawns FallingBlockEntity
       return

  2. candidates = []
     for direction in [NORTH, EAST, SOUTH, WEST]:
       adjacent = pos.relative(direction)   // BlockPos
       if !level.isLoaded(adjacent): skip   // ignore unloaded chunk edges
       if level.getBlockState(adjacent).canBeReplaced():
         depth = countFallDepth(level, adjacent)
         if depth > 0:
           candidates.add((direction, adjacent, depth))

  3. if candidates is empty:
       return   // block rests permanently

  4. best = candidate with max depth
              (tie-break: NORTH > EAST > SOUTH > WEST)

  5. if !level.getBlockState(best.adjacent).canBeReplaced():
       return   // best.adjacent was filled between tick scheduling and firing; don't delete self
     level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
     FallingBlockEntity.fall((ServerLevel) level, best.adjacent, state)
     // `state` is the BlockState parameter of scheduledTick (SpreadingGravel block state).
     // Verify against MC 1.21.1 source: expected signature is
     //   FallingBlockEntity.fall(ServerLevel, BlockPos, BlockState) → FallingBlockEntity
     // Sets best.adjacent to AIR (no-op, already replaceable),
     // creates entity at best.adjacent + 0.5 offset, adds it to the level.
     // scheduledTick only runs on the server so the ServerLevel cast is safe.
```

When the entity lands it calls `level.setBlockAndUpdate(pos, blockState)`, which triggers `onPlace` → schedules another tick → algorithm runs again. The loop terminates when the block is fully enclosed or finds no adjacent drop.

`SpreadingGravelBlock` does **not** override `onPlace` — it inherits `FallingBlock.onPlace`, which already schedules a tick via `checkFallable`. This is the mechanism that drives repeated spread steps after each landing.

### Helper Methods

**`countFallDepth(Level level, BlockPos from) → int`**  
Starting from `from.below()`, count consecutive blocks whose `canBeReplaced()` returns `true` (covers air, water, and other fluids). Returns 0 if the block directly below `from` is solid. No maximum depth cap — over a void pit this counts all the way to the build floor, which is an accepted trade-off for simplicity given tick frequency is low.

No `isLoaded()` guard inside the loop: in 1.21.1, chunks load as full vertical columns, so if `from` (the horizontal neighbour) is loaded, all blocks directly below it in the same column are also loaded. No cross-column traversal occurs.

**Replaceability rule**: delegate to `BlockState.canBeReplaced()` — the same check vanilla gravel uses — so water, air, and other non-solid replaceable blocks are treated as passable.

### Placement by Hand

Because `onPlace` always schedules a tick, a block placed by hand adjacent to a drop will immediately roll toward it on the next tick. This is intentional: Spreading Gravel always seeks the lowest reachable point regardless of how it arrived.

---

## Crafting Recipe

**Type:** Shapeless  
**Ingredients:** 3× gravel, 1× sand (any grid arrangement)  
**Output:** 3× spreading_gravel

Stored in `common/src/main/resources/data/spreading_gravel/recipes/spreading_gravel.json`.

---

## Assets

All assets live in `common` and are shared between both platform jars.

| Asset | Details |
|-------|---------|
| Texture | `spreading_gravel.png` — gravel texture variant with subtle colour difference to distinguish visually |
| Block model | `cube_all`, referencing spreading_gravel texture |
| Item model | Standard block item referencing block model |
| Blockstate | Single variant (no directional states) |
| Lang | `"block.spreading_gravel.spreading_gravel": "Spreading Gravel"` |
| Loot table | `loot/blocks/spreading_gravel.json` — self-drop (same pattern as vanilla gravel: drops itself, no fortune/silk-touch special cases). Note: in 1.21.1 the directory is `loot/`, not the legacy `loot_tables/`. |

**Water displacement**: when `best.adjacent` contains water, `FallingBlockEntity.fall` overwrites it with air before spawning the entity. This matches vanilla gravel behaviour and is intentional — Spreading Gravel displaces water as it moves.

---

## Platform Registration

### NeoForge

- Entry point: `@Mod("spreading_gravel")` class (`SpreadingGravelNeoForge`)
- In 1.21.1, the `@Mod`-annotated constructor receives `IEventBus modEventBus` as an injected parameter (NeoForge injects it automatically)
- Registration: `DeferredRegister<Block>` and `DeferredRegister<Item>`, each subscribed to the mod event bus via `.register(modEventBus)` in that constructor
- `DeferredRegister.register("spreading_gravel", () -> new SpreadingGravelBlock(...))` returns a `Supplier<Block>` which is assigned directly to `ModContent.SPREADING_GRAVEL`
- `DeferredRegister.register("spreading_gravel", () -> new BlockItem(ModContent.SPREADING_GRAVEL.get(), new Item.Properties()))` assigned to `ModContent.SPREADING_GRAVEL_ITEM`
- Mod metadata: `META-INF/neoforge.mods.toml` (note: NeoForge, not legacy Forge, so `neoforge.mods.toml`); required fields: `modId = "spreading_gravel"`, `version`, `[[dependencies.spreading_gravel]]` entry for NeoForge and Minecraft
- **Creative tab**: Spreading Gravel is craft-only; no creative tab registration is needed

### Fabric

- Entry point: `SpreadingGravelFabric implements ModInitializer`, registered in `fabric.mod.json`
- In `onInitialize()`: call `Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("spreading_gravel", "spreading_gravel"), new SpreadingGravelBlock(...))` and assign the returned `Block` wrapped in a `() -> block` lambda to `ModContent.SPREADING_GRAVEL`
- Register `new BlockItem(block, new Item.Properties())` to `BuiltInRegistries.ITEM`, assign supplier to `ModContent.SPREADING_GRAVEL_ITEM`
- Mod metadata: `fabric.mod.json`; required fields: `"id": "spreading_gravel"`, `"version"`, `"entrypoints": { "main": ["net.ethelred.spreading_gravel.fabric.SpreadingGravelFabric"] }`, Fabric loader and Minecraft version constraints in `"depends"`

---

## Verification Checklist

1. `./gradlew :neoforge:build :fabric:build` — both succeed, no errors
2. **NeoForge dev client:**
   - [ ] Craft 3× Spreading Gravel from 3 gravel + 1 sand
   - [ ] Block falls straight down when air below
   - [ ] Block rolls toward an adjacent pit when flat floor below
   - [ ] Two equal pits, one north and one east → rolls north
   - [ ] Block fully enclosed → stays permanently
   - [ ] Block falls through water
3. **Fabric dev client:** repeat steps above
