# shiguang_warehouse

本仓库用于 shiguangschedule 适配脚本的管理和测试。所有适配脚本将集中存放于此，方便软件拉取和测试。

**注意为避免代码出现问题，main分支启用分支保护，需要先合并到pending分支等待分支同步**

## 仓库结构说明


## 资源目录结构

每个学校或工具都有一个独立的目录，包含以下文件：
## root_index.yaml 填写与适配流程

所有适配学校/工具必须先在 `index/root_index.yaml` 文件中登记，CI/CD 构建脚本会根据此文件决定处理哪些资源。

### 字段说明

每个学校/工具条目需包含如下字段：

| 字段名           | 类型    | 说明                       |
| --------------- | ------- | -------------------------- |
| id              | String  | 唯一标识（拼音或缩写,如果可以更建议使用域名）<br>一般来说我们建议教务使用全大写 工具使用小写 |
| name            | String  | 中文名称                   |
| initial         | String  | 名称首字母（用于排序）     |
| resource_folder | String  | 资源文件夹名称 （建议和id保持一致）|

示例：
```yaml
schools: #固定字段
  - id: "GLOBAL_TOOLS"
    name: "通用工具与服务"
    initial: "G"
    resource_folder: "GLOBAL_TOOLS"

  - id: "CUST"
    name: "长春理工大学"
    initial: "C"
    resource_folder: "CUST"
```

### 适配注意

1. 若要适配新学校/工具，**必须先在 `root_index.yaml` 添加条目**，填写上述字段。
2. 在 `resources/` 下创建与 `resource_folder` 字段一致的文件夹。
3. 在该文件夹内添加 `adapters.yaml` 和适配脚本。
4. 只有在 `root_index.yaml` 已登记的学校/工具，才能提交适配文件。
5. 新增学校/工具时，请确保在适配 PR 中已将相关条目添加到 `root_index.yaml` 的学校列表，无需单独提交更新 PR。

```
资源目录名/
  ├── adapters.yaml  # 配置信息
  └── xxx.js         # 适配脚本
```

## adapters.yaml 配置说明


每个适配器配置应包含以下字段（YAML格式，字段全部必填）：

| 字段名           | 类型    | 说明                                   |
| --------------- | ------- | -------------------------------------- |
| adapter_id      | String  | 唯一标识（建议用拼音或英文缩写）,个人建议使用学校id加序号的形式        |
| adapter_name    | String  | 中文名称                               |
| category        | String  | 分类：`BACHELOR_AND_ASSOCIATE`(本科/专科)、`POSTGRADUATE`(研究生)、`GENERAL_TOOL`(通用工具) |
| asset_js_path   | String  | 适配脚本的**相对路径**（如 `school.js`）         |
| import_url      | String  | 系统登录URL（教务系统适配器必填，工具可为空） |
| maintainer      | String  | 维护者信息（如姓名或 GitHub 用户名）    |
| description     | String  | 简要说明（如适配用途、备注等）          |


示例：
```yaml
adapters: #固定字段
  - adapter_id: "GENERAL_TOOL_01" # id加上序号
    adapter_name: "组件测试"
    category: "GENERAL_TOOL"
    asset_js_path: "school.js" #相对路径
    import_url: ""
    maintainer: "星河欲转"
    description: "这是一个空网站，用于组件测试与演示模式"
```

**注意：**  
- 请严格按照上述字段填写，不要添加或减少字段。
- `importUrl` 一定要是登录页面。
- `asset_js_path` 填写对应学校的适配脚本**相对路径**。
- `maintainer` 填写维护者信息，便于后续沟通和维护。

## 开发流程

1.**Fork 仓库**
- 所有开发者需先 fork 本仓库（本仓库带有 `lighthouse` 标签，已经在开发者软件版本关闭检查逻辑，希望各位开发者对使用的git仓库链接负责）。

2.**添加适配代码**
>在**不更新索引**的情况下，修改任何yaml文件是没有作用的（任何对于yaml文件的修改都需要编译索引才能应用），所以我们才提供了asset_js_path: "test.js" 占位用于在不更新索引的情况下测试适配代码

fork仓库之后 建议测试代码不要在自己的主分支测试哦,可以在仓库在开一个测试分支,测试完成可以一次将正确的代码提交到主分支,这样你的提交历史就不会充斥错误的提交历史  
**注意** 仓库更改数据结构,我们的索引需要编译,软件只接收编译过的索引文件,如果你要测试适配代码,我建议你在`resources\GLOBAL_TOOLS\test.js`文件放置适配代码,我们定义这个位置是一个适配占位符,开发者版本添加了网址链接输入,希望了解适配流程,**注意提交pr请不要把测试的test.js也发上去哦！！**  
```yaml
- adapter_id: "GENERAL_TOOL_02"
  adapter_name: "适配代码测试"
  category: "GENERAL_TOOL"
  asset_js_path: "test.js"
  import_url: ""
  maintainer: "星河欲转"
  description: "空网站以及不存在适配代码,用于在不更新索引的情况下给开发者进行适配的软件测试"    
```
#### 如果要更新索引也可以自行了解仓库的ci配置 (我不建议测试适配,还要更新索引)

3.**软件测试**

- 开发者需要安装 dev(开发者版，图标红色)版本app,在软件的“我的-更多-更新仓库”中选择**自定义仓库或私有仓库**，来拉取并更新自己的仓库代码进行实际测试，完成 Beta 阶段适配验证。

4.**提交 PR**

- 测试通过后，提交 Pull Request，等待审核合并。

## 社区公约

本项目基于 MIT 协议开源，致力于教务系统的适配与维护。为保障项目的良性流转，请在参与或使用本项目时遵守以下约定：

### 1. 贡献与署名
我们感谢所有贡献者的无私分享，每一份适配代码都值得被尊重。在引用或二次开发时，请保留原始开发者的贡献记录（如 Git 提交历史）。这既是对他人劳动的认可，也是社区协作的基础。任何抹除或篡改记录的行为，均不符合社区规范。

### 2. 分支与免责声明
*   **适用范围**：本项目主要维护工作集中于官方仓库及官方分支。
*   **非官方项目**：任何脱离官方管理的第三方分支或衍生项目，其所有行为均与本项目官方无关。
*   **责任界定**：若第三方项目存在代码滥用、违规适配或分发不当，由此产生的一切后果均由该项目维护者自行承担。**本项目及无辜的原始贡献者不承担任何连带责任**。

## 注意事项

- 请确保 `adapters.yaml` 信息准确完整，符合规范要求。
- 每次提交适配代码或索引信息后，建议自测通过再提交 PR。
- 仓库需保留 `lighthouse` 标签，否则软件无法识别为适配仓库。

## 更多链接  
**[如何适配](https://github.com/XingHeYuZhuan/shiguangschedule/wiki/%E5%A6%82%E4%BD%95%E9%80%82%E9%85%8D%E6%95%99%E5%8A%A1)**  

**[浏览器测试插件](https://github.com/XingHeYuZhuan/shiguang_Tester)**

---  

如有问题或建议，欢迎提交 Issue 或 PR。
