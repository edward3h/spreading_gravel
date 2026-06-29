# Spreading Gravel Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Minecraft 1.21.1 mod that adds Spreading Gravel — a gravity block that rolls sideways toward the deepest available drop when it can no longer fall straight down.

**Architecture:** Three-subproject manual multiloader Gradle build (`common`, `neoforge`, `fabric`). All block logic lives in `common`; each platform module provides thin registration wrappers that populate shared suppliers in `ModContent`. No Architectury dependency.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.x, Fabric Loader 0.16.x, NeoForge Gradle 7.0.x, Fabric Loom 1.7.x, JUnit Jupiter 5, Mockito 5

**Spec:** `docs/superpowers/specs/2026-06-28-spreading-gravel-mod-design.md`

---

## File Map

| File | Responsibility |
|------|---------------|
| `settings.gradle` | Declares subprojects and plugin repos |
| `gradle.properties` | Shared versions and mod metadata |
| `common/build.gradle` | Compile against MC via NeoForge Gradle; test deps |
| `neoforge/build.gradle` | NeoForge platform; wires in common sources |
| `fabric/build.gradle` | Fabric Loom; wires in common sources |
| `common/…/SpreadingGravelBlock.java` | All spreading logic (`tick`, `findBestAdjacentPos`, `countFallDepth`) |
| `common/…/ModContent.java` | Static `Supplier<Block/Item>` fields; assigned by platforms at registration |
| `common/…/SpreadingGravelBlockTest.java` | JUnit + Mockito unit tests for block logic |
| `common/resources/pack.mcmeta` | Required for data-pack resources to load |
| `common/resources/assets/…` | Texture, block model, item model, blockstate, lang |
| `common/resources/data/…` | Recipe JSON, loot table JSON |
| `neoforge/…/SpreadingGravelNeoForge.java` | `@Mod` entry; DeferredRegister block + item |
| `neoforge/resources/META-INF/neoforge.mods.toml` | NeoForge mod metadata |
| `fabric/…/SpreadingGravelFabric.java` | `ModInitializer`; Registry.register block + item |
| `fabric/resources/fabric.mod.json` | Fabric mod metadata |

---

## Chunk 1: Gradle Scaffold

### Task 1: Root build files

**Files:**
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `build.gradle` (root)

- [ ] **Step 1: Create `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.neoforged.net/releases' }
        maven { url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'spreading_gravel'
include('common', 'neoforge', 'fabric')
```

- [ ] **Step 2: Create `gradle.properties`**

```properties
# Minecraft
minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.22)

# NeoForge — check https://neoforged.net for latest 1.21.1 build
neoforge_version=21.1.172
neoforge_version_range=[21.1,)
neoforge_loader_version_range=[1,)

# Fabric — check https://fabricmc.net/versions.html
fabric_loader_version=0.16.9

# Mod
mod_version=1.0.0
mod_id=spreading_gravel
mod_name=Spreading Gravel
mod_group=net.ethelred
mod_authors=edward3h
```

