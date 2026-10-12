package net.minecraft.client.render.vertex;

public class Tesselator {
    private BufferBuilder bufferBuilder;
    private BufferUploader uploader = new BufferUploader();
    private static final Tesselator INSTANCE = new Tesselator(2097152);

    public static Tesselator getInstance() {
        return INSTANCE;
    }

    public Tesselator(int size) {
        this.bufferBuilder = new BufferBuilder(size);
    }

    public void end() {
        this.bufferBuilder.end();
        this.uploader.end(this.bufferBuilder);
    }

    public BufferBuilder getBuffer() {
        return this.bufferBuilder;
    }
}
