package com.freecam;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public class FreecamClient implements ClientModInitializer {
    public static boolean active = false;

    // Kecepatan kamera (blok per tick) dan jangkauan pasang/hancurkan (blok)
    private static final double SPEED = 0.6;
    private static final double REACH = 64.0;

    private static Vec3d pos = Vec3d.ZERO;
    private static Vec3d prevPos = Vec3d.ZERO;
    private static BlockHitResult target = null;
    private static ClientPlayerEntity savedPlayer = null;
    private static Input savedInput = null;
    private static float savedYaw;
    private static float savedPitch;
    private static KeyBinding toggleKey;

    public static Vec3d getPos(float tickDelta) {
        return new Vec3d(
            MathHelper.lerp(tickDelta, prevPos.x, pos.x),
            MathHelper.lerp(tickDelta, prevPos.y, pos.y),
            MathHelper.lerp(tickDelta, prevPos.z, pos.z));
    }

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("Toggle Freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "Freecam"));
        ClientTickEvents.START_CLIENT_TICK.register(FreecamClient::onTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FreecamClient::onRender);
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outlineContext) -> !active);
    }

    private static void reset() {
        active = false;
        target = null;
        savedPlayer = null;
        savedInput = null;
    }

    private static void start(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        savedPlayer = player;
        savedInput = player.input;
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        player.input = new Input();
        pos = player.getCameraPosVec(1.0f);
        prevPos = pos;
        active = true;
        player.sendMessage(new LiteralText("Freecam: ON"), true);
    }

    private static void stop(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && savedInput != null) {
            player.input = savedInput;
            player.setYaw(savedYaw);
            player.setPitch(savedPitch);
            player.sendMessage(new LiteralText("Freecam: OFF"), true);
        }
        reset();
    }

    private static double axis(boolean plus, boolean minus) {
        return (plus ? 1.0 : 0.0) - (minus ? 1.0 : 0.0);
    }

    private static void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (active && (player == null || player != savedPlayer || client.world == null)) {
            reset();
        }

        while (toggleKey.wasPressed()) {
            if (player == null) {
                continue;
            }
            if (active) {
                stop(client);
            } else {
                start(client);
            }
        }

        if (!active || player == null) {
            return;
        }

        GameOptions options = client.options;
        Vec3d look = Vec3d.fromPolar(player.getPitch(), player.getYaw());

        // Cari blok yang dituju dari posisi kamera
        Vec3d end = pos.add(look.multiply(REACH));
        BlockHitResult hit = client.world.raycast(new RaycastContext(pos, end,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        target = hit.getType() == HitResult.Type.BLOCK ? hit : null;

        // Ambil klik hancurkan/pakai supaya tidak dipakai karakter
        boolean attack = false;
        while (options.attackKey.wasPressed()) {
            attack = true;
        }
        boolean use = false;
        while (options.useKey.wasPressed()) {
            use = true;
        }
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);

        if (client.currentScreen == null && target != null) {
            if (attack) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(target.getBlockPos());
                ClientPlayNetworking.send(Freecam.BREAK_PACKET, buf);
            }
            if (use) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockHitResult(target);
                ClientPlayNetworking.send(Freecam.USE_PACKET, buf);
            }
        }

        // Gerakkan kamera
        double forward = axis(options.forwardKey.isPressed(), options.backKey.isPressed());
        double strafe = axis(options.rightKey.isPressed(), options.leftKey.isPressed());
        double vertical = axis(options.jumpKey.isPressed(), options.sneakKey.isPressed());
        Vec3d right = Vec3d.fromPolar(0.0f, player.getYaw() + 90.0f);
        Vec3d move = look.multiply(forward).add(right.multiply(strafe)).add(0.0, vertical, 0.0);

        prevPos = pos;
        if (move.lengthSquared() > 1.0E-6) {
            double speed = options.sprintKey.isPressed() ? SPEED * 3.0 : SPEED;
            pos = pos.add(move.normalize().multiply(speed));
        }
    }

    private static void onRender(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (!active || target == null) {
            return;
        }
        Vec3d cam = context.camera().getPos();
        Box box = new Box(target.getBlockPos()).offset(-cam.x, -cam.y, -cam.z).expand(0.002);
        MatrixStack matrices = context.matrixStack();

        RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
        RenderSystem.lineWidth(3.0f);
        RenderSystem.disableDepthTest();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        WorldRenderer.drawBox(matrices, buffer, box, 1.0f, 1.0f, 0.2f, 1.0f);
        Tessellator.getInstance().draw();
        RenderSystem.enableDepthTest();
    }
}
