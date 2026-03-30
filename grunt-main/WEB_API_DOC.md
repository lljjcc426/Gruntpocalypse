# Grunt Web 接口文档

说明：本文档基于当前前端调用代码 `grunt-main/src/main/resources/web/js/api.js`、页面行为代码 `grunt-main/src/main/resources/web/js/app.js` 与服务端实现 `grunt-main/src/main/kotlin/net/spartanb312/grunt/web/WebServer.kt` 整理。

- 默认服务地址：`http://localhost:8080`
- WebSocket 地址规则：页面为 `http` 时使用 `ws://localhost:8080`，页面为 `https` 时使用 `wss://`
- 当前登录页 `login.js` 仅做前端页面跳转，不存在真实后端登录/鉴权接口，因此不纳入正文
- 当前文档只记录“前端实际调用”的接口，不再记录旧版全局配置与旧上传执行链路

### 【接口模块：配置与会话】

#### 1. 获取配置编辑器 Schema
- **请求方式**：`GET`
- **接口地址**：`/schema/config-editor.schema.json`
- **完整地址**：`http://localhost:8080/schema/config-editor.schema.json`

**请求参数**

无

**返回示例**
```json
{
  "version": 1,
  "defaults": {
    "Settings": {
      "Input": "input.jar",
      "Output": "output.jar",
      "Libraries": [],
      "DumpMappings": true
    },
    "StringEncrypt": {
      "Enabled": false,
      "Arrayed": false,
      "ReplaceInvokeDynamics": true,
      "Exclusion": []
    }
  },
  "sections": [
    {
      "key": "Settings",
      "title": "Settings",
      "kind": "general",
      "category": "General",
      "order": 0,
      "fields": [
        {
          "key": "Output",
          "label": "Output",
          "path": ["Settings", "Output"],
          "type": "string"
        }
      ]
    }
  ]
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回前端配置编辑器使用的静态 Schema |

#### 2. 创建会话
- **请求方式**：`POST`
- **接口地址**：`/api/session/create`
- **完整地址**：`http://localhost:8080/api/session/create`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| 无 | - | - | 前端发送空 JSON `{}` 创建新的混淆会话 |

**返回示例**
```json
{
  "status": "ok",
  "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
  "session": {
    "status": "IDLE",
    "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
    "currentStep": "",
    "progress": 0,
    "totalSteps": 0,
    "configUploaded": false,
    "inputUploaded": false,
    "outputAvailable": false,
    "libraryCount": 0,
    "libraryFiles": [],
    "assetCount": 0,
    "assetFiles": []
  }
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功创建会话并返回会话 ID 与初始状态 |

#### 3. 获取会话状态
- **请求方式**：`GET`
- **接口地址**：`/api/session/{sessionId}/status`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/status`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID，由创建会话接口返回 |

**返回示例**
```json
{
  "status": "READY",
  "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
  "currentStep": "",
  "progress": 0,
  "totalSteps": 0,
  "configUploaded": true,
  "inputUploaded": true,
  "outputAvailable": false,
  "configFileName": "config.json",
  "inputFileName": "demo.jar",
  "libraryCount": 2,
  "libraryFiles": ["guava.jar", "asm.jar"],
  "assetCount": 1,
  "assetFiles": ["customDictionary.txt"]
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "Session not found"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回当前会话状态 |
| 400 | 缺少 `sessionId` |
| 404 | 会话不存在 |

#### 4. 获取会话日志
- **请求方式**：`GET`
- **接口地址**：`/api/session/{sessionId}/logs`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/logs`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |

**返回示例**
```json
[
  "Reading JAR: D:/Grunt/.state/web/a9a6f2c9/input/demo.jar",
  "Processing...",
  "Running transformer: StringEncrypt (1/4)"
]
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "Session not found"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回会话日志数组 |
| 400 | 缺少 `sessionId` |
| 404 | 会话不存在 |

### 【接口模块：文件上传与混淆执行】

#### 1. 上传配置文件到会话
- **请求方式**：`POST`
- **接口地址**：`/api/session/{sessionId}/config`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/config`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| file（multipart） | File | 是 | 配置文件，前端上传当前编辑中的 JSON 配置 |

**返回示例**
```json
{
  "status": "ok",
  "fileName": "config.json",
  "config": {
    "Settings": {
      "Input": "demo.jar",
      "Output": "output.jar",
      "Libraries": []
    },
    "StringEncrypt": {
      "Enabled": true,
      "Arrayed": false,
      "ReplaceInvokeDynamics": true,
      "Exclusion": []
    }
  },
  "session": {
    "status": "READY",
    "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
    "configUploaded": true,
    "inputUploaded": false,
    "outputAvailable": false,
    "libraryCount": 0,
    "libraryFiles": [],
    "assetCount": 0,
    "assetFiles": []
  }
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "Invalid config file"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功上传并保存配置文件 |
| 400 | 缺少 `sessionId`、未上传文件或配置 JSON 非法 |
| 404 | 会话不存在 |
| 409 | 当前会话正在运行，禁止修改 |

#### 2. 上传输入 JAR 到会话
- **请求方式**：`POST`
- **接口地址**：`/api/session/{sessionId}/input`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/input`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| file（multipart） | File | 是 | 主输入 JAR 文件 |