- [ ] **Step 3: Initialise the Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.8
```

This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/`. Gradle 8.8 is the version shipped with NeoForge MDK for 1.21.1 — adjust if your NeoForge Gradle plugin requires a different version (check the plugin's README).

```bash
git add gradle/ gradlew gradlew.bat
git commit -m "chore: add Gradle wrapper"
```

- [ ] **Step 4: Create root `build.gradle`**

Do NOT pre-apply the `java` plugin here — Fabric Loom and NeoForge Gradle each own the `java` lifecycle for their subprojects, and pre-applying it from the root breaks Loom's remapping setup.

```groovy
// Root build — repositories only; each subproject applies its own platform plugin
subprojects {
    repositories {
        maven { url = 'https://maven.neoforged.net/releases' }
        maven { url = 'https://maven.fabricmc.net/' }
        mavenCentral()
    }
}
```

### Task 2: common/build.gradle + mod stub

**Files:**
- Create: `common/build.gradle`
- Create: `common/src/main/resources/META-INF/neoforge.mods.toml` (build-only stub; not packaged into platform jars)

- [ ] **Step 1: Write `common/build.gradle`**

```groovy
plugins {
    id 'net.neoforged.gradle.userdev' version '7.0.171'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// Suppress run configurations for common — it is not a runnable project
runs {}

dependencies {
    // MC via NeoForge. Use `implementation` (not `compileOnly`) so NeoForge Gradle 7.x
    // sets up the decompiled MC environment for both compilation and test runtime.
    // The common jar does not bundle NeoForge — that's controlled by the platform jars.
    implementation "net.neoforged:neoforge:${neoforge_version}"

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testImplementation 'org.mockito:mockito-core:5.8.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

> **Note:** NeoForge Gradle 7.x version numbers change frequently. If `7.0.171` doesn't resolve, check the [NeoForge Gradle releases](https://github.com/neoforged/NeoGradle/releases) for the latest `7.0.x` compatible with NeoForge 21.1.x.

- [ ] **Step 2: Write stub `common/src/main/resources/META-INF/neoforge.mods.toml`**

NeoForge Gradle 7.x requires mod metadata at configuration time even when `runs {}` is empty. This file satisfies that requirement; it is NOT copied into the platform jars (those supply their own `neoforge.mods.toml`).

```toml
modLoader = "javafml"
loaderVersion = "[1,)"
license = "MIT"

[[mods]]
modId = "spreading_gravel_common_stub"
version = "0.0.0"
displayName = "Spreading Gravel (common stub — not a real mod)"
```

### Task 3: Platform build files

**Files:**
- Create: `neoforge/build.gradle`
- Create: `fabric/build.gradle`

- [ ] **Step 1: Write `neoforge/build.gradle`**

```groovy
// Ensure common is evaluated before this project accesses its sourceSets
evaluationDependsOn(':common')

plugins {
    id 'net.neoforged.gradle.userdev' version '7.0.171'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

runs {
    // NeoForge Gradle 7.x API: modSource (singular), not modSources.add()
    // Verify against the MDK's neoforge/build.gradle if this doesn't resolve
    configureEach {
        modSource project(':common').sourceSets.main
    }
}

dependencies {
    implementation "net.neoforged:neoforge:${neoforge_version}"
    compileOnly(project(':common')) { transitive = false }
}

sourceSets.main.compileClasspath += project(':common').sourceSets.main.output
sourceSets.main.runtimeClasspath += project(':common').sourceSets.main.output

processResources {
    // Exclude the common module's stub neoforge.mods.toml so it doesn't overwrite
    // neoforge/src/main/resources/META-INF/neoforge.mods.toml
    from(project(':common').sourceSets.main.resources) {
        exclude 'META-INF/neoforge.mods.toml'
    }
}
```

- [ ] **Step 2: Write `fabric/build.gradle`**

```groovy
// Ensure common is evaluated before this project accesses its sourceSets
evaluationDependsOn(':common')

plugins {
    id 'fabric-loom' version '1.7.+'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

loom {
    // no mixin config needed for this mod
}

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${fabric_loader_version}"

    // Common module — same Mojang mappings, no remapping needed
    compileOnly(project(':common')) { transitive = false }
}

sourceSets.main.compileClasspath += project(':common').sourceSets.main.output
sourceSets.main.runtimeClasspath += project(':common').sourceSets.main.output

processResources {
    // Exclude the common module's stub neoforge.mods.toml (harmless in Fabric jar, but keep consistent)
    from(project(':common').sourceSets.main.resources) {
        exclude 'META-INF/neoforge.mods.toml'
    }
}
```

- [ ] **Step 3: Run initial Gradle sync to verify project structure resolves**

```bash
./gradlew projects
```

Expected: prints `common`, `neoforge`, `fabric` as subprojects with no errors.

- [ ] **Step 4: Commit scaffold**

```bash
git add settings.gradle gradle.properties build.gradle common/build.gradle neoforge/build.gradle fabric/build.gradle
git commit -m "feat: scaffold Gradle multiloader project structure"
```

---

## Chunk 2: Common Java — Block Logic + Tests

### Task 4: ModContent

**Files:**
- Create: `common/src/main/java/net/ethelred/spreading_gravel/ModContent.java`

- [ ] **Step 1: Write `ModContent.java`**

```java
package net.ethelred.spreading_gravel;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class ModContent {
    /** Assigned by each platform module during registration. */
    public static Supplier<Block> SPREADING_GRAVEL = null;
    public static Supplier<Item> SPREADING_GRAVEL_ITEM = null;
}
```

### Task 5: Write failing tests for SpreadingGravelBlock

**Files:**
- Create: `common/src/test/java/net/ethelred/spreading_gravel/SpreadingGravelBlockTest.java`

- [ ] **Step 1: Write test class**

```java
package net.ethelred.spreading_gravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpreadingGravelBlockTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Creates a mock Level where positions in the map return air (true) or stone (false).
     *  Any position not in the map defaults to stone (not replaceable). */
    private Level levelWith(Map<BlockPos, Boolean> blocks) {
        Level level = mock(Level.class);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        blocks.forEach((pos, isAir) ->
            when(level.getBlockState(pos)).thenReturn(
                isAir ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState()
            )
        );
        return level;
    }

    // ── countFallDepth ──────────────────────────────────────────────

    @Test
    void countFallDepth_returnsZeroWhenImmediatelyBlocked() {
        BlockPos from = new BlockPos(0, 10, 0);
        Level level = levelWith(Map.of()); // all stone
        assertEquals(0, SpreadingGravelBlock.countFallDepth(level, from));
    }

    @Test
    void countFallDepth_countsConsecutiveAir() {
        BlockPos from = new BlockPos(0, 10, 0);
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        blocks.put(from.below(),                     true); // y=9 air
        blocks.put(from.below().below(),             true); // y=8 air
        blocks.put(from.below().below().below(),     true); // y=7 air
        // y=6 defaults to stone
        Level level = levelWith(blocks);
        assertEquals(3, SpreadingGravelBlock.countFallDepth(level, from));
    }

    // ── findBestAdjacentPos ─────────────────────────────────────────

    @Test
    void findBest_returnsEmptyWhenAllSidesBlocked() {
        BlockPos pos = new BlockPos(0, 10, 0);
        Level level = levelWith(Map.of()); // all stone, including all adjacent
        assertTrue(SpreadingGravelBlock.findBestAdjacentPos(level, pos).isEmpty());
    }

    @Test
    void findBest_returnsEmptyWhenAdjacentOpenButNoDropBelow() {
        BlockPos pos = new BlockPos(0, 10, 0);
        // North adjacent is air, but floor is immediately below it (stone at y=9 below north)
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        blocks.put(pos.north(), true);
        // pos.north().below() defaults to stone (not in map)
        Level level = levelWith(blocks);
        assertTrue(SpreadingGravelBlock.findBestAdjacentPos(level, pos).isEmpty());
    }

    @Test
    void findBest_picksOnlyOpenDirectionWithDrop() {
        BlockPos pos = new BlockPos(0, 10, 0);
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        blocks.put(pos.east(), true);         // east adjacent: open
        blocks.put(pos.east().below(), true); // east has 1-block drop
        Level level = levelWith(blocks);
        assertEquals(Optional.of(pos.east()), SpreadingGravelBlock.findBestAdjacentPos(level, pos));
    }

    @Test
    void findBest_prefersDeepestDrop() {
        BlockPos pos = new BlockPos(0, 10, 0);
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        // North: 1-block drop
        blocks.put(pos.north(), true);
        blocks.put(pos.north().below(), true);
        // East: 3-block drop (should win)
        blocks.put(pos.east(), true);
        blocks.put(pos.east().below(), true);
        blocks.put(pos.east().below().below(), true);
        blocks.put(pos.east().below().below().below(), true);
        Level level = levelWith(blocks);
        assertEquals(Optional.of(pos.east()), SpreadingGravelBlock.findBestAdjacentPos(level, pos));
    }

    @Test
    void findBest_breaksTiesNorthBeforeEast() {
        BlockPos pos = new BlockPos(0, 10, 0);
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        // North and East: same depth (2)
        blocks.put(pos.north(), true);
        blocks.put(pos.north().below(), true);
        blocks.put(pos.north().below().below(), true);
        blocks.put(pos.east(), true);
        blocks.put(pos.east().below(), true);
        blocks.put(pos.east().below().below(), true);
        Level level = levelWith(blocks);
        assertEquals(Optional.of(pos.north()), SpreadingGravelBlock.findBestAdjacentPos(level, pos));
    }

    @Test
    void findBest_breaksTiesNorthBeforeSouthAndWest() {
        BlockPos pos = new BlockPos(0, 10, 0);
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST}) {
            blocks.put(pos.relative(dir), true);
            blocks.put(pos.relative(dir).below(), true);
        }
        Level level = levelWith(blocks);
        assertEquals(Optional.of(pos.north()), SpreadingGravelBlock.findBestAdjacentPos(level, pos));
    }

    @Test
    void findBest_skipsUnloadedAdjacentChunks() {
        BlockPos pos = new BlockPos(0, 10, 0);
        Map<BlockPos, Boolean> blocks = new HashMap<>();
        // East: open with drop — but East chunk is unloaded
        blocks.put(pos.east(), true);
        blocks.put(pos.east().below(), true);
        Level level = levelWith(blocks);
        when(level.isLoaded(pos.east())).thenReturn(false); // override isLoaded for east
        assertTrue(SpreadingGravelBlock.findBestAdjacentPos(level, pos).isEmpty());
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure (class doesn't exist yet)**

```bash
./gradlew :common:test 2>&1 | tail -20
```

Expected: compilation error — `SpreadingGravelBlock` not found.

### Task 6: Implement SpreadingGravelBlock

**Files:**
- Create: `common/src/main/java/net/ethelred/spreading_gravel/SpreadingGravelBlock.java`

- [ ] **Step 1: Write `SpreadingGravelBlock.java`**

```java
package net.ethelred.spreading_gravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class SpreadingGravelBlock extends FallingBlock {

    private static final List<Direction> SPREAD_ORDER =
        List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public SpreadingGravelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void scheduledTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos.below()).canBeReplaced()) {
            // Normal fall: delegate to FallingBlock
            super.scheduledTick(state, level, pos, random);
            return;
        }
        findBestAdjacentPos(level, pos).ifPresent(targetPos -> {
            // Re-check: another block may have filled targetPos since the tick was scheduled
            if (!level.getBlockState(targetPos).canBeReplaced()) return;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            // Verify FallingBlockEntity.fall signature against MC 1.21.1 source:
            //   expected: FallingBlockEntity.fall(Level, BlockPos, BlockState)
            FallingBlockEntity.fall(level, targetPos, state);
        });
    }

    /**
     * Returns the adjacent BlockPos (horizontally) that gives the greatest vertical fall depth.
     * Checks NORTH, EAST, SOUTH, WEST in that order; first maximum wins (tie-break).
     * Returns empty if no adjacent position has depth > 0 or all are unloaded/solid.
     */
    static Optional<BlockPos> findBestAdjacentPos(Level level, BlockPos pos) {
        BlockPos bestPos = null;
        int bestDepth = 0;
        for (Direction dir : SPREAD_ORDER) {
            BlockPos adj = pos.relative(dir);
            if (!level.isLoaded(adj)) continue;
            if (!level.getBlockState(adj).canBeReplaced()) continue;
            int depth = countFallDepth(level, adj);
            if (depth > bestDepth) {
                bestDepth = depth;
                bestPos = adj;
            }
        }
        return Optional.ofNullable(bestPos);
    }

    /**
     * Counts consecutive replaceable blocks directly below {@code from}.
     * Returns 0 if the block at from.below() is solid.
     * No depth cap — accepted trade-off for simplicity.
     */
    static int countFallDepth(Level level, BlockPos from) {
        int depth = 0;
        BlockPos check = from.below();
        while (level.getBlockState(check).canBeReplaced()) {
            depth++;
            check = check.below();
        }
        return depth;
    }
}
```

> **API note:** If `FallingBlockEntity.fall` does not compile with `Level`, try casting to `ServerLevel`. The `tick` method receives a `ServerLevel`, so `(ServerLevel) level` is safe here. Double-check the exact overload in the decompiled MC 1.21.1 source.

### Task 7: Run tests and verify they pass

- [ ] **Step 1: Run tests**

```bash
./gradlew :common:test
```

Expected: All 9 tests pass, no failures.

- [ ] **Step 2: If Bootstrap fails at runtime**, add this to `common/build.gradle` under `test {}`:

```groovy
test {
    useJUnitPlatform()
    // NeoForge Gradle may need this to provide deobfuscated classes to the test JVM:
    jvmArgs '-Dlog4j2.formatMsgNoLookups=true'
}
```

If Minecraft classes still aren't available in tests, check that NeoForge Gradle includes the deobfuscated jar on the `testRuntimeClasspath` — it typically does by default.

- [ ] **Step 3: Commit**

```bash
git add common/src/
git commit -m "feat: implement SpreadingGravelBlock with spreading logic"
```

---

## Chunk 3: Common Resources

### Task 8: pack.mcmeta

**Files:**
- Create: `common/src/main/resources/pack.mcmeta`

- [ ] **Step 1: Write `pack.mcmeta`**

```json
{
  "pack": {
    "description": "Spreading Gravel resources",
    "pack_format": 34,
    "supported_formats": [34]
  }
}
```

> **Note:** Verify `pack_format` against MC 1.21.1 (`net.minecraft.server.packs.PackType`). For 1.21.1, resource pack format is 34 and data pack format is 48. If the format is wrong, assets/data will silently fail to load. Both assets and data are served from the same jar, so the value here covers both.

### Task 9: Texture

**Files:**
- Create: `common/src/main/resources/assets/spreading_gravel/textures/block/spreading_gravel.png`

- [ ] **Step 1: Create the texture**

Copy `gravel.png` from the vanilla Minecraft jar (`assets/minecraft/textures/block/gravel.png`) and apply a subtle warm tint to distinguish it. Using ImageMagick:

```bash
# Extract gravel.png from the client jar first, then:
convert gravel.png -modulate 100,120,105 \
  common/src/main/resources/assets/spreading_gravel/textures/block/spreading_gravel.png
```

Or open in any image editor, apply a warm orange/brown hue shift, and save as a 16×16 PNG.

### Task 10: Block model, item model, blockstate

**Files:**
- Create: `common/src/main/resources/assets/spreading_gravel/models/block/spreading_gravel.json`
- Create: `common/src/main/resources/assets/spreading_gravel/models/item/spreading_gravel.json`
- Create: `common/src/main/resources/assets/spreading_gravel/blockstates/spreading_gravel.json`

- [ ] **Step 1: Write block model**

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "spreading_gravel:block/spreading_gravel"
  }
}
```

- [ ] **Step 2: Write item model**

```json
{
  "parent": "spreading_gravel:block/spreading_gravel"
}
```

- [ ] **Step 3: Write blockstate**

```json
{
  "variants": {
    "": { "model": "spreading_gravel:block/spreading_gravel" }
  }
}
```

### Task 11: Lang file

**Files:**
- Create: `common/src/main/resources/assets/spreading_gravel/lang/en_us.json`

- [ ] **Step 1: Write lang file**

```json
{
  "block.spreading_gravel.spreading_gravel": "Spreading Gravel"
}
```

### Task 12: Recipe

**Files:**
- Create: `common/src/main/resources/data/spreading_gravel/recipes/spreading_gravel.json`

- [ ] **Step 1: Write recipe**

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "minecraft:gravel" },
    { "item": "minecraft:gravel" },
    { "item": "minecraft:gravel" },
    { "item": "minecraft:sand" }
  ],
  "result": {
    "id": "spreading_gravel:spreading_gravel",
    "count": 3
  }
}
```

