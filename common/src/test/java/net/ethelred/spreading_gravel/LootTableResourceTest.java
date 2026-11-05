package net.ethelred.spreading_gravel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LootTableResourceTest {

    /** In 1.21+ loot tables live under {@code loot_table/} (singular); a wrong directory means no drops. */
    @Test
    void lootTableIsAtVanillaPath() {
        assertNotNull(
            getClass().getResource("/data/spreading_gravel/loot_table/blocks/spreading_gravel.json"),
            "Block loot table must be at data/<namespace>/loot_table/blocks/");
    }
}