**返回示例**
```json
{
  "status": "ok",
  "fileName": "demo.jar",
  "classCount": 128,
  "classes": [
    "com/example/Main",
    "com/example/service/UserService"
  ],
  "session": {
    "status": "READY",
    "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
    "configUploaded": false,
    "inputUploaded": true,
    "outputAvailable": false,
    "inputFileName": "demo.jar",
    "libraryCount": 0,
    "libraryFiles": [],
    "assetCount": 0,
    "assetFiles": []
  }
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "No JAR uploaded"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功上传输入 JAR，并返回类列表 |
| 400 | 缺少 `sessionId` 或未提供上传文件 |
| 404 | 会话不存在 |
| 409 | 当前会话正在运行，禁止修改 |
| 500 | 文件写入或 JAR 解析失败 |

#### 3. 上传依赖库文件到会话
- **请求方式**：`POST`
- **接口地址**：`/api/session/{sessionId}/libraries`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/libraries`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| files（multipart） | File[] | 是 | 依赖 JAR 列表，支持一次上传多个文件 |

**返回示例**
```json
{
  "status": "ok",
  "count": 2,
  "files": [
    "guava.jar",
    "asm.jar"
  ],
  "session": {
    "status": "READY",
    "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
    "libraryCount": 2,
    "libraryFiles": ["asm.jar", "guava.jar"],
    "assetCount": 0,
    "assetFiles": []
  }
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "No library files uploaded"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功上传依赖文件 |
| 400 | 缺少 `sessionId` 或未提供文件 |
| 404 | 会话不存在 |
| 409 | 当前会话正在运行，禁止修改 |
| 500 | 文件保存失败 |

#### 4. 上传附加资源文件到会话
- **请求方式**：`POST`
- **接口地址**：`/api/session/{sessionId}/assets`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/assets`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| files（multipart） | File[] | 是 | 附件文件列表，常用于上传自定义字典等普通资源 |

**返回示例**
```json
{
  "status": "ok",
  "count": 1,
  "files": [
    "customDictionary.txt"
  ],
  "session": {
    "status": "READY",
    "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d",
    "libraryCount": 0,
    "libraryFiles": [],
    "assetCount": 1,
    "assetFiles": ["customDictionary.txt"]
  }
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "No asset files uploaded"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功上传附件文件 |
| 400 | 缺少 `sessionId` 或未提供文件 |
| 404 | 会话不存在 |
| 409 | 当前会话正在运行，禁止修改 |
| 500 | 文件保存失败 |

#### 5. 启动混淆任务
- **请求方式**：`POST`
- **接口地址**：`/api/session/{sessionId}/obfuscate`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/obfuscate`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| 无 | - | - | 前端发送空 JSON `{}`；调用前通常已上传配置与输入 JAR |

**返回示例**
```json
{
  "status": "started"
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "No config uploaded"
}
```

```json
{
  "status": "error",
  "message": "Another obfuscation task is already running"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功启动混淆任务 |
| 400 | 缺少配置文件或输入 JAR，或缺少 `sessionId` |
| 404 | 会话不存在 |
| 409 | 已有其他会话正在执行混淆 |

#### 6. 下载混淆结果文件
- **请求方式**：`GET`
- **接口地址**：`/api/session/{sessionId}/download`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/download`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |

**返回示例**
```json
{
  "type": "binary",
  "contentDisposition": "attachment; filename=output.jar",
  "description": "成功时直接返回 JAR 文件流，不返回 JSON"
}
```

**常见失败示例**
```json
{
  "error": "No output file available"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回输出 JAR 文件流 |
| 400 | 缺少 `sessionId` |
| 404 | 会话不存在，或当前没有可下载结果 |

### 【接口模块：工程浏览与源码查看】

#### 1. 获取工程元信息
- **请求方式**：`GET`
- **接口地址**：`/api/session/{sessionId}/project/meta`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/project/meta?scope=input`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| scope（Query） | String | 是 | 查看范围，只允许 `input` 或 `output` |

**返回示例**
```json
{
  "status": "ok",
  "scope": "input",
  "available": true,
  "classCount": 128
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "Invalid scope"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回指定范围是否可用及类数量 |
| 400 | `scope` 非法，或缺少 `sessionId` |
| 404 | 会话不存在 |

#### 2. 获取工程类树
- **请求方式**：`GET`
- **接口地址**：`/api/session/{sessionId}/project/tree`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/project/tree?scope=output`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| scope（Query） | String | 是 | 查看范围，只允许 `input` 或 `output` |

**返回示例**
```json
{
  "status": "ok",
  "scope": "output",
  "classCount": 128,
  "classes": [
    "a/a",
    "a/b",
    "b/c"
  ]
}
```

