package net.minecraft.world.gen.structure;

import java.util.Random;
import net.minecraft.world.World;

public class MineshaftStart extends StructureStart {
    public MineshaftStart() {
    }

    public MineshaftStart(World world, Random random, int chunkX, int chunkZ) {
        super(chunkX, chunkZ);
        MineshaftPieces.Room mineshaftpieces$room = new MineshaftPieces.Room(0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
        this.pieces.add(mineshaftpieces$room);
        mineshaftpieces$room.addChildren(mineshaftpieces$room, this.pieces, random);
        this.findBounds();
        this.moveBelowSeaLevel(world, random, 10);
    }
}
