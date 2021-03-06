package astavie.thermallogistics.util;

import astavie.thermallogistics.ThermalLogistics;
import astavie.thermallogistics.attachment.ICrafter;
import astavie.thermallogistics.attachment.IRequester;
import astavie.thermallogistics.util.collection.EmptyList;
import astavie.thermallogistics.util.collection.FluidList;
import astavie.thermallogistics.util.collection.ItemList;
import astavie.thermallogistics.util.collection.StackList;
import astavie.thermallogistics.util.type.Type;
import cofh.thermaldynamics.duct.Attachment;
import cofh.thermaldynamics.duct.fluid.DuctUnitFluid;
import cofh.thermaldynamics.duct.fluid.GridFluid;
import cofh.thermaldynamics.duct.item.DuctUnitItem;
import cofh.thermaldynamics.duct.item.GridItem;
import cofh.thermaldynamics.duct.item.StackMap;
import cofh.thermaldynamics.multiblock.MultiBlockGrid;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

public class Snapshot {

	public static final Snapshot INSTANCE = new Snapshot();

	private long globalTick;
	private Map<MultiBlockGrid<?>, Long> tick = new HashMap<>();

	// static

	private Multimap<GridItem, IItemHandler> inventories = HashMultimap.create();
	private Multimap<GridFluid, IFluidHandler> tanks = HashMultimap.create();

	// experimental

	private Map<GridItem, ItemList> items = new HashMap<>();
	private Map<GridFluid, FluidList> fluids = new HashMap<>();

	private Map<GridItem, ItemList> itemsMutated = new HashMap<>();
	private Map<GridFluid, FluidList> fluidsMutated = new HashMap<>();
	private Map<RequesterReference<?>, StackList<?>> leftoversMutated = new HashMap<>();

	private Snapshot() {
	}

	public void clearMutated() {
		itemsMutated.clear();
		fluidsMutated.clear();
		leftoversMutated.clear();
	}

	public void applyMutated() {
		items = itemsMutated;
		fluids = fluidsMutated;

		for (Map.Entry<RequesterReference<?>, StackList<?>> entry : leftoversMutated.entrySet()) {
			applyLeftover(entry.getKey(), entry.getValue());
		}

		itemsMutated = new HashMap<>();
		fluidsMutated = new HashMap<>();
		leftoversMutated = new HashMap<>();
	}

	private void refresh(World world) {
		if (globalTick < world.getTotalWorldTime() + ThermalLogistics.INSTANCE.refreshDelay) {
			globalTick = world.getTotalWorldTime();
		} else return;

		for (Iterator<MultiBlockGrid<?>> iterator = tick.keySet().iterator(); iterator.hasNext(); ) {
			MultiBlockGrid<?> grid = iterator.next();
			if (!grid.worldGrid.tickingGrids.contains(grid)) {
				iterator.remove();

				inventories.removeAll(grid);
				tanks.removeAll(grid);

				//noinspection SuspiciousMethodCalls
				items.remove(grid);
				//noinspection SuspiciousMethodCalls
				fluids.remove(grid);
			}
		}
	}

	private void refresh(GridItem grid) {
		World world = grid.worldGrid.worldObj;

		refresh(world);

		if (tick.getOrDefault(grid, 0L) < world.getTotalWorldTime() + ThermalLogistics.INSTANCE.refreshDelay) {
			tick.put(grid, world.getTotalWorldTime());
		} else return;

		// CACHE INVENTORIES

		Collection<IItemHandler> handlers = inventories.get(grid);
		handlers.clear();

		items.computeIfAbsent(grid, g -> new ItemList());
		ItemList list = items.get(grid);
		list.clear();

		Set<IRequester<ItemStack>> requesters = new HashSet<>();

		for (DuctUnitItem duct : grid.nodeSet) {
			for (byte side = 0; side < 6; side++) {
				if ((!duct.isInput(side) && !duct.isOutput(side)) || !duct.parent.getConnectionType(side).allowTransfer)
					continue;

				DuctUnitItem.Cache cache = duct.tileCache[side];
				if (cache == null)
					continue;

				// Cache requesters crafters

				Attachment attachment = duct.parent.getAttachment(side);
				if (attachment != null) {
					StackHandler.addRequesters(requesters, attachment);
					StackHandler.addCraftable(list, attachment);

					if (!attachment.canSend())
						continue;
				}

				StackHandler.addRequesters(requesters, cache.tile);
				StackHandler.addCraftable(list, cache.tile);

				// Cache inventories

				IItemHandler inv = cache.getItemHandler(side ^ 1);
				if (inv != null && !handlers.contains(inv)) {
					handlers.add(inv);

					for (int slot = 0; slot < inv.getSlots(); slot++) {
						ItemStack extract = inv.getStackInSlot(slot);
						if (extract.isEmpty())
							continue;
						list.add(extract);
					}
				}
			}
		}

		// REMOVE REQUESTED ITEMS

		Map<IRequester<ItemStack>, ItemList> leftovers = new HashMap<>();

		// first complete stack list
		for (IRequester<ItemStack> requester : requesters) {
			StackList<ItemStack> requested = requester.getRequestedStacks();
			for (Type<ItemStack> type : requested.types()) {
				long leftover = list.remove(type, requested.amount(type));
				if (leftover > 0) {
					leftovers.computeIfAbsent(requester, r -> new ItemList());
					leftovers.get(requester).add(type, leftover);
				}
			}
		}

		// then cancel leftovers
		for (Map.Entry<IRequester<ItemStack>, ItemList> entry : leftovers.entrySet()) {

			StackMap travelling = grid.travelingItems.getOrDefault(entry.getKey().getDestination(), new StackMap());
			for (ItemStack item : travelling.getItems())
				entry.getValue().remove(item);

			for (Type<ItemStack> type : entry.getValue().types()) {
				long amount = entry.getValue().amount(type);
				entry.getKey().onFail(type, amount);
			}
		}
	}

