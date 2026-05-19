# 管理端权限分配与范围过滤方案

## 1. 文档目标

本文档用于把以下 3 件事一次性讲清楚：

1. 管理端“权限分配”页面应该展示哪些字段
2. 页面配置如何落到 `iam_user_role_assignment` 和 `iam_scope_rule`
3. 查询申请、审核、成绩等业务列表时，SQL 应如何按范围规则拼接

本文档是对 A 组 IAM 冻结口径的进一步细化，不新增核心表。

## 2. 核心原则

- `org_membership` 表示“用户客观上属于哪些组织”
- `iam_user_role_assignment` 表示“用户在某个组织节点上被授予什么角色”
- `iam_scope_rule` 表示“这条角色分配在某个权限码下，能看多大范围的数据”
- 权限判断必须同时考虑：
  - 是否拥有该权限码
  - 是否命中该权限码对应的范围规则
- 管理端不直接暴露底层全部 `scope_type`，而是提供受控选项，后端再映射为 `iam_scope_rule`

## 3. 页面范围

本方案仅覆盖管理端的“角色分配 / 可见范围配置”页面，不覆盖：

- 用户主数据录入页
- 组织树维护页
- 角色模板和权限字典维护页

## 4. 页面设计

### 4.1 页面结构

推荐拆成两个区域：

1. 角色分配基础信息
2. 可见范围规则配置

不要把“分配角色”和“配置范围规则”完全拆成两个孤立页面，否则管理员容易先分配角色却忘了配范围，导致“有权限但查不到数据”或“范围过宽”。

### 4.2 角色分配基础信息字段

| 字段 | 类型 | 必填 | 说明 | 落库位置 |
|---|---|---|---|---|
| `userId` | `number` | 是 | 被分配用户 | `iam_user_role_assignment.user_id` |
| `userNo` | `string` | 否 | 展示字段，便于检索 | 不单独落库 |
| `userName` | `string` | 否 | 展示字段 | 不单独落库 |
| `roleId` | `number` | 是 | 角色模板 | `iam_user_role_assignment.role_id` |
| `roleCode` | `string` | 否 | 展示字段 | 不单独落库 |
| `mountOrgUnitId` | `number` | 是 | 角色挂载组织节点 | `iam_user_role_assignment.org_unit_id` |
| `sourceType` | `enum` | 是 | `MANUAL/SYSTEM/IMPORT` | `iam_user_role_assignment.source_type` |
| `effectiveFrom` | `datetime` | 是 | 生效时间 | `iam_user_role_assignment.effective_from` |
| `effectiveTo` | `datetime` | 否 | 失效时间 | `iam_user_role_assignment.effective_to` |
| `assignmentStatus` | `enum` | 是 | `ACTIVE/INACTIVE` | `iam_user_role_assignment.status` |
| `assignedBy` | `number` | 否 | 当前操作人 | `iam_user_role_assignment.assigned_by` |

### 4.3 范围规则配置字段

范围规则推荐做成“按权限码配置规则”的结构。

页面行模型建议如下：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `permissionCode` | `string` | 是 | 例如 `manage.review.view` |
| `scopePreset` | `enum` | 是 | 受控预设，供页面选择 |
| `orgUnitId` | `number` | 条件必填 | 组织范围节点 |
| `categoryCode` | `string` | 条件必填 | 申请大类 |
| `itemCode` | `string` | 条件必填 | 申请子项 |
| `priority` | `number` | 是 | 范围规则优先级 |
| `enabled` | `boolean` | 是 | 是否启用 |
| `remark` | `string` | 否 | 管理端展示用备注，不要求一期落库 |

### 4.4 页面上的可见范围选项

不要让管理员直接选择底层 `scope_type`，建议只暴露以下预设：

| 页面选项 | 说明 | 对应 `scope_type` |
|---|---|---|
| `本人` | 仅看本人数据 | `SELF` |
| `本部门` | 仅看当前挂载组织节点 | `ORG_UNIT` |
| `本部门及下级` | 看当前组织节点及子树 | `ORG_SUBTREE` |
| `全部` | 看全部范围 | `ALL` |
| `本部门 + 指定类别` | 看当前组织节点下某个大类 | 推荐落两条规则 |
| `本部门及下级 + 指定类别` | 看当前组织子树下某个大类 | 推荐落两条规则 |
| `本部门 + 指定子项` | 看当前组织节点下某个子项 | `ORG_UNIT_ITEM` |
| `指定类别` | 不限组织，仅按大类筛选 | `CATEGORY` |
| `指定子项` | 不限组织，仅按子项筛选 | `ITEM` |

