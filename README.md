# antlr4-kotlin

A Kotlin Multiplatform port of the [ANTLR v4](https://github.com/antlr/antlr4)
runtime.

ANTLR (ANother Tool for Language Recognition) is a powerful parser generator for
reading, processing, executing, or translating structured text or binary files.
This repository ports the ANTLR **runtime** to Kotlin Multiplatform so that
parsers generated against the ANTLR grammar tooling can run across every KMP
target (JVM, Android, Android Native, iOS/tvOS/watchOS, macOS, Linux, Windows,
JS, and Wasm).

> **Status: port in progress.** The runtime is being translated from the
> upstream Java sources; not every target compiles cleanly yet.

## Credit — the original authors

All credit for ANTLR and its runtime design belongs to the upstream project.
This is a derivative work; the algorithms, structure, and the overwhelming
majority of the design are theirs.

- **Terence Parr** — ANTLR project lead and creator (University of San
  Francisco), and the author of the runtime this port is based on.
- **Major ANTLR contributors**, including Sam Harwell, Eric Vergnaud,
  Peter Boyer, Mike Lischke, Janyou, Ben Hamilton, Marcos Passos, Lingyu Li,
  Ivan Kochurkin, Justin King, Ken Domino, Jim Idle, and the many others
  recognized in the upstream
  [Authors and major contributors](https://github.com/antlr/antlr4#authors-and-major-contributors)
  section.

Upstream project:
- Source: <https://github.com/antlr/antlr4>
- Website: <https://www.antlr.org/>
- Documentation: <https://github.com/antlr/antlr4/blob/master/doc/index.md>

## License

BSD 3-Clause, the same license as upstream ANTLR. The upstream copyright
("Copyright (c) 2012-2022 The ANTLR Project") is retained in [LICENSE.txt](LICENSE.txt)
alongside the port copyright. See [LICENSE.txt](LICENSE.txt) for the full terms.
