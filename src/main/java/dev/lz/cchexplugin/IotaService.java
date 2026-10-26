package dev.lz.cchexplugin;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.GarbageIota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dan200.computercraft.api.lua.LuaException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Consumer;

final class IotaService {
    private static final int MAX_SNBT_LENGTH = 262_144;

    private IotaService() {}

    static Object[] read(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return failRead("empty_slot");
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(stack);
        if (holder == null) return failRead("not_iota_holder");

        CompoundTag tag = holder.readIotaTag();
        if (tag == null) {
            Iota empty = holder.emptyIota();
            if (empty == null) return failRead("empty_iota");
            tag = IotaType.serialize(empty);
        }
        IotaType<?> type = IotaType.getTypeFromTag(tag);
        if (type == null || !tag.contains(HexIotaTypes.KEY_DATA)) return failRead("invalid_iota");
        try {
            Iota decoded = IotaType.deserialize(tag, level);
            if (decoded == null || decoded.getType() != type) return failRead("invalid_iota");
        } catch (RuntimeException ex) {
            return failRead("invalid_iota");
        }
        ResourceLocation id = HexIotaTypes.REGISTRY.getKey(type);
        if (id == null) return failRead("invalid_iota");
        return new Object[] { Map.of("type", id.toString(), "snbt", tag.toString()) };
    }

    static Object[] type(ItemStack stack, ServerLevel level) {
        Object[] result = read(stack, level);
        if (result[0] == null) return result;
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) result[0];
        return new Object[] { data.get("type") };
    }

    static Object[] write(ItemStack original, ServerLevel level, String snbt, boolean simulate,
                          Consumer<ItemStack> commit)
        throws LuaException {
        if (original.isEmpty()) return failWrite("empty_slot");
        if (original.getCount() != 1) return failWrite("stack_count_not_one");

        ItemStack copy = original.copy();
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(copy);
        if (holder == null) return failWrite("not_iota_holder");
        Iota iota = parse(snbt, level);
        if (!holder.writeIota(iota, true)) return failWrite("item_rejected_iota");
        if (simulate) return new Object[] { true };
        if (!holder.writeIota(iota, false)) return failWrite("item_rejected_iota");
        commit.accept(copy);
        return new Object[] { true };
    }

    static boolean isHolder(ItemStack stack) {
        return !stack.isEmpty() && IXplatAbstractions.INSTANCE.findDataHolder(stack) != null;
    }

    private static Iota parse(String snbt, ServerLevel level) throws LuaException {
        if (snbt == null || snbt.isEmpty() || snbt.length() > MAX_SNBT_LENGTH)
            throw new LuaException("invalid_iota_snbt: expected 1 to " + MAX_SNBT_LENGTH + " characters");
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            IotaType<?> type = IotaType.getTypeFromTag(tag);
            if (type == null || !tag.contains(HexIotaTypes.KEY_DATA))
                throw new LuaException("invalid_iota_snbt: missing or unknown type/data");
            // A deserializer may turn malformed data into a different Iota. Reject that instead of
            // silently replacing the requested value with garbage or null.
            Iota iota = IotaType.deserialize(tag, level);
            if (iota == null || iota.getType() != type)
                throw new LuaException("invalid_iota_snbt: data does not match type");
            if (!isSafeToConstruct(iota, 0))
                throw new LuaException("unsupported_iota_type");
            return iota;
        } catch (CommandSyntaxException ex) {
            throw new LuaException("invalid_iota_snbt: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw new LuaException("invalid_iota_snbt: " + ex.getMessage());
        }
    }

    private static boolean isSafeToConstruct(Iota iota, int depth) {
        if (depth > 16) return false;
        if (iota instanceof ListIota list) {
            for (Iota element : list.getList()) {
                if (!isSafeToConstruct(element, depth + 1)) return false;
            }
            return true;
        }
        return iota instanceof NullIota || iota instanceof GarbageIota
            || iota instanceof DoubleIota || iota instanceof BooleanIota
            || iota instanceof Vec3Iota || iota instanceof PatternIota;
    }

    private static Object[] failRead(String code) { return new Object[] { null, code }; }
    private static Object[] failWrite(String code) { return new Object[] { false, code }; }
}
