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
│           └── data/spreading_gravel/
│               └── recipes/spreading_gravel.json
├── neoforge/
│   └── src/main/java/net/ethelred/spreading_gravel/neoforge/
│       ├── SpreadingGravelNeoForge.java
│       └── NeoForgeEvents.java
└── fabric/
    └── src/main/java/net/ethelred/spreading_gravel/fabric/
        ├── SpreadingGravelFabric.java
        └── FabricRegistry.java
```

`ModContent` holds static `Holder`/`RegistryObject` fields for the block and block item. Each platform module populates these during its registration phase so `common` code can reference them without platform imports.

---

## Block Behaviour

### Class

`SpreadingGravelBlock extends FallingBlock` (common module, no custom entity required).

### Scheduled Tick Algorithm

```
scheduledTick(state, level, pos, random):

  1. blockBelow = level.getBlockState(pos.below())
     if blockBelow.canBeReplaced():
       call super.scheduledTick(...)   // vanilla fall — spawns FallingBlockEntity
       return

  2. candidates = []
     for direction in [NORTH, EAST, SOUTH, WEST]:
       adjacent = pos.relative(direction)
       if level.getBlockState(adjacent).canBeReplaced():
         depth = countFallDepth(level, adjacent)
         if depth > 0:
           candidates.add((direction, depth))

  3. if candidates is empty:
       return   // block rests permanently

  4. best = candidate with max depth
              (tie-break: NORTH > EAST > SOUTH > WEST)

  5. level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL)
     spawn FallingBlockEntity carrying SpreadingGravel block state at best.adjacent
```

When the entity lands it calls `level.setBlockAndUpdate(pos, blockState)`, which triggers `onPlace` → schedules another tick → algorithm runs again. The loop terminates when the block is fully enclosed or finds no adjacent drop.

### Helper Methods

**`countFallDepth(Level level, BlockPos from) → int`**  
Starting from `from.below()`, count consecutive blocks whose `canBeReplaced()` returns `true` (covers air, water, and other fluids). Returns 0 if the block directly below `from` is solid.

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

---

## Platform Registration

### NeoForge

- Entry point: `@Mod("spreading_gravel")` class
- Registration: `DeferredRegister<Block>` and `DeferredRegister<Item>`
- Mod metadata: `META-INF/mods.toml`

### Fabric

- Entry point: `ModInitializer` registered in `fabric.mod.json`
- Registration: `Registry.register(BuiltInRegistries.BLOCK, ...)` and `Registry.register(BuiltInRegistries.ITEM, ...)`
- Mod metadata: `fabric.mod.json`

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
