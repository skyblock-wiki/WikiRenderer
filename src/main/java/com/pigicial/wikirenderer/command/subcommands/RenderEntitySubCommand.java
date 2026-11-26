package com.pigicial.wikirenderer.command.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pigicial.wikirenderer.mixin.access.LevelAccessor;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.render.entity.EntityRenderBoundsUtil;
import com.pigicial.wikirenderer.render.entity.EntityRenderable;
import com.pigicial.wikirenderer.render.entity.EntityVertexBounds;
import com.pigicial.wikirenderer.render.skyblock.frame_based.*;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.ScreenSchedulerAndSaver;
import com.pigicial.wikirenderer.util.Translate;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;

public class RenderEntitySubCommand extends WikiRendererSubCommand {
    private static final SuggestionProvider<FabricClientCommandSource> CLIENT_SUMMONABLE_ENTITIES;
    public static final List<? extends EntityType<?>> DEFAULT_INVISIBLE_ENTITY_TYPES;

    static {
        CLIENT_SUMMONABLE_ENTITIES = (_, builder) -> SuggestionContext.suggestResource(
                BuiltInRegistries.ENTITY_TYPE.stream().filter(EntityType::canSummon),
                builder,
                EntityType::getKey,
                entityType -> Component.translatable(Util.makeDescriptionId("entity", EntityType.getKey(entityType)))
        );

        DEFAULT_INVISIBLE_ENTITY_TYPES = List.of(
                EntityTypes.TEXT_DISPLAY,
                EntityTypes.OMINOUS_ITEM_SPAWNER,
                EntityTypes.MARKER,
                EntityTypes.INTERACTION,
                EntityTypes.ITEM,
                EntityTypes.ITEM_DISPLAY,
                EntityTypes.EVOKER_FANGS,
                EntityTypes.EXPERIENCE_ORB,
                EntityTypes.BLOCK_DISPLAY,
                EntityTypes.AREA_EFFECT_CLOUD
        );
    }

    @Override
    public String getName() {
        return "entity";
    }

    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> register(LiteralArgumentBuilder<FabricClientCommandSource> source, CommandBuildContext access) {
        return source.executes(context -> {
                    RenderEntitySubCommand.renderTargetedEntity(context);
                    return 0;
                })
                .then(argument("entity", ResourceArgument.resource(Registries.ENTITY_TYPE))
                        .suggests(CLIENT_SUMMONABLE_ENTITIES)
                        .executes(context -> {
                            this.renderEntity(context, false);
                            return 0;
                        })
                        .then(argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(context -> {
                                    this.renderEntity(context, true);
                                    return 0;
                                })));
    }

    private void renderEntity(CommandContext<FabricClientCommandSource> context, boolean useNbt) {
        Holder.Reference<?> typeReference = context.getArgument("entity", Holder.Reference.class);
        if (typeReference.value() instanceof EntityType<?> entityTypeReference) { // basically to avoid the intellij warning, this should be always true
            CompoundTag entityNbt = useNbt ? CompoundTagArgument.getCompoundTag(context, "nbt") : null;

            EntityRenderable renderable = EntityRenderable.fromEntityType(entityTypeReference, entityNbt);
            if (renderable != null) {
                ScreenSchedulerAndSaver.schedule(new RenderScreen(renderable));
            }
        }
    }

    // Based on the standard attackRange.getClosesetHit code, but instead uses custom entity render bounding box data for better accuracy
    public static void renderTargetedEntity(CommandContext<FabricClientCommandSource> context) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        AttackRange attackRange = new AttackRange(0, 30, 0, 30, 0.2f, 1);
        Entity targetEntity = getClosestHit(player, attackRange);
        if (targetEntity == null) {
            Translate.commandError(context, "no_entity");
            return;
        }

        if (GlobalProperties.get().sbFrameRenderingKeybindOverrides.get()) {
            tryToRenderFrameDataInstead(targetEntity, context);
            return;
        }

