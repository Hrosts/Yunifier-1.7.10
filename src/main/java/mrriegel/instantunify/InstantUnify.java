package mrriegel.instantunify;

import static net.minecraftforge.common.config.Configuration.CATEGORY_GENERAL;
import static net.minecraftforge.common.config.Configuration.NEW_LINE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;
import wanion.unidict.UniDict;
import wanion.unidict.resource.ResourceHandler;

@Mod(modid = InstantUnify.MODID, name = InstantUnify.NAME, version = InstantUnify.VERSION, dependencies = "before:unidict@[1.12.2-2.2,);", acceptedMinecraftVersions = "[1.12,1.13)", acceptableRemoteVersions = "*")
@EventBusSubscriber
public class InstantUnify {

	@Instance(InstantUnify.MODID)
	public static InstantUnify INSTANCE;

	public static final String VERSION = "1.1.2";
	public static final String NAME = "InstantUnify";
	public static final String MODID = "instantunify";

	//config
	public static Configuration config;
	public static List<String> blacklist, whitelist, preferredMods, blacklistMods;
	public static boolean drop, harvest, gui, second, death, useUnidict;
	public static int listMode;
	public static Map<String, List<String>> alternatives = new HashMap<>();

	private static Object resourceHandler;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());
		blacklist = new ArrayList<>(Arrays.asList(config.getStringList("blacklist", "List", new String[] { ".*Wood", ".*Glass.*", "stair.*", "fence.*", "plank.*", "slab.*", ".*Marble.*" }, "OreDict names that shouldn't be unified. (supports regex)" + NEW_LINE)));
		whitelist = new ArrayList<>(Arrays.asList(config.getStringList("whitelist", "List", new String[] { "block.*", "chunk.*", "dust.*", "dustSmall.*", "dustTiny.*", "gear.*", "gem.*", "ingot.*", "nugget.*", "ore.*", "plate.*", "rod.*" }, "OreDict names that should be unified. (supports regex)" + NEW_LINE)));
		listMode = config.getInt("listMode", "List", 2, 0, 3, "0 - use whitelist" + NEW_LINE + "1 - use blacklist" + NEW_LINE + "2 - use both lists" + NEW_LINE + "3 - use no list" + NEW_LINE);
		preferredMods = new ArrayList<>(Arrays.asList(config.getStringList("preferredMods", CATEGORY_GENERAL, new String[] { "minecraft", "thermalfoundation", "immersiveengineering", "embers" }, "Preferred Mods" + NEW_LINE)));
		blacklistMods = new ArrayList<>(Arrays.asList(config.getStringList("blacklistMods", CATEGORY_GENERAL, new String[] { "chisel", "astralsorcery" }, "Blacklisted Mods" + NEW_LINE)));
		drop = config.getBoolean("drop", "unifyEvent", true, "Unify when items drop.");
		harvest = config.getBoolean("harvest", "unifyEvent", true, "Unify when blocks are harvested.");
		death = config.getBoolean("death", "unifyEvent", false, "Unify drops when entities die.");
		second = config.getBoolean("second", "unifyEvent", false, "Unify every second items in player's inventory.");
		gui = config.getBoolean("gui", "unifyEvent", true, "Unify items in player's inventory when GUI is opened/closed.");
		useUnidict = config.getBoolean("useUnidict", CATEGORY_GENERAL, true, "Use UniDict's settings to unify. (Other settings from this mod will be ignored.)") && Loader.isModLoaded("unidict");
		List<List<String>> alts = Arrays.stream(config.getStringList("alternatives", CATEGORY_GENERAL, new String[] { "aluminum aluminium bauxite" }, "OreDict names that should be unified even if they are different." + NEW_LINE)).map(s -> Arrays.stream(s.trim().split("\\s+")).filter(ss -> !ss.isEmpty()).collect(Collectors.toList())).collect(Collectors.toList());
		for (List<String> lis : alts) {
			for (String n : lis) {
				List<String> copy = new ArrayList<>(lis);
				copy.remove(n);
				if (!copy.isEmpty())
					alternatives.put(n, copy);
			}
		}
		if (config.hasChanged())
			config.save();
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		if (useUnidict)
			resourceHandler = UniDict.getResourceHandler();
	}

	@Mod.EventHandler
	public void serverStart(FMLServerStartingEvent event) {
		if (useUnidict)
			try {
				((ResourceHandler) resourceHandler).populateIndividualStackAttributes();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void spawn(EntityJoinWorldEvent event) {
		if (drop && event.getEntity() instanceof EntityItem && !event.getWorld().isRemote) {
			EntityItem ei = (EntityItem) event.getEntity();
			ei.setItem(replace(ei.getItem()));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void harvest(HarvestDropsEvent event) {
		if (harvest)
			try {
				event.getDrops().replaceAll(InstantUnify::replace);
			} catch (UnsupportedOperationException e) {
				Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
				LogManager.getLogger(MODID).warn("Drops of " + block + " can't be replaced.");
			}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void death(LivingDropsEvent event) {
		if (death && !event.getEntity().world.isRemote)
			event.getDrops().forEach(ei -> ei.setItem(replace(ei.getItem())));
	}

	@SubscribeEvent
	public static void player(PlayerTickEvent event) {
		if (second && event.phase == Phase.END && !event.player.world.isRemote && event.player.ticksExisted % 20 == 0) {
			boolean changed = false;
			for (int i = 0; i < event.player.inventory.getSizeInventory(); i++) {
				ItemStack slot = event.player.inventory.getStackInSlot(i);
				Optional<ItemStack> op = replaceOptional(slot);
				if (op.isPresent()) {
					event.player.inventory.setInventorySlotContents(i, op.get());
					changed = true;
				}
			}
			if (changed && event.player.openContainer != null)
				event.player.openContainer.detectAndSendChanges();
		}
	}

	@SubscribeEvent
	public static void open(PlayerContainerEvent event) {
		if (gui && !event.getEntityPlayer().world.isRemote) {
			event.getContainer().inventorySlots.stream().filter(slot -> slot.inventory instanceof InventoryPlayer).forEach(slot -> slot.putStack(replace(slot.getStack())));
			event.getContainer().detectAndSendChanges();
		}
	}

	private static ItemStack replace(ItemStack orig) {
		Optional<ItemStack> op = replaceOptional(orig);
		return op.isPresent() ? op.get() : orig;
	}

	private static Optional<ItemStack> replaceOptional(ItemStack orig) {
		if (useUnidict && resourceHandler != null) {
			ItemStack res = ((ResourceHandler) resourceHandler).getMainItemStack(orig);
			return res.isEmpty() || res.isItemEqual(orig) ? Optional.empty() : Optional.of(res);
		}
		if (orig.isEmpty() || blacklistMods.contains(orig.getItem().getRegistryName().getResourceDomain()))
			return Optional.empty();
		final String[] oreNames = oreNames(orig);
		if (oreNames.length == 0)
			return Optional.empty();
		for (String o : oreNames) {
			if ((listMode == 0 || listMode == 2) && !whitelist.stream().anyMatch(s -> Pattern.matches(s, o)))
				return Optional.empty();
			if ((listMode == 1 || listMode == 2) && blacklist.stream().anyMatch(s -> Pattern.matches(s, o)))
				return Optional.empty();
		}
		List<ItemStack> stacks = OreDictionary.getOres(oreNames[0]).stream().sorted((s1, s2) -> {
			int i1 = preferredMods.indexOf(s1.getItem().getRegistryName().getResourceDomain()), i2 = preferredMods.indexOf(s2.getItem().getRegistryName().getResourceDomain());
			return Integer.compare(i1 == -1 ? 999 : i1, i2 == -1 ? 999 : i2);
		}).collect(Collectors.toList());
		if (stacks.stream().map(s -> s.getItem().getRegistryName().getResourceDomain()).distinct().count() == 1)
			return Optional.empty();
		for (ItemStack s : stacks) {
			if (Arrays.equals(oreNames, oreNames(s))) {
				if (s.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
					if (s.getItem() == orig.getItem())
						return Optional.empty();
				} else {
					ItemStack res = ItemHandlerHelper.copyStackWithSize(s, orig.getCount());
					return res.isItemEqual(orig) ? Optional.empty() : Optional.of(res);
				}
			}
		}
		return Optional.empty();
	}

	private static String[] oreNames(ItemStack s) {
		List<String> ores = Arrays.stream(OreDictionary.getOreIDs(s)).mapToObj(OreDictionary::getOreName).collect(Collectors.toList());
		for (String ore : new ArrayList<>(ores)) {
			for (Entry<String, List<String>> e : alternatives.entrySet()) {
				String key = e.getKey();
				if (ore.contains(key)) {
					List<String> val = e.getValue();
					for (String alt : val)
						ores.add(ore.replace(key, alt));
				}
			}
		}
		return ores.stream().distinct().sorted().toArray(String[]::new);

	}

}
