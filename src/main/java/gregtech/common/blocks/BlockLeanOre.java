package gregtech.common.blocks;

import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialFlags;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.ore.StoneType;
import gregtech.api.unification.ore.StoneTypes;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IBlockOre;
import gregtech.api.worldgen.config.OreConfigUtils;
import gregtech.client.model.OreBakedModel;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.common.blocks.properties.PropertyStoneType;
import gregtech.common.creativetab.GTCreativeTabs;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Lean (low-grade) Ore Block that yields crushed ore instead of raw ore.
 * <p>
 * Compared to regular {@link BlockOre}, lean ore represents a poorer-quality deposit:
 * <ul>
 *   <li>Drops {@link OrePrefix#crushed crushed ore} instead of {@link OrePrefix#rawOre raw ore}</li>
 *   <li>Each crushed ore must be further processed (washed → purified → smelted) to yield an ingot</li>
 *   <li>Slightly lower harvest level and hardness than regular ore</li>
 * </ul>
 *
 * @see BlockOre
 */
public class BlockLeanOre extends Block implements IBlockOre {

    public PropertyStoneType STONE_TYPE;
    public final Material material;

    private static StoneType[] $values;

    public BlockLeanOre(Material material, StoneType[] allowedValues) {
        super(material($values = allowedValues));
        setTranslationKey("lean_ore_block");
        setSoundType(SoundType.STONE);
        setHardness(2.0f);
        setResistance(3.0f);
        this.material = Objects.requireNonNull(material, "Material in BlockLeanOre can not be null!");
        // STONE_TYPE and blockState already initialized in createBlockState() during super() call
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_ORES);
    }

    private static net.minecraft.block.material.Material material(StoneType[] v) {
        return net.minecraft.block.material.Material.ROCK;
    }

    @NotNull
    @SuppressWarnings("deprecation")
    @Override
    public net.minecraft.block.material.Material getMaterial(@NotNull IBlockState state) {
        String harvestTool = getHarvestTool(state);
        if (harvestTool != null && harvestTool.equals(ToolClasses.SHOVEL)) {
            return net.minecraft.block.material.Material.GROUND;
        }
        return net.minecraft.block.material.Material.ROCK;
    }

    @NotNull
    @Override
    protected final BlockStateContainer createBlockState() {
        this.STONE_TYPE = PropertyStoneType.create("stone_type", $values);
        return new BlockStateContainer(this, STONE_TYPE);
    }

    @NotNull
    @Override
    public Item getItemDropped(@NotNull IBlockState state, @NotNull Random rand, int fortune) {
        // Lean ore drops crushed ore instead of raw ore
        ItemStack crushedStack = OreDictUnifier.get(OrePrefix.crushed, this.material);
        if (!crushedStack.isEmpty()) {
            return crushedStack.getItem();
        }

        // Fallback: drop the block itself if crushed form doesn't exist
        StoneType stoneType = state.getValue(STONE_TYPE);
        if (stoneType.shouldBeDroppedAsItem || StoneType.STONE_TYPE_REGISTRY.getIDForObject(stoneType) < 16) {
            return super.getItemDropped(state, rand, fortune);
        } else {
            IBlockState stoneOre = OreConfigUtils.getOreForMaterial(this.material).get(StoneTypes.STONE);
            return Item.getItemFromBlock(stoneOre.getBlock());
        }
    }

    @Override
    public int damageDropped(@NotNull IBlockState state) {
        ItemStack crushedStack = OreDictUnifier.get(OrePrefix.crushed, this.material);
        return crushedStack.isEmpty() ? 0 : crushedStack.getItemDamage();
    }

    @Override
    public int quantityDropped(IBlockState state, int fortune, @NotNull Random random) {
        // Lean ore: base 1 drop, fortune gives +0~1 extra per level (less generous than regular ore)
        int base = 1 + random.nextInt(fortune + 1);

        ItemStack crushedStack = OreDictUnifier.get(OrePrefix.crushed, this.material);
        return crushedStack.isEmpty() ? super.quantityDropped(state, fortune, random) : base;
    }

    @NotNull
    @Override
    public SoundType getSoundType(IBlockState state, @NotNull World world, @NotNull BlockPos pos,
                                  @Nullable Entity entity) {
        StoneType stoneType = state.getValue(STONE_TYPE);
        return stoneType.soundType;
    }

    @Override
    public String getHarvestTool(IBlockState state) {
        StoneType stoneType = state.getValue(STONE_TYPE);
        IBlockState stoneState = stoneType.stone.get();
        return stoneState.getBlock().getHarvestTool(stoneState);
    }

    @Override
    public int getHarvestLevel(IBlockState state) {
        return Math.max(state.getValue(STONE_TYPE).stoneMaterial.getBlockHarvestLevel(),
                material.getBlockHarvestLevel());
    }

    @NotNull
    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        StoneType stoneType = state.getValue(STONE_TYPE);
        if (stoneType.shouldBeDroppedAsItem) {
            return super.getSilkTouchDrop(state);
        }
        return super.getSilkTouchDrop(this.getDefaultState());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        if (meta >= STONE_TYPE.getAllowedValues().size()) {
            meta = 0;
        }
        return getDefaultState().withProperty(STONE_TYPE, STONE_TYPE.getAllowedValues().get(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return STONE_TYPE.getAllowedValues().indexOf(state.getValue(STONE_TYPE));
    }

    @NotNull
    @Override
    public ItemStack getPickBlock(@NotNull IBlockState state, @NotNull RayTraceResult target, @NotNull World world,
                                  @NotNull BlockPos pos, @NotNull EntityPlayer player) {
        return GTUtility.toItem(state);
    }

    @Override
    public boolean isFireSource(@NotNull World world, @NotNull BlockPos pos, @NotNull EnumFacing side) {
        if (side != EnumFacing.UP) return false;

        StoneType stoneType = world.getBlockState(pos).getValue(STONE_TYPE);
        return stoneType.stoneMaterial.hasFlag(MaterialFlags.FLAMMABLE);
    }

    @Override
    public void getSubBlocks(@NotNull CreativeTabs tab, @NotNull NonNullList<ItemStack> list) {
        if (tab == CreativeTabs.SEARCH || tab == GTCreativeTabs.TAB_GREGTECH_ORES) {
            blockState.getValidStates().stream()
                    .filter(state -> state.getValue(STONE_TYPE).shouldBeDroppedAsItem)
                    .forEach(blockState -> list.add(GTUtility.toItem(blockState)));
        }
    }

    @Override
    public boolean canRenderInLayer(@NotNull IBlockState state, @NotNull BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED ||
                material.getProperty(PropertyKey.ORE).isEmissive() && layer == BloomEffectUtil.getEffectiveBloomLayer();
    }

    @Override
    public IBlockState getOreBlock(StoneType stoneType) {
        return this.getDefaultState().withProperty(this.STONE_TYPE, stoneType);
    }

    @SideOnly(Side.CLIENT)
    public void onModelRegister() {
        ModelLoader.setCustomStateMapper(this, b -> b.getBlockState().getValidStates().stream()
                .collect(Collectors.toMap(
                        s -> s,
                        s -> OreBakedModel.registerLeanOreEntry(s.getValue(STONE_TYPE), this.material))));
        for (IBlockState state : this.getBlockState().getValidStates()) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), this.getMetaFromState(state),
                    OreBakedModel.registerLeanOreEntry(state.getValue(STONE_TYPE), this.material));
        }
    }
}
