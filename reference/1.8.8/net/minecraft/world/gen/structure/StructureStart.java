package net.minecraft.world.gen.structure;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public abstract class StructureStart {
    protected LinkedList<StructurePiece> pieces = new LinkedList<>();
    protected StructureBox bounds;
    private int chunkX;
    private int chunkZ;

    public StructureStart() {
    }

    public StructureStart(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public StructureBox getBounds() {
        return this.bounds;
    }

    public LinkedList<StructurePiece> getPieces() {
        return this.pieces;
    }

    public void postProcess(World world, Random random, StructureBox chunkBounds) {
        Iterator<StructurePiece> iterator = this.pieces.iterator();

        while (iterator.hasNext()) {
            StructurePiece structurepiece = iterator.next();
            if (structurepiece.getBounds().intersects(chunkBounds) && !structurepiece.postProcess(world, random, chunkBounds)) {
                iterator.remove();
            }
        }
    }

    protected void findBounds() {
        this.bounds = StructureBox.infinite();

        for (StructurePiece structurepiece : this.pieces) {
            this.bounds.union(structurepiece.getBounds());
        }
    }

    public NbtCompound toNbt(int chunkX, int chunkZ) {
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.putString("id", StructureRegistry.getId(this));
        nbtcompound.putInt("ChunkX", chunkX);
        nbtcompound.putInt("ChunkZ", chunkZ);
        nbtcompound.put("BB", this.bounds.toNbt());
        NbtList nbtlist = new NbtList();

        for (StructurePiece structurepiece : this.pieces) {
            nbtlist.addElement(structurepiece.toNbt());
        }

        nbtcompound.put("Children", nbtlist);
        this.writeValidityNbt(nbtcompound);
        return nbtcompound;
    }

    public void writeValidityNbt(NbtCompound nbt) {
    }

    public void readNbt(World world, NbtCompound nbt) {
        this.chunkX = nbt.getInt("ChunkX");
        this.chunkZ = nbt.getInt("ChunkZ");
        if (nbt.contains("BB")) {
            this.bounds = new StructureBox(nbt.getIntArray("BB"));
        }

        NbtList nbtlist = nbt.getList("Children", 10);

        for (int i = 0; i < nbtlist.size(); i++) {
            this.pieces.add(StructureRegistry.getPieceFromNbt(nbtlist.getCompound(i), world));
        }

        this.readValidityNbt(nbt);
    }

    public void readValidityNbt(NbtCompound nbt) {
    }

    protected void moveBelowSeaLevel(World world, Random random, int amount) {
        int i = world.getSeaLevel() - amount;
        int j = this.bounds.getSpanY() + 1;
        if (j < i) {
            j += random.nextInt(i - j);
        }

        int k = j - this.bounds.maxY;
        this.bounds.move(0, k, 0);

        for (StructurePiece structurepiece : this.pieces) {
            structurepiece.move(0, k, 0);
        }
    }

    protected void moveBetweenYCoords(World world, Random random, int minY, int maxY) {
        int i = maxY - minY + 1 - this.bounds.getSpanY();
        int j = 1;
        if (i > 1) {
            j = minY + random.nextInt(i);
        } else {
            j = minY;
        }

        int k = j - this.bounds.minY;
        this.bounds.move(0, k, 0);

        for (StructurePiece structurepiece : this.pieces) {
            structurepiece.move(0, k, 0);
        }
    }

    public boolean isValid() {
        return true;
    }

    public boolean isValid(ChunkPos pos) {
        return true;
    }

    public void postPlacement(ChunkPos pos) {
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }
}