### Task 13: Loot table

**Files:**
- Create: `common/src/main/resources/data/spreading_gravel/loot/blocks/spreading_gravel.json`

- [ ] **Step 1: Write loot table**

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "spreading_gravel:spreading_gravel"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
```

> **Note:** Directory is `loot/blocks/`, not `loot_tables/blocks/` — this changed in MC 1.21.

- [ ] **Step 2: Commit all resources**

```bash
git add common/src/main/resources/
git commit -m "feat: add common resources (texture, models, recipe, loot table)"
```

---

## Chunk 4: NeoForge Platform

### Task 14: NeoForge mod entry point

**Files:**
- Create: `neoforge/src/main/java/net/ethelred/spreading_gravel/neoforge/SpreadingGravelNeoForge.java`

- [ ] **Step 1: Write entry point**

```java
package net.ethelred.spreading_gravel.neoforge;

import net.ethelred.spreading_gravel.ModContent;
import net.ethelred.spreading_gravel.SpreadingGravelBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("spreading_gravel")
public class SpreadingGravelNeoForge {

    public SpreadingGravelNeoForge(IEventBus modEventBus) {
        // Registries.BLOCK / Registries.ITEM are ResourceKey<Registry<T>> — required by DeferredRegister.create
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, "spreading_gravel");
        DeferredRegister<Item>  items  = DeferredRegister.create(Registries.ITEM,  "spreading_gravel");

