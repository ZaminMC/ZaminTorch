package net.minecraft.client.render.vertex;

public class VertexBufferUploader extends BufferUploader {
    private VertexBuffer buffer = null;

    @Override
    public void end(BufferBuilder builder) {
        builder.clear();
        this.buffer.upload(builder.getBuffer());
    }

    public void setBuffer(VertexBuffer buffer) {
        this.buffer = buffer;
    }
}
