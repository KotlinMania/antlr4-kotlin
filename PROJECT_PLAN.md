# Project Plan - antlr4-kotlin

Stage: Runtime validation and Swift Export publish gate.

This repository is the Kotlin Multiplatform ANTLR runtime used by generated
ANTLR Kotlin lexers and parsers. It is a support project for
`proc-macro-kotlin`, but it is not a Rust API port and it should not be shaped
like Rust. The public surface here stays close to ANTLR's Java runtime:
`CharStream`, `Token`, `TokenSource`, `Lexer`, `Parser`, `CommonTokenStream`,
ATN/DFA simulation, parse trees, and runtime helpers.

The Maven artifact is `antlr4-kotlin`; the root namespace is
`io.github.kotlinmania.antlr4`.

---

## Critical Path

The workspace-level chain is:

```text
antlr4-kotlin runtime publishable
  -> proc-macro-kotlin v0.1.0 publishable
     -> proc-macro2-kotlin compiler variant wired
        -> serde_derive can be ported
           -> serde downstream crates can progress
```

`antlr4-kotlin` is needed when Kotlin grammar output is generated against an
ANTLR runtime. It is not the only lexer path in `proc-macro-kotlin`.
`proc-macro-kotlin` currently has a JetBrains `KotlinLexer` path, and that path
already converts Kotlin lexer tokens into Rust-shaped `proc_macro` token trees.

This repository's immediate job is narrower and concrete:

1. Every configured KotlinMania target must compile with the generated build
   template.
2. The runtime test suite must validate the mechanically translated Java
   runtime behavior.
3. Generated or ANTLR-compatible token sources must be able to feed
   `CommonTokenStream` reliably.
4. Swift Export must succeed without shrinking targets or excluding the runtime
   surface from the build gate.
5. A published artifact must be safe for `proc-macro-kotlin` to depend on.

---

## Caller Map

There are two tokenization paths that meet at a normalization boundary.

### Current proc-macro-kotlin path

```text
TokenStream.fromString(source)
  -> org.jetbrains.kotlin.kmp.lexer.KotlinLexer
  -> proc-macro-kotlin KtTokenAdapter
  -> proc-macro-kotlin TokenTree list
  -> proc-macro-kotlin TokenStream
```

This path owns the Rust-like public API:

- `TokenStream`
- `TokenTree`
- `Group`
- `Ident`
- `Punct`
- `Literal`
- `Span`

ANTLR runtime types do not appear in that public API.

### ANTLR-generated path

```text
source text
  -> antlr4-kotlin CharStreams
  -> generated Kotlin lexer : io.github.kotlinmania.antlr4.Lexer
  -> io.github.kotlinmania.antlr4.CommonTokenStream
  -> generated Kotlin parser : io.github.kotlinmania.antlr4.Parser
  -> parser output and/or token stream
  -> proc-macro-kotlin AntlrTokenAdapter
  -> proc-macro-kotlin TokenTree list
  -> proc-macro-kotlin TokenStream
```

The adapter belongs above this runtime, most likely in `proc-macro-kotlin`,
because its output type is `proc-macro-kotlin.TokenStream`. This repository
must not depend on `proc-macro-kotlin`.

Allowed dependency direction:

```text
proc-macro-kotlin -> antlr4-kotlin
antlr4-kotlin -/-> proc-macro-kotlin
```

---

## Architecture Boundary

`antlr4-kotlin` is responsible for:

- Character input: `CharStream`, `CodePointCharStream`, `CharStreams`,
  `UnbufferedCharStream`.
- Token production and buffering: `Token`, `WritableToken`, `CommonToken`,
  `TokenSource`, `Lexer`, `BufferedTokenStream`, `CommonTokenStream`,
  `UnbufferedTokenStream`.
- Runtime simulation: ATN, DFA, prediction contexts, lexer actions, parser
  interpreter behavior.