### 4.5 管理端字段交互建议

页面应有以下联动：

- 当 `scopePreset=本人/本部门/本部门及下级/全部` 时，隐藏类别和子项选择
- 当 `scopePreset=指定类别` 时，只显示 `categoryCode`
- 当 `scopePreset=指定子项` 时，先选 `categoryCode`，再联动 `itemCode`
- 当 `scopePreset=本部门 + 指定类别` 或 `本部门及下级 + 指定类别` 时，`orgUnitId` 默认使用 `mountOrgUnitId`
- 当角色的 `role_scope=SELF` 时，前端只能选 `本人`
- 当角色的 `role_scope=ALL` 时，可以允许选 `全部`，也可降级配置更小范围

## 5. 存储设计

### 5.1 推荐存储模型

管理端一次提交，后端拆成两层持久化：

1. 创建或更新 `iam_user_role_assignment`
2. 覆盖写该 assignment 下的 `iam_scope_rule`

原因：

- 角色分配是“身份授权”
- 范围规则是“数据授权”
- 两者生命周期不同，但在页面上应一次性提交

### 5.2 `iam_user_role_assignment` 的职责

该表只回答：

- 谁
- 被授予什么角色
- 这个角色挂在哪个组织节点
- 生效区间是什么

它不直接表达“可见德育申请”或“只能看本班数据”，这些应写入 `iam_scope_rule`。

### 5.3 `iam_scope_rule` 的推荐映射

#### 场景 A：本人

```json
{
  "permissionCode": "manage.review.view",
  "scopePreset": "SELF"
}
```

落库：

```text
scope_type = SELF
org_unit_id = null
category_code = null
item_code = null
```

#### 场景 B：本部门

```json
{
  "permissionCode": "manage.review.view",
  "scopePreset": "ORG_UNIT"
}
```

落库：

```text
scope_type = ORG_UNIT
org_unit_id = assignment.org_unit_id
category_code = null
item_code = null
```

#### 场景 C：本部门及下级

```json
{
  "permissionCode": "manage.review.view",
  "scopePreset": "ORG_SUBTREE"
}
```

落库：

```text
scope_type = ORG_SUBTREE
org_unit_id = assignment.org_unit_id
category_code = null
item_code = null
```

#### 场景 D：指定类别

```json
{
  "permissionCode": "manage.review.view",
  "scopePreset": "CATEGORY",
  "categoryCode": "MORAL"
}
```

落库：

```text
scope_type = CATEGORY
category_code = MORAL
```

#### 场景 E：指定子项

```json
{
  "permissionCode": "manage.review.view",
  "scopePreset": "ITEM",
  "itemCode": "SPORTS_ART_CONTRIBUTION"
}
```

落库：

```text
scope_type = ITEM
item_code = SPORTS_ART_CONTRIBUTION
```

#### 场景 F：本部门 + 指定类别

这是当前最重要的落地场景。

不建议新增新表；一期建议**落两条规则并求交集**：

规则 1：

```text
scope_type = ORG_UNIT
org_unit_id = assignment.org_unit_id
```

规则 2：

```text
scope_type = CATEGORY
category_code = MORAL
```

原因：

- 不改表结构
- 不新增枚举
- 查询层实现简单

#### 场景 G：本部门及下级 + 指定类别

同样落两条规则：

规则 1：

```text
scope_type = ORG_SUBTREE
org_unit_id = assignment.org_unit_id
```

规则 2：

```text
scope_type = CATEGORY
category_code = SPORTS
```

### 5.4 为什么不建议一期主推 `CUSTOM_EXPRESSION`

虽然表里有 `expression_json` 和 `CUSTOM_EXPRESSION`，但一期不要把常规范围逻辑做成表达式，原因是：

- 前端难配置
- 后端难排错
- SQL 难优化
- 联调时不透明

`CUSTOM_EXPRESSION` 只保留给极少数特殊场景，例如：

- 党员与非党员不同口径
- 多维组合条件超出固定枚举

## 6. 提交接口建议

### 6.0 接口层 DTO 草稿

本方案对应的接口层 DTO 建议固定在 `whut-eval-interfaces` 的 `iam` 包下：

