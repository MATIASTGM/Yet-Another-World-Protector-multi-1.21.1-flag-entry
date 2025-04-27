package de.z0rdak.yawp.mixin.flag.player;

import static de.z0rdak.yawp.api.MessageSender.sendFlagMsg;
import static de.z0rdak.yawp.core.flag.RegionFlag.ENTRY;

import de.z0rdak.yawp.api.FlagEvaluator;
import de.z0rdak.yawp.api.events.region.FlagCheckEvent;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
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


@Mixin(Entity.class)
public abstract class EntityMoveMixin {

    @Inject(
            method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blockEntry(MoverType type, Vec3 delta, CallbackInfo ci) {
        // 1) só players no servidor
        if (type != MoverType.PLAYER) return;
        Entity self = (Entity)(Object)this;
        if (!(self instanceof ServerPlayer player)) return;
        if (!HandlerUtil.isServerSide(player.level())) return;

        // 2) guarda posição antes
        double oldX = player.getX(),
                oldY = player.getY(),
                oldZ = player.getZ();

        // 3) calcula destino
        BlockPos target = new BlockPos(
                Mth.floor(oldX + delta.x),
                Mth.floor(oldY + delta.y),
                Mth.floor(oldZ + delta.z)
        );

        // 4) encontra a região e checa membro
        IProtectedRegion region = FlagEvaluator.findResponsibleRegion(target, HandlerUtil.getDimKey(player));
        if (region instanceof IMarkableRegion markable && !markable.permits(player)) {
            // pega a mensagem da flag ENTRY (caso customizada) ou usa fallback
            IFlag entryFlag = region.getFlag(RegionFlag.ENTRY.name);
            String msg = entryFlag != null
                    ? entryFlag.getFlagMsg().msg()
                    : "You can't come in here!";
            player.displayClientMessage(Component.literal(msg), true);

            // cancela e devolve o player um bloco pra trás
            ci.cancel();
            player.teleportTo(oldX, oldY, oldZ);
            return;
        }

        // 5) se for membro, processa normalmente a flag ENTRY
        FlagCheckEvent evt = new FlagCheckEvent(target, ENTRY, HandlerUtil.getDimKey(player), player);
        if (Services.EVENT.post(evt)) return;
        FlagEvaluator.processCheck(evt, deny -> {
            sendFlagMsg(deny);
            ci.cancel();
            player.teleportTo(oldX, oldY, oldZ);
        });
    }
}

