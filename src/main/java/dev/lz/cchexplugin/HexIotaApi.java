package dev.lz.cchexplugin;

import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.Map;

/** Global Lua API for Iota operations on inventories visible to this computer. */
public final class HexIotaApi implements ILuaAPI {
    private final IComputerSystem computer;

    public HexIotaApi(IComputerSystem computer) {
        this.computer = computer;
    }

    @Override
    public String[] getNames() {
        return new String[] { "hexIota" };
    }

    @LuaFunction(mainThread = true)
    public final Object[] readIota(String inventoryName, int slot) throws LuaException {
        InventoryTarget target = inventory(inventoryName);
        return IotaService.read(target.handler().getStackInSlot(index(target.handler(), slot)), target.level());
    }

    @LuaFunction(mainThread = true)
    public final Object[] getIotaType(String inventoryName, int slot) throws LuaException {
        InventoryTarget target = inventory(inventoryName);
        return IotaService.type(target.handler().getStackInSlot(index(target.handler(), slot)), target.level());
    }

    @LuaFunction(mainThread = true)
    public final boolean isIotaHolder(String inventoryName, int slot) throws LuaException {
        InventoryTarget target = inventory(inventoryName);
        return IotaService.isHolder(target.handler().getStackInSlot(index(target.handler(), slot)));
    }

    @LuaFunction(mainThread = true)
    public final Object[] canWriteIota(IArguments args) throws LuaException {
        return write(args.getString(0), args.getInt(1), snbt(args.get(2)), true);
    }

    @LuaFunction(mainThread = true)
    public final Object[] writeIota(IArguments args) throws LuaException {
        return write(args.getString(0), args.getInt(1), snbt(args.get(2)), false);
    }

    private static String snbt(Object value) throws LuaException {
        if (value instanceof String string) return string;
        if (value instanceof Map<?, ?> table && table.get("snbt") instanceof String string)
            return string;
        throw new LuaException("invalid_iota_argument: expected SNBT string or readIota result table");
    }

    private Object[] write(String inventoryName, int slot, String snbt, boolean simulate)
        throws LuaException {
        InventoryTarget target = inventory(inventoryName);
        IItemHandler handler = target.handler();
        int index = index(handler, slot);
        if (!(handler instanceof IItemHandlerModifiable mutable))
            return new Object[] { false, "inventory_read_only" };
        ItemStack stack = handler.getStackInSlot(index);
        return IotaService.write(stack, target.level(), snbt, simulate, updated -> {
            mutable.setStackInSlot(index, updated);
            target.markChanged().run();
        });
    }

    private InventoryTarget inventory(String name) throws LuaException {
        if (name == null || name.isBlank()) throw new LuaException("invalid_peripheral_name");
        IPeripheral peripheral = computer.getAvailablePeripheral(name);
        // In CC:T 1.114.0, IComputerSystem#getAvailablePeripheral returns null even when
        // peripheral.getNames() in Lua lists the side. Resolve local sides from the world.
        Object object = peripheral == null ? adjacentInventory(name) : peripheral.getTarget();
        if (object == null) throw new LuaException("peripheral_not_found: " + name);
        if (object instanceof BlockEntity blockEntity) {
            if (blockEntity.isRemoved() || !(blockEntity.getLevel() instanceof ServerLevel level))
                throw new LuaException("inventory_unloaded");
            IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler == null && blockEntity instanceof Container container)
                handler = new InvWrapper(container);
            if (handler == null) throw new LuaException("not_inventory: " + name);
            return new InventoryTarget(handler, level, blockEntity::setChanged);
        }
        if (object instanceof Container container)
            return new InventoryTarget(new InvWrapper(container), computer.getLevel(), container::setChanged);
        if (object instanceof IItemHandler handler)
            return new InventoryTarget(handler, computer.getLevel(), () -> {});
        throw new LuaException("not_inventory: " + name);
    }

    private BlockEntity adjacentInventory(String name) {
        Direction direction;
        if ("top".equals(name)) direction = Direction.UP;
        else if ("bottom".equals(name)) direction = Direction.DOWN;
        else {
            BlockState state = computer.getLevel().getBlockState(computer.getPosition());
            if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
            Direction front = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            direction = switch (name) {
                case "front" -> front;
                case "back" -> front.getOpposite();
                case "left" -> front.getCounterClockWise();
                case "right" -> front.getClockWise();
                default -> null;
            };
        }
        return direction == null ? null
            : computer.getLevel().getBlockEntity(computer.getPosition().relative(direction));
    }

    private static int index(IItemHandler inventory, int slot) throws LuaException {
        if (slot < 1 || slot > inventory.getSlots())
            throw new LuaException("slot_out_of_range: expected 1.." + inventory.getSlots());
        return slot - 1;
    }

    private record InventoryTarget(IItemHandler handler, ServerLevel level, Runnable markChanged) {}
}
