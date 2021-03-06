package astavie.thermallogistics.util.type;

import codechicken.lib.fluid.FluidUtils;
import cofh.core.network.PacketBase;
import cofh.core.util.helpers.StringHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FluidType implements Type<FluidStack> {

	private final FluidStack compare;

	public FluidType(FluidStack compare) {
		this.compare = compare == null ? null : FluidUtils.copy(compare, 1);
	}

	public static FluidType readPacket(PacketBase packet) {
		return new FluidType(packet.getFluidStack());
	}

	public static void writePacket(FluidType type, PacketBase packet) {
		packet.addFluidStack(type.compare);
	}

	public static FluidType readNbt(NBTTagCompound tag) {
		return new FluidType(FluidStack.loadFluidStackFromNBT(tag));
	}

	public static NBTTagCompound writeNbt(FluidType type) {
		return type.compare.writeToNBT(new NBTTagCompound());
	}

	@Override
	public FluidStack getAsStack() {
		return compare;
	}

	@Override
	public FluidStack withAmount(int amount) {
		return FluidUtils.copy(compare, amount);
	}

	@Override
	public String getDisplayName() {
		return compare.getLocalizedName();
	}

	@Override
	public String getMissingLocalization(long amount) {
		return StringHelper.localizeFormat("gui.logistics.terminal.missing.fluid", amount, getDisplayName());
	}

	@Override
	public void writePacket(PacketBase packet) {
		writePacket(this, packet);
	}

	@Override
	public int getPacketId() {
		return 1;
	}

	@Override
	public NBTTagCompound writeNbt() {
		return writeNbt(this);
	}

	@Override
	public boolean references(FluidStack stack) {
		return compare.isFluidEqual(stack);
	}

	@Override
	public int maxSize() {
		return Fluid.BUCKET_VOLUME;
	}

	@Override
	public boolean isNothing() {
		return compare == null;
	}

	@Override
	public boolean isIdentical(Type<FluidStack> other, boolean ignoreMod, boolean ignoreOreDict, boolean ignoreMetadata, boolean ignoreNbt) {
		return compare.getFluid() == other.getAsStack().getFluid() && (ignoreNbt || FluidStack.areFluidStackTagsEqual(compare, other.getAsStack()));
	}

	@Override
	public int normalSize() {
		return Fluid.BUCKET_VOLUME;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FluidType && compare.isFluidEqual(((FluidType) obj).compare);
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(compare.getFluid()).append(compare.tag).build();
	}

}
