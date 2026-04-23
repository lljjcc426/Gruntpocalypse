# Grunteon

Grunteon 是当前整合后的 Grunt 代码库，版本为 `3.0.0`。  
它保留了 2.x 时代的网页工作流，同时接入了 3.x 的执行核心，并继续向控制面 / worker 边界的方向演进。

当前仓库不是单纯的框架快照，而是一套可直接运行的混淆工作台，包含：

- 本地 Web UI 工作流
- CLI 配置驱动工作流
- `grunt-back` 控制面后端
- Docker 平台拓扑
- Windows 安装器脚本

## 当前定位

这个仓库现在同时承担三类用途：

- 本地浏览器混淆工具
- 本地 CLI 混淆工具
- 后续控制面与 worker 架构的集成基础

对应的实际代码现状是：

- `grunt-main` 仍然负责真正的混淆执行
- `grunt-back` 提供 Spring Boot 控制面与权限模型
- Web 前端仍保留静态资源形态
- Docker 拓扑已经包含 `control` / `worker` 拆分

## 环境要求

- Java 21
- Gradle Wrapper
- Windows 本地运行批处理脚本时建议使用 PowerShell / CMD

如果要构建 Windows 安装器，还需要：

- `Launch4j`
- `Inno Setup 6`

当前安装器脚本默认使用以下本机路径：

- `D:\Launch4j`
- `D:\Inno Setup 6`

如果你的工具不在这两个位置，需要先修改 [installer/build-installer.ps1](installer/build-installer.ps1)。

## 仓库结构

- `grunt-main`
  核心混淆执行模块，包含 Web UI 静态资源、会话服务、任务服务与主程序入口
- `grunt-back`
  Spring Boot 控制面，负责认证、授权、控制面 API、恢复与平台依赖接入
- `grunt-bootstrap`
  启动与模块装配入口
- `genesis`
  本地共享依赖模块
- `grunt-testcase`
  测试输入和验证样例
- `grunt-yapyap`
  扩展模块
- `docker`
  Dockerfile 和初始化脚本
- `docs`
  平台拓扑、恢复验证、Docker 环境等文档
- `tools`
  本地环境和 smoke 脚本
- `installer`
  Windows 安装器配置、启动器配置、图标与打包脚本

## 快速开始

### 1. 启动 Spring Web UI

这是当前最完整的本地使用方式。

启动：

```powershell
.\start-back.bat
```

默认访问地址：

```text
http://127.0.0.1:8082/login
```

如果端口冲突，可以传 Spring 参数：

```powershell
.\start-back.bat --server.port=8090
```

### 2. 启动嵌入式 Web 模式

这个模式直接从 `grunt-main` 的 fat jar 启动网页工作流。

先构建：

```powershell
.\gradlew.bat :grunt-main:distJar
```

再启动：

```powershell
.\start-web.bat
```

默认地址：

```text
http://127.0.0.1:8080/login
```

### 3. 启动 CLI

CLI 方式直接读取配置文件执行混淆。

```powershell
.\start-cli.bat config.json
```

也可以传完整路径：

```powershell
.\start-cli.bat path\to\config.json
```

## 默认开发账号

当前本地默认账号定义在 `grunt-back` 配置里，开发环境可直接使用：

- `user / grunteon-user`
- `platform-admin / grunteon-platform-admin`
- `super-admin / grunteon-super-admin`

这些默认值适合本地开发，不适合共享环境或正式环境。

## 主要能力

当前仓库已经包含以下几类主要能力：

- Typed config 与旧版 Web schema 适配
- 输入 jar、库文件、资源文件上传
- 浏览器会话式混淆工作流
- CLI 配置驱动混淆工作流
- 项目树 / 源码查看
- 输出文件下载
- 控制面任务 API
- MinIO 对象存储接入
- PostgreSQL 元数据读写
- 基于角色和所有权的访问控制
- Docker 下 control / worker 拆分运行

混淆能力覆盖的模块包括：

- Encrypt
- Miscellaneous
- Optimize
- Renaming
- Controlflow
- Redirect
- Other

