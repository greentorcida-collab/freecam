package com.freecam;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class Freecam implements ModInitializer {
    public static final Identifier BREAK_PACKET = new Identifier("freecam", "break");
    public static final Identifier USE_PACKET = new Identifier("freecam", "use");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(BREAK_PACKET,
            (server, player, handler, buf, responseSender) -> {
                BlockPos pos = buf.readBlockPos();
                server.execute(() -> {
                    ServerWorld world = player.getServerWorld();
                    if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) {
                        return;
                    }
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.getHardness(world, pos) < 0) {
                        return;
                    }
                    world.breakBlock(pos, true, player);
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(USE_PACKET,
            (server, player, handler, buf, responseSender) -> {
                BlockHitResult hit = buf.readBlockHitResult();
                server.execute(() -> {
                    ServerWorld world = player.getServerWorld();
                    BlockPos pos = hit.getBlockPos();
                    if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) {
                        return;
                    }
                    ItemStack stack = player.getMainHandStack();
                    player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND, hit);
                });
            });
    }
}