| 用途 | 类名 | 路径 |
|---|---|---|
| 创建角色分配请求 | `CreateRoleAssignmentRequest` | `edu.whut.eval.interfaces.iam.request.CreateRoleAssignmentRequest` |
| 修改角色分配请求 | `UpdateRoleAssignmentRequest` | `edu.whut.eval.interfaces.iam.request.UpdateRoleAssignmentRequest` |
| 新增范围规则请求 | `CreateScopeRuleRequest` | `edu.whut.eval.interfaces.iam.request.CreateScopeRuleRequest` |
| 角色分配响应 | `RoleAssignmentResponse` | `edu.whut.eval.interfaces.iam.response.RoleAssignmentResponse` |
| 范围规则响应 | `ScopeRuleResponse` | `edu.whut.eval.interfaces.iam.response.ScopeRuleResponse` |

其中：

- `A-14` 和 `A-15` 统一复用 `RoleAssignmentResponse`
- `A-17` 和 `A-18` 统一复用 `ScopeRuleResponse`
- 时间字段在 DTO 草稿阶段统一保留为 `String`，便于先冻结 JSON 契约，再决定是否在 assembler 层转成 `OffsetDateTime`

### 6.1 页面提交体建议

建议新增一个“角色分配 + 范围规则整包保存”请求体：

```json
{
  "userId": 1010,
  "roleId": 4003,
  "mountOrgUnitId": 2002,
  "sourceType": "MANUAL",
  "effectiveFrom": "2026-05-20T00:00:00",
  "effectiveTo": null,
  "assignmentStatus": "ACTIVE",
  "scopeRules": [
    {
      "permissionCode": "manage.review.view",
      "scopePreset": "ORG_SUBTREE"
    },
    {
      "permissionCode": "manage.review.view",
      "scopePreset": "CATEGORY",
      "categoryCode": "MORAL"
    },
    {
      "permissionCode": "manage.students.view",
      "scopePreset": "ORG_SUBTREE"
    }
  ]
}
```

### 6.2 后端处理顺序

建议固定为：

1. 校验 `userId/roleId/mountOrgUnitId`
2. 创建或更新 `iam_user_role_assignment`
3. 逻辑删除或停用旧 `iam_scope_rule`
4. 按新请求重建启用中的 `iam_scope_rule`
5. 返回 assignment + scopeRules 快照

### 6.3 校验规则

- `ORG_UNIT` / `ORG_SUBTREE` / `ORG_UNIT_ITEM` 必须有 `orgUnitId`；默认取 assignment 的挂载组织
- `CATEGORY` 必须有 `categoryCode`
- `ITEM` / `ORG_UNIT_ITEM` 必须有 `itemCode`
- `itemCode` 必须属于所选 `categoryCode`
- 同一 assignment + permissionCode 下，禁止出现完全重复规则
- 若角色模板本身是 `SELF` 级别，不允许配置 `ALL`

## 7. 查询 SQL 拼接方案

## 7.1 基本思路

不要把所有规则都直接硬拼成一条巨型 SQL。推荐流程：

1. 先查出当前用户对某个 `permissionCode` 可用的全部 `iam_scope_rule`
2. 在应用层把规则归类成：
   - 组织范围规则
   - 类别范围规则
   - 子项范围规则
3. 再生成业务 SQL 的 `WHERE` 子句

### 7.2 规则归类

对同一个 `permissionCode`：

- 组织范围组：
  - `SELF`
  - `ORG_UNIT`
  - `ORG_SUBTREE`
  - `ALL`
- 类别范围组：
  - `CATEGORY`
- 子项范围组：
  - `ITEM`
  - `ORG_UNIT_ITEM`

### 7.3 查询申请列表的字段前提

申请主表 `application_submission` 至少要有：

- `applicant_user_id`
- `org_unit_id`
- `category_code`
- `item_code`

这几个字段在冻结稿中已经存在，可以直接用于过滤。

### 7.4 SQL 拼接优先级

建议优先级如下：

1. 若命中 `ALL`，组织范围条件可为空
2. 否则若存在 `ORG_SUBTREE`，生成子树条件
3. 否则若存在 `ORG_UNIT`，生成等值条件
4. 若存在 `SELF`，补充 `applicant_user_id = currentUserId`
5. 类别规则统一拼成 `category_code IN (...)`
6. 子项规则统一拼成 `item_code IN (...)`
7. 若同时存在组织规则和类别规则，按交集处理