**常见失败示例**
```json
{
  "status": "error",
  "scope": "output",
  "message": "No output class structure available"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回类全名列表 |
| 400 | `scope` 非法，或缺少 `sessionId` |
| 404 | 会话不存在，或该范围当前没有类结构 |

#### 3. 获取类反编译源码
- **请求方式**：`GET`
- **接口地址**：`/api/session/{sessionId}/project/source`
- **完整地址**：`http://localhost:8080/api/session/{sessionId}/project/source?scope=input&class=com/example/Main`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Path） | String | 是 | 会话 ID |
| scope（Query） | String | 是 | 查看范围，只允许 `input` 或 `output` |
| class（Query） | String | 是 | 类名；不能为空，不能含 `..`、`:`，不能以 `/` 或 `\` 开头，且必须匹配 `^[A-Za-z0-9_/$.\\-]+$` |

**返回示例**
```json
{
  "status": "ok",
  "scope": "input",
  "class": "com/example/Main",
  "language": "java",
  "code": "public class Main {\n    public static void main(String[] args) {\n    }\n}"
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "Invalid class name",
  "class": "../Main"
}
```

```json
{
  "status": "error",
  "message": "Class not found",
  "class": "com/example/MissingClass"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 成功返回 Java 反编译源码 |
| 400 | `scope` 或 `class` 参数非法，或缺少 `sessionId` |
| 404 | 会话不存在、指定类不存在，或对应输入/输出 JAR 不可用 |
| 500 | 反编译过程发生内部异常 |

### 【接口模块：实时通道】

#### 1. 控制台日志通道
- **请求方式**：`WebSocket`
- **接口地址**：`/ws/console?sessionId={sessionId}`
- **完整地址**：`ws://localhost:8080/ws/console?sessionId={sessionId}`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Query） | String | 是 | 会话 ID |

**返回示例**
```json
{
  "type": "log",
  "message": "Running transformer: StringEncrypt (1/4)"
}
```

**常见失败示例**
```json
{
  "status": "error",
  "message": "Session not found"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 101 | WebSocket 握手成功，随后持续推送日志消息 |
| 连接关闭 | `sessionId` 缺失或会话不存在时，服务端发送错误消息后关闭连接 |

#### 2. 混淆进度通道
- **请求方式**：`WebSocket`
- **接口地址**：`/ws/progress?sessionId={sessionId}`
- **完整地址**：`ws://localhost:8080/ws/progress?sessionId={sessionId}`

**请求参数**
| 参数名 | 类型 | 是否必传 | 说明 |
| :--- | :--- | :--- | :--- |
| sessionId（Query） | String | 是 | 会话 ID |

**返回示例**
```json
{
  "step": "StringEncrypt",
  "current": 1,
  "total": 4,
  "progress": 25
}
```

```json
{
  "step": "Completed",
  "current": 4,
  "total": 4,
  "progress": 100
}
```

```json
{
  "step": "",
  "progress": 0,
  "status": "IDLE"
}
```

```json
{
  "step": "Error",
  "error": "No config uploaded"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 101 | WebSocket 握手成功；首帧会返回当前会话状态，执行期间持续推送进度帧 |
| 连接关闭 | `sessionId` 缺失或会话不存在时，服务端发送错误消息后关闭连接 |

### 【接口模块：通用状态码与约束】

#### 1. 常见 HTTP 状态码
- **请求方式**：`通用说明`
- **接口地址**：`适用于以上 HTTP 接口`

**请求参数**

无

**返回示例**
```json
{
  "status": "error",
  "message": "Session not found"
}
```

**返回码说明**
| 状态码 | 含义 |
| :--- | :--- |
| 200 | 请求成功 |
| 400 | 参数缺失、参数格式错误、上传内容为空或请求体非法 |
| 404 | 会话不存在、类不存在，或对应资源当前不可用 |
| 409 | 当前会话不可编辑，或已有其他会话正在执行混淆 |
| 500 | 服务端内部处理异常，如文件写入失败、JAR 解析失败、反编译失败 |

#### 2. 通用约束说明
- **请求方式**：`通用说明`
- **接口地址**：`适用于以上接口`

**请求参数**

无

**返回示例**
```json
{
  "scope": "input",
  "sessionId": "a9a6f2c9-3fb4-4ec0-9cdd-6f8dd9f4b68d"
}
```

**返回码说明**
| 约束项 | 说明 |
| :--- | :--- |
| 默认主机 | `http://localhost:8080` |
| `sessionId` | 会话相关接口必传；HTTP 为 Path 参数，WebSocket 为 Query 参数 |
| `scope` | 仅允许 `input` 或 `output` |
| `class` | 必须满足 `^[A-Za-z0-9_/$.\\-]+$`，且不能包含 `..`、`:`，不能以 `/` 或 `\` 开头 |
| 上传 Content-Type | `multipart/form-data` |
| 配置上传字段 | `file` |
| 输入 JAR 上传字段 | `file` |
| 依赖上传字段 | `files` |
| 附件上传字段 | `files` |
| 会话状态枚举 | `IDLE`、`UPLOADING`、`READY`、`RUNNING`、`COMPLETED`、`ERROR` |
