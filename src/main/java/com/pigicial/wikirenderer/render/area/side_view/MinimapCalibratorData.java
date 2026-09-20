package com.pigicial.wikirenderer.render.area.side_view;

import com.mojang.blaze3d.platform.NativeImage;
import com.pigicial.wikirenderer.render.area.AreaPropertyBundle;
import com.pigicial.wikirenderer.render.area.AreaRenderable;
import com.pigicial.wikirenderer.render.export.CropData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For usage on <a href="https://hypixelskyblock.minecraft.wiki/w/Module:Minimap/Datasheet">the Hypixel SkyBlock Wiki's Module:Minimap/Datasheet Minimap Calibrator tool</a>
 */
public record MinimapCalibratorData(
        int topLeftImagePixelX,
        int topLeftImagePixelY,
        double topLeftMapCoordX,
        double topLeftMapCoordY,
        int bottomRightImagePixelX,
        int bottomRightImagePixelY,
        double bottomRightMapCoordX,
        double bottomRightMapCoordY,
        int imageWidth,
        int imageHeight
) {

    // this is a little but not entirely black magic to me
    public static MinimapCalibratorData getCalibrationData(AreaRenderable renderable, @Nullable CropData cropData, NativeImage image) {
        Map<ViewportCorner, Vec3> corners = MinimapCalibratorData.getViewportCorners(renderable);
        Vec3 originTopLeft = corners.get(ViewportCorner.TOP_LEFT);

        double resolution = renderable.getExportResolution();
        Vec3 right = corners.get(ViewportCorner.TOP_RIGHT).subtract(originTopLeft).scale(1.0 / resolution);
        Vec3 down = corners.get(ViewportCorner.BOTTOM_LEFT).subtract(originTopLeft).scale(1.0 / resolution);

        int cropX = (cropData != null) ? cropData.minX() : 0;
        int cropY = (cropData != null) ? cropData.minY() : 0;

        Vec3 worldTopLeft = originTopLeft
                .add(right.scale(cropX))
                .add(down.scale(cropY));

        Vec3 worldBottomRight = worldTopLeft
                .add(right.scale(image.getWidth() - 1))
                .add(down.scale(image.getHeight() - 1));

        Vec2 mapTopLeft = projectToMapPlane(renderable, worldTopLeft);
        Vec2 mapBottomRight = projectToMapPlane(renderable, worldBottomRight);

        return new MinimapCalibratorData(
                0, 0, mapTopLeft.x, mapTopLeft.y,
                image.getWidth() - 1, image.getHeight() - 1, mapBottomRight.x, mapBottomRight.y,
                image.getWidth(), image.getHeight()
        );
    }

    private static Map<ViewportCorner, Vec3> getViewportCorners(AreaRenderable renderable) {
        AABB boundingBox = renderable.mesh.bounds.buildBoundingBox();
        int xSize = (int) boundingBox.getXsize();
        int ySize = (int) boundingBox.getYsize();
        int zSize = (int) boundingBox.getZsize();

        Matrix4f projection = new Matrix4f().setOrtho(-1, 1, -1, 1, -100, 1000);

        Matrix4fStack modelView = new Matrix4fStack(2);
        renderable.getProperties().applyToViewMatrix(renderable, modelView);
        modelView.translate(-xSize / 2f, -ySize / 2f, -zSize / 2f);

        Matrix4f combined = new Matrix4f(projection).mul(modelView);
        combined.invert();

        Map<ViewportCorner, Vec3> corners = new HashMap<>();
        for (ViewportCorner corner : ViewportCorner.values()) {
            Vec3 unprojectedCoords = MinimapCalibratorData.unproject(renderable, combined, corner.getNDCX(), corner.getNDCY());
            corners.put(corner, unprojectedCoords);
        }


        return corners;
    }

    private static Vec2 projectToMapPlane(AreaRenderable renderable, Vec3 worldPosition) {
        AreaPropertyBundle props = renderable.getProperties();

        return switch (props.sideViewSlant) {
            case BELOW, ABOVE -> new Vec2((float) worldPosition.x, (float) worldPosition.z);
            case SIDE -> switch (props.sideViewRotation) {
                case NORTH, SOUTH -> new Vec2((float) worldPosition.x, (float) worldPosition.y);
                case EAST, WEST -> new Vec2((float) worldPosition.z, (float) worldPosition.y);
            };
        };
    }

    private static Vec3 unproject(AreaRenderable renderable, Matrix4f invMatrix, float ndcX, float ndcY) {
        Vector4f pos = new Vector4f(ndcX, ndcY, 0, 1);
        pos.mul(invMatrix);

        AreaPropertyBundle properties = renderable.getProperties();
        double pixelsPerBlock = properties.getPixelsPerBlockResolution();
        float halfPixelWorldOffset = 0;
        if (pixelsPerBlock == 4 && properties.halfPixelOffsetFor4x4.get()) {
            halfPixelWorldOffset = 0.5f / (float) pixelsPerBlock;
        }

        BlockPos minCorner = renderable.mesh.bounds.getMinCorner();
        return new Vec3(pos.x - halfPixelWorldOffset, pos.y, pos.z - halfPixelWorldOffset).add(minCorner.getX(), minCorner.getY(), minCorner.getZ());
    }

    public String toFileText(String imageFileName, String areaCommand) {
        DecimalFormat df = new DecimalFormat("0.####");

        List<String> lines = new ArrayList<>();
        lines.add("Use this data on https://hypixelskyblock.minecraft.wiki/w/Module:Minimap/Datasheet in the following order:");
        lines.add("");
        lines.add("Corresponding minimap file name: " + imageFileName);
        lines.add("Run command: " + areaCommand);
        lines.add("");
        lines.add("Top-left Image Pixel X: " + this.topLeftImagePixelX);
        lines.add("Top-left Image Pixel Y: " + this.topLeftImagePixelY);
        lines.add("Top-left Map Coordinate X: " + df.format(this.topLeftMapCoordX));
        lines.add("Top-left Map Coordinate Y: " + df.format(this.topLeftMapCoordY));
        lines.add("Bottom-right Image Pixel X: " + this.bottomRightImagePixelX);
        lines.add("Bottom-right Image Pixel Y: " + this.bottomRightImagePixelY);
        lines.add("Bottom-right Map Coordinate X: " + df.format(this.bottomRightMapCoordX));
        lines.add("Bottom-right Map Coordinate Y: " + df.format(this.bottomRightMapCoordY));
        lines.add("Image Width: " + this.imageWidth);
        lines.add("Image Height: " + this.imageHeight);
        lines.add("");
        lines.add("Also be sure to update:");
        lines.add("- https://hypixelskyblock.minecraft.wiki/w/Module:Minimap/Aliases");
        lines.add("- https://hypixelskyblock.minecraft.wiki/w/Template:Minimap/styles.css");
        return String.join("\n", lines);
    }
}
