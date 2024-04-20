package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import com.mojang.brigadier.ParseResults;
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
import de.erdbeerbaerlp.dcintegration.common.util.MinecraftPermission;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricServerInterface;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
    public void execute(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfoReturnable<Integer> ci) {
        final ServerCommandSource source = parseResults.getContext().getSource();
        String name = source.getName();
        command = command.replaceFirst(Pattern.quote("/"), "");
        if (DiscordIntegration.INSTANCE != null) {
            if (!Configuration.instance().commandLog.channelID.equals("0")) {
                if ((!Configuration.instance().commandLog.commandWhitelist && !ArrayUtils.contains(Configuration.instance().commandLog.ignoredCommands, command.split(" ")[0])) ||
                        (Configuration.instance().commandLog.commandWhitelist && ArrayUtils.contains(Configuration.instance().commandLog.ignoredCommands, command.split(" ")[0])))
                    DiscordIntegration.INSTANCE.sendMessage(Configuration.instance().commandLog.message
                            .replace("%sender%", name)
                            .replace("%cmd%", command)
                            .replace("%cmd-no-args%", command.split(" ")[0]), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().commandLog.channelID));
            }
            boolean raw = false;
            if (((command.startsWith("say")) && Configuration.instance().messages.sendOnSayCommand) || (command.startsWith("me") && Configuration.instance().messages.sendOnMeCommand)) {
                String msg = command.replace("say ", "");
                if (command.startsWith("say"))
                    msg = msg.replaceFirst("say ", "");
                if (command.startsWith("me")) {
                    raw = true;
                    msg = "*" + MessageUtils.escapeMarkdown(msg.replaceFirst("me ", "").trim()) + "*";
                }


                if(Configuration.instance().webhook.enable && name.equals("Rcon") && Configuration.instance().webhook.useServerNameForRcon){
                    name = Configuration.instance().webhook.serverName;
                }else if(Configuration.instance().webhook.enable && name.equals("Server") && Configuration.instance().webhook.useServerNameForConsole){
                    name = Configuration.instance().webhook.serverName;
                }
                final Entity sourceEntity = source.getEntity();

                DiscordIntegration.INSTANCE.sendMessage(name, sourceEntity != null ? sourceEntity.getUuid().toString() : "0000000", new DiscordMessage(null, msg, !raw), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.chatOutputChannelID));
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
                                    source.getPlayerOrThrow();
                                    source.sendError(Text.literal(Localization.instance().commands.consoleOnly));
                                } catch (CommandSyntaxException e) {
                                    final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, null));
                                    source.sendFeedback( Text.Serializer.fromJson(txt), false);
                                }
                                break;
                            case PLAYER_ONLY:
                                try {
                                    final ServerPlayerEntity player = source.getPlayerOrThrow();
                                    if (!mcSubCommand.needsOP() && ((FabricServerInterface) DiscordIntegration.INSTANCE.getServerInterface()).playerHasPermissions(player, MinecraftPermission.RUN_DISCORD_COMMAND, MinecraftPermission.USER)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        source.sendFeedback(Text.Serializer.fromJson(txt), false);
                                    } else if (((FabricServerInterface) DiscordIntegration.INSTANCE.getServerInterface()).playerHasPermissions(player, MinecraftPermission.RUN_DISCORD_COMMAND_ADMIN)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        source.sendFeedback( Text.Serializer.fromJson(txt), false);
                                    } else if (source.hasPermissionLevel(4)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        source.sendFeedback( Text.Serializer.fromJson(txt), false);
                                    } else {
                                        source.sendError(Text.literal(Localization.instance().commands.noPermission));
                                    }
                                } catch (CommandSyntaxException e) {
                                    source.sendError(Text.literal(Localization.instance().commands.ingameOnly));

                                }
                                break;
                            case BOTH:
                                try {
                                    final ServerPlayerEntity player = source.getPlayerOrThrow();
                                    if (!mcSubCommand.needsOP() && ((FabricServerInterface) DiscordIntegration.INSTANCE.getServerInterface()).playerHasPermissions(player, MinecraftPermission.RUN_DISCORD_COMMAND, MinecraftPermission.USER)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        source.sendFeedback(Text.Serializer.fromJson(txt), false);
                                    } else if (((FabricServerInterface) DiscordIntegration.INSTANCE.getServerInterface()).playerHasPermissions(player, MinecraftPermission.RUN_DISCORD_COMMAND_ADMIN)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        source.sendFeedback( Text.Serializer.fromJson(txt), false);
                                    } else if (source.hasPermissionLevel(4)) {
                                        final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, player.getUuid()));
                                        source.sendFeedback( Text.Serializer.fromJson(txt), false);
                                    } else {
                                        source.sendError(Text.literal(Localization.instance().commands.noPermission));
                                    }
                                } catch (CommandSyntaxException e) {
                                    final String txt = GsonComponentSerializer.gson().serialize(mcSubCommand.execute(cmdArgs, null));
                                    source.sendFeedback( Text.Serializer.fromJson(txt), false);
                                }
                                break;
                        }
                    }
                }
                ci.cancel();
            }
        }
    }
}
