package romelo333.notenoughwands;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

import java.awt.*;

public class FreezePotion extends Potion {
    public static FreezePotion freezePotion;

    ResourceLocation icon = new ResourceLocation(NotEnoughWands.MODID + ":textures/gui/effects/freeze.png");

    public FreezePotion() {
        super(new ResourceLocation("freeze"), false, Color.BLUE.getRed());
    }
}
