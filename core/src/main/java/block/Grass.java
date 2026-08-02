package block;

import main.world.Chunk;

public class Grass extends Block {
    private static final Grass INSTANCE = new Grass();

    private Grass() {
        super(Chunk.GRASS, "Grass", "block/grass.png", true);
    }
}
