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
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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
                                    final ItemStack is = new ItemStack(Registries.ITEM.get((new Identifier(item.get("id").getAsString()))));
                                    if (item.has("tag")) {
                                        final NbtCompound tag = NbtCompoundArgumentType.nbtCompound().parse(new StringReader(item.get("tag").getAsString()));
                                        is.setNbt(tag);
                                    }
                                    final NbtCompound itemTag = is.getOrCreateNbt();
                                    final EmbedBuilder b = new EmbedBuilder();
                                    String title = is.hasCustomName() ? is.getName().getString() : new TranslatableTextContent(is.getItem().getTranslationKey()).toString();
                                    if (title.isEmpty())
                                        title = Text.translatable(is.getItem().getTranslationKey()).getString();
                                    else
                                        b.setFooter(Text.translatable(is.getItem().getTranslationKey()).getString());
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
                                        for (int i = 0; i < is.getEnchantments().size(); ++i) {
                                            final NbtCompound compoundnbt = is.getEnchantments().getCompound(i);
                                            final Enchantment ench = Enchantment.byRawId(compoundnbt.getInt("id"));
                                            if (compoundnbt.get("lvl") != null) {
                                                    final int level;
                                                    if (compoundnbt.get("lvl") instanceof NbtString) {
                                                        level = Integer.parseInt(compoundnbt.getString("lvl").replace("s", ""));
                                                    } else
                                                        level = compoundnbt.getInt("lvl") == 0 ? compoundnbt.getShort("lvl") : compoundnbt.getInt("lvl");
                                                    tooltip.append(Formatting.strip(ench.getName(level).getString())).append("\n");
                                                }
                                        }
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
