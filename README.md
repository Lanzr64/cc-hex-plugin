# CC Hex Inventory Bridge

Minecraft 1.20.1 / Forge 47 的服务端功能 mod。需要服务端已有 CC:Tweaked 1.114.0 与 Hex Casting 0.11.1；玩家客户端照常安装这两个内容 mod，但不需要安装本桥接 mod。本 mod 不注册新方块、物品或实体。

## 全局 `hexIota` API

每台 CC 计算机都有全局 `hexIota` API。库存通过相对于电脑的方位名指定：`top`、`bottom`、`front`、`back`、`left`、`right`，例如正上方箱子是 `"top"`。CC:Tweaked 1.114.0 的全局 API 上下文无法取得 `peripheral.getNames()` 中的外设对象，因此本版本通过世界坐标查找紧邻电脑的库存；有线网络名称（如 `"minecraft:chest_0"`）暂不支持。方法不会加到 `peripheral.wrap(name)` 返回的库存对象上。

| 方法 | 返回值 |
| --- | --- |
| `hexIota.readIota(inventoryName, slot)` | `{ type = "modid:type", snbt = "{...}" }`，失败时 `nil, errorCode` |
| `hexIota.getIotaType(inventoryName, slot)` | 类型 ID，失败时 `nil, errorCode` |
| `hexIota.isIotaHolder(inventoryName, slot)` | 此槽位是否是 Hex Iota 容器 |
| `hexIota.canWriteIota(inventoryName, slot, value)` | `true` 或 `false, errorCode`；不修改物品 |
| `hexIota.writeIota(inventoryName, slot, value)` | `true` 或 `false, errorCode` |

槽位编号与 CC 库存 API 一样从 **1** 开始。无效外设名、未找到外设、非库存外设、库存已卸载和槽位越界会分别抛出 `invalid_peripheral_name`、`peripheral_not_found`、`not_inventory`、`inventory_unloaded`、`slot_out_of_range` Lua 错误。其他常见返回错误码：`empty_slot`、`not_iota_holder`、`empty_iota`、`invalid_iota`、`item_rejected_iota`、`stack_count_not_one`、`inventory_read_only`。写入只支持能安全替换槽位的库存；封印焦点通常会返回 `item_rejected_iota`。

```lua
local iota, err = hexIota.readIota("left", 1)
if not iota then error(err) end

local ok, reason = hexIota.writeIota("right", 3, iota)
if not ok then error(reason) end
print("已复制", iota.type)
```

`value` 可以是 `readIota` 返回的 `{type, snbt}` 表，也可以直接是其中的 `snbt` 字符串。`snbt` 是 Hex Casting 序列化后的完整 Iota NBT。读取支持已安装模组能够反序列化的 Iota；写入支持基础的 null、garbage、数字、布尔、向量、图案和由这些值组成的列表。其他类型会返回 `unsupported_iota_type`，避免通过原始 NBT 伪造扩展模组的受限数据。写入限制输入长度为 262144 字符。此表与 Ducky Peripherals 接受的 Lua Iota 表结构不同。

## 构建

项目 `libs/` 中的 Hex Casting JAR 仅作开发期 API 依赖，不打包进本 mod。构建时 Gradle 会从 SquidDev Maven 取得 CC:Tweaked API。依照本机环境约定，将 Java 与 Gradle 缓存放在 `D:\ENV`：

```powershell
$env:JAVA_HOME='D:\ENV\jdk\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME='D:\ENV\gradle-cache'
.\gradlew.bat --project-cache-dir D:\ENV\gradle-project-cache\cc-hex-plugin build
```

产物位于 `build/libs/`。外设需要向 CC 暴露方块实体、库存容器或物品处理器作为目标；无法取得目标的自定义外设会报 `not_inventory`。
