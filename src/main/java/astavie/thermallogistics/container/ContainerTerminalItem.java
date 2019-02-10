package astavie.thermallogistics.container;

import astavie.thermallogistics.tile.TileTerminalItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

public class ContainerTerminalItem extends ContainerTerminal {

	public ContainerTerminalItem(TileTerminalItem tile, InventoryPlayer inventory) {
		super(tile, inventory);
	}

	@Override
	protected void addSlots() {
		for (int y = 0; y < 3; y++)
			for (int x = 0; x < 9; x++)
				addSlotToContainer(new Slot(((TileTerminalItem) super.tile).inventory, x + y * 9, 8 + x * 18, 100 + y * 18));
	}

	@Override
	protected int getPlayerInventoryVerticalOffset() {
		return 168;
	}

	@Override
	protected int getSizeInventory() {
		return 28;
	}

}
