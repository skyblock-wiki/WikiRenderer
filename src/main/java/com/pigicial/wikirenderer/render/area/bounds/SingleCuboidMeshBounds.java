package com.pigicial.wikirenderer.render.area.bounds;

import com.pigicial.wikirenderer.render.area.AreaPropertyBundle;
import com.pigicial.wikirenderer.render.area.WorldBlockMesh;
import com.pigicial.wikirenderer.render.area.side_view.ExpansionSide;
import com.pigicial.wikirenderer.render.area.side_view.MeshSideRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class SingleCuboidMeshBounds implements ExpandableMeshBounds {
    private BlockPos min;
    private BlockPos max;

    public SingleCuboidMeshBounds(BlockPos first, BlockPos second) {
        this.min = first;
        this.max = second;
        this.recalculateCorners();
    }

    @Override
    public boolean isInBounds(BlockPos pos) {
        return this.min.getX() <= pos.getX() && this.min.getY() <= pos.getY() && this.min.getZ() <= pos.getZ()
               && this.max.getX() >= pos.getX() && this.max.getY() >= pos.getY() && this.max.getZ() >= pos.getZ();
    }

    @Override
    public AABB buildBoundingBox() {
        return AABB.encapsulatingFullBlocks(min, max);
    }

    @Override
    public BlockPos getMinCorner() {
        return min;
    }

    @Override
    public BlockPos getMaxCorner() {
        return max;
    }

    @Override
    public List<Iterable<BlockPos>> buildBlockPositionsForSubMesh(BlockPos from, BlockPos to) {
        return List.of(BlockPos.betweenClosed(from, to));
    }

    @Override
    public void move(WorldBlockMesh mesh, ExpansionSide side, MeshSideRotation currentRotation, int multiplier) {
        BlockPos oldMin = this.min;
        BlockPos oldMax = this.max;

        this.modifyBounds(side.getDirection(currentRotation), multiplier);
        if (mesh.lastUpdateUsesWalkabilityFilter != AreaPropertyBundle.INSTANCE.useWalkabilityFilter.get()) {
            mesh.scheduleRebuild(true);
        } else {
            this.dirtyDifference(mesh, oldMin, oldMax, this.min, this.max, multiplier >= 1);
        }
    }

    private void modifyBounds(Vec3i worldFacing, int multiplier) {
        boolean isTouchingMinX = worldFacing.getX() < 0;
        boolean isTouchingMaxX = worldFacing.getX() > 0;
        boolean isTouchingMinZ = worldFacing.getZ() < 0;
        boolean isTouchingMaxZ = worldFacing.getZ() > 0;

        int minChangeX = isTouchingMinX ? (worldFacing.getX() * multiplier) : 0;
        int maxChangeX = isTouchingMaxX ? (worldFacing.getX() * multiplier) : 0;
        int minChangeZ = isTouchingMinZ ? (worldFacing.getZ() * multiplier) : 0;
        int maxChangeZ = isTouchingMaxZ ? (worldFacing.getZ() * multiplier) : 0;

        this.min = min.offset(minChangeX, 0, minChangeZ);
        this.max = max.offset(maxChangeX, 0, maxChangeZ);
        this.recalculateCorners();
    }

    // theres probably a better way to do this but whatever
    private void recalculateCorners() {
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ()); // /wikirender area pos -4 -4 -1 16 36 17

        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    // todo: spamming expand can cause some chunks to fail to update
    private void dirtyDifference(WorldBlockMesh mesh, BlockPos oldMin, BlockPos oldMax, BlockPos newMin, BlockPos newMax, boolean increasing) {
        AABB oldBox = AABB.encapsulatingFullBlocks(oldMin, oldMax);
        AABB newBox = AABB.encapsulatingFullBlocks(newMin, newMax);

        AABB dirtyZone = increasing ? getBoxDelta(newBox, oldBox) : getBoxDelta(oldBox, newBox);
        if (dirtyZone == null) return;

        int minSX = SectionPos.blockToSectionCoord((int) Math.floor(dirtyZone.minX));
        int minSY = SectionPos.blockToSectionCoord((int) Math.floor(dirtyZone.minY));
        int minSZ = SectionPos.blockToSectionCoord((int) Math.floor(dirtyZone.minZ));
        int maxSX = SectionPos.blockToSectionCoord((int) Math.ceil(dirtyZone.maxX) - 1);
        int maxSY = SectionPos.blockToSectionCoord((int) Math.ceil(dirtyZone.maxY) - 1);
        int maxSZ = SectionPos.blockToSectionCoord((int) Math.ceil(dirtyZone.maxZ) - 1);

        for (int x = minSX; x <= maxSX; x++) {
            for (int y = minSY; y <= maxSY; y++) {
                for (int z = minSZ; z <= maxSZ; z++) {
                    mesh.setDirty(x, y, z, true);
                }
            }
        }
    }

    private AABB getBoxDelta(AABB big, AABB small) {
        double minY = Math.max(big.minY, small.minY);
        double maxY = Math.min(big.maxY, small.maxY);

        if (big.minX < small.minX) {
            return new AABB(big.minX, minY, big.minZ, small.minX, maxY, big.maxZ);
        }
        if (big.maxX > small.maxX) {
            return new AABB(small.maxX, minY, big.minZ, big.maxX, maxY, big.maxZ);
        }
        if (big.minZ < small.minZ) {
            return new AABB(big.minX, minY, big.minZ, big.maxX, maxY, small.minZ);
        }
        if (big.maxZ > small.maxZ) {
            return new AABB(big.minX, minY, small.maxZ, big.maxX, maxY, big.maxZ);
        }
        return null;
    }
}
