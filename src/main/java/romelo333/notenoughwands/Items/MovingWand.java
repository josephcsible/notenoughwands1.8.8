package romelo333.notenoughwands.Items;


import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.registry.GameRegistry;
import romelo333.notenoughwands.Config;
import romelo333.notenoughwands.varia.Tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MovingWand extends GenericWand {
    private float maxHardness = 50;
    private int placeDistance = 4;

    public Map<String,Double> blacklisted = new HashMap<String, Double>();

    public MovingWand() {
        setup("moving_wand").xpUsage(3).availability(AVAILABILITY_NORMAL).loot(5);
    }

    @Override
    public void initConfig(Configuration cfg) {
        super.initConfig(cfg);
        maxHardness = (float) cfg.get(Config.CATEGORY_WANDS, getUnlocalizedName() + "_maxHardness", maxHardness, "Max hardness this block can move.)").getDouble();
        placeDistance = cfg.get(Config.CATEGORY_WANDS, getUnlocalizedName() + "_placeDistance", placeDistance, "Distance at which to place blocks in 'in-air' mode").getInt();

        ConfigCategory category = cfg.getCategory(Config.CATEGORY_MOVINGBLACKLIST);
        if (category.isEmpty()) {
            // Initialize with defaults
            blacklist(cfg, "tile.shieldBlock");
            blacklist(cfg, "tile.shieldBlock2");
            blacklist(cfg, "tile.shieldBlock3");
            blacklist(cfg, "tile.solidShieldBlock");
            blacklist(cfg, "tile.invisibleShieldBlock");
            setCost(cfg, "tile.mobSpawner", 5.0);
            setCost(cfg, "tile.blockAiry", 20.0);
        } else {
            for (Map.Entry<String, Property> entry : category.entrySet()) {
                blacklisted.put(entry.getKey(), entry.getValue().getDouble());
            }
        }
    }

    private void blacklist(Configuration cfg, String name) {
        setCost(cfg, name, -1.0);
    }

    private void setCost(Configuration cfg, String name, double cost) {
        cfg.get(Config.CATEGORY_MOVINGBLACKLIST, name, cost);
        blacklisted.put(name, cost);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean b) {
        super.addInformation(stack, player, list, b);
        NBTTagCompound compound = stack.getTagCompound();
        if (!hasBlock(compound)) {
            list.add(TextFormatting.RED + "Wand is empty.");
        } else {
            int id = compound.getInteger("block");
            Block block = (Block) Block.blockRegistry.getObjectById(id);
            int meta = compound.getInteger("meta");
            String name = Tools.getBlockName(block, meta);
            list.add(TextFormatting.GREEN + "Block: " + name);
        }
        list.add("Right click to take a block.");
        list.add("Right click again on block to place it down.");
    }

    private boolean hasBlock(NBTTagCompound compound) {
        return compound != null && compound.hasKey("block");
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack stack, World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            NBTTagCompound compound = stack.getTagCompound();
            if (hasBlock(compound)) {
                Vec3d lookVec = player.getLookVec();
                Vec3d start = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
                int distance = this.placeDistance;
                Vec3d end = start.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
                RayTraceResult position = world.rayTraceBlocks(start, end);
                if (position == null) {
                    place(stack, world, new BlockPos(end), null);
                }
            }
        }
        return new ActionResult(EnumActionResult.SUCCESS, stack);
    }


    @Override
    public EnumActionResult onItemUseFirst(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote) {
            NBTTagCompound compound = stack.getTagCompound();
            if (hasBlock(compound)) {
                place(stack, world, pos, side);
            } else {
                pickup(stack, player, world, pos);
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return EnumActionResult.SUCCESS;
    }

    private void place(ItemStack stack, World world, BlockPos pos, EnumFacing side) {
        BlockPos pp = pos.offset(side);
        NBTTagCompound tagCompound = stack.getTagCompound();
        int id = tagCompound.getInteger("block");
        Block block = Block.blockRegistry.getObjectById(id);
        int meta = tagCompound.getInteger("meta");

        IBlockState blockState = block.getStateFromMeta(meta);
        world.setBlockState(pp, blockState, 3);
        if (tagCompound.hasKey("tedata")) {
            NBTTagCompound tc = (NBTTagCompound) tagCompound.getTag("tedata");
            TileEntity tileEntity = world.getTileEntity(pp);
            if (tileEntity != null) {
                tc.setInteger("x", pp.getX());
                tc.setInteger("y", pp.getY());
                tc.setInteger("z", pp.getZ());
                tileEntity.readFromNBT(tc);
                tileEntity.markDirty();
                world.notifyBlockUpdate(pp, blockState, blockState, 3);
            }
        }

        tagCompound.removeTag("block");
        tagCompound.removeTag("tedata");
        tagCompound.removeTag("meta");
        stack.setTagCompound(tagCompound);
    }

    private void pickup(ItemStack stack, EntityPlayer player, World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        int meta = block.getMetaFromState(state);
        double cost = checkPickup(player, world, pos, block, maxHardness, blacklisted);
        if (cost < 0.0) {
            return;
        }

        if (!checkUsage(stack, player, (float) cost)) {
            return;
        }

        NBTTagCompound tagCompound = Tools.getTagCompound(stack);
        String name = Tools.getBlockName(block, meta);
        if (name == null) {
            Tools.error(player, "You cannot select this block!");
        } else {
            int id = Block.blockRegistry.getIDForObject(block);
            tagCompound.setInteger("block", id);
            tagCompound.setInteger("meta", meta);

            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity != null) {
                NBTTagCompound tc = new NBTTagCompound();
                tileEntity.writeToNBT(tc);
                world.removeTileEntity(pos);
                tc.removeTag("x");
                tc.removeTag("y");
                tc.removeTag("z");
                tagCompound.setTag("tedata", tc);
            }
            world.setBlockToAir(pos);

            Tools.notify(player, "You took: " + name);
            registerUsage(stack, player, (float) cost);
        }
    }

    @Override
    protected void setupCraftingInt(Item wandcore) {
        GameRegistry.addRecipe(new ItemStack(this), "re ", "ew ", "  w", 'r', Items.redstone, 'e', Items.ender_pearl, 'w', wandcore);
    }
}