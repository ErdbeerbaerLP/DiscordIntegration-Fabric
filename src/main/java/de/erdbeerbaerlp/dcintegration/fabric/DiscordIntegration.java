package de.erdbeerbaerlp.dcintegration.fabric;

import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.Discord;
import de.erdbeerbaerlp.dcintegration.common.storage.CommandRegistry;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.*;
import de.erdbeerbaerlp.dcintegration.fabric.api.FabricDiscordEventHandler;
import de.erdbeerbaerlp.dcintegration.fabric.command.McCommandDiscord;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricServerInterface;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static de.erdbeerbaerlp.dcintegration.common.util.Variables.LOGGER;
import static de.erdbeerbaerlp.dcintegration.common.util.Variables.discord_instance;

public class DiscordIntegration implements DedicatedServerModInitializer {
    /**
     * Modid
     */
    public static final String MODID = "dcintegration";
    /**
     * Contains timed-out player UUIDs, gets filled in MixinNetHandlerPlayServer
     */
    public static final ArrayList<UUID> timeouts = new ArrayList<>();
    public static boolean stopped = false;

    public static SignedMessage handleChatMessage(SignedMessage message, ServerPlayerEntity player) {
        if (discord_instance == null) return message;
        if (PlayerLinkController.getSettings(null, player.getUuid()).hideFromDiscord) {
            return message;
        }

        final SignedMessage finalMessage = message;
        if (discord_instance.callEvent((e) -> {
            if (e instanceof FabricDiscordEventHandler) {
                return ((FabricDiscordEventHandler) e).onMcChatMessage(finalMessage.getContent(), player);
            }
            return false;
        })) {
            return message;
        }
        final String text = MessageUtils.escapeMarkdown(message.getContent().getString());
        final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(message.getContent());
        if (discord_instance != null) {
            final StandardGuildMessageChannel channel = discord_instance.getChannel(Configuration.instance().advanced.chatOutputChannelID);
            if (channel == null) {
                return message;
            }
            discord_instance.sendMessage(FabricMessageUtils.formatPlayerName(player), player.getUuid().toString(), new DiscordMessage(embed, text, true), channel);
            final String json = Text.Serializer.toJson(message.getContent());
            final Component comp = GsonComponentSerializer.gson().deserialize(json);
            final String editedJson = GsonComponentSerializer.gson().serialize(MessageUtils.mentionsToNames(comp, channel.getGuild()));
            final MutableText txt = Text.Serializer.fromJson(editedJson);
            //message = message.withUnsignedContent(txt);
            message = SignedMessage.ofUnsigned(txt.getString());
        }
        return message;
    }

    @Override
    public void onInitializeServer() {
        try {
            Discord.loadConfigs();
            ServerLifecycleEvents.SERVER_STARTED.register(this::serverStarted);
            if (!Configuration.instance().general.botToken.equals("INSERT BOT TOKEN HERE")) {
                ServerLifecycleEvents.SERVER_STARTING.register(this::serverStarting);
                ServerLifecycleEvents.SERVER_STOPPED.register(this::serverStopped);
                ServerLifecycleEvents.SERVER_STOPPING.register(this::serverStopping);
            } else {
                System.err.println("Please check the config file and set an bot token");
            }
        } catch (IOException e) {
            System.err.println("Config loading failed");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            System.err.println("Failed to read config file! Please check your config file!\nError description: " + e.getMessage());
            System.err.println("\nStacktrace: ");
            e.printStackTrace();
        }
    }

    private void serverStarting(MinecraftServer minecraftServer) {
        discord_instance = new Discord(new FabricServerInterface(minecraftServer));
        try {
            //Wait a short time to allow JDA to get initiaized
            System.out.println("Waiting for JDA to initialize to send starting message... (max 5 seconds before skipping)");
            for (int i = 0; i <= 5; i++) {
                if (discord_instance.getJDA() == null) Thread.sleep(1000);
                else break;
            }
            if (discord_instance.getJDA() != null) {
                Thread.sleep(2000); //Wait for it to cache the channels
                if (!Localization.instance().serverStarting.isEmpty()) {
                    CommandRegistry.registerDefaultCommandsFromConfig();
                    if (discord_instance.getChannel() != null)
                        Variables.startingMsg = discord_instance.sendMessageReturns(Localization.instance().serverStarting, discord_instance.getChannel(Configuration.instance().advanced.serverChannelID));
                }
            }
        } catch (InterruptedException | NullPointerException ignored) {
        }
        new McCommandDiscord(minecraftServer.getCommandManager().getDispatcher());
    }

    private void serverStarted(MinecraftServer minecraftServer) {
        System.out.println("Started");
        if (discord_instance != null) {
        Variables.started = new Date().getTime();
            if (Variables.startingMsg != null) {
                Variables.startingMsg.thenAccept((a) -> a.editMessage(Localization.instance().serverStarted).queue());
            } else discord_instance.sendMessage(Localization.instance().serverStarted);
            discord_instance.startThreads();
        }
        UpdateChecker.runUpdateCheck("https://raw.githubusercontent.com/ErdbeerbaerLP/Discord-Integration-Fabric/1.19/update-checker.json");
        if (!DownloadSourceChecker.checkDownloadSource(new File(DiscordIntegration.class.getProtectionDomain().getCodeSource().getLocation().getPath().split("%")[0]))) {
            LOGGER.warn("You likely got this mod from a third party website.");
            LOGGER.warn("Some of such websites are distributing malware or old versions.");
            LOGGER.warn("Download this mod from an official source (https://www.curseforge.com/minecraft/mc-mods/dcintegration) to hide this message");
            LOGGER.warn("This warning can also be suppressed in the config file");
        }
    }

    private void serverStopping(MinecraftServer minecraftServer) {
        if (discord_instance != null) {
            discord_instance.sendMessage(Localization.instance().serverStopped);
            discord_instance.stopThreads();
        }
        this.stopped = true;
    }

    private void serverStopped(MinecraftServer minecraftServer) {
        if (discord_instance != null) {
            if (!stopped && discord_instance.getJDA() != null) minecraftServer.execute(() -> {
                discord_instance.stopThreads();
                try {
                    discord_instance.sendMessageReturns(Localization.instance().serverCrash, discord_instance.getChannel(Configuration.instance().advanced.serverChannelID)).get();
                } catch (InterruptedException | ExecutionException ignored) {
                }
            });
            discord_instance.kill();
        }
    }

}