- Parse tree runtime: contexts, terminal nodes, listeners, visitors, tree
  walking, pattern and XPath helpers where the runtime needs them.
- Generated parser compatibility across all KotlinMania targets.

`proc-macro-kotlin` is responsible for:

- Rust-shaped `proc_macro` API.
- Mapping source tokens into `TokenTree`.
- Span semantics presented to proc macro callers.
- `proc-macro2-kotlin` compiler/fallback dispatch.

The adapter boundary is responsible for:

- Skipping hidden-channel ANTLR tokens such as whitespace/comments.
- Mapping ANTLR token type + token text to `Ident`, `Literal`, `Punct`, or
  delimiter `Group`.
- Preserving token source offsets and line/column data as `Span` input.
- Keeping ANTLR token constants private to the adapter implementation.

No ANTLR runtime type should become part of the public `proc_macro` API.

---

## Current State

Build structure:

- `build.gradle.kts` is generated from the `proc-macro-kotlin` template shape
  and should stay byte-for-byte synchronized with the parent template source
  when the workspace blasts it out.
- `.github/workflows/*.yml` files are generated workflow material and should be
  copied from `proc-macro-kotlin` without hand edits.
- `gradle.properties` and `gradle/libs.versions.toml` carry repo-specific
  identity values and dependency bundle names.

Runtime source:

- The main runtime source is present under
  `src/commonMain/kotlin/io/github/kotlinmania/antlr4`.
- JVM-specific stream and I/O helpers are under
  `src/jvmMain/kotlin/io/github/kotlinmania/antlr4`.
- Mechanically translated source and tests also exist under
  `/Volumes/stuff/Projects/kotlinmania/toport/antlr4`.

Testing:

- Initial runtime tests are now present for `IntegerList` and
  `CodePointCharStream`.
- `IntegerList` was corrected to expose the upstream-shaped public class name
  and to encode supplementary Unicode code points as UTF-16 surrogate pairs.
- Broad runtime test parity is still thin compared with the translated Java
  test material.

Current full build gate:

- Android, Android Native, JVM, JS, Wasm, Apple, Linux, Windows compilation and
  framework assembly reach the Swift Export section locally.
- `swiftExportSmokeTest` currently fails at `macosArm64DebugSwiftExport` with
  Kotlin Swift Export optional-wrapper handling (`KT-66875`).
- The same Swift Export path also reports a Kotlin worker classpath failure for
  `kotlinx/coroutines/internal/intellij/IntellijCoroutines`.

---

## Execution Plan

### 1. Keep the generated build surface synchronized

The build and workflow files are generated inputs. Do not edit them for local
preference.

Validation commands:

```bash
cmp build.gradle.kts /Volumes/stuff/Projects/kotlinmania/proc-macro-kotlin/build.gradle.kts
for f in .github/workflows/*.yml; do
  cmp "$f" "/Volumes/stuff/Projects/kotlinmania/proc-macro-kotlin/$f"
done
```

Expected repo-specific differences live in:

- `gradle.properties`
- `gradle/libs.versions.toml`
- source and tests

### 2. Prove runtime behavior before expanding tool integration

Port tests from the translated ANTLR runtime test suite in layers:

1. Pure common runtime tests:
   - `misc/IntegerList`
   - intervals and interval sets
   - prediction context value behavior
   - ATN serializer/deserializer helpers that do not require generated grammar
     fixtures
2. JVM stream tests:
   - `CharStreams`
   - `CodePointCharStream`
   - `UnbufferedCharStream`
   - encoding and invalid-input behavior
3. Token stream tests:
   - `ListTokenSource`
   - `BufferedTokenStream`
   - `CommonTokenStream`
   - `UnbufferedTokenStream`
   - `TokenStreamRewriter`
4. Lexer/parser runtime tests:
   - hand-built token sources first
   - generated lexer fixtures second
   - generated parser fixtures third