        ModContent.SPREADING_GRAVEL = blocks.register("spreading_gravel",
            () -> new SpreadingGravelBlock(
                Block.Properties.ofFullCopy(Blocks.GRAVEL)
            )
        );

        ModContent.SPREADING_GRAVEL_ITEM = items.register("spreading_gravel",
            () -> new BlockItem(ModContent.SPREADING_GRAVEL.get(), new Item.Properties())
        );

        blocks.register(modEventBus);
        items.register(modEventBus);
    }
}
```

> **API note:** If `DeferredRegister.create` doesn't compile, confirm `net.minecraft.core.registries.Registries` is on the classpath (it is part of vanilla MC, not NeoForge-specific). Check NeoForge 21.1.x javadoc for any signature changes.

### Task 15: NeoForge mod metadata

**Files:**
- Create: `neoforge/src/main/resources/META-INF/neoforge.mods.toml`

- [ ] **Step 1: Write `neoforge.mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "${neoforge_loader_version_range}"
license = "MIT"

[[mods]]
modId = "spreading_gravel"
version = "${mod_version}"
displayName = "Spreading Gravel"
description = "Adds Spreading Gravel, a gravity block that flows sideways toward the deepest nearby drop."

[[dependencies.spreading_gravel]]
modId = "neoforge"
type = "required"
versionRange = "${neoforge_version_range}"
ordering = "NONE"
side = "BOTH"

