# XHS-IPFilter 🛡️

> 小红书评论 IP 属地过滤器 — LSPosed / Vector Xposed 模块

按**城市白名单**过滤小红书评论区，自动隐藏非白名单 IP 属地的评论。只显示你关心的城市用户的评论。

---

## ✨ 特性

- **三层过滤**：数据层 (setItems) → ne8.c 同步 → View层 (setText)，层叠兜底
- **白名单机制**：配置文件驱动，一行一个城市，改完即生效
- **子评论链处理**：自动清理孤儿子评论 + 修正"展开X条回复"计数
- **零性能损耗**：前置快速过滤，99% 的 setText 调用在 3 字节内被拒绝
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
| 3（默认） | 内置默认值 | 10 个主要城市 |

### 配置格式
```
上海
北京
广州
深圳
杭州
成都
重庆
武汉
南京
天津
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

## 🧠 架构

```
┌──────────────────────────────────────────────┐
│              VectorHSEntry.java               │
│  模块入口 → Hook Application.attach            │
├──────────────────────────────────────────────┤
│          DataLayerFilter.java                 │
│  Hook setItems(List) → 过滤非白名单评论        │
│  ├─ 第一轮：移除父评论 + 收集已移除 ID          │
│  ├─ 第二轮：移除孤儿子评论（rootCommentId）      │
│  ├─ 第三轮：清理 subComments 内部子评论         │
│  ├─ 第四轮：同步 ne8.c 展开计数                 │
│  └─ 第五轮：清理孤儿 ne8.c 项                  │
├──────────────────────────────────────────────┤
│            IPFilterCore.java                  │
│  Hook TextView.setText → View 层兜底          │
│  ├─ IP 属地检测（正则 + 快速过滤）              │
│  ├─ 向上查找评论卡片根 View → 隐藏             │
│  └─ "展开X条回复"检测 → 隐藏空展开             │
├──────────────────────────────────────────────┤
│            PrefManager.java                   │
│  白名单管理：文件 > SP > 默认值                 │
├──────────────────────────────────────────────┤
│            LogWriter.java                     │
│  日志系统 → /sdcard/Download/ipfilter_log.txt  │
└──────────────────────────────────────────────┘
```

---

## 📊 版本历史

| 版本 | 日期 | 变化 |
|------|------|------|
| **v3.4.0** | 2026-05-03 | 正式 release：移除调试探针代码，白名单迁入 APP 数据目录，代码净化 |
| v3.3.0 | 2026-05-03 | ne8.c 展开计数同步，subComments 内部子评论清理，文件化白名单 |
| v3.1.0 | 2026-05-02 | 数据层 + View 层双层过滤，ne8.c 垃圾回收 |
| v2.8 | 2026-05-01 | View 层 annihilate，连带隐藏子评论 |
| v1.9.1 | 2026-04-30 | 首个可用版本，IP 属地正则匹配 |

---

## 📄 License

MIT

---

## ⚠️ 免责声明

本项目仅供学习研究 Xposed 框架技术使用。请遵守相关法律法规，勿用于任何违规用途。