5. Tool-backed grammar tests:
   - only after the runtime can execute generated fixtures consistently

The first goal is not line count. The goal is to create tests that expose
mechanical translation defects in runtime behavior.

### 3. Establish the ANTLR-to-proc-macro adapter contract

Add a small fixture that models what `proc-macro-kotlin` will consume:

```text
ANTLR TokenSource
  -> CommonTokenStream
  -> adapter input contract
  -> expected normalized token sequence
```

The fixture can start in this repository as runtime validation, but the actual
adapter implementation should live in `proc-macro-kotlin` because it returns
`proc-macro-kotlin.TokenStream`.

Minimum adapter input contract:

- `Token.type`
- `Token.text`
- `Token.channel`
- `Token.startIndex`
- `Token.stopIndex`
- `Token.line`
- `Token.charPositionInLine`
- optional token vocabulary supplied by the generated lexer/parser

Minimum adapter output contract:

- identifiers and keywords become `TokenTree.Ident`
- literals become `TokenTree.Literal`
- punctuation becomes one or more `TokenTree.Punct`
- delimiters become nested `TokenTree.Group`
- hidden-channel tokens do not appear in output
- source positions are available for `Span`

### 4. Fix Swift Export by API shape, not target removal

The failing `macosArm64DebugSwiftExport` task is part of the publish gate. The
fix must preserve the configured target surface.

Work shape:

1. Identify the exported declaration producing unsupported optional wrapping.
2. Change the Kotlin API shape so Swift Export can bridge it.
3. Keep Java/ANTLR runtime compatibility where generated Kotlin callers need
   the original shape.
4. Re-run `swiftExportSmokeTest`, then full `build`.

Likely search areas:

- nullable generic return types
- nullable platform-style factory parameters
- `Token?`, `TokenSource?`, `CharStream?`, `RuleContext?`
- generic token factories and listener APIs

### 5. Publish antlr4-kotlin only after tests and gates are meaningful

Publication should wait for:

- generated build/workflow sync confirmed
- full target compilation gate passing
- Swift Export smoke test passing
- runtime test suite has meaningful pure runtime and token stream coverage
- generated or ANTLR-compatible token source fixture proves parser caller shape

### 6. Wire proc-macro-kotlin above this runtime

Once `antlr4-kotlin` is published:

1. Add the Maven dependency in `proc-macro-kotlin`.
2. Add `AntlrTokenAdapter` beside `KtTokenAdapter`.
3. Keep `TokenStream.fromString` JetBrains-backed until the ANTLR-generated
   Kotlin grammar path is fully validated.
4. Add explicit tests that compare normalized output from the JetBrains lexer
   path and ANTLR token path for equivalent Kotlin snippets.
5. Wire `proc-macro2-kotlin` compiler mode only after
   `proc-macro-kotlin.TokenStream` behavior is stable.

---

## Test Source Inventory

Translated runtime tests are under:

```text
/Volumes/stuff/Projects/kotlinmania/toport/antlr4/runtime-testsuite
```

Translated tool tests and tool classes are under:

```text
/Volumes/stuff/Projects/kotlinmania/toport/antlr4/tool-testsuite
/Volumes/stuff/Projects/kotlinmania/toport/antlr4/tool
```

The runtime test port should draw from `runtime-testsuite` first. The tool
suite is useful once generated grammar fixtures are running.

---

## Done Criteria

This repository is ready to unblock `proc-macro-kotlin` when:

- `./gradlew build --no-daemon --no-configuration-cache` passes.
- Android, Android Native, JVM, JS, Wasm, Apple, Linux, and Windows target
  compilation remain in the build gate.
- Swift Export smoke testing passes locally.
- Runtime tests cover character streams, token streams, core ATN/DFA behavior,
  and parser-compatible token source flow.
- The ANTLR-to-proc-macro adapter contract is proven by tests.
- Generated build and workflow files remain synchronized with
  `proc-macro-kotlin`.
