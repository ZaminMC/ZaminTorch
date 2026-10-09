package net.minecraft.world.gen.structure;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StructureRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Map<String, Class<? extends StructureStart>> ID_TO_START = Maps.newHashMap();
    private static Map<Class<? extends StructureStart>, String> START_TO_ID = Maps.newHashMap();
    private static Map<String, Class<? extends StructurePiece>> ID_TO_PIECE = Maps.newHashMap();
    private static Map<Class<? extends StructurePiece>, String> PIECE_TO_ID = Maps.newHashMap();

    private static void registerStart(Class<? extends StructureStart> startType, String id) {
        ID_TO_START.put(id, startType);
        START_TO_ID.put(startType, id);
    }

    static void registerPiece(Class<? extends StructurePiece> pieceType, String id) {
        ID_TO_PIECE.put(id, pieceType);
        PIECE_TO_ID.put(pieceType, id);
    }

    public static String getId(StructureStart start) {
        return START_TO_ID.get(start.getClass());
    }

    public static String getId(StructurePiece piece) {
        return PIECE_TO_ID.get(piece.getClass());
    }

    public static StructureStart getStartFromNbt(NbtCompound nbt, World world) {
        StructureStart structurestart = null;

        try {
            Class<? extends StructureStart> oclass = ID_TO_START.get(nbt.getString("id"));
            if (oclass != null) {
                structurestart = oclass.newInstance();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed Start with id " + nbt.getString("id"));
            exception.printStackTrace();
        }

        if (structurestart != null) {
            structurestart.readNbt(world, nbt);
        } else {
            LOGGER.warn("Skipping Structure with id " + nbt.getString("id"));
        }

        return structurestart;
    }

    public static StructurePiece getPieceFromNbt(NbtCompound nbt, World world) {
        StructurePiece structurepiece = null;

        try {
            Class<? extends StructurePiece> oclass = ID_TO_PIECE.get(nbt.getString("id"));
            if (oclass != null) {
                structurepiece = oclass.newInstance();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed Piece with id " + nbt.getString("id"));
            exception.printStackTrace();
        }

        if (structurepiece != null) {
            structurepiece.readNbt(world, nbt);
        } else {
            LOGGER.warn("Skipping Piece with id " + nbt.getString("id"));
        }

        return structurepiece;
    }

    static {
        registerStart(MineshaftStart.class, "Mineshaft");
        registerStart(VillageStructure.Start.class, "Village");
        registerStart(FortressStructure.Start.class, "Fortress");
        registerStart(StrongholdStructure.Start.class, "Stronghold");
        registerStart(TempleStructure.Start.class, "Temple");
        registerStart(OceanMonumentStructure.Start.class, "Monument");
        MineshaftPieces.register();
        VillagePieces.register();
        FortressPieces.register();
        StrongholdPieces.register();
        TemplePieces.register();
        OceanMonumentPieces.register();
    }
}
