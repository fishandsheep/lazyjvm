# LazyJVM

LazyJVM 是面向本机 JVM 的终端诊断工具（TUI）。它发现当前用户可连接的 JVM，通过本地 JMX 采集指标，展示堆内存、GC、线程和 JFR 状态，并提供目标 JVM 支持的 `jcmd` 诊断命令。

LazyJVM 默认使用中文界面，也支持英文界面；技术名称（JVM、JDK、JMX、JFR、`jcmd`、命令名和原始命令输出）保持原文。

## 功能

- 发现并连接当前用户、当前 PID namespace 内的本地 HotSpot/OpenJDK JVM。
- 概览、内存 / GC、线程、JFR、命令和报告六个工作区。
- 通过 JMX MXBeans 展示堆、CPU、类加载、线程和 GC 采样历史。
- 命令页按前缀分组（例如 `GC`、`VM`、`ManagementAgent`），按风险级别和名称排序。
- 命令页隐藏 `JFR.*`；JFR 操作集中在 JFR 工作区。
- 命令支持键盘执行，也支持鼠标双击执行；执行输出可滚动、复制和保留历史。
- 导出包含报告、环境摘要、指标和命令输出的诊断 ZIP；默认不导出堆转储、环境变量和完整系统属性。

## 环境要求

- JDK 21 或更高版本。
- Maven 3.9 或更高版本。
- Linux 或 macOS。
- 目标 JVM：JDK 8–25 范围内的本地 HotSpot/OpenJDK 兼容 JVM，且运行用户和 PID namespace 与 LazyJVM 相同。

## 快速开始

```bash
git clone git@github.com:fishandsheep/lazyjvm.git
cd lazyjvm

mvn test
mvn verify
mvn package -Pruntime-image
```

运行可执行 JAR：

```bash
java -jar distribution/target/lazyjvm-0.1.0-SNAPSHOT-all.jar
```

直接连接指定 PID 并导出一次诊断包：

```bash
java -jar distribution/target/lazyjvm-0.1.0-SNAPSHOT-all.jar \
  12345 --snapshot report.zip
```

`mvn package -Pruntime-image` 还会生成 `distribution/target/lazyjvm-runtime.zip`。

## 启动参数

| 参数 | 说明 |
| --- | --- |
| `--language zh-CN` | 使用中文界面（默认）。 |
| `--language en` / `--lang en` | 使用英文界面。 |
| `--ascii` | 使用 ASCII 边框和图表字符；同时自动使用英文，避免非 Unicode 终端出现乱码。 |
| `--no-color` | 禁用终端颜色。 |
| `--refresh 1s` | 设置轻量采样间隔。 |
| `--history 60m` | 设置内存中的历史窗口。 |
| `--jdk-home PATH` | 指定包含目标兼容 `jcmd` 的 JDK。 |
| `--snapshot ZIP` | 非交互式导出诊断包；必须同时提供 PID。 |
| `--debug-log PATH` | 将未捕获诊断写入指定文件。 |

非 UTF-8 终端会自动切换到英文界面。普通命令页不会重复展示 JFR 命令；高风险命令执行前会要求输入目标 PID 和命令名确认。

## TUI 操作

| 按键 / 操作 | 作用 |
| --- | --- |
| `Tab` / `Shift+Tab` | 在工作区、主内容和命令输出间切换焦点。 |
| `j/k`、上下方向键 | 在当前区域移动或滚动。 |
| `PgUp` / `PgDn` / `Home` / `End` | 翻页、回到开头或跳到末尾。 |
| `1`–`6` | 快速打开六个工作区。 |
| `Enter` / `x` | 打开选中项、折叠命令组或执行选中命令。 |
| 鼠标单击 | 选中命令或聚焦区域。 |
| 鼠标双击命令行 | 执行命令。 |
| 鼠标滚轮 | 滚动鼠标所在区域。 |
| `/` | 实时筛选进程或命令；Enter 保留，Esc 恢复。 |
| `y` | 通过 OSC 52 复制当前完整命令输出。 |
| `p` / `r` / `e` | 暂停采样 / 立即采样 / 导出报告。 |
| `?` / `F1` | 上下文帮助 / 全局帮助。 |
| `Esc` / `q` | 返回进程发现 / 退出。 |

宽终端中，命令树和 Command Output 左右排列；窄终端中上下排列，输出区域至少占主体高度的 60%。顶部摘要显示 PID、主类、JDK 和连接状态，不再常驻 Target Detail 面板。

## 项目结构

- `domain/`：诊断模型、指标和历史数据。
- `jvm-adapter/`：JVM 发现、本地 JMX、`jcmd` 和报告导出。
- `tui/`：终端布局、渲染、输入处理和交互测试。
- `distribution/`：Picocli 启动入口、可执行 JAR 和 jlink runtime image。

## 安全边界

LazyJVM 只连接本机 JVM，不使用 shell，也不会自动调用 `sudo`。高成本诊断（堆转储、GC、JFR 等）不会自动执行。命令输出、JVM 参数和导出的报告可能包含敏感信息，请在分享前检查并脱敏。

## English summary

LazyJVM is a local JVM diagnostics TUI for Linux and macOS. It discovers attachable JVMs, samples JMX MXBeans, shows heap / GC / thread / JFR information, runs supported `jcmd` commands, and exports diagnostic ZIP bundles.

The UI is Chinese by default. Use `--language en` or `--lang en` for English. `--ascii` selects ASCII glyphs and English copy; non-UTF-8 terminals use the same fallback. Commands can be run with `Enter` / `x` or by double-clicking a command row. `JFR.*` commands are kept on the JFR page, and high-impact actions require confirmation.

Build with JDK 21+ and Maven 3.9+:

```bash
mvn test
mvn verify
mvn package -Pruntime-image
java -jar distribution/target/lazyjvm-0.1.0-SNAPSHOT-all.jar
```

Project: <https://github.com/fishandsheep/lazyjvm>
