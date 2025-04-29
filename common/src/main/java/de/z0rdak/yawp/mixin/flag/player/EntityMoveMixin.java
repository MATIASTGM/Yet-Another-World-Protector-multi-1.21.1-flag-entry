package de.z0rdak.yawp.mixin.flag.player;

import static de.z0rdak.yawp.api.MessageSender.sendFlagMsg;
import static de.z0rdak.yawp.core.flag.RegionFlag.ENTRY;

import de.z0rdak.yawp.api.FlagEvaluator;
import de.z0rdak.yawp.api.events.region.FlagCheckEvent;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.region.IMarkableRegion;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.handler.HandlerUtil;
import de.z0rdak.yawp.platform.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    @Inject(
            method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("RETURN"),
            cancellable = true
    )
    private void afterMove(MoverType type, Vec3 delta, CallbackInfo ci) {
        if (type != MoverType.PLAYER) return;
        Entity self = (Entity)(Object)this;
        if (!(self instanceof ServerPlayer player)) return;
        if (!HandlerUtil.isServerSide(player.level())) return;

        // posição antes do movimento
        double oldX = player.getX() - delta.x;
        double oldY = player.getY() - delta.y;
        double oldZ = player.getZ() - delta.z;

        // posição atual (dentro da região)
        BlockPos target = new BlockPos(
                Mth.floor(player.getX()),
                Mth.floor(player.getY()),
                Mth.floor(player.getZ())
        );

        IProtectedRegion region = FlagEvaluator.findResponsibleRegion(target, HandlerUtil.getDimKey(player));
        if (!(region instanceof IMarkableRegion)) return;

        // 1) requiredTag: bloqueia quem não tiver
        String requiredTag = region.getRequiredTag();
        if (requiredTag != null) {
            Set<String> tags = player.getTags();
            if (!tags.contains(requiredTag)) {
                IFlag entryFlag = region.getFlag(ENTRY.name);
                String msg = entryFlag != null
                        ? entryFlag.getFlagMsg().msg()
                        : "§cVocê não pode entrar aqui!";
                player.displayClientMessage(Component.literal(msg), true);
                player.teleportTo(oldX, oldY, oldZ);
                ci.cancel();
                return;
            } else {
                //  quem tiver a tag, já entra e para aqui
                return;
            }
        }

        // 2) sem requiredTag, aplica a flag ENTRY normal
        FlagCheckEvent evt = new FlagCheckEvent(target, ENTRY, HandlerUtil.getDimKey(player), player);
        if (Services.EVENT.post(evt)) return;
        FlagEvaluator.processCheck(evt, deny -> {
            sendFlagMsg(deny);
            player.teleportTo(oldX, oldY, oldZ);
            ci.cancel();
        });
    }
}