更细的执行与平台说明，可以继续看 `docs/` 下的文档。

## 主要入口

### 批处理入口

- [start-back.bat](start-back.bat)
- [start-web.bat](start-web.bat)
- [start-cli.bat](start-cli.bat)

### 前端资源

- [grunt-main/src/main/resources/web/login.html](grunt-main/src/main/resources/web/login.html)
- [grunt-main/src/main/resources/web/index.html](grunt-main/src/main/resources/web/index.html)
- [grunt-main/src/main/resources/web/js/login.js](grunt-main/src/main/resources/web/js/login.js)
- [grunt-main/src/main/resources/web/js/app.js](grunt-main/src/main/resources/web/js/app.js)
- [grunt-main/src/main/resources/web/js/api.js](grunt-main/src/main/resources/web/js/api.js)
- [grunt-main/src/main/resources/web/schema/config-schema.json](grunt-main/src/main/resources/web/schema/config-schema.json)

### 安装器入口

- [installer/build-installer.bat](installer/build-installer.bat)
- [installer/build-installer.ps1](installer/build-installer.ps1)
- [installer/Grunteon-web.xml](installer/Grunteon-web.xml)
- [installer/Grunteon_Install.iss](installer/Grunteon_Install.iss)

## Windows 安装器

仓库已经带有一套 Windows 安装器脚本，用于生成：

- `Grunteon.exe`
- `Grunteon-Setup-3.0.0.exe`

构建命令：

```powershell
.\installer\build-installer.bat
```

它会实际执行以下步骤：

1. 构建 `grunt-main-all.jar`
2. 复制安装包运行时文件到 `installer/build/package/`
3. 用 Launch4j 生成 `Grunteon.exe`
4. 用 Inno Setup 生成最终安装器

图标文件当前使用：

- [installer/assets/grunteon.ico](installer/assets/grunteon.ico)

说明：

- `installer/build/` 是构建产物目录，默认不进 Git
- 安装器运行时依赖 `Java 21+`
- 安装版默认启动 Web UI 模式

## Docker 平台启动

复制环境文件：

```powershell
Copy-Item .env.platform.example .env.platform
```

启动：

```powershell
docker compose --env-file .env.platform -f compose.platform.yml up -d --build
```

当前 Docker 相关模块包括：

- `grunt-back`
- `worker`
- `postgres`
- `redis`
- `kafka`
- `minio`

## 常用验证

构建 fat jar：

```powershell
.\gradlew.bat :grunt-main:distJar
```

运行测试：

```powershell
.\gradlew.bat test
```

Docker 恢复 smoke：

```powershell
.\tools\smoke-control-plane-state-docker.ps1
```

中断恢复 smoke：

```powershell
.\tools\smoke-control-plane-recovery-interrupted.ps1
```

## 文档

- [docs/container-topology.md](docs/container-topology.md)
- [docs/docker-environment.md](docs/docker-environment.md)
- [docs/docker-recovery-validation.md](docs/docker-recovery-validation.md)
- [docs/grunt-back-integration.md](docs/grunt-back-integration.md)

## 当前状态

目前这套仓库已经可以作为可运行项目使用，但整体架构仍在演进中。

已经具备：

- 可运行的本地 Web UI
- 可运行的 CLI
- 可运行的 Spring 控制面
- 已接入的 PostgreSQL / MinIO / Docker 工作流
- 已整理的 Windows 安装器脚本

仍在继续推进：

- 更完整的远程 worker 执行
- 更成熟的调度与编排能力
- 旧式双轨 Web 层的进一步收敛

## License

Grunteon 使用 Apache License 2.0。  
Yapyap 使用 PolyForm Strict License 1.0.0。

代际说明：

| Generation | Versions | Aim of obfuscation | License | Commercial Use |
| --- | --- | --- | --- | --- |
| Grunt | 1.0.0-1.5.x | Lightweight and stability | MIT | Allowed |
| Gruntpocalypse | 2.0.0-2.5.x | Diversity and intensity | LGPL3 | Restricted |
| Grunteon | 3.0.0- | Industrial-grade and efficient | Apache2 | Allowed |