### 7.5 查询申请列表 SQL 模板

```sql
SELECT
    s.id,
    s.applicant_user_id,
    s.org_unit_id,
    s.category_code,
    s.item_code,
    s.title,
    s.status,
    s.submitted_at
FROM application_submission s
WHERE 1 = 1
  AND (
        :hasAll = 1
        OR s.org_unit_id IN (:orgUnitIds)
        OR s.applicant_user_id = :currentUserId
      )
  AND (
        :categoryRuleEmpty = 1
        OR s.category_code IN (:categoryCodes)
      )
  AND (
        :itemRuleEmpty = 1
        OR s.item_code IN (:itemCodes)
      )
ORDER BY s.updated_at DESC;
```

### 7.6 `ORG_SUBTREE` 的实现方式

不要在每条业务 SQL 里临时递归组织树。

推荐两种实现：

1. 先根据 `org_unit.path` 在应用层查出子树节点 ID 集合，再传给业务 SQL
2. 如果组织规模较大，再单独引入闭包表或祖先链缓存

一期推荐用方案 1。

例如：

```sql
SELECT id
FROM org_unit
WHERE path = '/WHUT/CS'
   OR path LIKE '/WHUT/CS/%';
```

然后把结果作为 `:orgUnitIds` 传给业务查询。

### 7.7 “本部门 + 指定类别”的 SQL 例子

当管理员拥有两条规则：

- `ORG_UNIT(2002)`
- `CATEGORY(MORAL)`

则最终业务条件应是：

```sql
AND s.org_unit_id IN (2002)
AND s.category_code IN ('MORAL')
```

不是并集，而是交集。

### 7.8 “本部门及下级 + 指定类别”的 SQL 例子

假设当前挂载组织是 `2002`，其子树展开后得到：

```text
2002, 2005, 2006, 2009, 2010, 2011
```

再加规则 `CATEGORY = SPORTS`，SQL 变成：

```sql
AND s.org_unit_id IN (2002, 2005, 2006, 2009, 2010, 2011)
AND s.category_code IN ('SPORTS')
```

### 7.9 MyBatis 动态 SQL 示例

下面给出适用于申请列表查询的 MyBatis 版本动态 SQL。约定调用方已经提前算好：

- `hasAll`
- `hasSelf`
- `currentUserId`
- `orgUnitIds`
- `categoryCodes`
- `itemCodes`

示例 Mapper：

```xml
<select id="selectApplicationPageByScope" resultType="ApplicationListRow">
  SELECT
      s.id,
      s.applicant_user_id,
      s.org_unit_id,
      s.category_code,
      s.item_code,
      s.title,
      s.status,
      s.submitted_at,
      s.updated_at
  FROM application_submission s
  WHERE 1 = 1

  <if test="!hasAll">
    <trim prefix="AND (" suffix=")" prefixOverrides="OR">
      <if test="orgUnitIds != null and orgUnitIds.size() > 0">
        OR s.org_unit_id IN
        <foreach collection="orgUnitIds" item="orgUnitId" open="(" separator="," close=")">
          #{orgUnitId}
        </foreach>
      </if>

      <if test="hasSelf">
        OR s.applicant_user_id = #{currentUserId}
      </if>
    </trim>
  </if>

  <if test="categoryCodes != null and categoryCodes.size() > 0">
    AND s.category_code IN
    <foreach collection="categoryCodes" item="categoryCode" open="(" separator="," close=")">
      #{categoryCode}
    </foreach>
  </if>

  <if test="itemCodes != null and itemCodes.size() > 0">
    AND s.item_code IN
    <foreach collection="itemCodes" item="itemCode" open="(" separator="," close=")">
      #{itemCode}
    </foreach>
  </if>

  ORDER BY s.updated_at DESC
</select>
```

对于“本部门及下级 + 指定类别”，最终会变成：

```sql
WHERE 1 = 1
  AND s.org_unit_id IN (2002, 2005, 2006, 2009, 2010, 2011)
  AND s.category_code IN ('MORAL')
```

这里的关键点是：

- 组织条件内部可以是并集
- 类别条件内部可以是并集
- 组织组和类别组之间必须是交集

### 7.10 查询学生管理、成绩查询时的复用

同一套范围模型可以复用到：

- 学生管理页
- 审核列表
- 奖学金资格列表
- 最终成绩查询

