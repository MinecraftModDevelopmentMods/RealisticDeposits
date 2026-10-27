package zone.moddev.mc.realisticdeposits.client;

import java.io.IOException;
import java.util.List;

import zone.moddev.mc.realisticdeposits.RealisticDeposits;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.config.RuntimeState;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** Configuration summary with live geometry/alignment reload. */
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
		int contentWidth = contentWidth(width);
		int gap = 8;
		int buttonWidth = Math.min(150, (contentWidth - gap) / 2);
		int left = (width - (buttonWidth * 2 + gap)) / 2;
		buttonList.add(new GuiButton(RELOAD, left, y, buttonWidth, 20,
				I18n.format("button.realisticdeposits.reload")));
		buttonList.add(new GuiButton(DONE, left + buttonWidth + gap, y, buttonWidth, 20,
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
		int contentWidth = contentWidth(width);
		int y = 18;
		drawCenteredString(fontRendererObj, I18n.format("screen.realisticdeposits.title"),
				width / 2, y, 0xFFFFFF);
		y += 24;
		drawCenteredString(fontRendererObj, "Version " + RealisticDeposits.VERSION,
				width / 2, y, 0xA0A0A0);
		y += 22;
		y = drawWrappedCentered(I18n.format("screen.realisticdeposits.loaded",
				state.catalogs().size(), state.definitions().size()), y, contentWidth, 0xFFFFFF);
		y += 8;
		y = drawWrappedCentered(I18n.format("screen.realisticdeposits.generation_status",
				state.integrationStatus().name().toLowerCase()), y, contentWidth, 0xFFD080);
		y += 4;
		y = drawWrappedCentered(I18n.format("screen.realisticdeposits.reload_policy"),
				y, contentWidth, 0xFFD080);
		y += 8;
		y = drawWrappedCentered(I18n.format("screen.realisticdeposits.orespawn_ui"),
				y, contentWidth, 0xA0A0A0);
		if (state.configRoot() != null) {
			y += 8;
			drawCenteredString(fontRendererObj,
					trimToWidth(state.configRoot().getPath(), contentWidth),
					width / 2, y, 0x808080);
			y += 10;
		}
		if (!message.isEmpty()) {
			List<String> lines = fontRendererObj.listFormattedStringToWidth(message, contentWidth);
			int messageY = Math.min(y + 8, height - 42 - lines.size() * 10);
			drawWrappedCentered(lines, messageY, 0xFFFFFF);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private int drawWrappedCentered(String value, int y, int maximumWidth, int color) {
		return drawWrappedCentered(fontRendererObj.listFormattedStringToWidth(value, maximumWidth), y, color);
	}

	private int drawWrappedCentered(List<String> lines, int y, int color) {
		for (String line : lines) {
			drawCenteredString(fontRendererObj, line, width / 2, y, color);
			y += 10;
		}
		return y;
	}

	private String trimToWidth(String value, int maximumWidth) {
		if (fontRendererObj.getStringWidth(value) <= maximumWidth) return value;
		String prefix = "...";
		int start = 0;
		while (start < value.length()
				&& fontRendererObj.getStringWidth(prefix + value.substring(start)) > maximumWidth) {
			start++;
		}
		return prefix + value.substring(start);
	}

	static int contentWidth(int screenWidth) {
		return Math.min(390, Math.max(120, screenWidth - 32));
	}
}
