package de.erdbeerbaerlp.dcintegration.fabric;

import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.addon.AddonLoader;
import de.erdbeerbaerlp.dcintegration.common.addon.DiscordAddonMeta;
import de.erdbeerbaerlp.dcintegration.common.storage.CommandRegistry;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.*;
import de.erdbeerbaerlp.dcintegration.fabric.api.FabricDiscordEventHandler;
import de.erdbeerbaerlp.dcintegration.fabric.bstats.Metrics;
import de.erdbeerbaerlp.dcintegration.fabric.command.McCommandDiscord;
import de.erdbeerbaerlp.dcintegration.fabric.util.CompatibilityUtils;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricServerInterface;
import eu.pb4.styledchat.StyledChatEvents;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.INSTANCE;
import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.LOGGER;

public class DiscordIntegrationMod implements DedicatedServerModInitializer {
    /**
     * Modid
     */
    public static final String MODID = "dcintegration";
    /**
     * Contains timed-out player UUIDs, gets filled in MixinNetHandlerPlayServer
     */
    public static final ArrayList<UUID> timeouts = new ArrayList<>();
    public static boolean stopped = false;

    public static Text handleChatMessage(Text messageIn, ServerPlayerEntity player, Function<ServerPlayerEntity, Text> playerMessageFactory) {
        if (DiscordIntegration.INSTANCE == null) return messageIn;
        if (!((FabricServerInterface)DiscordIntegration.INSTANCE.getServerInterface()).playerHasPermissions(player, MinecraftPermission.SEMD_MESSAGES, MinecraftPermission.USER))
            return messageIn;
        if (LinkManager.isPlayerLinked(player.getUuid()) && LinkManager.getLink(null, player.getUuid()).settings.hideFromDiscord) {
            return messageIn;
        }

        final Text finalMessage;
        if (messageIn instanceof TranslatableText) {
            finalMessage = new LiteralText((String)((TranslatableText) messageIn).getArgs()[1]);
        } else finalMessage = messageIn;
        if (DiscordIntegration.INSTANCE.callEvent((e) -> {
            if (e instanceof FabricDiscordEventHandler) {
                return ((FabricDiscordEventHandler) e).onMcChatMessage(finalMessage, player);
            }
            return false;
        })) {
            return messageIn;
        }
        final String text = MessageUtils.escapeMarkdown(finalMessage.getString());
        final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(messageIn);
        if (DiscordIntegration.INSTANCE != null) {
            final GuildMessageChannel channel = DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.chatOutputChannelID);
            if (channel == null) {
                return messageIn;
            }
            final String json = Text.Serializer.toJson(message.getContent());
            final Component comp = GsonComponentSerializer.gson().deserialize(json);
            if(INSTANCE.callEvent((e)->e.onMinecraftMessage(comp, player.getUuid()))){
                return message;
            }
            if (!Localization.instance().discordChatMessage.isBlank())
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.chatMessages.asEmbed) {
                    final String avatarURL = Configuration.instance().webhook.playerAvatarURL.replace("%uuid%", player.getUuid().toString()).replace("%uuid_dashless%", player.getUuid().toString().replace("-", "")).replace("%name%", player.getName().getString()).replace("%randomUUID%", UUID.randomUUID().toString());
                    if (!Configuration.instance().embedMode.chatMessages.customJSON.isBlank()) {
                        final EmbedBuilder b = Configuration.instance().embedMode.chatMessages.toEmbedJson(Configuration.instance().embedMode.chatMessages.customJSON
                                .replace("%uuid%", player.getUuid().toString())
                                .replace("%uuid_dashless%", player.getUuid().toString().replace("-", ""))
                                .replace("%name%", FabricMessageUtils.formatPlayerName(player))
                                .replace("%randomUUID%", UUID.randomUUID().toString())
                                .replace("%avatarURL%", avatarURL)
                                .replace("%msg%", text)
                                .replace("%playerColor%", "" + TextColors.generateFromUUID(player.getUuid()).getRGB())
                        );
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.chatOutputChannelID));
                    } else {
                        EmbedBuilder b = Configuration.instance().embedMode.chatMessages.toEmbed();
                        if (Configuration.instance().embedMode.chatMessages.generateUniqueColors)
                            b = b.setColor(TextColors.generateFromUUID(player.getUuid()));
                        b = b.setAuthor(FabricMessageUtils.formatPlayerName(player), null, avatarURL)
                                .setDescription(text);
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.chatOutputChannelID));
                    }
                } else
                    DiscordIntegration.INSTANCE.sendMessage(FabricMessageUtils.formatPlayerName(player), player.getUuid().toString(), new DiscordMessage(embed, text, true), channel);

            if (!Configuration.instance().compatibility.disableParsingMentionsIngame) {
                final String editedJson = GsonComponentSerializer.gson().serialize(MessageUtils.mentionsToNames(comp, channel.getGuild()));
                final MutableText txt = Text.Serializer.fromJson(editedJson);
                messageIn = Text.Serializer.fromJson(editedJson);
            }
        }
        return messageIn;
    }

    private Text styledChat(Text txt, ServerPlayerEntity player, boolean filtered) {
        if ((LinkManager.isPlayerLinked(player.getUuid()) && LinkManager.getLink(null, player.getUuid()).settings.hideFromDiscord) || filtered) {
            return txt;
        }

        Text finalTxt = txt;
        boolean cancelled = DiscordIntegration.INSTANCE.callEvent((e) -> {
            if (e instanceof FabricDiscordEventHandler) {
                return ((FabricDiscordEventHandler) e).onMcChatMessage(finalTxt, player);
            }
            return false;
        });

        if (cancelled) {
            return txt;
        }

        String messageText = MessageUtils.escapeMarkdown(txt.getString());
        final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(txt);
        if (DiscordIntegration.INSTANCE != null) {
            GuildMessageChannel channel = DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.chatOutputChannelID);
            if (channel == null) {
                return txt;
            }
            DiscordIntegration.INSTANCE.sendMessage(FabricMessageUtils.formatPlayerName(player), player.getUuid().toString(), new DiscordMessage(embed, messageText, true), channel);

            final String json = Text.Serializer.toJson(txt);
            Component comp = GsonComponentSerializer.gson().deserialize(json);
            final String editedJson = GsonComponentSerializer.gson().serialize(MessageUtils.mentionsToNames(comp, channel.getGuild()));

            txt = Text.Serializer.fromJson(editedJson);
        }

        return txt;
    }

    public static Metrics bstats;
    @Override
    public void onInitializeServer() {

        bstats = new Metrics(9765);

        try {
            DiscordIntegration.loadConfigs();
            ServerLifecycleEvents.SERVER_STARTED.register(this::serverStarted);
            if (!Configuration.instance().general.botToken.equals("INSERT BOT TOKEN HERE")) {
                ServerLifecycleEvents.SERVER_STARTING.register(this::serverStarting);
                ServerLifecycleEvents.SERVER_STOPPED.register(this::serverStopped);
                ServerLifecycleEvents.SERVER_STOPPING.register(this::serverStopping);
                if (CompatibilityUtils.styledChatLoaded()) {
                    StyledChatEvents.MESSAGE_CONTENT_SEND.register(this::styledChat);
                }
            } else {
                DiscordIntegration.LOGGER.error("Please check the config file and set an bot token");
            }
        } catch (IOException e) {
            DiscordIntegration.LOGGER.error("Config loading failed");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            DiscordIntegration.LOGGER.error("Failed to read config file! Please check your config file!\nError description: " + e.getMessage());
            DiscordIntegration.LOGGER.error("\nStacktrace: ");
            e.printStackTrace();
        }
    }

    private void serverStarting(MinecraftServer minecraftServer) {
        DiscordIntegration.INSTANCE = new DiscordIntegration(new FabricServerInterface(minecraftServer));
        try {
            //Wait a short time to allow JDA to get initiaized
            DiscordIntegration.LOGGER.info("Waiting for JDA to initialize to send starting message... (max 5 seconds before skipping)");
            for (int i = 0; i <= 5; i++) {
                if (DiscordIntegration.INSTANCE.getJDA() == null) Thread.sleep(1000);
                else break;
            }
            if (DiscordIntegration.INSTANCE.getJDA() != null) {
                Thread.sleep(2000); //Wait for it to cache the channels
                CommandRegistry.registerDefaultCommands();
                if (!Localization.instance().serverStarting.isEmpty()) {

                    if (!Localization.instance().serverStarting.isBlank())
                        if (DiscordIntegration.INSTANCE.getChannel() != null) {
                            final MessageCreateData m;
                            if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.startMessages.asEmbed)
                                m = new MessageCreateBuilder().setEmbeds(Configuration.instance().embedMode.startMessages.toEmbed().setDescription(Localization.instance().serverStarting).build()).build();
                            else
                                m = new MessageCreateBuilder().addContent(Localization.instance().serverStarting).build();
                            DiscordIntegration.startingMsg = DiscordIntegration.INSTANCE.sendMessageReturns(m, DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                        }
                }
            }
        } catch (InterruptedException | NullPointerException ignored) {
        }
        new McCommandDiscord(minecraftServer.getCommandManager().getDispatcher());
    }

    private void serverStarted(MinecraftServer minecraftServer) {
        DiscordIntegration.LOGGER.info("Started");
        if (DiscordIntegration.INSTANCE != null) {
            DiscordIntegration.started = new Date().getTime();
            if (!Localization.instance().serverStarted.isBlank())
                if (DiscordIntegration.startingMsg != null) {
                    if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.startMessages.asEmbed) {
                        if (!Configuration.instance().embedMode.startMessages.customJSON.isBlank()) {
                            final EmbedBuilder b = Configuration.instance().embedMode.startMessages.toEmbedJson(Configuration.instance().embedMode.startMessages.customJSON);
                            DiscordIntegration.startingMsg.thenAccept((a) -> a.editMessageEmbeds(b.build()).queue());
                        } else
                            DiscordIntegration.startingMsg.thenAccept((a) -> a.editMessageEmbeds(Configuration.instance().embedMode.startMessages.toEmbed().setDescription(Localization.instance().serverStarted).build()).queue());
                    } else
                        DiscordIntegration.startingMsg.thenAccept((a) -> a.editMessage(Localization.instance().serverStarted).queue());
                } else {
                    if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.startMessages.asEmbed) {
                        if (!Configuration.instance().embedMode.startMessages.customJSON.isBlank()) {
                            final EmbedBuilder b = Configuration.instance().embedMode.startMessages.toEmbedJson(Configuration.instance().embedMode.startMessages.customJSON);
                            DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                        } else
                            DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(Configuration.instance().embedMode.startMessages.toEmbed().setDescription(Localization.instance().serverStarted).build()),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                    } else
                        DiscordIntegration.INSTANCE.sendMessage(Localization.instance().serverStarted,INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                }
            DiscordIntegration.INSTANCE.startThreads();
        }
        UpdateChecker.runUpdateCheck("https://raw.githubusercontent.com/ErdbeerbaerLP/Discord-Integration-Fabric/1.19.2/update-checker.json");
        if (!DownloadSourceChecker.checkDownloadSource(new File(DiscordIntegrationMod.class.getProtectionDomain().getCodeSource().getLocation().getPath().split("%")[0]))) {
            LOGGER.warn("You likely got this mod from a third party website.");
            LOGGER.warn("Some of such websites are distributing malware or old versions.");
            LOGGER.warn("Download this mod from an official source (https://www.curseforge.com/minecraft/mc-mods/dcintegration) to hide this message");
            LOGGER.warn("This warning can also be suppressed in the config file");
        }

        bstats.addCustomChart(new Metrics.DrilldownPie("addons", () -> {
            final Map<String, Map<String, Integer>> map = new HashMap<>();
            if (Configuration.instance().bstats.sendAddonStats) {  //Only send if enabled, else send empty map
                for (DiscordAddonMeta m : AddonLoader.getAddonMetas()) {
                    final Map<String, Integer> entry = new HashMap<>();
                    entry.put(m.getVersion(), 1);
                    map.put(m.getName(), entry);
                }
            }
            return map;
        }));
    }

    private void serverStopping(MinecraftServer minecraftServer) {
        Metrics.MetricsBase.scheduler.shutdownNow();
        if (DiscordIntegration.INSTANCE != null) {
            if (!Localization.instance().serverStopped.isBlank())
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.stopMessages.asEmbed) {
                    if (!Configuration.instance().embedMode.stopMessages.customJSON.isBlank()) {
                        final EmbedBuilder b = Configuration.instance().embedMode.stopMessages.toEmbedJson(Configuration.instance().embedMode.stopMessages.customJSON);
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                    } else
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(Configuration.instance().embedMode.stopMessages.toEmbed().setDescription(Localization.instance().serverStopped).build()));
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().serverStopped);
            DiscordIntegration.INSTANCE.stopThreads();
        }
        stopped = true;
    }

    private void serverStopped(MinecraftServer minecraftServer) {
        Metrics.MetricsBase.scheduler.shutdownNow();
        if (DiscordIntegration.INSTANCE != null) {
            if (!stopped && DiscordIntegration.INSTANCE.getJDA() != null) minecraftServer.execute(() -> {
                DiscordIntegration.INSTANCE.stopThreads();
                if (!Localization.instance().serverCrash.isBlank())
                    try {
                        if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.stopMessages.asEmbed) {
                            DiscordIntegration.INSTANCE.sendMessageReturns(new MessageCreateBuilder().addEmbeds(Configuration.instance().embedMode.stopMessages.toEmbed().setDescription(Localization.instance().serverCrash).build()).build(), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID)).get();
                        } else
                            DiscordIntegration.INSTANCE.sendMessageReturns(new MessageCreateBuilder().setContent(Localization.instance().serverCrash).build(), DiscordIntegration.INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID)).get();
                    } catch (InterruptedException | ExecutionException ignored) {
                    }
            });
            DiscordIntegration.INSTANCE.kill(false);
        }
    }

}