	private void refresh(GridFluid grid) {
		World world = grid.worldGrid.worldObj;

		refresh(world);

		if (tick.getOrDefault(grid, 0L) < world.getTotalWorldTime() + ThermalLogistics.INSTANCE.refreshDelay) {
			tick.put(grid, world.getTotalWorldTime());
		} else return;

		// CACHE TANKS

		Collection<IFluidHandler> handlers = tanks.get(grid);
		handlers.clear();

		fluids.computeIfAbsent(grid, g -> new FluidList());
		FluidList list = fluids.get(grid);
		list.clear();

		if (grid.hasValidFluid()) {
			list.add(grid.getFluid());
		}

		Set<IRequester<FluidStack>> requesters = new HashSet<>();

		for (DuctUnitFluid duct : grid.nodeSet) {
			for (byte k = 0; k < 6; k++) {
				byte side = (byte) ((k + duct.internalSideCounter) % 6);

				if ((!duct.isInput(side) && !duct.isOutput(side)) || !duct.parent.getConnectionType(side).allowTransfer)
					continue;

				DuctUnitFluid.Cache cache = duct.tileCache[side];
				if (cache == null)
					continue;

				// Cache requesters and crafters

				Attachment attachment = duct.parent.getAttachment(side);
				if (attachment != null) {
					StackHandler.addRequesters(requesters, attachment);
					StackHandler.addCraftable(list, attachment);

					if (!attachment.canSend())
						continue;
				}

				StackHandler.addRequesters(requesters, cache.tile);
				StackHandler.addCraftable(list, cache.tile);

				// Cache tanks

				IFluidHandler inv = cache.getHandler(side ^ 1);
				if (inv != null && !handlers.contains(inv)) {
					handlers.add(inv);

					for (IFluidTankProperties tank : inv.getTankProperties()) {
						FluidStack extract = tank.getContents();
						if (extract == null)
							continue;
						list.add(extract);
					}
				}
			}
		}

		// REMOVE REQUESTED FLUIDS

		Map<IRequester<FluidStack>, FluidList> leftovers = new HashMap<>();

		// first complete stack list
		for (IRequester<FluidStack> requester : requesters) {
			StackList<FluidStack> requested = requester.getRequestedStacks();
			for (Type<FluidStack> type : requested.types()) {
				long leftover = list.remove(type, requested.amount(type));
				if (leftover > 0) {
					leftovers.computeIfAbsent(requester, r -> new FluidList());
					leftovers.get(requester).add(type, leftover);
				}
			}
		}

		// then cancel leftovers
		for (Map.Entry<IRequester<FluidStack>, FluidList> entry : leftovers.entrySet()) {
			for (Type<FluidStack> type : entry.getValue().types()) {
				long amount = entry.getValue().amount(type);
				entry.getKey().onFail(type, amount);
			}
		}
	}

	public Collection<IItemHandler> getInventories(GridItem grid) {
		refresh(grid);
		return inventories.get(grid);
	}

	public Collection<IFluidHandler> getTanks(GridFluid grid) {
		refresh(grid);
		return tanks.get(grid);
	}

	@SuppressWarnings("unchecked")
	public <I> StackList<I> getStacks(MultiBlockGrid<?> grid) {
		if (grid instanceof GridItem)
			return (StackList<I>) getItems((GridItem) grid);
		else if (grid instanceof GridFluid)
			return (StackList<I>) getFluids((GridFluid) grid);
		return null;
	}

	public ItemList getItems(GridItem grid) {
		refresh(grid);
		return items.getOrDefault(grid, new ItemList());
	}

	public FluidList getFluids(GridFluid grid) {
		refresh(grid);
		return fluids.getOrDefault(grid, new FluidList());
	}

	// Auto-crafting

	@SuppressWarnings("unchecked")
	public <I> StackList<I> getMutatedStacks(MultiBlockGrid<?> grid) {
		if (grid instanceof GridItem)
			return (StackList<I>) getMutatedItems((GridItem) grid);
		else if (grid instanceof GridFluid)
			return (StackList<I>) getMutatedFluids((GridFluid) grid);
		return null;
	}

	public ItemList getMutatedItems(GridItem grid) {
		itemsMutated.computeIfAbsent(grid, g -> {
			ItemList itemList = new ItemList();
			itemList.addAll(getItems(grid));
			return itemList;
		});
		return itemsMutated.get(grid);
	}

	public FluidList getMutatedFluids(GridFluid grid) {
		fluidsMutated.computeIfAbsent(grid, g -> {
			FluidList itemList = new FluidList();
			itemList.addAll(getFluids(grid));
			return itemList;
		});
		return fluidsMutated.get(grid);
	}

	@SuppressWarnings("unchecked")
	public <I> StackList<I> getLeftovers(RequesterReference<I> reference) {
		leftoversMutated.computeIfAbsent(reference, ref -> {
			IRequester<I> requester = reference.get();
			if (requester instanceof ICrafter) {
				return ((ICrafter<I>) requester).getLeftovers().copy();
			}
			return EmptyList.getInstance();
		});

		return (StackList<I>) leftoversMutated.get(reference);
	}

	@SuppressWarnings("unchecked")
	private <I> void applyLeftover(RequesterReference<I> reference, StackList<?> list) {
		((ICrafter<I>) reference.get()).applyLeftovers((StackList<I>) list);
	}

}
