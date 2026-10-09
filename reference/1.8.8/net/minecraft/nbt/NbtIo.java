package net.minecraft.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;

public class NbtIo {
    public static NbtCompound readCompressed(InputStream is) throws IOException {
        DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(is)));

        try {
            return read(datainputstream, NbtReadLimiter.UNLIMITED);
        } finally {
            datainputstream.close();
        }
    }

    public static void writeCompressed(NbtCompound nbt, OutputStream os) throws IOException {
        DataOutputStream dataoutputstream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(os)));

        try {
            write(nbt, dataoutputstream);
        } finally {
            dataoutputstream.close();
        }
    }

    public static void writeSafely(NbtCompound nbt, File file) throws IOException {
        File file1 = new File(file.getAbsolutePath() + "_tmp");
        if (file1.exists()) {
            file1.delete();
        }

        write(nbt, file1);
        if (file.exists()) {
            file.delete();
        }

        if (file.exists()) {
            throw new IOException("Failed to delete " + file);
        }

        file1.renameTo(file);
    }

    public static void write(NbtCompound nbt, File file) throws IOException {
        DataOutputStream dataoutputstream = new DataOutputStream(new FileOutputStream(file));

        try {
            write(nbt, dataoutputstream);
        } finally {
            dataoutputstream.close();
        }
    }

    public static NbtCompound read(File file) throws IOException {
        if (!file.exists()) {
            return null;
        }

        DataInputStream datainputstream = new DataInputStream(new FileInputStream(file));

        try {
            return read(datainputstream, NbtReadLimiter.UNLIMITED);
        } finally {
            datainputstream.close();
        }
    }

    public static NbtCompound read(DataInputStream is) throws IOException {
        return read(is, NbtReadLimiter.UNLIMITED);
    }

    public static NbtCompound read(DataInput input, NbtReadLimiter limiter) throws IOException {
        NbtElement nbtelement = read(input, 0, limiter);
        if (nbtelement instanceof NbtCompound) {
            return (NbtCompound)nbtelement;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(NbtCompound nbt, DataOutput output) throws IOException {
        write((NbtElement)nbt, output);
    }

    private static void write(NbtElement nbt, DataOutput output) throws IOException {
        output.writeByte(nbt.getType());
        if (nbt.getType() != 0) {
            output.writeUTF("");
            nbt.write(output);
        }
    }

    private static NbtElement read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        byte b0 = input.readByte();
        if (b0 == 0) {
            return new NbtEnd();
        }

        input.readUTF();
        NbtElement nbtelement = NbtElement.create(b0);

        try {
            nbtelement.read(input, depth, limiter);
            return nbtelement;
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.of(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");
            crashreportcategory.add("Tag name", "[UNNAMED TAG]");
            crashreportcategory.add("Tag type", b0);
            throw new CrashException(crashreport);
        }
    }
}
