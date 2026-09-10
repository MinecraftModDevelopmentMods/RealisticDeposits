package zone.moddev.mc.realisticdeposits.client;

import java.io.IOException;

import zone.moddev.mc.realisticdeposits.RealisticDeposits;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.config.RuntimeState;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** Read-only alpha configuration summary with an explicit reload action. */
public final class RealisticDepositsConfigScreen extends GuiScreen {
	private static final int DONE = 0;
	private static final int RELOAD = 1;
	private final GuiScreen parent;
	private String message = "";

	public RealisticDepositsConfigScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		int y = height - 32;
		buttonList.add(new GuiButton(RELOAD, width / 2 - 154, y, 150, 20,
				I18n.format("button.realisticdeposits.reload")));
		buttonList.add(new GuiButton(DONE, width / 2 + 4, y, 150, 20,
				I18n.format("gui.done")));
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == DONE) {
			mc.displayGuiScreen(parent);
		} else if (button.id == RELOAD) {
			try {
				RealisticDepositsRuntime.reload();
				message = I18n.format("status.realisticdeposits.reload_ok");
			} catch (RuntimeException e) {
				message = I18n.format("status.realisticdeposits.reload_failed", e.getMessage());
			}
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		RuntimeState state = RealisticDepositsRuntime.state();
		drawCenteredString(fontRendererObj, I18n.format("screen.realisticdeposits.title"), width / 2, 24, 0xFFFFFF);
		drawCenteredString(fontRendererObj, "Version " + RealisticDeposits.VERSION,
				width / 2, 48, 0xA0A0A0);
		drawCenteredString(fontRendererObj, I18n.format("screen.realisticdeposits.loaded",
				state.catalogs().size(), state.definitions().size()), width / 2, 72, 0xFFFFFF);
		drawCenteredString(fontRendererObj, I18n.format("screen.realisticdeposits.alpha_status"),
				width / 2, 96, 0xFFD080);
		drawCenteredString(fontRendererObj, I18n.format("screen.realisticdeposits.worldgen_disabled"),
				width / 2, 112, 0xFFD080);
		drawCenteredString(fontRendererObj, I18n.format("screen.realisticdeposits.orespawn_ui_pending"),
				width / 2, 136, 0xA0A0A0);
		if (state.configRoot() != null) {
			drawCenteredString(fontRendererObj, trim(state.configRoot().getPath(), 90),
					width / 2, 164, 0x808080);
		}
		if (!message.isEmpty()) drawCenteredString(fontRendererObj, message, width / 2, 188, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private static String trim(String value, int length) {
		return value.length() <= length ? value : "..." + value.substring(value.length() - length + 3);
	}
}
