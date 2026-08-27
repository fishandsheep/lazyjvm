---
name: LazyJVM
description: A calm, keyboard-first JVM operator console on a dark neutral terminal field.
colors:
  graphite-field: "#181818"
  graphite-raised: "#303030"
  body-text: "#dcdcdc"
  muted-text: "#969696"
  panel-rule: "#696969"
  focus-green: "#64cd78"
  selection-blue: "#235596"
  info-cyan: "#50bedc"
  activity-amber: "#ebaA4b"
  caution-yellow: "#f0cd50"
  danger-red: "#f05f5f"
spacing:
  cell: "1 terminal cell"
  panel-inset: "1 terminal cell"
  content-inset: "2 terminal cells"
---

# Design System: LazyJVM

## North star

LazyJVM is a compact JVM evidence console. It combines live JMX history with exact, explicit diagnostic actions in a panel structure familiar to LazyGit users. Dark neutral surfaces keep attention on evidence; green marks the focused frame, blue marks the selected row, cyan carries information, and yellow/red carry warnings and errors.

The default interface is Chinese (`zh-CN`), with English (`en`) as an explicit alternative. Copy is never bilingual in one frame. Technical names—JVM, JDK, JMX, JFR, jcmd, command names, JVM arguments, and raw command output—stay unchanged.

## Color and fallback rules

- Green: active/focused panel border and live/healthy state.
- Blue: selected workspace, command, process, or result row.
- Cyan: informational labels, titles, prompts, and successful interaction affordances.
- Amber: heap/activity and invocation previews.
- Yellow: warnings, pause state, medium-impact commands, and elevated utilization.
- Red: errors, deadlocks, high-impact commands, and destructive confirmations.
- No-color mode removes SGR sequences while preserving markers, labels, borders, and layout.
- 16-color and 256-color terminals use the same semantic mapping with lower-fidelity values.

`--ascii` controls borders and chart glyphs. Because ASCII terminals cannot safely display localized glyphs, it also selects English copy. A non-UTF-8 terminal applies the same English fallback. Unicode panels use `╭─╮╰─╯` corners; ASCII panels use `+--+`.

## Geometry

The canvas minimum is 40×12. Row 0 is the product/status header, row 1 is the workspace strip, the middle rows are the body, and the final row is the shortcut/status footer.

- Below 80 columns: the active page owns the body; the workspace opens as a temporary popup when focused.
- 80–99 columns: a 17-column workspace rail sits left of the active page.
- 100 columns and wider: a 21-column workspace rail sits left of the active page.

There is no Target Detail rail or dialog. The header carries the connected target summary: PID, main class, JDK version, and connection state.

The Commands page gets its own two-region geometry. At 100 columns and wider, the command tree is left and Command Output is right. Below that threshold, the tree is above output; output takes at least 60% of the page body. The output panel keeps the result selector, copy action, normalized raw text, and range hint.

All drawing and hit testing share `UiLayout`; CJK display width is measured in terminal cells. Rounded borders, selection rows, and focus borders remain aligned at 40×12, 80×24, and 120×30.

## Navigation and scroll model

Focus cycles through Workspace, Main, and Command Output. `h/l` and Left/Right do not change pages. Number keys open pages directly. `Enter` and `x` open or execute the selected item.

Every scrollable region uses the same clamping, page movement, Home/End, selection-following, and `first-last/total` range hint rules:

- process picker rows;
- command groups and command rows;
- memory pools and thread states;
- Reports recent events;
- Command Output lines and execution history.

Mouse wheel input uses the rectangle under the pointer, then assigns focus to that region. Missing metric samples remain gaps; no chart interpolates them.

## Commands

Normal Commands hides `JFR.*`; the JFR page owns those controls. Remaining commands are grouped by the prefix before `.`, including `GC`, `VM`, `ManagementAgent`, and `Thread`. Groups sort by name, start expanded, show collapsed/expanded state and command count, and fold with `Enter` or `x`. Commands within each group sort by impact (`LOW`, `MEDIUM`, `HIGH`) and then name. High-impact commands require an exact target PID and command confirmation.

## Pages

- Overview: coordinate charts for process/system CPU, heap, threads, and GC pause, with units, history window, source, quality, and gaps.
- Memory/GC: labeled memory-pool rows and GC interval statistics. It does not use an unlabeled coordinate-free area chart.
- Threads: thread counts, state distribution, and Java-level deadlock status.
- JFR: capability status and explicit `JFR.*` actions; recording never starts automatically.
- Commands: grouped diagnostic actions beside persistent output.
- Reports: safe ZIP contents and scrollable recent activity.

## Copy and dialogs

Global help (`F1`) describes controls. Contextual help (`?`) explains the selected page, command, or output. Search is live and reversible. High-impact confirmation shows the full invocation and blocks execution if the terminal is too small to display it. Errors show recovery text and explicit retry/escape/quit actions.
