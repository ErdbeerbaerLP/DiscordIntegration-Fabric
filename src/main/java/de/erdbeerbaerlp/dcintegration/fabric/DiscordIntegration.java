package de.erdbeerbaerlp.dcintegration.fabric;

import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.Discord;
import de.erdbeerbaerlp.dcintegration.common.storage.CommandRegistry;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import de.erdbeerbaerlp.dcintegration.common.util.UpdateChecker;
import de.erdbeerbaerlp.dcintegration.common.util.Variables;
import de.erdbeerbaerlp.dcintegration.fabric.api.FabricDiscordEventHandler;
import de.erdbeerbaerlp.dcintegration.fabric.command.McCommandDiscord;
import de.erdbeerbaerlp.dcintegration.fabric.util.CompatibilityUtils;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricServerInterface;
import eu.pb4.styledchat.StyledChatEvents;
import me.bymartrixx.playerevents.api.event.CommandExecutionCallback;
import me.bymartrixx.playerevents.api.event.PlayerDeathCallback;
import me.bymartrixx.playerevents.api.event.PlayerJoinCallback;
import me.bymartrixx.playerevents.api.event.PlayerLeaveCallback;
import net.dv8tion.jda.api.entities.*;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

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
    private boolean stopped = false;
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Override
    public void onInitializeServer() {
        try {
            Configuration.instance().loadConfig();
            if (!Configuration.instance().general.botToken.equals("INSERT BOT TOKEN HERE")) {
                ServerLifecycleEvents.SERVER_STARTING.register(this::serverStarting);
                ServerLifecycleEvents.SERVER_STARTED.register(this::serverStarted);
                ServerLifecycleEvents.SERVER_STOPPED.register(this::serverStopped);
                ServerLifecycleEvents.SERVER_STOPPING.register(this::serverStopping);
                PlayerJoinCallback.EVENT.register(this::playerJoined);
                PlayerLeaveCallback.EVENT.register(this::playerLeft);
                PlayerDeathCallback.EVENT.register(this::death);
                CommandExecutionCallback.EVENT.register(this::command);
                if (CompatibilityUtils.styledChatLoaded()) {
                    StyledChatEvents.MESSAGE_CONTENT_SEND.register(this::styledChat);
                }
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

    private void command(String s, ServerCommandSource serverCommandSource) {
        String command = s.replaceFirst(Pattern.quote("/"), "");
        if (!Configuration.instance().commandLog.channelID.equals("0")) {
            if (!ArrayUtils.contains(Configuration.instance().commandLog.ignoredCommands, command.split(" ")[0]))
                discord_instance.sendMessage(Configuration.instance().commandLog.message
                        .replace("%sender%", serverCommandSource.getName())
                        .replace("%cmd%", command)
                        .replace("%cmd-no-args%", command.split(" ")[0]), discord_instance.getChannel(Configuration.instance().commandLog.channelID));
        }
        if (discord_instance != null) {
            boolean raw = false;

            if (((command.startsWith("say")) && Configuration.instance().messages.sendOnSayCommand) || (command.startsWith("me") && Configuration.instance().messages.sendOnMeCommand)) {
                String msg = command.replace("say ", "");
                if (command.startsWith("say"))
                    msg = msg.replaceFirst("say ", "");
                if (command.startsWith("me")) {
                    raw = true;
                    msg = "*" + MessageUtils.escapeMarkdown(msg.replaceFirst("me ", "").trim()) + "*";
                }
                discord_instance.sendMessage(serverCommandSource.getName(), serverCommandSource.getEntity().getUuid().toString(), new DiscordMessage(null, msg, !raw), discord_instance.getChannel(Configuration.instance().advanced.chatOutputChannelID));
            }
        }
    }

    private void death(ServerPlayerEntity p, DamageSource s) {
        if (PlayerLinkController.getSettings(null, p.getUuid()).hideFromDiscord) return;
        if (discord_instance != null) {
            final Text deathMessage = s.getDeathMessage(p);
            final MessageEmbed embed = FabricMessageUtils.genItemStackEmbedIfAvailable(deathMessage);
            discord_instance.sendMessage(new DiscordMessage(embed, Configuration.instance().localization.playerDeath.replace("%player%", FabricMessageUtils.formatPlayerName(p)).replace("%msg%", Formatting.strip(deathMessage.getString()).replace(FabricMessageUtils.formatPlayerName(p) + " ", ""))), discord_instance.getChannel(Configuration.instance().advanced.deathsChannelID));
        }

    }

    private void playerLeft(ServerPlayerEntity p, MinecraftServer minecraftServer) {
        if (stopped) return; //Try to fix player leave messages after stop!
        if (PlayerLinkController.getSettings(null, p.getUuid()).hideFromDiscord) return;
        if (discord_instance != null && !timeouts.contains(p.getUuid()))
            discord_instance.sendMessage(Configuration.instance().localization.playerLeave.replace("%player%", FabricMessageUtils.formatPlayerName(p)));
        else if (discord_instance != null && timeouts.contains(p.getUuid())) {
            discord_instance.sendMessage(Configuration.instance().localization.playerTimeout.replace("%player%", FabricMessageUtils.formatPlayerName(p)));
            timeouts.remove(p.getUuid());
        }
    }

    private void playerJoined(ServerPlayerEntity p, MinecraftServer minecraftServer) {
        if (PlayerLinkController.getSettings(null, p.getUuid()).hideFromDiscord) return;
        if (discord_instance != null) {
            discord_instance.sendMessage(Configuration.instance().localization.playerJoin.replace("%player%", FabricMessageUtils.formatPlayerName(p)));

            // Fix link status (if user does not have role, give the role to the user, or vice versa)
            final Thread fixLinkStatus = new Thread(() -> {
                if (Configuration.instance().linking.linkedRoleID.equals("0")) return;
                final UUID uuid = p.getUuid();
                if (!PlayerLinkController.isPlayerLinked(uuid)) return;
                final Guild guild = discord_instance.getChannel().getGuild();
                final Role linkedRole = guild.getRoleById(Configuration.instance().linking.linkedRoleID);
                if (PlayerLinkController.isPlayerLinked(uuid)) {
                    final Member member = guild.getMemberById(PlayerLinkController.getDiscordFromPlayer(uuid));
                    if (!member.getRoles().contains(linkedRole))
                        guild.addRoleToMember(member, linkedRole).queue();
                }
            });
            fixLinkStatus.setDaemon(true);
            fixLinkStatus.start();
        }
    }


    private Text styledChat(Text txt, ServerPlayerEntity player, boolean filtered) {
        if (PlayerLinkController.getSettings(null, player.getUuid()).hideFromDiscord || filtered) {
            return txt;
        }

        Text finalTxt = txt;
        boolean cancelled = discord_instance.callEvent((e) -> {
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
        if (discord_instance != null) {
            TextChannel channel = discord_instance.getChannel(Configuration.instance().advanced.chatOutputChannelID);
            if (channel == null) {
                return txt;
            }
            discord_instance.sendMessage(FabricMessageUtils.formatPlayerName(player), player.getUuid().toString(), new DiscordMessage(embed, messageText, true), channel);

            final String json = Text.Serializer.toJson(txt);
            Component comp = GsonComponentSerializer.gson().deserialize(json);
            final String editedJson = GsonComponentSerializer.gson().serialize(MessageUtils.mentionsToNames(comp, channel.getGuild()));

            txt = Text.Serializer.fromJson(editedJson);
        }

        return txt;
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
                if (!Configuration.instance().localization.serverStarting.isEmpty()) {
                    CommandRegistry.registerDefaultCommandsFromConfig();
                    if (discord_instance.getChannel() != null)
                        Variables.startingMsg = discord_instance.sendMessageReturns(Configuration.instance().localization.serverStarting, discord_instance.getChannel(Configuration.instance().advanced.serverChannelID));
                }
            }
        } catch (InterruptedException | NullPointerException ignored) {
        }
        new McCommandDiscord(minecraftServer.getCommandManager().getDispatcher());
    }

    private void serverStarted(MinecraftServer minecraftServer) {
        System.out.println("Started");
        Variables.started = new Date().getTime();
        if (discord_instance != null) {
            if (Variables.startingMsg != null) {
                Variables.startingMsg.thenAccept((a) -> a.editMessage(Configuration.instance().localization.serverStarted).queue());
            } else discord_instance.sendMessage(Configuration.instance().localization.serverStarted);
        }
        if (discord_instance != null) {
            discord_instance.startThreads();
        }
        UpdateChecker.runUpdateCheck("https://raw.githubusercontent.com/ErdbeerbaerLP/Discord-Integration-Fabric/1.17/update-checker.json");
    }

    private void serverStopping(MinecraftServer minecraftServer) {
        if (discord_instance != null) {
            discord_instance.sendMessage(Configuration.instance().localization.serverStopped);
            discord_instance.stopThreads();
        }
        this.stopped = true;
    }

    private void serverStopped(MinecraftServer minecraftServer) {
        if (discord_instance != null) {
            if (!stopped && discord_instance.getJDA() != null) minecraftServer.execute(() -> {
                discord_instance.stopThreads();
                try {
                    discord_instance.sendMessageReturns(Configuration.instance().localization.serverCrash, discord_instance.getChannel(Configuration.instance().advanced.serverChannelID)).get();
                } catch (InterruptedException | ExecutionException ignored) {
                }
            });
            discord_instance.kill();
        }
    }

}