[[dependencies.spreading_gravel]]
modId = "minecraft"
type = "required"
versionRange = "${minecraft_version_range}"
ordering = "NONE"
side = "BOTH"
```

> The `${…}` placeholders are expanded by NeoForge Gradle from `gradle.properties` at build time.

### Task 16: Build and verify NeoForge jar

- [ ] **Step 1: Build NeoForge jar**

```bash
./gradlew :neoforge:build
```

Expected: `neoforge/build/libs/spreading_gravel-*.jar` produced with no errors.

- [ ] **Step 2: Check jar contents**

```bash
jar tf neoforge/build/libs/spreading_gravel-*.jar | grep -E "spreading_gravel|pack.mcmeta"
```

Expected lines include:
- `META-INF/neoforge.mods.toml`
- `net/ethelred/spreading_gravel/SpreadingGravelBlock.class`
- `assets/spreading_gravel/textures/block/spreading_gravel.png`
- `data/spreading_gravel/recipes/spreading_gravel.json`
- `data/spreading_gravel/loot/blocks/spreading_gravel.json`
- `pack.mcmeta`

If `pack.mcmeta` or `assets/` or `data/` are missing, the `processResources { from … }` wiring in `neoforge/build.gradle` needs to be corrected.

- [ ] **Step 3: Commit**

```bash
git add neoforge/
git commit -m "feat: NeoForge platform registration and mod metadata"
```

---

## Chunk 5: Fabric Platform

### Task 17: Fabric mod entry point

**Files:**
- Create: `fabric/src/main/java/net/ethelred/spreading_gravel/fabric/SpreadingGravelFabric.java`

- [ ] **Step 1: Write entry point**

```java
package net.ethelred.spreading_gravel.fabric;

