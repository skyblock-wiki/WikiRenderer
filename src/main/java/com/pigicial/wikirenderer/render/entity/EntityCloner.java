package com.pigicial.wikirenderer.render.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.mixin.access.ElytraAnimationStateAccessor;
import com.pigicial.wikirenderer.render.entity.player.ProfileFetchMode;
import com.pigicial.wikirenderer.render.entity.player.RenderablePlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EntityCloner {
    @Nullable
    public static Entity copy(Entity source) {
        if (source instanceof Player player) {
            return copyPlayer(player);
        }

        CompoundTag nbt = saveNBT(source);
        nbt.putString("id", EntityType.getKey(source.getType()).toString());

        Entity clonedEntity = EntityType.loadEntityRecursive(nbt, source.level(), new EntitySpawnRequest(EntitySpawnReason.LOAD, true), EntityProcessor.NOP);
        if (clonedEntity == null) return null;

        List<SynchedEntityData.DataValue<?>> nonDefaultValues = source.getEntityData().getNonDefaultValues();
        if (nonDefaultValues != null) {
            clonedEntity.getEntityData().assignValues(nonDefaultValues);
        }

        if (clonedEntity instanceof Leashable leashableClone && source instanceof Leashable leashableSource) {
            leashableClone.setLeashData(leashableSource.getLeashData());
        }

        clonedEntity.setId(Integer.MAX_VALUE); // if id is 0 (not set), it fails
        clonedEntity.tick();

        clonedEntity.setXRot(source.getXRot());
        clonedEntity.setYRot(source.getYRot());
        if (clonedEntity instanceof LivingEntity livingClone && source instanceof LivingEntity livingSource) {
            livingClone.hurtTime = 0;
            livingClone.deathTime = 0;
            livingClone.yHeadRot = livingSource.yHeadRot;
            livingClone.yHeadRotO = livingSource.yHeadRotO;
            livingClone.yBodyRot = livingSource.yBodyRot;
            livingClone.yBodyRotO = livingSource.yBodyRotO;
            livingClone.getAttributes().assignAllValues(livingSource.getAttributes());
        }

        return clonedEntity;
    }

    public static Entity copyEntityAndPassengers(Entity source) {
        source = source.getRootVehicle();

        List<Entity> entityStack = new ArrayList<>();
        EntityRenderable.applyToEntityAndPassengers(source, entity -> {
            Entity entityClone = copy(entity);
            if (entityClone != null) {
                if (entityClone.getVehicle() != null) {
                    entityClone.getVehicle().remove(Entity.RemovalReason.DISCARDED);
                }
                entityClone.getPassengers().forEach(passenger -> passenger.remove(Entity.RemovalReason.DISCARDED));

                entityStack.add(entityClone);
            }
        });

        Entity bottomEntity = entityStack.getFirst();

        Entity recentTopEntity = bottomEntity;
        for (int i = 1, entityStackSize = entityStack.size(); i < entityStackSize; i++) {
            Entity passenger = entityStack.get(i);
            passenger.startRiding(recentTopEntity);
            recentTopEntity = passenger;
        }

        EntityRenderable.applyToEntityAndPassengers(bottomEntity, Entity::tick);
        return bottomEntity;
    }

    public static RenderablePlayerEntity copyPlayer(Player originalPlayer) {
        GameProfile originalProfile = originalPlayer.getGameProfile();
        GameProfile fakeProfile = new GameProfile(originalProfile.id(), originalProfile.name(), new PropertyMap(originalProfile.properties()));

        RenderablePlayerEntity playerClone = new RenderablePlayerEntity(fakeProfile, ProfileFetchMode.TEXTURE);

        CompoundTag nbt = saveNBT(originalPlayer);
        try (ProblemReporter.ScopedCollector loggingRead = new ProblemReporter.ScopedCollector(playerClone.problemPath(), WikiRenderer.LOGGER)) {
            playerClone.load(TagValueInput.create(loggingRead, playerClone.registryAccess(), nbt));
        }

        List<SynchedEntityData.DataValue<?>> nonDefaultValues = originalPlayer.getEntityData().getNonDefaultValues();
        if (nonDefaultValues != null) {
            playerClone.getEntityData().assignValues(nonDefaultValues);
        }

        playerClone.hurtTime = 0;
        playerClone.deathTime = 0;
        playerClone.tick();

        playerClone.setXRot(originalPlayer.getXRot());
        playerClone.setYRot(originalPlayer.getYRot());

        playerClone.yHeadRot = originalPlayer.yHeadRot;
        playerClone.yHeadRotO = originalPlayer.yHeadRotO;
        playerClone.yBodyRot = originalPlayer.yBodyRot;
        playerClone.yBodyRotO = originalPlayer.yBodyRotO;
        playerClone.getAttributes().assignAllValues(originalPlayer.getAttributes());

        ElytraAnimationStateAccessor elytraData = (ElytraAnimationStateAccessor) playerClone.elytraAnimationState;
        elytraData.isometric$setRotX((float) (Math.PI / 12));
        elytraData.isometric$setRotZ((float) (-Math.PI / 12));
        playerClone.elytraAnimationState.tick();

        return playerClone;
    }

    public static CompoundTag saveNBT(Entity entity) {
        ProblemReporter.ScopedCollector logging = new ProblemReporter.ScopedCollector(entity.problemPath(), WikiRenderer.LOGGER);
        TagValueOutput view = TagValueOutput.createWithContext(logging, entity.registryAccess());
        entity.saveWithoutId(view);

        CompoundTag savedNbt = view.buildResult();
        logging.close();

        return savedNbt;
    }
}
