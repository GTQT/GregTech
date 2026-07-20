package gregtech.integration.tic.traits;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.translation.I18n;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * A generic TiC trait that bakes a vanilla enchantment into the tool's NBT.
 *
 * <p>
 * Instances are created dynamically from a GT material's {@code ToolProperty} enchantment map and cached in
 * {@link GregtechTraits}. If the same enchantment is already present at a higher level it is left unchanged; if it is
 * present at a lower level the level is upgraded.
 */
public class TraitEnchantment extends AbstractTrait {

    private final Enchantment enchantment;
    private final int level;

    public TraitEnchantment(String identifier, int color, Enchantment enchantment, int level) {
        super(identifier, color);
        this.enchantment = enchantment;
        this.level = level;
    }

    @Override
    public String getLocalizedName() {
        return enchantment.getTranslatedName(level);
    }

    @Override
    public String getLocalizedDesc() {
        // §o%s§r\n<desc> — the %s is replaced with the enchantment name (italic, material-colored)
        return I18n.translateToLocalFormatted("gregtech.trait.ench.desc", enchantment.getTranslatedName(level));
    }

    @Override
    public void applyEffect(NBTTagCompound rootCompound, NBTTagCompound modifierTag) {
        super.applyEffect(rootCompound, modifierTag);

        NBTTagList enchList = rootCompound.getTagList("ench", 10);
        int enchId = Enchantment.getEnchantmentID(enchantment);

        // Check if this enchantment is already present
        for (int i = 0; i < enchList.tagCount(); i++) {
            NBTTagCompound tag = enchList.getCompoundTagAt(i);
            if (tag.getShort("id") == enchId) {
                // Keep the higher level
                if (tag.getShort("lvl") < level) {
                    tag.setShort("lvl", (short) level);
                }
                return;
            }
        }

        // Add new enchantment entry
        NBTTagCompound enchTag = new NBTTagCompound();
        enchTag.setShort("id", (short) enchId);
        enchTag.setShort("lvl", (short) level);
        enchList.appendTag(enchTag);
        rootCompound.setTag("ench", enchList);
    }
}