import net.ethelred.spreading_gravel.ModContent;
import net.ethelred.spreading_gravel.SpreadingGravelBlock;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

public class SpreadingGravelFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SpreadingGravelBlock block = new SpreadingGravelBlock(
            net.minecraft.world.level.block.Block.Properties.ofFullCopy(Blocks.GRAVEL)
        );

        Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("spreading_gravel", "spreading_gravel"),
            block
        );
        ModContent.SPREADING_GRAVEL = () -> block;

        BlockItem item = new BlockItem(block, new Item.Properties());
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("spreading_gravel", "spreading_gravel"),
            item
        );
        ModContent.SPREADING_GRAVEL_ITEM = () -> item;
    }
}
```

> **API note:** `ResourceLocation.fromNamespaceAndPath` is the 1.21.1 API. In earlier versions this was `new ResourceLocation("namespace", "path")`. Check Fabric's MC 1.21.1 mappings if the method doesn't resolve.

### Task 18: Fabric mod metadata

**Files:**
- Create: `fabric/src/main/resources/fabric.mod.json`

- [ ] **Step 1: Write `fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "spreading_gravel",
  "version": "1.0.0",
  "name": "Spreading Gravel",
  "description": "Adds Spreading Gravel, a gravity block that flows sideways toward the deepest nearby drop.",
  "authors": ["edward3h"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": [
      "net.ethelred.spreading_gravel.fabric.SpreadingGravelFabric"
    ]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.1"
  }
}
```

### Task 19: Build and verify Fabric jar

- [ ] **Step 1: Build Fabric jar**

```bash
./gradlew :fabric:build
```

Expected: `fabric/build/libs/spreading_gravel-*.jar` produced with no errors.

- [ ] **Step 2: Check jar contents**

```bash
jar tf fabric/build/libs/spreading_gravel-*-dev.jar 2>/dev/null | grep -E "spreading_gravel|pack.mcmeta" \
  || jar tf fabric/build/libs/spreading_gravel-*.jar | grep -E "spreading_gravel|pack.mcmeta"
