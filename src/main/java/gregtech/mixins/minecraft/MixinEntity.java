package gregtech.mixins.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;

import gtqt.api.util.calculateFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class MixinEntity {


    @Shadow
    public float rotationYaw;
    /**
     * @author MeowmelMuku
     * @reason 尼玛东南西北分不清？？？？
     */
    @Overwrite
    public EnumFacing getHorizontalFacing()
    {
        // 获取标准化后的yaw角度（0~360）
        float normalizedYaw = calculateFacing.gregTech$normalizeYaw(this.rotationYaw);

        // 使用精确计算替代索引计算
        return calculateFacing.gregTech$calculateFacingFromYaw(normalizedYaw);
    }

}