区别只在于业务主表不同，但范围条件仍然来源于：

- 组织字段
- 类别字段
- 子项字段
- 本人字段

### 7.11 Service 层伪代码

下面给出一个面向后端实现的伪代码，重点展示“本部门 + 指定类别”交集逻辑。

```java
public ApplicationScopeQuery buildScopeQuery(
        Long currentUserId,
        String permissionCode,
        Long assignmentId
) {
    IamUserRoleAssignment assignment = assignmentRepository.findActiveById(assignmentId);
    requireNonNull(assignment, "assignment not found");

    List<IamScopeRule> rules = scopeRuleRepository.findActiveRules(
            assignmentId,
            permissionCode,
            LocalDateTime.now()
    );

    if (rules.isEmpty()) {
        throw new ForbiddenException("no scope rule configured");
    }

    boolean hasAll = false;
    boolean hasSelf = false;
    Set<Long> orgUnitIds = new LinkedHashSet<>();
    Set<String> categoryCodes = new LinkedHashSet<>();
    Set<String> itemCodes = new LinkedHashSet<>();

    for (IamScopeRule rule : rules) {
        switch (rule.getScopeType()) {
            case ALL -> hasAll = true;
            case SELF -> hasSelf = true;
            case ORG_UNIT -> orgUnitIds.add(rule.getOrgUnitId());
            case ORG_SUBTREE -> {
                Set<Long> subtreeIds = orgUnitQueryService.findSubtreeIds(rule.getOrgUnitId());
                orgUnitIds.addAll(subtreeIds);
            }
            case CATEGORY -> categoryCodes.add(rule.getCategoryCode());
            case ITEM -> itemCodes.add(rule.getItemCode());
            case ORG_UNIT_ITEM -> {
                orgUnitIds.add(rule.getOrgUnitId());
                itemCodes.add(rule.getItemCode());
            }
            default -> throw new UnsupportedOperationException("unsupported scope type");
        }
    }

    if (!hasAll && !hasSelf && orgUnitIds.isEmpty()) {
        throw new ForbiddenException("no org scope resolved");
    }

    return new ApplicationScopeQuery(
            hasAll,
            hasSelf,
            currentUserId,
            List.copyOf(orgUnitIds),
            List.copyOf(categoryCodes),
            List.copyOf(itemCodes)
    );
}
```

查询服务调用示例：

```java
public PageResult<ApplicationListRow> queryReviewPage(
        Long currentUserId,
        Long assignmentId,
        ReviewListQuery query
) {
    ApplicationScopeQuery scopeQuery = buildScopeQuery(
            currentUserId,
            "manage.review.view",
            assignmentId
    );

    if (query.getCategoryCode() != null) {
        scopeQuery.intersectCategory(query.getCategoryCode());
    }

    if (query.getItemCode() != null) {
        scopeQuery.intersectItem(query.getItemCode());
    }

    return applicationMapper.selectApplicationPageByScope(
            scopeQuery.toMapperParam(query)
    );
}
```

其中“本部门 + 指定类别”的交集逻辑体现在两步：

1. `ORG_UNIT` 规则先解析成 `orgUnitIds`
2. `CATEGORY` 规则再解析成 `categoryCodes`

最终 Mapper 只会收到：

```text
orgUnitIds = [2002]
categoryCodes = [MORAL]
```

而不是把两条规则做成 OR。

因此最终 SQL 必然是：

```sql
AND s.org_unit_id IN (2002)
AND s.category_code IN ('MORAL')
```

这就是交集，不是并集。

## 8. 推荐实现口径

一期建议固定以下策略：

- 页面只暴露受控 `scopePreset`
- “组织范围”和“类别限制”允许同时配置
- “组织 + 类别”不新增表结构，先落两条 `iam_scope_rule`
- 查询层按交集拼接 SQL
- `CUSTOM_EXPRESSION` 保留但不作为常规 UI 入口

## 9. 一句话结论

如果后续要支持“某个辅导员只能看本学院的德育申请”，推荐落法就是：

1. 在 `iam_user_role_assignment` 上把辅导员角色挂到学院节点
2. 在 `iam_scope_rule` 上写两条规则：
   - `ORG_SUBTREE`
   - `CATEGORY = MORAL`
3. 业务查询 SQL 同时拼接组织子树条件和类别条件

这套方案兼容当前冻结表，不需要改核心数据库结构。
