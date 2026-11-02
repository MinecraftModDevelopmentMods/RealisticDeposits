package zone.moddev.mc.realisticdeposits.client;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import zone.moddev.mc.orespawn.api.client.WorldSettingsExtension;
import zone.moddev.mc.orespawn.api.client.WorldSettingsExtensionRegistry;
import zone.moddev.mc.realisticdeposits.content.ModContent;

/** A small disposable client world for the gravel model, screen and save checks. */
@Mod(modid = "rdgoldclientprobe", name = "Gold Client Checks", version = "1",
		dependencies = "required-after:realisticdeposits", clientSideOnly = true)
public final class GoldClientProbe {
	private static final BlockPos GRAVEL = new BlockPos(8, 100, 8);
	private GuiScreen parent;
	private RealisticDepositsConfigScreen screen;
	private int phase;
	private int ticks;
	private int frames;
	private volatile boolean placed;
	private volatile Throwable failure;

	@Mod.EventHandler
	public void initialize(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void rendered(RenderWorldLastEvent event) {
		frames++;
	}

	@SubscribeEvent
	public void tick(TickEvent.ClientTickEvent event) throws Exception {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		Block gold = ModContent.GOLD_GRAVEL;
		Block diamond = ModContent.DIAMOND_GRAVEL;
		if (++ticks > 3600 || failure != null) {
			if (failure != null) failure.printStackTrace();
			System.err.println("RD_GOLD_CLIENT_FAILED phase=" + phase);
			mc.shutdown();
			return;
		}

		if (phase == 0 && mc.currentScreen instanceof GuiMainMenu) {
			mc.gameSettings.pauseOnLostFocus = false;
			mc.gameSettings.renderDistanceChunks = 3;
			parent = mc.currentScreen;
			List<WorldSettingsExtension> extensions = WorldSettingsExtensionRegistry.extensions().stream()
					.filter(entry -> "realisticdeposits".equals(entry.id().getResourceDomain()))
					.collect(Collectors.toList());
			if (extensions.size() != 1) throw new IllegalStateException("Expected one settings entry");
			screen = (RealisticDepositsConfigScreen) extensions.get(0).createScreen(parent);
			if (new RealisticDepositsGuiFactory().mainConfigGuiClass() != screen.getClass()) {
				throw new IllegalStateException("The two settings entry points differ");
			}
			mc.displayGuiScreen(screen);
			phase = 1;
			ticks = 0;
		} else if (phase == 1 && ticks > 20) {
			java.lang.reflect.Method done = screen.getClass().getDeclaredMethod("func_146284_a", GuiButton.class);
			done.setAccessible(true);
			done.invoke(screen, new GuiButton(0, 0, 0, "Done"));
			if (mc.currentScreen != parent) throw new IllegalStateException("Settings lost their parent screen");
			phase = 2;
			ticks = 0;
		} else if ((phase == 2 || (phase == 5 && ticks > 40)) && mc.currentScreen instanceof GuiMainMenu
				&& !mc.isIntegratedServerRunning()) {
			if (phase == 5) {
				ResourcePackRepository repository = mc.getResourcePackRepository();
				repository.updateRepositoryEntriesAll();
				ResourcePackRepository.Entry testPack = repository.getRepositoryEntriesAll().stream()
						.filter(entry -> "gold-host-test.zip".equals(entry.getResourcePackName()))
						.findFirst().orElseThrow(() -> new IllegalStateException("The host-texture test pack is missing"));
				List<ResourcePackRepository.Entry> selected = new ArrayList<>(repository.getRepositoryEntries());
				selected.add(testPack);
				repository.setRepositories(selected);
				mc.gameSettings.resourcePacks.add(testPack.getResourcePackName());
				mc.refreshResources();
			}

			mc.launchIntegratedServer("gold-client-world", "Gold Client Checks",
					new WorldSettings(3346071746553592696L, GameType.CREATIVE, true, false, WorldType.FLAT));
			placed = false;
			phase++;
			ticks = 0;
			frames = 0;
		} else if ((phase == 3 || phase == 6) && mc.player != null) {
			final boolean reload = phase == 6;
			phase++;
			mc.getIntegratedServer().addScheduledTask(() -> {
				try {
					net.minecraft.entity.player.EntityPlayerMP player = mc.getIntegratedServer().getPlayerList()
							.getPlayerByUUID(mc.player.getUniqueID());
					if (reload) {
						if (player.getServerWorld().getBlockState(GRAVEL).getBlock() != ModContent.GOLD_GRAVEL
								|| !"Saved prospecting gravel".equals(player.inventory.getStackInSlot(0).getDisplayName())) {
							throw new IllegalStateException("Gold gravel or its saved name changed on reload");
						}
					} else {
						for (int x = 4; x <= 12; x++) {
							for (int z = 4; z <= 14; z++) {
								player.getServerWorld().setBlockState(new BlockPos(x, 99, z), Blocks.STONE.getDefaultState(), 2);
							}
						}
						player.getServerWorld().setBlockState(GRAVEL, gold.getDefaultState(), 2);
						player.getServerWorld().setBlockState(GRAVEL.east(2), diamond.getDefaultState(), 2);
						ItemStack saved = new ItemStack(Item.getItemFromBlock(ModContent.GOLD_GRAVEL), 7);
						saved.setStackDisplayName("Saved prospecting gravel");
						player.inventory.setInventorySlotContents(0, saved);
					}
					player.connection.setPlayerLocation(8.5, 101, 13.5, 180, 25);
					placed = true;
				} catch (Throwable error) {
					failure = error;
				}
			});
			ticks = 0;
		} else if ((phase == 4 || phase == 7) && placed && ticks > 100 && frames > 20
				&& mc.world != null && mc.world.getBlockState(GRAVEL).getBlock() == ModContent.GOLD_GRAVEL) {
			IBakedModel model = mc.getBlockRendererDispatcher().getBlockModelShapes()
					.getModelForState(gold.getDefaultState());
			for (EnumFacing face : EnumFacing.values()) {
				if (model.getQuads(gold.getDefaultState(), face, 0).size() != 4) {
					throw new IllegalStateException("The gravel face or its three gold flecks did not bake: " + face);
				}
			}
			if (!"minecraft:blocks/gravel".equals(model.getParticleTexture().getIconName())) {
				throw new IllegalStateException("The model lost its vanilla gravel background");
			}
			boolean replaced = Arrays.deepEquals(model.getParticleTexture().getFrameTextureData(0),
					mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/diamond_block").getFrameTextureData(0));
			if (replaced != (phase == 7)) {
				throw new IllegalStateException("The resource pack did not replace only the reloaded gravel host");
			}
			ScreenShotHelper.saveScreenshot(new File("."), "gold-client-" + phase + ".png",
					mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
			// Pause before leaving so queued world packets finish while the world is still loaded.
			mc.displayGuiScreen(new GuiIngameMenu());
			phase = phase == 4 ? 9 : 10;
			ticks = 0;
		} else if ((phase == 9 || phase == 10) && ticks > 40) {
			if (!(mc.currentScreen instanceof GuiIngameMenu)) {
				throw new IllegalStateException("The pause menu did not open before saving");
			}

			java.lang.reflect.Method quit = GuiIngameMenu.class.getDeclaredMethod("func_146284_a", GuiButton.class);
			quit.setAccessible(true);
			quit.invoke(mc.currentScreen, new GuiButton(1, 0, 0, "Save and Quit to Title"));
			phase = phase == 9 ? 5 : 8;
			ticks = 0;
		} else if ((phase == 5 || phase == 8) && mc.world == null && !mc.isIntegratedServerRunning() && ticks > 40) {
			if (phase == 5) {
				mc.displayGuiScreen(parent);
			} else {
				Properties result = new Properties();
				result.setProperty("config_entry_points", "PASS");
				result.setProperty("model_faces", "4 per side");
				result.setProperty("fresh_render", "PASS");
				result.setProperty("reload_render_and_named_stack", "PASS");
				result.setProperty("resource_pack_host", "PASS");
				try (FileOutputStream output = new FileOutputStream("gold-client-pass.properties")) {
					result.store(output, "Gold packaged-client checks");
				}
				MinecraftForge.EVENT_BUS.unregister(this);
				mc.shutdown();
			}
		}
	}
}
