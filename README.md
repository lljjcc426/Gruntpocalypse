# Gruntpocalypse

Gruntpocalypse 是基于 [SpartanB312/Grunt](https://github.com/SpartanB312/Grunt) 持续演进的新一代 JVM 字节码混淆工程。项目在继承原有混淆器能力、插件体系与兼容性经验的基础上，新增了面向浏览器的 Web 服务与可视化操作界面，使配置编辑、文件上传、执行控制、日志查看与产物下载能够在统一的网页工作台中完成。

本项目面向需要对 Java / Kotlin 应用、插件或发行产物进行字节码级保护的研发与工程场景，重点关注以下目标：

- 保留原 Grunt 在重命名、字符串加密、控制流混淆、调用重定向、资源后处理等方面的核心能力
- 提供基于 Ktor 的 Web 服务端，支持浏览器侧完成混淆任务编排
- 同时兼容传统控制台模式与 Swing 图形界面模式，便于逐步迁移既有使用习惯
- 通过会话化目录管理、实时日志与进度推送，提升批处理与调试效率

## 项目定位

与上游 Grunt 相比，Gruntpocalypse 不仅是一次功能延续，更是一次面向服务化与可视化使用方式的工程扩展。当前仓库已将 Web 资源、WebSocket 实时通信、配置结构描述以及会话级输出管理纳入主工程，适用于本地桌面环境、内网交付环境以及需要更低上手门槛的团队协作场景。

## 核心能力

### 1. 混淆引擎能力

项目保留并扩展了原有的 JVM 字节码处理链路，覆盖以下主要能力域：

- 重命名：类、字段、方法、局部变量重命名，以及部分反射支持
- 加密：字符串、数字、常量池与算术表达式加密
- 重定向：方法代理、字段代理、`String.equals` 替换、`InvokeDynamic` 调用封装
- 控制流：伪跳转、分支变换、Switch 变换、垃圾代码注入等控制流扰动能力
- 优化与整理：调试信息移除、枚举优化、死代码清理、Kotlin 元数据相关处理
- 杂项处理：水印、无效类生成、成员打乱、字段声明重排、资源后处理等

### 2. Web 工作台能力

新增网页端后，用户可通过浏览器完成完整的混淆工作流：

- 创建独立会话并隔离输入、配置、依赖库、资源文件与输出产物
- 上传输入 JAR、依赖库与附加资源文件
- 基于 Schema 的配置编辑与配置文件导入导出
- 查看实时控制台日志与执行进度
- 浏览输入 / 输出 JAR 内部类结构
- 在线反编译查看类源码，便于校验混淆效果
- 在任务完成后直接下载混淆产物

### 3. 多运行模式

当前程序入口支持三种运行方式：

- Web 模式：默认推荐模式，提供浏览器端工作台
- Swing 模式：保留原有桌面图形界面，兼容历史使用习惯
- Console 模式：适合脚本化处理、自动化流水线或无图形环境执行

## 技术架构

项目采用 Gradle 多模块组织，主要模块包括：

- `grunt-main`：主程序、混淆流程、Web 服务、图形界面与运行入口
- `genesis`：底层字节码构造与辅助能力
- `grunt-plugin`：插件扩展接口
- `grunt-authenticator`、`grunt-verp`：插件或扩展能力模块
- `grunt-annotation`、`grunt-hwid`：子模块与相关支持组件

其中，网页端静态资源位于 `grunt-main/src/main/resources/web`，Web 服务端基于 Ktor Netty 启动，默认端口为 `8080`，默认访问地址为：

```text
http://localhost:8080/login
```

## 构建方式

在项目根目录执行：

```powershell
.\gradlew.bat :grunt-main:jar
```

构建完成后，可在 `grunt-main/build/libs/` 下获取主程序 JAR。

## 启动方式

### Web 模式

```powershell
java -jar .\grunt-main\build\libs\grunt-main.jar --web --port=8080 config.json
```

说明：

- `--web`：显式启用 Web 模式
- `--port=8080`：指定 Web 服务监听端口
- `config.json`：指定启动时加载的配置文件

当程序在无参数场景下启动时，默认会进入 Web 模式。

### Swing 模式

```powershell
java -jar .\grunt-main\build\libs\grunt-main.jar --gui config.json
```

### Console 模式

```powershell
java -jar .\grunt-main\build\libs\grunt-main.jar config.json
```

## Web 端典型使用流程

1. 启动程序并访问 `http://localhost:8080/login`
2. 进入 Web 工作台后创建当前会话
3. 上传或编辑配置文件
4. 上传待处理输入 JAR
5. 按需上传依赖库与资源文件
6. 发起混淆任务并实时观察日志与进度
7. 在输出视图中检查类结构或在线反编译结果
8. 下载最终混淆产物

Web 模式下的会话数据默认保存在：

```text
.state/web
```

该目录下会按会话维度划分配置、输入、依赖、资源与输出文件，便于问题排查与结果追溯。

## 使用建议

- 控制流相关变换对依赖完整性较为敏感，建议优先补齐项目依赖后再启用高强度配置
- 若出现 `VerifyError` 或字节码校验失败，应首先检查输入 JAR 与依赖库是否完整
- 在依赖难以完全补齐的场景下，可谨慎调整 `computeMaxs` / 控制流相关配置，以平衡稳定性与混淆强度
- 对生产产物执行混淆前，建议先在测试环境验证运行时兼容性、反射行为与资源映射结果

## 项目特性概览

下表列出当前工程中的主要特性类别与代表能力：

| 类别 | 代表能力 |
| --- | --- |
| 重命名 | `ClassRename`、`FieldRename`、`MethodRename`、`LocalVarRename` |
| 加密 | `StringEncrypt`、`NumberEncrypt`、`ConstPoolEncrypt`、`ArithmeticEncrypt` |
| 重定向 | `MethodScramble`、`FieldScramble`、`InvokeDynamic`、`StringEqualsRedirect` |
| 控制流 | `RandomArithmeticExpr`、`BogusConditionJump`、`TableSwitchJump`、`SwitchExtractor` |
| 优化 | `SourceDebugRemove`、`EnumOptimization`、`DeadCodeRemove`、`KotlinOptimize` |
| 杂项 | `Watermark`、`TrashClass`、`ClonedClass`、`ShuffleMembers`、`PostProcess` |
| 插件扩展 | `RemoteLoader`、插件化加载与扩展机制 |

如需了解更细粒度的配置项，可结合 Web 端配置编辑器或查看 `config-schema.json` 所定义的结构说明。

## 兼容性与许可

项目继承了原 Grunt 在线程模型、字节码处理链路与插件机制方面的设计经验，并继续面向 JVM 字节码混淆场景进行维护与扩展。

本仓库当前代码采用 GPLv3 许可证发布。历史上的早期 Grunt 版本曾使用 MIT 许可证，具体以对应版本与仓库声明为准。

## 致谢

感谢原始项目 [SpartanB312/Grunt](https://github.com/SpartanB312/Grunt) 提供的设计基础与工程积累。Gruntpocalypse 在此基础上继续向服务化、可视化与工程化方向推进。
