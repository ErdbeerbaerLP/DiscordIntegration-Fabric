package de.erdbeerbaerlp.dcintegration.fabric.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import de.erdbeerbaerlp.dcintegration.fabric.util.accessors.ShowInTooltipAccessor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.Arrays;

public class FabricMessageUtils extends MessageUtils {
    public static String formatPlayerName(ServerPlayerEntity player) {
        if (player.getPlayerListName() != null)
            return Formatting.strip(player.getPlayerListName().getString());
        else
            return Formatting.strip(player.getName().getString());
    }

    public static MessageEmbed genItemStackEmbedIfAvailable(final Text component, World w) {
        if (!Configuration.instance().forgeSpecific.sendItemInfo) return null;
        JsonObject json;
        try {
            final JsonElement jsonElement = JsonParser.parseString(Text.Serialization.toJsonString(component, w.getRegistryManager()));
            if (jsonElement.isJsonObject())
                json = jsonElement.getAsJsonObject();
            else return null;
        } catch (final IllegalStateException ex) {
            ex.printStackTrace();
            return null;
        }
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
                                    // final ItemStack is = new ItemStack(Registries.ITEM.get((new Identifier(item.get("id").getAsString()))));
                                    final NbtCompound tag = NbtCompoundArgumentType.nbtCompound().parse(new StringReader(item.getAsString()));
                                    final ItemStack is = ItemStack.fromNbt(w.getRegistryManager(), tag).orElseThrow();

                                    final ComponentMap itemTag = is.getComponents();
                                    final EmbedBuilder b = new EmbedBuilder();
                                    Text title = (Text) itemTag.getOrDefault(DataComponentTypes.CUSTOM_NAME, new TranslatableTextContent(is.getItem().getTranslationKey(), is.getItem().getName().getString(), null));
                                    if (title.toString().isEmpty())
                                        title = Text.translatable(is.getItem().getTranslationKey());
                                    else
                                        b.setFooter(is.getRegistryEntry().getKeyOrValue().left().get().getValue().toString());
                                    b.setTitle(title.getString());
                                    final StringBuilder tooltip = new StringBuilder();
                                    boolean[] flags = new boolean[6]; // Enchantments, Modifiers, Unbreakable, CanDestroy, CanPlace, Other
                                    Arrays.fill(flags, false); // Set everything visible

                                    //Add Enchantments
                                    if (itemTag.contains(DataComponentTypes.ENCHANTMENTS)) {
                                        final ItemEnchantmentsComponent e = itemTag.get(DataComponentTypes.ENCHANTMENTS);
                                        if (((ShowInTooltipAccessor) e).discordIntegrationFabric$showsInTooltip())
                                            for (RegistryEntry<Enchantment> ench : e.getEnchantments()) {
                                                tooltip.append(Formatting.strip(ench.value().getName(ench, e.getLevel(ench)).getString())).append("\n");
                                            }
                                    }
                                    if(itemTag.contains(DataComponentTypes.LORE)) {
                                        final LoreComponent l = itemTag.get(DataComponentTypes.LORE);
                                        //Add Lores
                                        for (Text line : l.lines()) {
                                            tooltip.append("_").append(line.getString()).append("_\n");
                                        }
                                    }
                                    //Add 'Unbreakable' Tag
                                    if(itemTag.contains(DataComponentTypes.UNBREAKABLE)){
                                        final UnbreakableComponent unb = itemTag.get(DataComponentTypes.UNBREAKABLE);
                                        if (unb.showInTooltip())
                                            tooltip.append("Unbreakable\n");
                                    }
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