```

Fabric Loom may produce multiple jars; check the one without `-dev` or `-sources` suffix. Expected: same set of entries as NeoForge jar — classes, assets, data, `pack.mcmeta`, `fabric.mod.json`.

- [ ] **Step 3: Build both and run unit tests one final time**

```bash
./gradlew :neoforge:build :fabric:build :common:test
```

Expected: all succeed.

- [ ] **Step 4: Commit**

```bash
git add fabric/
git commit -m "feat: Fabric platform registration and mod metadata"
```

---

## In-Game Verification

Once both jars build, test in the respective dev clients:

**NeoForge:** `./gradlew :neoforge:runClient`
**Fabric:** `./gradlew :fabric:runClient`

Checklist (run in each):

- [ ] `/give @s spreading_gravel:spreading_gravel` works (quick alternative to crafting)
- [ ] Block falls straight down when air below
- [ ] Block placed on flat floor next to a 1-block-deep pit → rolls into pit on next tick
- [ ] Block placed on flat floor with 3-block-deep pit north and 1-block-deep pit east → goes north
- [ ] Block placed on flat floor with equal-depth pits north and east → goes north (tie-break)
- [ ] Block fully surrounded by solid blocks on all horizontal sides → stays permanently
- [ ] Block falls through water column
- [ ] Recipe: craft 3 gravel + 1 sand → 3× Spreading Gravel (survival mode or recipe book)
- [ ] Breaking the block drops itself (not nothing)
