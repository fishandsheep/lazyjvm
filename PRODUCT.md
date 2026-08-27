# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Stack

Java 21, Maven, JLine 4.3.1, jlink. LazyJVM ships with its own minimized Java 21 runtime for Linux and macOS and monitors local HotSpot-compatible JVMs running JDK 8 through 25.

## Users

Java developers and SREs diagnosing a local JVM from a development terminal or production server where command names, options, and raw outputs are too difficult to recall and correlate quickly.

## Product Purpose

LazyJVM brings JVM discovery, continuous health metrics, memory and GC behavior, thread diagnostics, JFR control, diagnostic commands, and report export into one keyboard-first terminal interface. Success means an operator can select a JVM, understand its current health, investigate a likely cause, and export evidence without memorizing JDK tool syntax.

## Positioning

Unlike a command reference or a single-purpose monitor, LazyJVM capability-probes the selected JVM, combines live JMX data with supported JDK diagnostic commands, preserves history, and presents both the explanation and the exact underlying command in one LazyGit-style workflow.

## Operating Context

LazyJVM runs as a separate local process under the same operating-system user and PID namespace as the target JVM. It is used interactively over terminals with varying color depth and size, including remote SSH sessions. Default monitoring must remain production-safe; expensive diagnostics are explicit actions. The default UI is Chinese (`zh-CN`), English is selectable with `--language en`, and one frame never mixes localized copy.

## Capabilities and Constraints

- Local Linux and macOS only for the first release; no remote JMX, SSH orchestration, Windows, core dump analysis, or full heap-dump analysis.
- Target JDK 8 through 25 with capability-based degradation; features are not assumed identical across versions or garbage collectors.
- One-second lightweight sampling and a bounded 60-minute in-memory history by default.
- Attach/JMX MXBeans are the primary live source. Target-matched `jcmd` supplies diagnostic commands. JFR is exposed only when the target reports support.
- High-impact operations require a target-and-command confirmation. LazyJVM never elevates privileges or invokes commands through a shell.
- Exports include a readable report, structured environment and metric data, and selected raw command output. Heap dumps are excluded by default.

## Brand Commitments

The product name is LazyJVM. Interaction and visual hierarchy follow the LazyGit family: numbered workspaces, `hjkl` and arrow navigation, visible contextual shortcuts, fast drill-in and escape-back behavior, dense but calm information, rounded terminal panels, green focus borders, blue selected rows, and a polished dark neutral surface.

## Evidence on Hand

No benchmarks, customer claims, screenshots, logo, or production captures are available. Demonstration JVM values in tests or documentation must be labeled synthetic.

## Product Principles

- Reveal capability and impact before offering action.
- Keep live state concise while making deeper evidence one keystroke away.
- Preserve source, units, and confidence for every metric.
- Degrade honestly when a target JVM cannot provide a feature.
- Never let monitoring or a failed diagnostic freeze the terminal interface.

## Accessibility & Inclusion

All states use text or symbols in addition to color. The interface supports 16-color, 256-color, truecolor, no-color, and ASCII fallbacks; keyboard-only operation, terminal resize, and one-language rendering are mandatory.
