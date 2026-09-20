# 生物部位拆解系统(mob part dismemberment)实施计划

## Context

CyberWare 1.21.1 移植版(w1shadel 的非官方移植,MIT,本仓库为用户自己的分支)目前只能往玩家身上装机械义体。用户要新增玩法:**把活体生物放进手术舱拆解成身体部位物品**(只拆表面部位:头/躯干/四肢,内脏不拆),拆完生物死亡,部位物品可直接装到玩家身上替换对应部位并改变玩家外观。效果加成(属性/药水类)留到下一期,本期打通"拆解→物品→3D 渲染→安装→外观"全链路。

已确认范围:
- 首批 8 种生物:zombie / husk / drowned / skeleton / stray / wither_skeleton(人形)+ creeper / spider(非人形验证)。末影人不可拴绳,排除
- 玩家部位系统扩展 HEAD/TORSO 槽位(108→126 格),含旧档迁移
- 拆解产物存手术舱,开门直接捡取

## 关键设计决策

1. **单一物品 + 数据组件**(不做 54 个物品 id):新物品 `mob_part`,新数据组件 `MOB_ID`(ResourceLocation)+ `PART_TYPE`(BodyPartType)。`MobPartItem extends CyberwareItem` 覆写 `getSlot/getBodyPartType/getEssenceCost` 按组件动态返回——surgeon 校验、SurgeryManager 冲突解析零改动。
2. **BodyPartType/BodyRegionEnum 末尾追加** `HEAD`、`TORSO`:旧存档槽位索引全部不变,迁移只是扩容。
3. **3D 渲染零贴图**:BEWLR(仿 [CyberSkullItemRenderer](file:///d:/cyberware/Cyberware-Reforged-1.21.1-dev/src/main/java/com/maxwell/cyber_ware_port/common/block/cyberskull/CyberSkullItemRenderer.java))用 `Minecraft.getModelSet().bakeLayer(ModelLayers.ZOMBIE/...)` 烘焙原版模型,贴图用原版实体贴图路径;按 PART_TYPE 只显示对应 part(humanoid/creeper/spider 模型的 part 字段都是 public)。
4. **拆解进舱方式**:栓绳把生物拉进舱(与玩家手术共用 `findPatient` 逻辑,放宽为 LivingEntity 且排除玩家,择最近实体);拆解中 `setNoAi()+setInvulnerable()`,完成后 `discard()`(不产生常规掉落)。

## 实施步骤

### P1 槽位扩展 + 存档迁移(风险最高,先做)
- `common\item\base\BodyPartType.java`:末尾追加 `HEAD, TORSO`
- `common\block\robosurgeon\BodyRegionEnum.java`:末尾追加 `HEAD, TORSO`(getTotalSlots 自动变 126,HEAD 起始槽 108、TORSO 117)
- `RobosurgeonBlockEntity`:补 `SLOT_HEAD = BodyRegionEnum.HEAD.getStartSlot()`、`SLOT_TORSO = ...` 常量
- `init\ModItems.java`:用 `registerHumanPart()` 模式注册 `HUMAN_HEAD`、`HUMAN_TORSO`(模型贴图先复用 `body_part_skin` 类风格占位)
- [CyberwareUserData.java](file:///d:/cyberware/Cyberware-Reforged-1.21.1-dev/src/main/java/com/maxwell/cyber_ware_port/common/capability/CyberwareUserData.java) `deserializeNBT`(L457):反序列化后若 `getSlots() < BodyRegionEnum.getTotalSlots()`,把旧 108 格内容复制进新的 126 格 handler(索引平移为零,直接按位拷贝),并给 HEAD/TORSO 槽补 `HUMAN_HEAD/HUMAN_TORSO`(旧档玩家默认还有头和躯干);`initializeDefaultParts`(L475)同样补两个默认部位
- `RobosurgeonMenu` 布局:确认 GUI 如何展示 108 槽(翻页/滚动),把 HEAD/TORSO 两区加进去
- 验证:`gradlew build` 通过;旧档进世界义体不丢;拆装 human head/torso 正常

### P2 MobPartItem + 数据组件
- `init\ModDataComponents.java`:仿 GHOST 模式注册 `MOB_ID`(ResourceLocation codec)、`PART_TYPE`(BodyPartType 枚举 codec)
- 新建 `common\item\cyberware\MobPartItem extends CyberwareItem`:`getEssenceCost/getSlot/getBodyPartType/isPristine` 读组件;物品名走动态翻译 key `item.cyber_ware_port.mob_part.<mob>.<part>`
- `init\ModItems.java`:注册单一 `MOB_PART`;`MobSurfaceParts` 注册表(EntityType→部位列表):人形=HEAD+TORSO+ARM_L+ARM_R+LEG_L+LEG_R,creeper=HEAD+TORSO+LEG×4,spider=HEAD+LEG×8(creeper/spider 腿安装时可入任一腿槽)
- 模型 json:`mob_part.json` 用 `item/generated` 占位 + BEWLR 接管渲染
- 验证:`/give` 拿到带组件的部位物品,名字正确

### P3 部位物品 3D 渲染
- 新建 `client\model\MobPartRenderRegistry`:`EntityType→(ModelLayerLocation, 贴图 ResourceLocation)` 映射,8 种生物(zombie 系共享 HumanoidModel 层;creeper/spider 用各自 `ModelLayers` 常量)
- 新建 `client\render\MobPartItemRenderer extends BlockEntityWithoutLevelRenderer`(仿 CyberSkullItemRenderer):按 PART_TYPE 置 visible 渲染对应部件,head 类部件旋转 180° 对齐头颅渲染习惯;`IClientItemExtensions.getCustomRenderer()` 挂到 MOB_PART(ModItems 现有 skull 挂法,L48-70 参考)
- 验证:手持/掉落物/GUI 中查看 8 种生物的各部位均为对应生物材质的 3D 部件

### P4 手术舱拆解流程
- `RobosurgeonBlockEntity.findPatient`(L215):放宽为舱内 LivingEntity,排除玩家则走拆解模式;拆解期间 `setNoAi/setInvulnerable`,进度复用现有 progress+SyncSurgeryProgressPacket
- 新建 `common\block\robosurgeon\MobDismemberManager.execute()`(对位 SurgeryManager.execute):按 MobSurfaceParts 生成部位物品(非 pristine 状态可后议,v1 直接 pristine)写入舱输出栏,然后生物 `discard()`
- [SurgeryChamberBlockEntity](file:///d:/cyberware/Cyberware-Reforged-1.21.1-dev/src/main/java/com/maxwell/cyber_ware_port/common/block/surgerychamber/SurgeryChamberBlockEntity.java):新增输出 ItemStackHandler(存 NBT、getUpdateTag 同步);`toggleDoor` 开门时把输出栏内容直接 `player.getInventory().add()`(放不下的掉地上)——满足"开舱即收集",不做新 GUI
- 生物进舱辅助:玩家持栓绳右击舱门可把拴住的生物收入舱内(简化寻路问题)
- 验证:栓绳拉僵尸进舱→关门→机器人外科医生推进度→开舱收到 6 个部位物品,生物消失无常规掉落

### P5 玩家安装 + 外观
- [ForgeClientEvents.java](file:///d:/cyberware/Cyberware-Reforged-1.21.1-dev/src/main/java/com/maxwell/cyber_ware_port/client/ForgeClientEvents.java) L177-203:扩展部位隐藏逻辑——HEAD 区装有非 HUMAN_HEAD 时隐藏 `PlayerModel.head/hat`,TORSO 区同理隐藏 `body/jacket`
- 新建 `client\upgrades\MobPartPlayerLayer`(仿 CyberwarePlayerLayer 的 copyFrom 模式):按已装 mob part 用 MobPartRenderRegistry 的模型+贴图渲染玩家对应部件;ModClientEvents 注册 layerRenderer
- 手术舱正常手术流程安装 mob part(SurgeryManager 无需改,冲突解析按 BodyPartType 自动工作)
- 验证:装僵尸头/僵尸手臂后第一/三人称外观正确;换回 human 部位恢复原皮肤

## 风险点
- **存档迁移**(P1):ItemStackHandler NBT 带 Size 字段,反序列化后需主动扩容+补默认头/躯干,务必先备份存档测试
- **findPatient 取实体顺序**:AABB 查询 `.get(0)` 可能命中掉落物之外的无关实体,改为按距离排序择优
- **RobosurgeonMenu 126 槽布局**:GUI 贴图可能放不下,必要时加滚动/翻页
- **多人同步**:拆解进度已有包;舱输出栏变更需走 getUpdatePacket
- **第一人称**:隐藏 head 不影响第一人称手臂;TORSO 替换后皮肤套装有视觉断层,属预期效果

## 端到端验证
1. `gradlew build` 全绿
2. 创造测试:旧档读入义体完整;give mob_part 各组件组合渲染正确
3. 生存流程:栓绳拉 8 种生物进舱拆解→收集→手术装到自己身上→外观变化→换回人件恢复
4. 多人:两客户端同时观察拆解进度与舱内物品同步
