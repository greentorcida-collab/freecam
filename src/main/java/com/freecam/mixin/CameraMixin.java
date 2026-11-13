package com.freecam.mixin;

import com.freecam.FreecamClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void freecam$update(BlockView area, Entity focusedEntity, boolean third,
                                boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (FreecamClient.active) {
            Vec3d p = FreecamClient.getPos(tickDelta);
            this.setPos(p.x, p.y, p.z);
            this.thirdPerson = true;
        }
    }
}