        ScreenSchedulerAndSaver.schedule(new RenderScreen(EntityRenderable.fromEntity(targetEntity)));
    }

    public static void tryToRenderFrameDataInstead(Entity targetEntity, CommandContext<FabricClientCommandSource> context) {
        if (!(targetEntity instanceof LivingEntity livingEntity)) {
            Translate.commandError(context, "not_equippable_entity");
            return;
        }

        SkyBlockTimingDataCacher cacher = SkyBlockTimingDataCacher.getInstance();
        HeadTexturesTiming textureData = cacher.getTextureData(livingEntity);
        DyeColorTiming dyeColorData = cacher.getDyeColorData(livingEntity);
        if (textureData == null && dyeColorData == null) {
            Translate.commandError(context, "no_texture_or_dye_color_data");
            return;
        }
        if (textureData != null) {
            textureData.log();
            ScreenSchedulerAndSaver.schedule(new RenderScreen(new ItemFrameBasedRenderable(targetEntity.getUUID(), textureData::getTextureTimings)));
        } else {
            dyeColorData.log();
            ScreenSchedulerAndSaver.schedule(new RenderScreen(new DyedArmorFrameBasedRenderable(targetEntity.getUUID(), dyeColorData::getColorTimings)));
        }

    }

    public static Entity getClosestHit(Player source, AttackRange attackRange) {
        Collection<EntityHitResult> collection = getHitEntitiesAlong(source, attackRange);

        Entity closestEntity = null;
        Vec3 eyePosition = source.getEyePosition(1);

        double lowestDistance = Double.MAX_VALUE;
        for (EntityHitResult hitEntity : collection) {
            double distance = eyePosition.distanceToSqr(hitEntity.getLocation());
            if (distance < lowestDistance) {
                lowestDistance = distance;
                closestEntity = hitEntity.getEntity();
            }
        }

        return closestEntity;
    }

    private static Collection<EntityHitResult> getHitEntitiesAlong(Player playerSource, AttackRange attackRange) {
        Vec3 headLookAngle = playerSource.getHeadLookAngle();
        Vec3 eyePosition = playerSource.getEyePosition();
        Vec3 from = eyePosition.add(headLookAngle.scale(attackRange.effectiveMinRange(playerSource)));
        double movementComponent = playerSource.getKnownMovement().dot(headLookAngle);
        Vec3 to = eyePosition.add(headLookAngle.scale(attackRange.effectiveMaxRange(playerSource) + Math.max(0.0, movementComponent)));
        return getHitEntitiesAlong(playerSource, eyePosition, from, to, attackRange.hitboxMargin());
    }

    private static Collection<EntityHitResult> getHitEntitiesAlong(Player playerSource, Vec3 origin, Vec3 from, Vec3 to, float entityMargin) {
        Level level = playerSource.level();
        BlockHitResult blockHitResult = level.clip(new ClipContext(origin, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, playerSource));
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            Vec3 hitPosition = blockHitResult.getLocation();

            // the traverse loop ends right before it hits a block, so if you're looking at the ground for instance, it'll hit the air at y=#.0 right above it
            Vec3 direction = to.subtract(from).normalize().multiply(0.1, 0.1, 0.1);
            Vec3 avoidedPosition = hitPosition.add(direction);
            BlockPos avoidedBlockPosition = BlockPos.containing(avoidedPosition.x, avoidedPosition.y, avoidedPosition.z);
            BlockState blockState = playerSource.level().getBlockState(avoidedBlockPosition);

            if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
                to = hitPosition; // prevent entities past the location from being targeted
                if (origin.distanceToSqr(hitPosition) < origin.distanceToSqr(from)) {
                    return null; // block hit
                }
            }
        }

        AABB searchArea = AABB.ofSize(from, entityMargin, entityMargin, entityMargin).expandTowards(to.subtract(from)).inflate(1.0);
        return getManyEntityHitResult(level, playerSource, from, to, searchArea);
    }

    public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Player source, Vec3 from, Vec3 to, AABB targetSearchArea) {
        List<EntityHitResult> collector = new ArrayList<>();

        AABB expandedTargetSearchArea = new AABB(
                targetSearchArea.minX - 3, targetSearchArea.minY - 3, targetSearchArea.minZ - 3,
                targetSearchArea.maxX + 3, targetSearchArea.maxY + 3, targetSearchArea.maxZ + 3
        );

        for (Entity entity : ((LevelAccessor) level).wikirenderer$getEntities().getAll()) {
            if (entity instanceof EnderDragonPart) continue; // crash fix
            if (entity == source) continue;

            // accurate enough check to remove most entities without instead checking for the more expensive rendered bounding box data
            if (!expandedTargetSearchArea.contains(entity.position())) continue;

            EntityVertexBounds entitySubBounds = EntityRenderBoundsUtil.getPositionOffsetBasedBounds(entity);
            if (entitySubBounds == null) continue;

            for (AABB bound : entitySubBounds.getClipBounds()) {
                Optional<Vec3> exactHit = bound.clip(from, to);
                if (exactHit.isPresent()) {
                    collector.add(new EntityHitResult(entity, bound.getCenter()));
                    break;
                }
            }

        }

        return collector;
    }
}
