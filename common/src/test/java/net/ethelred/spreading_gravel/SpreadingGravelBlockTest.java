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
