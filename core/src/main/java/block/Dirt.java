package block;

import main.world.Chunk;

public class Dirt extends Block {
    private static final Dirt INSTANCE = new Dirt();

    private Dirt() {
        super(Chunk.DIRT, "Dirt", "block/soil.png", true);
    }
}
