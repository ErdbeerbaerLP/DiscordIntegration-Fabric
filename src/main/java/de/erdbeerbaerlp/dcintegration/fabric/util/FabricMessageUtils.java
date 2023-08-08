package de.erdbeerbaerlp.dcintegration.fabric.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Arrays;

public class FabricMessageUtils extends MessageUtils {
    public static String formatPlayerName(ServerPlayerEntity player){
        if(player.getPlayerListName() != null)
            return Formatting.strip(player.getPlayerListName().getString());
        else
            return Formatting.strip(player.getName().getString());
    }

    public static MessageEmbed genItemStackEmbedIfAvailable(Text component) {
        if (!Configuration.instance().forgeSpecific.sendItemInfo) return null;
        final JsonObject json = JsonParser.parseString(Text.Serializer.toJson(component)).getAsJsonObject();
        if (json.has("with")) {
            final JsonArray args = json.getAsJsonArray("with");
            for (JsonElement el : args) {
                if (el instanceof JsonObject arg1) {
                    if (arg1.has("hoverEvent")) {
                        final JsonObject hoverEvent = arg1.getAsJsonObject("hoverEvent");
                        if (hoverEvent.has("action") && hoverEvent.get("action").getAsString().equals("show_item") && hoverEvent.has("contents")) {
                            if (hoverEvent.getAsJsonObject("contents").has("tag")) {
                                final JsonObject item = hoverEvent.getAsJsonObject("contents").getAsJsonObject();
                                try {
                                    final ItemStack is = new ItemStack(Registry.ITEM.get((new Identifier(item.get("id").getAsString()))));
                                    if (item.has("tag")) {
                                        final NbtCompound tag = NbtCompoundArgumentType.nbtCompound().parse(new StringReader(item.get("tag").getAsString()));
                                        is.setNbt(tag);
                                    }
                                    final NbtCompound itemTag = is.getOrCreateNbt();
                                    final EmbedBuilder b = new EmbedBuilder();
                                    String title = is.hasCustomName() ? is.getName().getString() : new TranslatableText(is.getItem().getTranslationKey(), is.getItem().getName().getString(),null).toString();
                                    if (title.isEmpty())
                                        title = new TranslatableText(is.getItem().getTranslationKey()).getString();
                                    else
                                        b.setFooter(is.getItem().getRegistryEntry().getKeyOrValue().left().get().getValue().toString());
                                    b.setTitle(title);
                                    final StringBuilder tooltip = new StringBuilder();
                                    boolean[] flags = new boolean[6]; // Enchantments, Modifiers, Unbreakable, CanDestroy, CanPlace, Other
                                    Arrays.fill(flags, false); // Set everything visible

                                    if (itemTag.contains("HideFlags")) {
                                        final int input = (itemTag.getInt("HideFlags"));
                                        for (int i = 0; i < flags.length; i++) {
                                            flags[i] = (input & (1 << i)) != 0;
                                        }
                                    }
                                    //Add Enchantments
                                    if (!flags[0]) {
                                        EnchantmentHelper.fromNbt(is.getEnchantments()).forEach((ench,level)->{
                                                tooltip.append(Formatting.strip(ench.getName(level).getString())).append("\n");
                                        });
                                    }
                                    //Add Lores
                                    final NbtList list = itemTag.getCompound("display").getList("Lore", 8);
                                    list.forEach((nbt) -> {
                                        try {
                                            if (nbt instanceof NbtString) {
                                                final Text comp = TextArgumentType.text().parse(new StringReader(nbt.asString()));
                                                tooltip.append("_").append(comp.getString()).append("_\n");
                                            }
                                        } catch (CommandSyntaxException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    //Add 'Unbreakable' Tag
                                    if (!flags[2] && itemTag.contains("Unbreakable") && itemTag.getBoolean("Unbreakable"))
                                        tooltip.append("Unbreakable\n");
                                    b.setDescription(tooltip.toString());
                                    return b.build();
                                } catch (CommandSyntaxException ignored) {
                                    //Just go on and ignore it
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
