# XHS-IPFilter 🛡️ (Survival v4.0.2)

> 小红书评论 IP 属地过滤器 — LSPosed / Vector Xposed 模块
>
> **v4.0 生存版**：砍掉 View 层 hook（setText），只走数据层，大幅降低检测风险。

按**城市白名单**过滤小红书评论区，自动移除非白名单 IP 属地的评论。只显示你关心的城市用户的评论。

---

## ⚠️ v3.x → v4.0 核心变化

| | v3.x 最终版 | v4.0.2 生存版 |
|---|---|---|
| Hook 点位 | 3 个 | **2 个** |
| setText hook | ✅ 🔴 高风险 | ❌ 砍了 |
| View 层操纵 | IPFilterCore 400+ 行 | ❌ 整文件删除 |
| 过滤方式 | 数据层删 item + View 层隐藏 | **只走数据层删 item** |
| ne8.c 同步 | 和 IPFilterCore 耦合 | **独立纯反射** |
| 暴露面 | 大（双维度） | **小（单维度）** |

---

## ✨ 特性

- **纯数据层过滤**：Hook `CommentListAdapter.setItems(List)`，在数据进 RecyclerView 前过滤
- **四轮清理**：
  1. 移除非白名单父评论
  2. 移除孤儿子评论 + 关联的 ne8.c 展开控件
  3. 清理白名单父评论的 subComments 内部列表
  4. 同步 ne8.c 展开计数（防"展开5条→点开0条"）
- **白名单机制**：配置文件驱动，一行一个城市，改完即生效
- **零 View 层干预**：无 setText hook，无 setVisibility，无 View 操作
- **支持 LSPosed / Vector**：基于现代 libxposed API

---

## 📦 安装

### 前置条件
- Android 8.0+
- 已安装 [LSPosed](https://github.com/LSPosed/LSPosed) 或 [Vector](https://github.com/orgs/Vector-Hook/repositories)
- 目标应用：小红书 `com.xingin.xhs`

### 步骤
1. 从 [Releases](https://github.com/gkeyes/XHS-IPFilter/releases) 下载最新 APK
2. 安装 APK
3. 在 LSPosed/Vector 中启用模块，勾选「小红书」
4. 重启小红书

---

## ⚙️ 白名单配置

配置文件位置（任选其一）：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1（最高） | `/data/data/com.xingin.xhs/files/ipfilter_whitelist.txt` | APP 数据目录，编辑需 root |
| 2 | SharedPreferences | 自动从文件同步 |
| 3（默认） | 内置默认值 | 上海、北京 |

### 配置格式
```
上海
北京
广州
深圳
```

> 一行一个城市名。修改后**重启小红书**立即生效。

### 通过 ADB 编辑
```bash
adb shell "echo '上海' > /data/data/com.xingin.xhs/files/ipfilter_whitelist.txt"
adb shell "echo '北京' >> /data/data/com.xingin.xhs/files/ipfilter_whitelist.txt"
```

---

## 🔧 编译

```bash
git clone https://github.com/gkeyes/XHS-IPFilter.git
cd XHS-IPFilter
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

或一键编译安装：
```bash
./build_and_install.sh
```

---

## 🧠 架构 (v4.0.2 Survival)

```
┌──────────────────────────────────────────────┐
│              VectorHSEntry.java               │
│  模块入口 → Hook Application.attach            │
├──────────────────────────────────────────────┤
│          DataLayerFilter.java                 │
│  Hook setItems(List) → 过滤非白名单评论        │
│  ├─ 第一轮：移除父评论 + 收集已移除 ID          │
│  ├─ 第二轮：移除孤儿子评论 + 清理关联 ne8.c      │
│  ├─ 第三轮：清理 subComments 内部子评论         │
│  └─ 第四轮：同步 ne8.c 展开计数                │
├──────────────────────────────────────────────┤
│            PrefManager.java                   │
│  白名单管理：文件 > SP > 默认值                 │
├──────────────────────────────────────────────┤
│            LogWriter.java                     │
│  日志系统（静默模式）                           │
└──────────────────────────────────────────────┘
```

> ❌ IPFilterCore.java 已删除。View 层零干预。

---

## 📊 版本历史

| 版本 | 日期 | 变化 |
|------|------|------|
| **v4.0.2** | 2026-05-03 | 补 ne8.c 同步，4 轮清理完整闭环 |
| v4.0.1 | 2026-05-03 | 数据层删 item（修复 4.0.0 清空 content 无效）|
| v4.0.0 | 2026-05-03 | 生存版初版：砍 setText + IPFilterCore，只走数据层 |
| --- | --- | **以下为 v3.x 旧版（已归档）** |
| v3.4.0 | 2026-05-03 | 正式 release：移除调试探针，白名单迁入 APP 数据目录 |
| v3.3.0 | 2026-05-03 | ne8.c 展开计数同步，subComments 清理，文件化白名单 |
| v3.1.0 | 2026-05-02 | 数据层 + View 层双层过滤 |
| v2.8 | 2026-05-01 | View 层 annihilate |
| v1.9.1 | 2026-04-30 | 首个可用版本 |

---

## 📄 License

MIT

---

## ⚠️ 免责声明

本项目仅供学习研究 Xposed 框架技术使用。请遵守相关法律法规，勿用于任何违规用途。
