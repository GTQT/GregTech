package gregtech.common.items.armor;

import gregtech.api.items.armor.IArmorLogic;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

public class HazmatSuit implements IArmorLogic {

    protected final EntityEquipmentSlot SLOT;

    public HazmatSuit(EntityEquipmentSlot slot){
        this.SLOT = slot;
    }

    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        return this.SLOT;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        String armorTexture = "hazmat";
        return SLOT != EntityEquipmentSlot.LEGS ?
                String.format("gregtech:textures/armor/%s_1.png", armorTexture) :
                String.format("gregtech:textures/armor/%s_2.png", armorTexture);
    }

    @Override
    public float getRadiationResistance() {
        return 0.00f;
    }
}
