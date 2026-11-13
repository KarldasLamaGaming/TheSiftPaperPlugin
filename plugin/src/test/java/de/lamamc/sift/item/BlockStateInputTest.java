package de.lamamc.sift.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockStateInputTest {

    @Test
    void theStateBecomesTheBlockStateComponentOfTheItem() {
        assertEquals("minecraft:mushroom_stem[minecraft:block_state={down:\"false\",east:\"true\",up:\"false\"}]",
                SiftItems.blockStateInput("minecraft:mushroom_stem", "minecraft:mushroom_stem[down=false,east=true,up=false]"));
        assertEquals("minecraft:string[minecraft:block_state={attached:\"true\",disarmed:\"false\"}]",
                SiftItems.blockStateInput("minecraft:string", "minecraft:tripwire[attached=true,disarmed=false]"));
        assertEquals("minecraft:stone", SiftItems.blockStateInput("minecraft:stone", "minecraft:stone"));
    }
}
