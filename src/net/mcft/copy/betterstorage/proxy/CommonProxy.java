package net.mcft.copy.betterstorage.proxy;

import net.mcft.copy.betterstorage.addon.Addon;
import net.mcft.copy.betterstorage.block.TileEntityArmorStand;
import net.mcft.copy.betterstorage.block.TileEntityBackpack;
import net.mcft.copy.betterstorage.block.TileEntityLocker;
import net.mcft.copy.betterstorage.block.TileEntityReinforcedChest;
import net.mcft.copy.betterstorage.block.crate.CratePileCollection;
import net.mcft.copy.betterstorage.block.crate.TileEntityCrate;
import net.mcft.copy.betterstorage.container.ContainerBetterStorage;
import net.mcft.copy.betterstorage.inventory.InventoryBackpackEquipped;
import net.mcft.copy.betterstorage.item.ItemBackpack;
import net.mcft.copy.betterstorage.misc.PropertiesBackpackItems;
import net.mcft.copy.betterstorage.utils.EntityUtils;
import net.mcft.copy.betterstorage.utils.NbtUtils;
import net.mcft.copy.betterstorage.utils.PlayerUtils;
import net.mcft.copy.betterstorage.utils.StackUtils;
import net.mcft.copy.betterstorage.utils.WorldUtils;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.Event.Result;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.WorldEvent.Save;
import net.minecraftforge.event.world.WorldEvent.Unload;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy implements IPlayerTracker {
	
	public void init() {
		MinecraftForge.EVENT_BUS.register(this);
		GameRegistry.registerPlayerTracker(this);
		registerTileEntites();
	}
	
	public void registerTileEntites() {
		GameRegistry.registerTileEntity(TileEntityCrate.class, "container.crate");
		GameRegistry.registerTileEntity(TileEntityReinforcedChest.class, "container.reinforcedChest");
		GameRegistry.registerTileEntity(TileEntityLocker.class, "container.locker");
		GameRegistry.registerTileEntity(TileEntityArmorStand.class, "container.armorStand");
		GameRegistry.registerTileEntity(TileEntityBackpack.class, "container.backpack");
		Addon.registerAllTileEntites();
	}
	
	@ForgeSubscribe
	public void onWorldSave(Save event) {
		CratePileCollection.saveAll(event.world);
	}
	
	@ForgeSubscribe
	public void onWorldUnload(Unload event) {
		CratePileCollection.unload(event.world);
	}
	
	@ForgeSubscribe
	public void onPlayerInteract(PlayerInteractEvent event) {
		
		// Places an equipped backpack when the player right clicks
		// on the ground while sneaking and holding nothing.
		
		if (event.action != Action.RIGHT_CLICK_BLOCK) return;
		EntityPlayer player = event.entityPlayer;
		if (player.getCurrentEquippedItem() != null || !player.isSneaking()) return;
		ItemStack backpack = ItemBackpack.getBackpack(player);
		if (backpack == null) return;
		// Try to place the backpack as if it was being held and used by the player.
		backpack.getItem().onItemUse(backpack, player, player.worldObj, event.x, event.y, event.z, event.face, 0, 0, 0);
		// Only continue if the backpack was places successfully.
		if (backpack.stackSize > 0) return;
		player.swingItem();
		event.useBlock = Result.DENY;
		
	}
	
	@ForgeSubscribe
	public void onEntityInteract(EntityInteractEvent event) {
		
		// Right clicking the back of another player will
		// open the GUI for that backpack.
		
		if (event.entity.worldObj.isRemote) return;
		EntityPlayerMP player = (EntityPlayerMP)event.entity;
		if (!(event.target instanceof EntityLiving)) return;
		EntityLiving target = (EntityLiving)event.target;
		
		ItemStack backpack = ItemBackpack.getBackpack(target);
		if (backpack == null) return;
		ItemBackpack backpackType = (ItemBackpack)backpack.getItem();
		
		int columns = backpackType.getColumns();
		int rows = backpackType.getRows();
		
		PropertiesBackpackItems backpackItems =
				EntityUtils.getProperties(target, PropertiesBackpackItems.class);
		if (backpackItems.contents == null)
			backpackItems.contents = new ItemStack[columns * rows];
		
		IInventory inventory = new InventoryBackpackEquipped(player, target);
		if (!inventory.isUseableByPlayer(player)) return;
		Container container = new ContainerBetterStorage(player, inventory, columns, rows);
		
		String title = StackUtils.get(backpack, "", "display", "Name");
		PlayerUtils.openGui(player, "container.backpack", columns, rows, title, container);
		
		player.swingItem();
		
	}
	
	@ForgeSubscribe
	public void onEntityConstruction(EntityConstructing event) {
		if ((FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) ||
		    !(event.entity instanceof EntityLiving)) return;
		EntityUtils.createProperties(event.entity, PropertiesBackpackItems.class);
	}
	
	@ForgeSubscribe
	public void onLivingDeath(LivingDeathEvent event) {
		
		// Drops the contents from an equipped backpack when the entity dies.
		
		EntityLiving entity = event.entityLiving;
		if (entity.worldObj.isRemote) return;
		ItemStack backpack = ItemBackpack.getBackpack(entity);
		if (backpack == null) return;
		PropertiesBackpackItems backpackItems = ItemBackpack.getBackpackItems(entity);
		
		boolean keepInventory = entity.worldObj.getGameRules().getGameRuleBooleanValue("keepInventory");
		if ((entity instanceof EntityPlayer) && keepInventory) {
			
			// If keep inventory is on, instead temporarily save the contents
			// to the persistent NBT tag and get them back when the player respawns.
			
			EntityPlayer player = (EntityPlayer)entity;
			NBTTagCompound compound = player.getEntityData();
			NBTTagCompound persistent;
			if (!compound.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
				persistent = new NBTTagCompound();
				compound.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);
			} else persistent = compound.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);;
			
			NBTTagCompound backpackCompound = new NBTTagCompound();
			backpackCompound.setInteger("count", backpackItems.contents.length);
			backpackCompound.setTag("Items", NbtUtils.writeItems(backpackItems.contents));
			persistent.setTag("Backpack", backpackCompound);
			
		} else {
			
			for (ItemStack stack : backpackItems.contents)
				WorldUtils.dropStackFromEntity(entity, stack);
			ItemBackpack.removeBackpack(entity, false);
			
		}
		
	}
	
	// IPlayerTracker implementation
	
	@Override
	public void onPlayerLogin(EntityPlayer player) {  }
	@Override
	public void onPlayerLogout(EntityPlayer player) {  }
	@Override
	public void onPlayerChangedDimension(EntityPlayer player) {  }
	@Override
	public void onPlayerRespawn(EntityPlayer player) {
		
		// If the player dies when when keepInventory is on and respawns,
		// retrieve the backpack items from the eir persistent NBT tag.
		
		NBTTagCompound compound = player.getEntityData();
		if (!compound.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) return;
		NBTTagCompound persistent = compound.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
		if (!persistent.hasKey("Backpack")) return;
		NBTTagCompound backpack = persistent.getCompoundTag("Backpack");
		
		int size = backpack.getInteger("count");
		ItemStack[] contents = new ItemStack[size];
		NbtUtils.readItems(contents, backpack.getTagList("Items"));
		
		ItemBackpack.getBackpackItems(player).contents = contents;
		
		persistent.removeTag("Backpack");
		if (persistent.hasNoTags())
			compound.removeTag(EntityPlayer.PERSISTED_NBT_TAG);
		
	}
	
}
