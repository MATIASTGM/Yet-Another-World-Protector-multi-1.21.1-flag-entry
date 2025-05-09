package de.z0rdak.yawp.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.z0rdak.yawp.api.commands.CommandConstants;
import de.z0rdak.yawp.api.events.flag.FlagEvent;
import de.z0rdak.yawp.commands.arguments.ArgumentUtil;
import de.z0rdak.yawp.commands.arguments.flag.IFlagArgumentType;
import de.z0rdak.yawp.commands.arguments.region.RegionArgumentType;
import de.z0rdak.yawp.core.flag.FlagMessage;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.data.region.RegionDataManager;
import de.z0rdak.yawp.platform.Services;
import de.z0rdak.yawp.util.text.Messages;
import de.z0rdak.yawp.util.text.messages.multiline.MultiLineMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;




import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static de.z0rdak.yawp.api.commands.CommandConstants.*;
import static de.z0rdak.yawp.commands.arguments.ArgumentUtil.*;
import static de.z0rdak.yawp.util.ChatLinkBuilder.*;
import static de.z0rdak.yawp.api.MessageSender.sendCmdFeedback;

final class FlagCommands {

    private FlagCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal(FLAG)
                .then(literal(GLOBAL)
                        .executes(ctx -> CommandUtil.promptRegionFlagList(ctx, getGlobalRegion(), 0))
                        .then(flagSubCmd((ctx) -> getGlobalRegion())))
                .then(literal(DIM)
                        .then(flagDimSubCommands()))
                .then(literal(LOCAL)
                        .then(flagLocalSubCommands()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> flagDimSubCommands() {
        return Commands.argument(DIM.toString(), DimensionArgument.dimension())
                .executes(ctx -> CommandUtil.promptRegionFlagList(ctx, getDimCacheArgument(ctx).getDimensionalRegion(), 0))
                .then(flagSubCmd((ctx) -> getDimCacheArgument(ctx).getDimensionalRegion()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> flagLocalSubCommands() {
        return Commands.argument(DIM.toString(), DimensionArgument.dimension())
                .then(Commands.argument(CommandConstants.LOCAL.toString(), StringArgumentType.word())
                        .suggests((ctx, builder) -> RegionArgumentType.region().listSuggestions(ctx, builder))
                        .executes(ctx -> CommandUtil.promptRegionFlagList(ctx, getDimCacheArgument(ctx).getDimensionalRegion(), 0))
                        .then(flagSubCmd(ArgumentUtil::getRegionArgument))
                );
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> flagSubCmd(Function<CommandContext<CommandSourceStack>, IProtectedRegion> regionSupplier) {
        return Commands.argument(FLAG.toString(), StringArgumentType.word())
                .suggests((ctx, builder) -> IFlagArgumentType.flag().listSuggestions(ctx, builder))
                .executes(ctx -> promptFlagInfo(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx)))
                .then(literal(INFO)
                        .executes(ctx -> promptFlagInfo(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx)))
                )
                .then(literal(STATE)
                        .executes(ctx -> setFlagState(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx)))
                        .then(Commands.argument(STATE.toString(), StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FlagState.ValidFlagStates(), builder))
                                .executes(ctx -> setFlagState(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), getFlagStateArgument(ctx))))
                )
                .then(literal(OVERRIDE)
                        .executes(ctx -> setOverride(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx)))
                        .then(Commands.argument(OVERRIDE.toString(), BoolArgumentType.bool())
                                .executes(ctx -> setOverride(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), getOverrideArgument(ctx))))
                )
                .then(literal(MSG)

                        .then(literal(MUTE)
                                .executes(ctx -> setFlagMuteState(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx)))
                                .then(Commands.argument(MUTE.toString(), BoolArgumentType.bool())
                                        .executes(ctx -> setFlagMuteState(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), muteArgument(ctx))))
                        )
                        .then(literal(SET)
                                .then(Commands.argument(MSG.toString(), StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(flagMsgExamples(), builder))
                                        .executes(ctx -> setRegionFlagMsg(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), getFlagMsgArgument(ctx))))
                        )
                        .then(literal(CLEAR)
                                .executes(ctx -> setRegionFlagMsg(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), FlagMessage.CONFIG_MSG))
                        )
                );
    }

    private static List<String> flagMsgExamples() {
        final int amountOfExamples = 10;
        List<String> examples = new ArrayList<>(amountOfExamples);
        for (int i = 0; i < amountOfExamples; i++) {
            examples.add(Component.translatableWithFallback("cli.flag.msg.Component.example." + i, "<Your flag message here>").getString());
        }
        return examples;
    }

    private static int promptFlagInfo(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag) {
        if (flag == null) return 1;
        MultiLineMessage.send(ctx.getSource(), MultiLineMessage.flagDetail(region, flag));
        return 0;
    }

    private static int setFlagMuteState(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag regionFlag) {
        if (regionFlag == null) return 1;
        if (region.containsFlag(regionFlag.getName())) {
            IFlag flag = region.getFlag(regionFlag.getName());
            return setFlagMuteState(ctx, region, flag, !flag.getFlagMsg().isMuted());
        } else {
            MutableComponent hint = Component.translatableWithFallback("cli.msg.info.region.flag.add-hint", "Add flag by clicking: %s", buildSuggestAddFlagLink(region));
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.msg.info.region.flag.not-present", "Region %s does not contain flag '%s'. %",
                    buildRegionInfoLink(region), regionFlag.getName(), hint));
            return 1;
        }
    }

    private static int setFlagMuteState(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, boolean setMuted) {
        if (flag == null) return 1;
        flag.getFlagMsg().mute(setMuted);
        String muteState = flag.getFlagMsg().isMuted() ? "on" : "off";
        MutableComponent infoMsg = Component.translatableWithFallback("cli.flag.msg.mute.success.text", "Set mute state of %s to: '%s'",
                buildFlagInfoLink(region, flag), muteState);
        MutableComponent undoLink = buildRegionActionUndoLink(ctx.getInput(), String.valueOf(!setMuted), String.valueOf(setMuted));
        MutableComponent msg = Messages.substitutable("%s %s", infoMsg, undoLink);
        sendCmdFeedback(ctx.getSource(), msg);
        RegionDataManager.save();
        return 0;

    }

    private static int setRegionFlagMsg(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, String flagMsgStr) {
        if (flag == null) return 1;
        String oldFlagMsg = flag.getFlagMsg().msg();

        FlagEvent.UpdateFlagMessageEvent editMsgEvent = new FlagEvent.UpdateFlagMessageEvent(ctx.getSource(), region, flag, flagMsgStr);
        Services.EVENT.post(editMsgEvent);
        
        FlagMessage flagMsg = new FlagMessage(flagMsgStr, flag.getFlagMsg().isMuted());
        flag.setFlagMsg(flagMsg);
        MutableComponent infoMsg = Component.translatableWithFallback("cli.flag.msg.msg.success.text", "Set message of %s to: '%s'",
                buildFlagInfoLink(region, flag), flagMsgStr);
        MutableComponent undoLink = buildRegionActionUndoLink(ctx.getInput(), flagMsgStr, oldFlagMsg);
        MutableComponent msg = Messages.substitutable("%s %s", infoMsg, undoLink);
        sendCmdFeedback(ctx.getSource(), msg);
        RegionDataManager.save();
        return 0;
    }

    private static int setFlagState(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag regionFlag) {
        if (regionFlag == null) return 1;
        if (region.containsFlag(regionFlag.getName())) {
            IFlag flag = region.getFlag(regionFlag.getName());
            if (flag.getState() == FlagState.ALLOWED || flag.getState() == FlagState.DENIED) {
                return setFlagState(ctx, region, regionFlag, FlagState.invert(flag.getState()));
            }
            if (flag.getState() == FlagState.DISABLED) {
                return setFlagState(ctx, region, regionFlag, FlagState.DENIED);
            }
            return setFlagState(ctx, region, regionFlag, flag.getState());
        } else {
            MutableComponent hint = Component.translatableWithFallback("cli.msg.info.region.flag.add-hint", "Add flag by clicking: %s", buildSuggestAddFlagLink(region));
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.msg.info.region.flag.not-present", "Region %s does not contain flag '%s'. %",
                    buildRegionInfoLink(region), regionFlag.getName(), hint));
            return 1;
        }
    }

    private static int setFlagState(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, FlagState flagState) {
        if (flag == null) return 1;
        FlagState oldState = flag.getState();
        flag.setState(flagState);
        MutableComponent undoLink = buildRegionActionUndoLink(ctx.getInput(), flagState.name, oldState.name);
        MutableComponent infoMsg = Component.translatableWithFallback("cli.flag.state.success.text", "Set flag state of %s to: '%s'",
                buildFlagInfoLink(region, flag), flag.getState().name);
        MutableComponent msg = Messages.substitutable("%s %s", infoMsg, undoLink);
        sendCmdFeedback(ctx.getSource(), msg);
        RegionDataManager.save();
        return 0;

    }

    private static int setOverride(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag regionFlag) {
        if (regionFlag == null) return 1;
        if (region.containsFlag(regionFlag.getName())) {
            IFlag flag = region.getFlag(regionFlag.getName());
            return setOverride(ctx, region, flag, !flag.doesOverride());
        } else {
            MutableComponent hint = Component.translatableWithFallback("cli.msg.info.region.flag.add-hint", "Add flag by clicking: %s", buildSuggestAddFlagLink(region));
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.msg.info.region.flag.not-present", "Region %s does not contain flag '%s'. %",
                    buildRegionInfoLink(region), regionFlag.getName(), hint));
            return 1;
        }
    }

    private static int setOverride(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, boolean override) {
        if (flag == null) return 1;
        flag.setOverride(override);
        String overrideState = flag.doesOverride() ? "on" : "off";
        MutableComponent infoMsg = Component.translatableWithFallback("cli.flag.override.success.text", "Set flag override for %s to %s",
                buildFlagInfoLink(region, flag), overrideState);
        MutableComponent undoLink = buildRegionActionUndoLink(ctx.getInput(), String.valueOf(!override), String.valueOf(override));
        MutableComponent msg = Messages.substitutable("%s %s", infoMsg, undoLink);
        sendCmdFeedback(ctx.getSource(), msg);
        RegionDataManager.save();
        return 0;
    }

}
