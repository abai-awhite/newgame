package block;

import main.world.Chunk;

public class Stone extends Block {
    private static final Stone INSTANCE = new Stone();

    private Stone() {
        super(Chunk.STONE, "Stone", "block/stone.png", true);
    }
}
