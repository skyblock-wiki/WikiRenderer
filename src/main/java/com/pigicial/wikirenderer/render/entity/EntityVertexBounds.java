package com.pigicial.wikirenderer.render.entity;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class EntityVertexBounds {
    private static final double TEXT_MERGE_THRESHOLD = 0.2;

    private AABB bounds;
    private final List<AABB> textBounds = new ArrayList<>();
    private boolean boundsOnlyMadeFromText;

    protected EntityVertexBounds(float x, float y, float z, boolean firstPointIsText) {
        this.bounds = new AABB(x, y, z, x, y, z);
        this.boundsOnlyMadeFromText = firstPointIsText;
        if (firstPointIsText) {
            addPoint(x, y, z, true);
        }
    }

    public AABB getBounds() {
        return bounds;
    }

    public List<AABB> getClipBounds() {
        if (boundsOnlyMadeFromText) {
            return textBounds;
        } else {
            return List.of(bounds);
        }
    }

    protected void addPoint(float x, float y, float z, boolean text) {
        bounds = new AABB(Math.min(bounds.minX, x), Math.min(bounds.minY, y), Math.min(bounds.minZ, z), Math.max(bounds.maxX, x), Math.max(bounds.maxY, y), Math.max(bounds.maxZ, z));

        if (!text) {
            boundsOnlyMadeFromText = false;
        } else {
            // text is handled as separate bounding boxes, otherwise they can turn into a very oversized single box when rotated at a 45-degree angle, which
            // can make it harder to select entities "behind" this large bounding box, even though from the player's perspective they're not looking at the text, they're
            // looking at the entity instead, since the entity target logic prioritizes closer entities, and the text's large bounding box is closer in that case
            boolean merged = false;
            for (int i = 0; i < textBounds.size(); i++) {
                AABB box = textBounds.get(i);
                if (isNear(box, x, y, z)) {
                    AABB newBox = new AABB(Math.min(box.minX, x), Math.min(box.minY, y), Math.min(box.minZ, z), Math.max(box.maxX, x), Math.max(box.maxY, y), Math.max(box.maxZ, z));
                    textBounds.set(i, newBox);
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                double padding = TEXT_MERGE_THRESHOLD / 2D;
                textBounds.add(new AABB(x - padding, y - padding, z - padding, x + padding, y + padding, z + padding));
            }
        }
    }

    private boolean isNear(AABB box, float x, float y, float z) {
        return x >= box.minX && x <= box.maxX
               && y >= box.minY && y <= box.maxY
               && z >= box.minZ && z <= box.maxZ;
    }

    public void move(double xOffset, double yOffset, double zOffset) {
        if (bounds != null) {
            bounds = bounds.move(xOffset, yOffset, zOffset);
        }
        for (int i = 0, textBoundsSize = textBounds.size(); i < textBoundsSize; i++) {
            AABB textBound = textBounds.get(i);
            textBounds.set(i, textBound.move(xOffset, yOffset, zOffset));
        }
    }
}
