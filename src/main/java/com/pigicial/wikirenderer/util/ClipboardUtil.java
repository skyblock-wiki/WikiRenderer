package com.pigicial.wikirenderer.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.pigicial.wikirenderer.mixin.access.NativeImageInvoker;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDL_ClipboardCleanupCallback;
import org.lwjgl.sdl.SDL_ClipboardDataCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// i will admit that a bunch of this is vibecoded, but it works so whatever
public class ClipboardUtil {

    public static void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private record MimeData(long pointer, long length) { }

    public static void setClipboard(NativeImage nativeImage) throws IOException {
        byte[] pngBytes = encodeImageForClipboardUsage(nativeImage);

        // image/bmp is required for discord on windows, because reasons
        byte[] bmpBytes = encodePngAsAlphaBmp(pngBytes);

        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("image/bmp", bmpBytes);
        payloads.put("PNG", pngBytes);
        payloads.put("image/png", pngBytes);

        List<Long> nativePointers = new ArrayList<>();
        Map<String, MimeData> buffers = new LinkedHashMap<>();

        for (Map.Entry<String, byte[]> entry : payloads.entrySet()) {
            byte[] data = entry.getValue();
            long pointer = MemoryUtil.nmemAlloc(data.length);
            if (pointer == 0L) {
                for (long p : nativePointers) MemoryUtil.nmemFree(p);
                return;
            }
            nativePointers.add(pointer);

            ByteBuffer nativeBuffer = MemoryUtil.memByteBuffer(pointer, data.length);
            nativeBuffer.put(data);

            buffers.put(entry.getKey(), new MimeData(pointer, data.length));
        }

        long userdata = MemoryUtil.nmemAlloc(1);
        if (userdata == 0L) {
            for (long p : nativePointers) MemoryUtil.nmemFree(p);
            return;
        }

        final SDL_ClipboardDataCallback dataCallback = SDL_ClipboardDataCallback.create((user, mime_type, size) -> {
            if (mime_type == 0L) {
                MemoryUtil.memPutAddress(size, 0);
                return MemoryUtil.NULL;
            }
            String requestedMime = MemoryUtil.memASCII(mime_type);

            for (Map.Entry<String, MimeData> entry : buffers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(requestedMime)) {
                    MimeData mimeData = entry.getValue();
                    MemoryUtil.memPutAddress(size, mimeData.length);
                    return mimeData.pointer;
                }
            }

            MemoryUtil.memPutAddress(size, 0);
            return MemoryUtil.NULL;
        });

        SDL_ClipboardCleanupCallback[] cleanupHolder = new SDL_ClipboardCleanupCallback[1];
        cleanupHolder[0] = SDL_ClipboardCleanupCallback.create(user -> {
            for (long p : nativePointers) {
                MemoryUtil.nmemFree(p);
            }
            MemoryUtil.nmemFree(userdata);
            dataCallback.free();
            if (cleanupHolder[0] != null) {
                cleanupHolder[0].free();
            }
        });

        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] mimeTypes = buffers.keySet().toArray(new String[0]);
            PointerBuffer mimeTypeBuffer = stack.mallocPointer(mimeTypes.length);
            for (int i = 0; i < mimeTypes.length; i++) {
                mimeTypeBuffer.put(i, stack.ASCII(mimeTypes[i]));
            }

            SDLClipboard.SDL_SetClipboardData(
                    dataCallback,
                    cleanupHolder[0],
                    userdata,
                    mimeTypeBuffer
            );
        }
    }

    public static byte[] encodeImageForClipboardUsage(@NotNull NativeImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (WritableByteChannel channel = Channels.newChannel(out)) {
            ((NativeImageInvoker) (Object) image).wikirenderer$checkAllocated();
            ((NativeImageInvoker) (Object) image).wikirenderer$writeToChannel(channel);
            return out.toByteArray();
        }
    }

    // required for copying clipboard images to discord on windows
    private static byte[] encodePngAsAlphaBmp(byte[] pngBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (source == null) {
            throw new IOException("Could not decode PNG for BMP conversion");
        }

        int bmpFileHeaderSize = 14;
        int bmpInfoHeaderSize = 40;
        int pixelsPerMeter72Dpi = 2835; // arbitrary

        int width = source.getWidth();
        int height = source.getHeight();
        int pixelBytes = width * height * 4;
        int pixelOffset = bmpFileHeaderSize + bmpInfoHeaderSize;
        int fileSize = pixelOffset + pixelBytes;

        ByteBuffer buf = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);

        // https://en.wikipedia.org/wiki/BMP_file_format#File_header
        buf.put((byte) 'B').put((byte) 'M');
        buf.putInt(fileSize);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putInt(pixelOffset);

        // https://en.wikipedia.org/wiki/BMP_file_format#DIB_header
        buf.putInt(bmpInfoHeaderSize)
                .putInt(width)
                .putInt(height) // positive = bottom-up
                .putShort((short) 1) // biPlanes
                .putShort((short) 32) // biBitCount or bits per pixel (4 bytes per, rgba)
                .putInt(0) // biCompression, 0 = no compression
                .putInt(pixelBytes) // biSizeImage
                .putInt(pixelsPerMeter72Dpi) // biXPelsPerMeter (arbitrary for this)
                .putInt(pixelsPerMeter72Dpi) // biXPelsPerMeter (arbitrary for this)
                .putInt(0) // biClrUsed
                .putInt(0); // biClrImportant (0 = all colors important)

        // getRGB returns non-premultiplied ARGB for any source image type (row-major, top-down).
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);

        IntBuffer pixelView = buf.asIntBuffer();
        for (int row = height - 1; row >= 0; row--) {
            pixelView.put(pixels, row * width, width);
        }

        return buf.array();
    }
}