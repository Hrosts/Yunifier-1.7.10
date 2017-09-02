package mrriegel.yunifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;

@Mod(modid = Yunifier.MODID, name = Yunifier.NAME, version = Yunifier.VERSION, acceptableRemoteVersions = "*")
@EventBusSubscriber
public class Yunifier {

	@Instance(Yunifier.MODID)
	public static Yunifier INSTANCE;

	public static final String VERSION = "1.0.0";
	public static final String NAME = "Yunifier";
	public static final String MODID = "yunifier";

	//config
	public static Configuration config;
	public static List<String> blacklist, preferredMods;
	public static boolean drop, harvest, gui, second;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());
		blacklist = new ArrayList<String>(Arrays.asList(config.getStringList("blacklist", Configuration.CATEGORY_GENERAL, new String[] { "stair.*", "fence.*" }, "OreDict names that shouldn't be unified. (supports regex)")));
		preferredMods = new ArrayList<String>(Arrays.asList(config.getStringList("preferredMods", Configuration.CATEGORY_GENERAL, new String[] { "immersiveengineering", "embers" }, "")));
		drop = config.getBoolean("drop", "unifyEvent", true, "Unify when items drop.");
		harvest = config.getBoolean("harvest", "unifyEvent", true, "Unify when blocks are harvested.");
		second = config.getBoolean("second", "unifyEvent", false, "Unify every second items in player's inventory.");
		gui = config.getBoolean("gui", "unifyEvent", true, "Unify when GUI is opened/closed.");

		if (config.hasChanged())
			config.save();
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void spawn(EntityJoinWorldEvent event) {
		if (event.getEntity() instanceof EntityItem && drop) {
			EntityItem ei = (EntityItem) event.getEntity();
			ei.setItem(replace(ei.getItem()));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void drop(HarvestDropsEvent event) {
		for (int i = 0; i < event.getDrops().size() && harvest; i++) {
			event.getDrops().replaceAll(Yunifier::replace);
		}
	}

	@SubscribeEvent
	public static void player(PlayerTickEvent event) {
		if (event.phase == Phase.END && event.side.isServer() && event.player.ticksExisted % 20 == 0 && second) {
			event.player.inventory.setItemStack(replace(event.player.inventory.getItemStack()));
			for (int i = 0; i < event.player.inventory.getSizeInventory(); i++) {
				ItemStack slot = event.player.inventory.getStackInSlot(i);
				ItemStack rep = replace(slot);
				if (!slot.isItemEqual(rep))
					event.player.inventory.setInventorySlotContents(i, rep);
			}
		}
	}

	@SubscribeEvent
	public static void open(PlayerContainerEvent event) {
		if (gui)
			event.getContainer().inventorySlots.forEach(slot -> slot.inventory.setInventorySlotContents(slot.getSlotIndex(), replace(slot.inventory.getStackInSlot(slot.getSlotIndex()))));
	}

	private static ItemStack replace(ItemStack orig) {
		if (orig.isEmpty())
			return orig;
		int[] ia = OreDictionary.getOreIDs(orig);
		if (ia.length != 1)
			return orig;
		for (String s : blacklist)
			if (Pattern.matches(s, OreDictionary.getOreName(ia[0])))
				return orig;
		List<ItemStack> stacks = new ArrayList<ItemStack>(OreDictionary.getOres(OreDictionary.getOreName(ia[0])));
		stacks.sort((ItemStack s1, ItemStack s2) -> {
			int i1 = preferredMods.indexOf(s1.getItem().getRegistryName().getResourceDomain()), i2 = preferredMods.indexOf(s2.getItem().getRegistryName().getResourceDomain());
			return Integer.compare(i1 == -1 ? 999 : i1, i2 == -1 ? 999 : i2);
		});
		if (stacks.stream().map(s -> s.getItem().getRegistryName().getResourceDomain()).distinct().count() == 1)
			return orig;
		for (ItemStack s : stacks) {
			if (Arrays.equals(ia, OreDictionary.getOreIDs(s)) && s.getItemDamage() != OreDictionary.WILDCARD_VALUE) {
				return ItemHandlerHelper.copyStackWithSize(s, orig.getCount());
			}
		}
		return orig;
	}

}