package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dcshadow.dev.vankka.mcdiscordreserializer.discord.DiscordSerializer;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.minecraftCommands.MCSubCommand;
import de.erdbeerbaerlp.dcintegration.common.minecraftCommands.McCommandRegistry;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.regex.Pattern;

@Mixin(CommandManager.class)
public class CommandManagerMixin {

    @Inject(method = "execute", cancellable = true, at = @At("HEAD"))
    public void execute(ServerCommandSource commandSource, String command, CallbackInfoReturnable<Integer> cir){

        command = command.replaceFirst(Pattern.quote("/"), "");
        if (!Configuration.instance().commandLog.channelID.equals("0")) {
            if (!ArrayUtils.contains(Configuration.instance().commandLog.ignoredCommands, command.split(" ")[0]))
                DiscordIntegration.INSTANCE.sendMessage(Configuration.instance().commandLog.message
                        .replace("%sender%", commandSource.getName())
                        .replace("%cmd%", command)
                        .replace("%cmd-no-args%", command.split(" ")[0]), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().commandLog.channelID));
        }
        if (DiscordIntegration.INSTANCE != null) {
            boolean raw = false;

            if (((command.startsWith("say")) && Configuration.instance().messages.sendOnSayCommand) || (command.startsWith("me") && Configuration.instance().messages.sendOnMeCommand)) {
                String msg = command.replace("say ", "");
                if (command.startsWith("say"))
                    msg = msg.replaceFirst("say ", "");
                if (command.startsWith("me")) {
                    raw = true;
                    msg = "*" + MessageUtils.escapeMarkdown(msg.replaceFirst("me ", "").trim()) + "*";
                }
                final Entity sourceEntity = commandSource.getEntity();
                DiscordIntegration.INSTANCE.sendMessage(commandSource.getName(), sourceEntity != null ? sourceEntity.getUuid().toString() : "0000000", new DiscordMessage(null, msg, !raw), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.chatOutputChannelID));
            }

            if(command.startsWith("tellraw ") && !Configuration.instance().messages.tellrawSelector.isBlank()){
                final String[] args = command.replace("tellraw ", "").replace("dc ", "").split(" ");
                if(args[0].equals(Configuration.instance().messages.tellrawSelector)){
                    DiscordIntegration.INSTANCE.sendMessage(DiscordSerializer.INSTANCE.serialize(GsonComponentSerializer.gson().deserialize(command.replace("tellraw " + args[0], ""))));
                }
            }

            if (command.startsWith("discord ") || command.startsWith("dc ")) {
                final String[] args = command.replace("discord ", "").replace("dc ", "").split(" ");
                for (MCSubCommand mcSubCommand : McCommandRegistry.getCommands()) {
                    if (args[0].equals(mcSubCommand.getName())) {
                        final String[] cmdArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                        switch (mcSubCommand.getType()) {
                            case CONSOLE_ONLY:
                                try {
                                    commandSource.getPlayer();
                                    commandSource.sendError(new LiteralText(Localization.instance().commands.consoleOnly));
                                } catch (CommandSyntaxException e) {
                                    final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, null));
                                    commandSource.sendFeedback(Text.Serializer.fromJson(txt), false);
                                }
                                break;
                            case PLAYER_ONLY:
                                try {
                                    final ServerPlayerEntity player = commandSource.getPlayer();
                                    if (!mcSubCommand.needsOP()) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        commandSource.sendFeedback(Text.Serializer.fromJson(txt), false);
                                    } else if (commandSource.hasPermissionLevel(4)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        commandSource.sendFeedback(Text.Serializer.fromJson(txt), false);
                                    } else {
                                        commandSource.sendError(new LiteralText(Localization.instance().commands.noPermission));
                                    }
                                } catch (CommandSyntaxException e) {
                                    commandSource.sendError(new LiteralText(Localization.instance().commands.ingameOnly));

                                }
                                break;
                            case BOTH:
                                try {
                                    final ServerPlayerEntity player = commandSource.getPlayer();
                                    if (!mcSubCommand.needsOP()) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        commandSource.sendFeedback(Text.Serializer.fromJson(txt), false);
                                    } else if (commandSource.hasPermissionLevel(4)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        commandSource.sendFeedback(Text.Serializer.fromJson(txt), false);
                                    } else {
                                        commandSource.sendError(new LiteralText(Localization.instance().commands.noPermission));
                                    }
                                } catch (CommandSyntaxException e) {
                                    final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, null));
                                    commandSource.sendFeedback(Text.Serializer.fromJson(txt), false);
                                }
                                break;
                        }
                    }
                    cir.setReturnValue(1);
                }
            }
        }
    }
}
