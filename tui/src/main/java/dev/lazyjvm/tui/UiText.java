package dev.lazyjvm.tui;

import dev.lazyjvm.domain.Capability;
import dev.lazyjvm.domain.CommandImpact;
import dev.lazyjvm.domain.DiagnosticCommand;
import dev.lazyjvm.domain.MetricKey;

/** One-language UI copy. JVM, JDK, JMX, JFR, jcmd, and command names stay technical. */
final class UiText {
    record Help(String title, String meaning, String value, String source, String recommendation,
                String risk, String nextStep) {}

    private UiText() {}

    static String label(UiLanguage language, String chinese, String english) {
        return language.isEnglish() ? english : chinese;
    }

    static String label(boolean ascii, String chinese, String english) {
        return label(ascii ? UiLanguage.EN : UiLanguage.ZH_CN, chinese, english);
    }

    static String impact(CommandImpact impact, UiLanguage language) {
        if (language.isEnglish()) return impact.name();
        return switch (impact) {
            case LOW -> "低风险";
            case MEDIUM -> "中风险";
            case HIGH -> "高风险";
        };
    }

    static String impact(CommandImpact impact, boolean ascii) {
        return impact(impact, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static Help metric(MetricKey key, UiLanguage language) {
        if (language.isEnglish()) {
            return switch (key.id()) {
                case "process.cpu" -> new Help("Process CPU", "Current CPU use by target JVM process.", "%",
                        "JMX", "No fixed recommendation.", "Values near 100% can indicate CPU saturation.",
                        "Compare with system CPU and thread state.");
                case "system.cpu" -> new Help("System CPU", "Overall host CPU use.", "%", "JMX",
                        "No fixed recommendation.", "High values increase process CPU pressure.",
                        "Check other processes on the host.");
                case "heap.used" -> new Help("Heap used", "Currently used Java heap space.", "bytes", "JMX",
                        "No fixed recommendation.", "Values near max can cause frequent GC or OOM.",
                        "Compare with GC pause and heap max.");
                case "heap.committed" -> new Help("Heap committed", "Heap space committed by JVM to the OS.", "bytes",
                        "JMX", "No fixed recommendation.", "Growth increases process memory use.",
                        "Compare with used and max.");
                case "heap.max" -> new Help("Heap max", "Maximum heap space allowed by JVM.", "bytes", "JMX",
                        "No fixed recommendation.", "A low limit can create memory pressure.",
                        "Compare with application working set and container limit.");
                case "threads.live" -> new Help("Live threads", "Current number of Java threads.", "threads", "JMX",
                        "No fixed recommendation.", "Unexpected growth can indicate a leak or blocking.",
                        "Inspect Threads and Thread.print.");
                case "threads.peak" -> new Help("Peak threads", "Highest thread count recorded since startup.", "threads",
                        "JMX", "No fixed recommendation.", "A high peak can hide a short burst.",
                        "Compare with the history window and thread states.");
                case "gc.time" -> new Help("GC time", "GC time delta between adjacent samples.", "ms/sample", "JMX",
                        "No fixed recommendation.", "Growing pauses directly affect latency.",
                        "Inspect collectors and heap pressure.");
                default -> new Help(key.label(), "Metric reported by target JVM.", key.unit(), "JMX",
                        "No fixed recommendation.", "Data may be unavailable.", "Inspect history and data quality.");
            };
        }
        return switch (key.id()) {
            case "process.cpu" -> new Help("进程 CPU", "目标 JVM 当前进程 CPU 使用率。", "%", "JMX",
                    "暂无固定推荐值", "持续接近 100% 可能表示 CPU 饱和。", "结合系统 CPU 与线程状态判断。");
            case "system.cpu" -> new Help("系统 CPU", "主机整体 CPU 使用率。", "%", "JMX",
                    "暂无固定推荐值", "高值会放大进程 CPU 压力。", "检查同机其他进程。");
            case "heap.used" -> new Help("已用堆", "Java 堆中当前已使用空间。", "bytes", "JMX",
                    "暂无固定推荐值", "长期接近 max 可能触发频繁 GC 或 OOM。", "同时查看 GC pause 和 heap max。");
            case "heap.committed" -> new Help("已提交堆", "JVM 已向操作系统提交的堆空间。", "bytes", "JMX",
                    "暂无固定推荐值", "提交空间增长会增加进程内存占用。", "与 used、max 一起观察。");
            case "heap.max" -> new Help("堆上限", "JVM 允许使用的最大堆空间。", "bytes", "JMX",
                    "暂无固定推荐值", "上限过低可能导致内存压力。", "结合应用工作集和容器限制评估。");
            case "threads.live" -> new Help("活动线程", "当前存在的 Java 线程数量。", "threads", "JMX",
                    "暂无固定推荐值", "异常增长可能表示线程泄漏或阻塞。", "查看 Threads 页面和 Thread.print。");
            case "threads.peak" -> new Help("线程峰值", "自启动以来记录的线程数量峰值。", "threads", "JMX",
                    "暂无固定推荐值", "峰值高于当前值可隐藏短时线程爆发。", "与历史窗口和线程状态比较。");
            case "gc.time" -> new Help("GC 暂停", "相邻采样之间 GC 时间增量。", "ms/sample", "JMX",
                    "暂无固定推荐值", "暂停增长会直接影响延迟。", "检查 GC collector 与堆压力。");
            default -> new Help(key.label(), "目标 JVM 指标。", key.unit(), "JMX", "暂无固定推荐值",
                    "数据可能不可用。", "查看历史趋势和数据质量。");
        };
    }

    static Help metric(MetricKey key, boolean ascii) {
        return metric(key, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static Help command(DiagnosticCommand command, UiLanguage language) {
        if (language.isEnglish()) {
            return new Help(command.name(), command.description(), "command output", "jcmd",
                    "No fixed recommendation.", command.impact().name() + " impact.",
                    "Review output before the next action.");
        }
        String risk = switch (command.impact()) {
            case LOW -> "低风险：读取诊断信息。";
            case MEDIUM -> "中风险：可能消耗明显 CPU 或暂停时间。";
            case HIGH -> "高风险：可能改变运行状态、写入大文件或产生暂停。";
        };
        return new Help(command.name(), commandDescription(command, language), "原始输出", "jcmd",
                "暂无固定推荐值", risk, "确认参数、目标 PID 和输出路径后执行。");
    }

    static Help command(DiagnosticCommand command, boolean ascii) {
        return command(command, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static String commandDescription(DiagnosticCommand command, UiLanguage language) {
        if (language.isEnglish()) return command.description();
        return switch (command.name()) {
            case "Thread.print" -> "打印线程栈和锁信息。";
            case "GC.heap_info" -> "显示当前堆和收集器摘要。";
            case "GC.class_histogram" -> "按类统计堆对象。";
            case "GC.heap_dump" -> "写入可能很大的 HPROF 堆转储。";
            case "GC.run" -> "请求执行完整垃圾回收。";
            case "VM.flags" -> "显示当前 JVM 标志。";
            case "VM.command_line" -> "显示目标启动命令。";
            case "VM.version" -> "显示 JVM 版本和构建信息。";
            case "JFR.check" -> "列出活动 Flight Recorder 记录。";
            case "JFR.start" -> "启动 Flight Recorder 记录。";
            case "JFR.dump" -> "将记录数据写入 JFR 文件。";
            case "JFR.stop" -> "停止 Flight Recorder 记录。";
            default -> "目标 JVM 报告的诊断命令。";
        };
    }

    static String commandDescription(DiagnosticCommand command, boolean ascii) {
        return commandDescription(command, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static Help page(String title, UiLanguage language) {
        return language.isEnglish()
                ? new Help(title, "Workspace page for JVM evidence.", "current sample", "JMX",
                "No fixed recommendation.", "Missing data stays unavailable.", "Use Tab to move focus.")
                : new Help(title, "工作区页面，用于查看 JVM 证据。", "当前采样值", "JMX",
                "暂无固定推荐值", "缺失数据不会被插值。", "使用 Tab 切换焦点。");
    }

    static Help page(String title, boolean ascii) {
        return page(title, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static Help target(String field, UiLanguage language) {
        return language.isEnglish()
                ? new Help(field, "Connected target identity and capability metadata.", "current target",
                "Attach/JMX/jcmd", "No fixed recommendation.", "Unavailable capabilities limit actions.",
                "Inspect the target before running commands.")
                : new Help(field, "已连接目标的身份、能力和采样元数据。", "当前目标", "Attach/JMX/jcmd",
                "暂无固定推荐值", "能力缺失会限制可用操作。", "执行命令前先确认目标信息。");
    }

    static Help target(String field, boolean ascii) {
        return target(field, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static Help capability(Capability capability, UiLanguage language) {
        if (language.isEnglish()) {
            return new Help(capability.name(), "Target capability.", "available/unavailable", "Attach/JMX/jcmd",
                    "No fixed recommendation.", "Unavailable capability limits actions.",
                    "Use a compatible target or tool.");
        }
        String meaning = switch (capability) {
            case JMX -> "通过本地 JMX 读取指标。";
            case PROCESS_CPU -> "读取进程 CPU 使用率。";
            case MEMORY_POOLS -> "读取堆和内存池。";
            case GARBAGE_COLLECTION -> "读取 GC 次数和时间。";
            case THREADS -> "读取线程数量和状态。";
            case THREAD_CPU -> "读取线程 CPU 时间。";
            case DEADLOCK_DETECTION -> "检测 Java 层死锁。";
            case JCMD -> "执行目标兼容的 jcmd 命令。";
            case JFR -> "访问 Flight Recorder 能力。";
        };
        return new Help(capability.name(), meaning, "可用/不可用", "Attach/JMX/jcmd",
                "暂无固定推荐值", "能力缺失会限制相关页面或命令。", "检查 JDK、权限和目标能力。");
    }

    static Help capability(Capability capability, boolean ascii) {
        return capability(capability, ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }

    static String noRecommendation(UiLanguage language) {
        return language.isEnglish() ? "No fixed recommendation." : "暂无固定推荐值";
    }

    static String noRecommendation(boolean ascii) {
        return noRecommendation(ascii ? UiLanguage.EN : UiLanguage.ZH_CN);
    }
}
