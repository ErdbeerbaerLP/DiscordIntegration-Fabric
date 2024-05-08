package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import de.erdbeerbaerlp.dcintegration.fabric.util.accessors.ShowInTooltipAccessor;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemEnchantmentsComponent.class)
public class ItemEnchantmentsComponentMixin implements ShowInTooltipAccessor {
    @Shadow @Final boolean showInTooltip;


    public boolean discordIntegrationFabric$showsInTooltip() {
        return showInTooltip;
    }
}
