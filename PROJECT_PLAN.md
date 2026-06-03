# Project Plan - antlr4-kotlin

Stage: Architecture map, runtime validation, Swift Export gate, and downstream
publish wiring.

This repository is the Kotlin Multiplatform ANTLR runtime used by generated
ANTLR Kotlin lexers and parsers. It is a support project for
`proc-macro-kotlin`, but it is not a Rust API port. Its public shape should
stay close to ANTLR's Java runtime: `CharStream`, `Token`, `TokenSource`,
`Lexer`, `Parser`, `CommonTokenStream`, ATN/DFA simulation, parse trees, and
runtime helpers.

The Maven artifact is `antlr4-kotlin`; the root namespace is
`io.github.kotlinmania.antlr4`.

---

## Critical Path

The workspace-level chain is:

```text
antlr4-kotlin runtime publishable
  -> proc-macro-kotlin v0.1.0 publishable
     -> proc-macro2-kotlin compiler variant wired
        -> serde_derive can compile and run
           -> serde downstream crates can progress
```

`antlr4-kotlin` is the runtime dependency for Kotlin grammar output generated
against an ANTLR runtime. It is not the only lexer path in
`proc-macro-kotlin`. `proc-macro-kotlin` already has a JetBrains
`KotlinLexer` path that converts Kotlin lexer tokens into Rust-shaped
`proc_macro` token trees.

This repository has a focused release job:

1. Preserve every configured KotlinMania target in the generated build gate.
2. Prove the translated runtime against ANTLR runtime tests.
3. Prove generated or ANTLR-compatible token sources can feed
   `CommonTokenStream` and `Parser` consistently.
4. Make Swift Export pass by changing API shape where needed, not by shrinking
   targets.
5. Publish an artifact that `proc-macro-kotlin` can consume safely.

---

## Generated Build Rules

`build.gradle.kts` and `.github/workflows/*.yml` are generated material. Treat
them as blasted-out files copied from `proc-macro-kotlin`, not as handwritten
project configuration.

Validation commands:

```bash
cmp build.gradle.kts /Volumes/stuff/Projects/kotlinmania/proc-macro-kotlin/build.gradle.kts
for f in .github/workflows/*.yml; do
  cmp "$f" "/Volumes/stuff/Projects/kotlinmania/proc-macro-kotlin/$f"
done
```

Repo-specific configuration belongs in:

- `gradle.properties`
- `gradle/libs.versions.toml`
- source files
- test files

When the generated parent changes property names or version-catalog bundle
names, synchronize the properties and TOML files. Do not patch the generated
Gradle script to compensate.

Current identity facts:

- Maven artifact: `antlr4-kotlin`
- Root namespace: `io.github.kotlinmania.antlr4`
- Runtime classes package: `io.github.kotlinmania.antlr4`

---

## Repository Roles

### antlr4-kotlin

Owns the ANTLR runtime contract:

- Character input: `IntStream`, `CharStream`, `ANTLRInputStream`,
  `CodePointCharStream`, JVM `CharStreams`, `UnbufferedCharStream`.
- Token production: `Token`, `WritableToken`, `CommonToken`, `TokenSource`,
  `TokenFactory`, `CommonTokenFactory`, `Lexer`.
- Token buffering: `TokenStream`, `BufferedTokenStream`,
  `CommonTokenStream`, `UnbufferedTokenStream`, `ListTokenSource`,
  `TokenStreamRewriter`.
- Parser runtime: `Recognizer`, `Parser`, `ParserRuleContext`,
  `RuleContext`, error strategy/listener support.
- Prediction runtime: ATN, DFA, prediction contexts, semantic predicates,
  lexer actions, parser interpreter behavior.
- Parse tree runtime: contexts, terminal nodes, listeners, visitors, tree
  walking, pattern helpers where the runtime requires them.

This repo should not expose any `proc-macro-kotlin` type and should not depend
on `proc-macro-kotlin`.

### proc-macro-kotlin

Owns the Rust-shaped `proc_macro` API:

- `TokenStream`
- `TokenTree`
- `Group`
- `Ident`
- `Punct`
- `Literal`
- `Span`
- parse/expand outcomes and diagnostics

It also owns source-token normalization. Its existing internal
`KtTokenAdapter` is the blueprint for an `AntlrTokenAdapter` because both
produce the same `TokenTree` list and both need access to internal helpers
such as `TokenStreamData` and `Literal.fromKotlinString`.

Allowed dependency direction:

```text
proc-macro-kotlin -> antlr4-kotlin
antlr4-kotlin -/-> proc-macro-kotlin
```

### proc-macro2-kotlin

Owns compiler/fallback dispatch for callers that want a Rust `proc_macro2`
style API.

Current code shape is fallback-only:

- `Detection.insideProcMacro()` always resolves to false.
- Public `TokenStream` wraps `FallbackTokenStream`.
- Public `Span`, `Group`, `Ident`, `Punct`, and `Literal` wrap fallback
  internals.
- `Wrapper.kt` is a placeholder layer rather than an active compiler/fallback
  union.

Target code shape:

- Restore a real wrapper layer.
- Public proc-macro2 types store wrapper internals, not fallback internals
  directly.
- Wrapper internals can represent either fallback values or
  `proc-macro-kotlin` compiler values.
- `Detection.forceFallback()` still pins fallback mode.
- `Detection.unforceFallback()` re-runs detection.
- Detection can select compiler mode once `proc-macro-kotlin v0.1.0` is a
  usable dependency.

The local `proc-macro2-kotlin` branch already has an uncommitted
`implementation("io.github.kotlinmania:proc-macro-kotlin:0.1.0")` dependency
in `build.gradle.kts`; that is the intended downstream dependency edge. That
repo has local dirty files, so this plan records the connection without
editing that checkout.

---

## Rust Ecosystem Graph

The upstream Rust crates are not independent pieces. They form one token
pipeline.

Source trees inspected:

- `/Volumes/stuff/Projects/kotlinmania/proc-macro-kotlin/tmp/proc-macro`
- `/Volumes/stuff/Projects/kotlinmania/proc-macro2-kotlin/tmp/proc-macro2`
- `/Volumes/stuff/Projects/kotlinmania/quote-kotlin/tmp/quote`
- `/Volumes/stuff/Projects/kotlinmania/syn-kotlin/tmp/syn`
- `/Volumes/stuff/Projects/kotlinmania/serde-kotlin/tmp/serde`
- `/Volumes/stuff/Projects/kotlinmania/unicode-ident-kotlin/tmp/unicode-ident`
- `/Volumes/stuff/Projects/kotlinmania/toport/kotlin-spec`
- `/Volumes/stuff/Projects/kotlinmania/toport/kmp-parser`
- `/Volumes/stuff/Projects/kotlinmania/toport/jflex`
- `/Volumes/stuff/Projects/kotlinmania/toport/jflex-skeleton`

The `../toport` folder is the local source pool for this work. It contains
mechanically translated Kotlin material produced with IntelliJ as well as
grammar files and generated artifacts. Treat those Kotlin files as concrete
port inputs to evaluate, repair, and wire together, not as prose references.

Upstream dependency shape:

```text
proc_macro
  compiler-provided TokenStream, TokenTree, Span, Diagnostic bridge

proc-macro2
  depends on unicode-ident
  wraps proc_macro when compiler proc_macro is available
  uses fallback TokenStream parser everywhere else

quote
  depends on proc-macro2
  quote! and ToTokens produce proc_macro2::TokenStream

syn
  depends on proc-macro2
  optionally depends on quote for printing
  depends on unicode-ident
  parses proc_macro/proc_macro2 streams into Rust syntax trees

serde_derive
  depends on proc-macro2, quote, syn
  takes proc_macro::TokenStream at derive entry
  parses into syn::DeriveInput
  generates proc_macro2::TokenStream
  converts back into proc_macro::TokenStream

serde / serde_core
  runtime traits and helper APIs consumed by generated code
```

KotlinMania dependency shape should mirror the same direction:

```text
antlr4-kotlin
  -> no proc macro dependency

proc-macro-kotlin
  -> antlr4-kotlin

proc-macro2-kotlin
  -> proc-macro-kotlin
  -> unicode-ident-kotlin if the fallback parser needs the sibling artifact

quote-kotlin
  -> proc-macro2-kotlin

syn-kotlin
  -> proc-macro2-kotlin
  -> quote-kotlin for printing and compile-error output
  -> unicode-ident-kotlin if identifier tables are not vendored locally

serde-kotlin
  -> proc-macro2-kotlin
  -> quote-kotlin
  -> syn-kotlin
```

Do not make `quote-kotlin` depend on `syn-kotlin`. Upstream quote mentions Syn
as a caller, not as a dependency.

### Repository Split Policy

Create new KotlinMania sibling repos when a mechanically translated `toport`
area has its own upstream identity, its own build/test surface, or more than
one likely consumer. Do not bury reusable toolchain ports inside
`antlr4-kotlin` or `proc-macro-kotlin` just because the first consumer is this
macro pipeline.

No existing sibling repos were found for these candidate names during this
planning pass:

- `jflex-kotlin`
- `kmp-parser-kotlin`
- `kotlin-parser-kotlin`
- `kotlin-spec-kotlin`
- `intellij-syntax-kotlin`

Candidate repo boundaries:

```text
jflex-kotlin
  JFlex generator, KotlinEmitter, Kotlin skeletons, scanner-generation tests.
  Needed if we regenerate KotlinFlexLexer or KDocFlexLexer from .flex specs.

kmp-parser-kotlin
  JetBrains KMP lexer/parser package:
  Kotlin.flex, KDoc.flex, generated flex lexers, KtTokens, KDocTokens,
  KotlinParser, parser utils, JVM lexer tests.
  Needed if the JetBrains compiler-derived lexer/parser is reused beyond
  proc-macro-kotlin.

kotlin-spec-kotlin
  Kotlin ANTLR grammar package:
  KotlinLexer.g4, KotlinParser.g4, UnicodeClasses.g4, token metadata,
  generated ANTLR Kotlin lexer/parser fixtures.
  Needed if generated Kotlin grammar artifacts become a reusable input to
  proc-macro-kotlin or other Kotlin compiler tooling.

intellij-platform-syntax-kotlin
  JetBrains com.intellij.platform.syntax.* support types if they need to be
  shared outside the existing vendored proc-macro-kotlin surface.
```

Keep code private to the current repo only when it is a focused fixture,
one-off validation harness, or test-only adapter. Promote it into a sibling
repo once it becomes a reusable library or generation tool.

### Upstream proc_macro

Rust source: `proc-macro-kotlin/tmp/proc-macro/src/lib.rs`.

`proc_macro` is the compiler-facing API:

- procedural macro entry functions accept and return `proc_macro::TokenStream`
- `TokenStream` is a shallow iterable sequence of `TokenTree`
- `TokenTree` has `Group`, `Ident`, `Punct`, and `Literal`
- `Span` carries byte range, line, column, file, source text, and hygiene
- the real implementation talks to rustc through `bridge::client::Methods`

Kotlin source: `proc-macro-kotlin/src/commonMain/.../procmacro`.

The Kotlin port already provides the Rust-shaped surface and a
`TokenStream.fromString` parser backed by JetBrains Kotlin lexer tokens. The
missing architectural piece is not another public token API. It is source and
span fidelity plus an ANTLR-backed adapter path when generated Kotlin grammar
output is ready.

### Upstream proc-macro2

Rust sources:

- `proc-macro2-kotlin/tmp/proc-macro2/src/lib.rs`
- `proc-macro2-kotlin/tmp/proc-macro2/src/wrapper.rs`
- `proc-macro2-kotlin/tmp/proc-macro2/src/fallback.rs`
- `proc-macro2-kotlin/tmp/proc-macro2/src/parse.rs`
- `proc-macro2-kotlin/tmp/proc-macro2/src/detection.rs`

The key Rust design is `wrapper.rs`:

```text
imp::TokenStream
  -> Compiler(DeferredTokenStream(proc_macro::TokenStream))
  -> Fallback(fallback::TokenStream)
```

Every public type in `lib.rs` stores `imp::*`, not the fallback type directly.
`Detection.inside_proc_macro()` decides whether constructors create compiler or
fallback values. Mixed compiler/fallback values intentionally fail through the
`mismatch` path because a single token stream must not silently combine
different backends.

The Kotlin port currently stores fallback internals directly in public types.
That shape is enough for tests and non-macro parsing, but it cannot serve
serde derive as a compiler-backed bridge. The Kotlin target shape is:

```text
public proc-macro2 TokenStream
  -> WrapperTokenStream.Compiler(proc-macro-kotlin TokenStream)
  -> WrapperTokenStream.Fallback(FallbackTokenStream)
```

The same wrapper split applies to `Span`, `Group`, `Ident`, `Punct`,
`Literal`, iterators, `LexError`, and conversion functions.

### Upstream quote

Rust sources:

- `quote-kotlin/tmp/quote/src/lib.rs`
- `quote-kotlin/tmp/quote/src/to_tokens.rs`
- `quote-kotlin/tmp/quote/src/ext.rs`
- `quote-kotlin/tmp/quote/src/runtime.rs`

`quote!` produces `proc_macro2::TokenStream`. Interpolation is driven by the
`ToTokens` trait. Rust derive code relies on:

- `ToTokens.to_tokens(&mut TokenStream)`
- `TokenStreamExt.append`
- `append_all`
- `append_separated`
- `append_terminated`
- `quote_spanned!` for span-preserving output

Kotlin cannot reuse Rust macro expansion syntax directly. The Kotlin code
shape should keep the trait layer and write expansions as explicit builders:

```text
val tokens = TokenStream.new()
staticFragment.toTokens(tokens)
fieldIdent.toTokens(tokens)
Comma.default().toTokens(tokens)
```

Static fragments can use `TokenStream.fromString` where the fragment is small
and stable. Interpolated derive output should prefer builder calls so spans,
groups, and punctuation are controlled directly.

### Upstream syn

Rust sources:

- `syn-kotlin/tmp/syn/src/lib.rs`
- `syn-kotlin/tmp/syn/src/parse_macro_input.rs`
- `syn-kotlin/tmp/syn/src/parse.rs`
- `syn-kotlin/tmp/syn/src/buffer.rs`
- `syn-kotlin/tmp/syn/src/error.rs`
- `syn-kotlin/tmp/syn/src/derive.rs`
- `syn-kotlin/tmp/syn/src/attr.rs`

Syn consumes `proc_macro2::TokenStream` and produces Rust syntax trees. Its
critical internal shape is:

```text
TokenStream
  -> TokenBuffer::new2
  -> Cursor
  -> ParseBuffer / ParseStream
  -> Parse implementations
  -> syntax tree
```

For derive macros, the important public entry is:

```text
proc_macro::TokenStream
  -> parse_macro_input!(input as DeriveInput)
  -> syn::DeriveInput
```

On parse failure, `syn::Error.to_compile_error()` produces a token stream that
invokes `compile_error!` in Rust. In Kotlin, `SynError.toCompileError()` is the
same responsibility. This makes span quality a functional requirement, because
serde reports attribute and derive-shape errors through Syn spans.

### Upstream serde_derive

Rust sources:

- `serde-kotlin/tmp/serde/serde_derive/src/lib.rs`
- `serde-kotlin/tmp/serde/serde_derive/src/ser.rs`
- `serde-kotlin/tmp/serde/serde_derive/src/de.rs`
- `serde-kotlin/tmp/serde/serde_derive/src/fragment.rs`
- `serde-kotlin/tmp/serde/serde_derive/src/internals/*.rs`

The Rust derive entry points are compact:

```text
derive_serialize(input: proc_macro::TokenStream)
  -> parse_macro_input!(input as syn::DeriveInput)
  -> ser::expand_derive_serialize(&mut input)
  -> Result<proc_macro2::TokenStream, syn::Error>
  -> unwrap_or_else(syn::Error::into_compile_error)
  -> into proc_macro::TokenStream

derive_deserialize(input: proc_macro::TokenStream)
  -> parse_macro_input!(input as syn::DeriveInput)
  -> de::expand_derive_deserialize(&mut input)
  -> Result<proc_macro2::TokenStream, syn::Error>
  -> unwrap_or_else(syn::Error::into_compile_error)
  -> into proc_macro::TokenStream
```

The translated Kotlin serde derive code already exists under
`serde-kotlin/src/commonMain/kotlin/io/github/kotlinmania/serde/serdederive`.
It uses:

- `io.github.kotlinmania.procmacro2.TokenStream`
- `io.github.kotlinmania.quote.ToTokens`
- `io.github.kotlinmania.syn.*`
- `Ctxt` to collect `SynError`
- `Container.fromAst` to lower Syn AST into serde's internal AST
- `Fragment` wrappers for expression/block generated fragments

That partial port should be treated as an existing caller, not as future
scratch work.

### Output Backend Policy

This is a Kotlin-only port. The product backend generates Kotlin, not Rust.

Rust output still matters because it gives the port a precise proof target.
When the Kotlin port needs to prove semantic parity against upstream Rust, the
same derive model should be able to emit Rust-shaped output and compare that
against upstream expectations.

The generator shape should be:

```text
input tokens
  -> parser
  -> syntax tree
  -> serde internal model
  -> language-neutral expansion model
     -> Rust output backend for parity proof
     -> Kotlin output backend for product use
```

Do not bake Rust syntax into the serde semantic passes. Rust-specific syntax
belongs in the Rust output backend. Kotlin-specific syntax belongs in the
Kotlin output backend.

Recommended backend split:

```text
Serde derive analysis
  Container.fromAst
  attr/check/name/case logic
  serialize/deserialize expansion decisions

Rust backend
  emits proc-macro2-kotlin TokenStream
  uses quote-kotlin builders
  validates against upstream serde_derive behavior

Kotlin backend
  emits proc-macro-kotlin TokenStream or Kotlin source fragments
  uses Kotlin names, Kotlin type syntax, Kotlin trait/interface calls
  is the backend shipped for Kotlin-only consumers
```

The Rust backend is a test oracle and compatibility harness. It is not the
final code-generation target.

---

## Caller Maps

### Existing JetBrains Lexer Path

This path is already implemented in `proc-macro-kotlin`:

```text
TokenStream.fromString(source)
  -> org.jetbrains.kotlin.kmp.lexer.KotlinLexer
  -> proc-macro-kotlin KtTokenAdapter
  -> TokenTree list
  -> TokenStream(TokenStreamData)
```

The normalization algorithm in `KtTokenAdapter` is the contract to preserve:

1. Lex raw Kotlin tokens.
2. Filter whitespace and comments.
3. Collapse string-template token runs into a synthetic string literal.
4. Group delimiters recursively.
5. Convert flat tokens into `Ident`, `Literal`, `Punct`, and `Group`.

Important existing behavior:

- Keywords and identifiers become `Ident` values.
- String, char, integer, and float tokens use internal `Literal.fromKotlin*`
  helpers.
- Single-character punctuation becomes one `Punct` with `Spacing.ALONE`.
- Multi-character operators decompose into `Punct` chains with
  `Spacing.JOINT` except for the final `Spacing.ALONE` item.
- Compound Kotlin tokens such as safe casts and negated `in`/`is` become the
  Rust-shaped token sequence expected by callers.
- Spans currently use `Span.callSite()` for lexed tokens.

### ANTLR Runtime Path

This is the path `antlr4-kotlin` needs to make possible:

```text
source text
  -> common input: ANTLRInputStream(source)
     or JVM input: CharStreams.fromString(source)
  -> generated Kotlin lexer : io.github.kotlinmania.antlr4.Lexer
  -> CommonTokenStream(lexer)
  -> generated Kotlin parser : io.github.kotlinmania.antlr4.Parser
  -> parser result and/or token stream
  -> proc-macro-kotlin AntlrTokenAdapter
  -> TokenTree list
  -> TokenStream(TokenStreamData)
```

The all-target fixture path should start with `ANTLRInputStream(String)`.
`CharStreams` is currently JVM-specific and belongs in JVM-only stream tests.

### Kotlin Grammar Source Path

The Kotlin ANTLR grammar source is in:

```text
/Volumes/stuff/Projects/kotlinmania/toport/kotlin-spec
```

Files:

- `KotlinLexer.g4`
- `KotlinParser.g4`
- `UnicodeClasses.g4`
- `KotlinLexer.tokens`
- `UnicodeClasses.tokens`
- `build.gradle`
- `KotlinParser_main.kt`

`KotlinParser.g4` is a parser grammar with `tokenVocab = KotlinLexer`.
`KotlinLexer.g4` imports `UnicodeClasses`. The lexer has Kotlin-specific mode
actions, including brace mode handling around `LCURL` and `RCURL`; generation
for Kotlin output must translate those actions into Kotlin runtime code rather
than preserving Java action text.

`build.gradle` and `KotlinParser_main.kt` in that folder currently contain
`404: Not Found`; they are placeholders in this checkout, not generation
inputs. The grammar inputs are the `.g4` files plus token metadata.

Generation path to prove:

```text
../toport/kotlin-spec/KotlinLexer.g4
../toport/kotlin-spec/KotlinParser.g4
../toport/kotlin-spec/UnicodeClasses.g4
  -> ANTLR Kotlin target generation
  -> generated KotlinLexer : io.github.kotlinmania.antlr4.Lexer
  -> generated KotlinParser : io.github.kotlinmania.antlr4.Parser
  -> common parser fixture using ANTLRInputStream
  -> CommonTokenStream
  -> parser entry points: kotlinFile and script
  -> proc-macro-kotlin AntlrTokenAdapter when token normalization is needed
```

The current JetBrains KMP parser and lexer sources remain useful comparison
inputs:

```text
/Volumes/stuff/Projects/kotlinmania/toport/kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/KotlinLexer.kt
/Volumes/stuff/Projects/kotlinmania/toport/kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/KotlinParser.kt
```

The ANTLR-generated grammar path and the JetBrains KMP lexer path should be
compared by normalized token/tree behavior, not by matching implementation
classes.

### JetBrains KMP And JFlex Source Path

The current `proc-macro-kotlin` lexer path comes from JetBrains KMP parser
sources, not from the ANTLR grammar. These mechanically translated Kotlin
sources are under `../toport` and are part of the Kotlin compiler lineage.

Core paths:

```text
/Volumes/stuff/Projects/kotlinmania/toport/kmp-parser
/Volumes/stuff/Projects/kotlinmania/toport/jflex
/Volumes/stuff/Projects/kotlinmania/toport/jflex-skeleton
```

JFlex generator inputs and mechanically translated emitter code:

- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/Kotlin.flex`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/KDoc.flex`
- `jflex/jflex/src/main/java/jflex/generator/KotlinEmitter.kt`
- `jflex/jflex/src/main/java/jflex/generator/KotlinCountEmitter.kt`
- `jflex/jflex/src/main/java/jflex/generator/KotlinHiCountEmitter.kt`
- `jflex/jflex/src/main/java/jflex/generator/KotlinHiLowEmitter.kt`
- `jflex/jflex/src/main/java/jflex/generator/KotlinPackEmitter.kt`
- `jflex/jflex/src/main/jflex/kotlin_skeleton.nested`
- `jflex/jflex/src/main/resources/jflex/skeleton_kotlin.default`
- `jflex-skeleton/idea-flex-kotlin.skeleton`

Generated lexer outputs and Kotlin wrappers:

- `kmp-parser/common/src/gen/KotlinFlexLexer.kt`
- `kmp-parser/common/src/gen/KDocFlexLexer.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/KotlinLexer.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/KDocLexer.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/KtTokens.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/lexer/KDocTokens.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/utils/SyntaxElementTypesWithIds.kt`

Mechanically translated parser sources:

- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/KotlinParser.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/AbstractParser.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/KtNodeTypes.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/utils/KotlinParsing.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/utils/KotlinExpressionParsing.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/utils/AbstractKotlinParsing.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/utils/SemanticWhitespaceAwareSyntaxBuilders.kt`
- `kmp-parser/common/src/org/jetbrains/kotlin/kmp/parser/utils/KotlinWhitespaceAndCommentsBinders.kt`

Lexer comparison tests:

- `kmp-parser/jvm/test/org/jetbrains/kotlin/kmp/infra/AbstractTestLexer.kt`
- `kmp-parser/jvm/test/org/jetbrains/kotlin/kmp/infra/NewTestLexer.kt`
- `kmp-parser/jvm/test/org/jetbrains/kotlin/kmp/infra/TestSyntaxElement.kt`

Relationship to this plan:

```text
Kotlin.flex
  -> JFlex KotlinEmitter + Kotlin skeleton
  -> KotlinFlexLexer
  -> KotlinLexer : FlexAdapter(KotlinFlexLexer)
  -> proc-macro-kotlin KtTokenAdapter
  -> proc-macro-kotlin TokenStream

KotlinLexer.g4 / KotlinParser.g4 / UnicodeClasses.g4
  -> ANTLR Kotlin target generation
  -> generated KotlinLexer / KotlinParser using antlr4-kotlin
  -> proc-macro-kotlin AntlrTokenAdapter
  -> proc-macro-kotlin TokenStream
```

The JetBrains/JFlex path is already the working comparison path for
`TokenStream.fromString`. The ANTLR path should be validated against it for
lexical normalization and parser entry behavior. JFlex itself is not a runtime
dependency of `antlr4-kotlin`; it is a compiler-lineage source and comparison
input.

### proc-macro2 Compiler Variant Path

Once `proc-macro-kotlin` is published and wired:

```text
proc-macro2-kotlin TokenStream.fromString(source)
  -> Detection.insideProcMacro()
  -> wrapper selection
     -> fallback parser path:
          FallbackTokenStream.fromStrChecked(source)
     -> compiler path:
          proc-macro-kotlin TokenStream.fromString(source)
  -> proc-macro2 public TokenStream
```

Token construction should follow the same wrapper split:

```text
proc-macro2 TokenTree.Group
  -> WrapperGroup.Fallback(FallbackGroup)
  -> WrapperGroup.Compiler(proc-macro-kotlin Group)

proc-macro2 TokenTree.Ident
  -> WrapperIdent.Fallback(FallbackIdent)
  -> WrapperIdent.Compiler(proc-macro-kotlin Ident)

proc-macro2 TokenTree.Punct
  -> WrapperPunct.Fallback(FallbackPunct)
  -> WrapperPunct.Compiler(proc-macro-kotlin Punct)

proc-macro2 TokenTree.Literal
  -> WrapperLiteral.Fallback(FallbackLiteral)
  -> WrapperLiteral.Compiler(proc-macro-kotlin Literal)
```

The fallback parser stays valuable. It is the compatibility path for callers
outside compiler mode and for Rust-token text where Kotlin grammar input is
not the active source of truth.

### serde Derive End-To-End Path

The end-to-end Kotlin shape should preserve the Rust derive control flow while
adding explicit output backend selection:

```text
deriveSerialize(input: proc-macro-kotlin TokenStream)
  -> proc-macro2-kotlin TokenStream.fromProcMacro(input)
  -> syn-kotlin parseMacroInput(tokens, DeriveInput.parser)
     -> Success(DeriveInput)
     -> CompileError(TokenStream)
  -> serde-kotlin serdederive expandDeriveSerialize(DeriveInput)
     -> SynResult<TokenStream>
  -> serde derive expansion model
  -> output backend
     -> Rust: quote-kotlin / proc-macro2-kotlin TokenStream
     -> Kotlin: proc-macro-kotlin TokenStream or Kotlin source fragments
  -> proc-macro-kotlin TokenStream

deriveDeserialize follows the same shape.
```

The macro-entry functions should be thin. Real serde logic stays in functions
that accept parsed AST values and return a language-neutral expansion model.
Rust parity tests can render that model through the Rust backend. Kotlin
consumers render it through the Kotlin backend.

---

## Adapter Contract

`AntlrTokenAdapter` should live in `proc-macro-kotlin`, beside
`KtTokenAdapter`.

Input:

- `CommonTokenStream` or a filled `TokenStream`
- generated token vocabulary when available
- source name or source identity
- original source text when available

Required ANTLR token fields:

- `Token.type`
- `Token.text`
- `Token.channel`
- `Token.startIndex`
- `Token.stopIndex`
- `Token.line`
- `Token.charPositionInLine`
- `Token.tokenSource`
- `Token.inputStream`

Output:

- `List<TokenTree>`
- then `TokenStream(TokenStreamData(output))`

Core algorithm:

1. Fill the ANTLR token stream.
2. Drop hidden-channel tokens and EOF.
3. Group delimiter pairs by token text or generated token type:
   - `(` and `)` -> `Delimiter.PARENTHESIS`
   - `{` and `}` -> `Delimiter.BRACE`
   - `[` and `]` -> `Delimiter.BRACKET`
4. Convert identifiers and keywords to `Ident`.
5. Convert string, char, integer, and float spellings to `Literal` with the
   existing internal Kotlin literal helpers.
6. Convert punctuation to `Punct` values.
7. Preserve `Spacing.JOINT` for decomposed multi-character operators.
8. Attach spans from ANTLR start/stop offsets and line/column fields.

The adapter should not expose ANTLR token constants in the public
`proc_macro` API. Generated grammar-specific constants should be private to
adapter tests or injected through a small internal vocabulary map.

### Span Work

Current `proc-macro-kotlin SpanData` supports:

- `CallSite`
- `MixedSite`
- `DefSite`
- `Synthetic(IntRange)`

ANTLR tokens provide richer source information:

- byte or UTF-16-ish offsets from `startIndex` and `stopIndex`
- line
- column
- source name
- source text via `Token.inputStream`

The adapter can start with `Synthetic` ranges for structural correctness, but
diagnostics and source text fidelity need a source-backed `SpanData` shape in
`proc-macro-kotlin`. The natural shape is:

```text
SourceBacked(
  sourceId,
  sourceName,
  sourceText,
  byteRange,
  line,
  column
)
```

That work belongs in `proc-macro-kotlin`, not here. `antlr4-kotlin` needs to
keep token offsets, line, column, token source, and input stream behavior
correct enough for that span layer to trust.

---

## API Shape And Swift Export

The current full build reaches Swift Export and then fails in
`macosArm64DebugSwiftExport` with unsupported optional-wrapper handling. The
fix should change exported API shape while preserving generated parser
compatibility and every target in the build gate.

High-risk exported shapes in this runtime:

- `TokenFactory<Symbol : Token?>`
- `TokenSource.nextToken(): Token?`
- `TokenSource.inputStream: CharStream?`
- `Token.tokenSource: TokenSource?`
- `Token.inputStream: CharStream?`
- `CommonToken.source: Pair<TokenSource?, CharStream?>?`
- collections such as `List<Token?>`, `Array<String?>?`, and nullable maps
- generic listener/interpreter APIs that return nullable runtime types

Triage sequence:

1. Run the failing Swift Export task with stacktrace and info logging.
2. Identify the exact declaration Swift Export is lowering when it reports
   optional wrapping.
3. Prefer non-null public facade types where ANTLR compatibility allows it.
4. Where generated code needs a Java-shaped nullable API, isolate Swift-facing
   wrappers or reshape generics so Swift Export sees a simpler signature.
5. Re-run `swiftExportSmokeTest`.
6. Re-run full `build`.

The worker classpath error for
`kotlinx/coroutines/internal/intellij/IntellijCoroutines` is a separate
toolchain/classpath symptom on the same Swift Export path. It should be
validated after the optional-wrapper declaration is isolated so the two
failures are not mixed together.

---

## Test Ladder

Current committed tests:

- `src/commonTest/.../misc/IntegerListTest.kt`
- `src/jvmTest/.../CodePointCharStreamTest.kt`

Translated runtime tests exist under:

```text
/Volumes/stuff/Projects/kotlinmania/toport/antlr4/runtime-testsuite/test
```

Descriptor-driven runtime fixtures exist under:

```text
/Volumes/stuff/Projects/kotlinmania/toport/antlr4/runtime-testsuite/resources/org/antlr/v4/test/runtime/descriptors
```

Descriptor inventory:

| Area | Count |
| --- | ---: |
| CompositeLexers | 2 |
| CompositeParsers | 15 |
| FullContextParsing | 15 |
| LeftRecursion | 98 |
| LexerErrors | 12 |
| LexerExec | 42 |
| Listeners | 7 |
| ParseTrees | 10 |
| ParserErrors | 34 |
| ParserExec | 50 |
| Performance | 7 |
| SemPredEvalLexer | 8 |
| SemPredEvalParser | 26 |
| Sets | 31 |

Port order:

1. Pure common runtime tests:
   - `IntegerList`
   - `Interval`
   - `IntervalSet`
   - prediction context value behavior
   - ATN serializer/deserializer helpers that do not require generated grammar
     fixtures
2. JVM stream tests:
   - `CharStreams`
   - `CodePointCharStream`
   - `UnbufferedCharStream`
   - encoding and invalid-input behavior
3. Common token source and token stream tests:
   - `CommonToken`
   - `CommonTokenFactory`
   - `ListTokenSource`
   - `BufferedTokenStream`
   - `CommonTokenStream`
   - `UnbufferedTokenStream`
   - `TokenStreamRewriter`
4. Generated or ANTLR-compatible fixture tests:
   - hand-built token sources first
   - generated lexer fixtures second
   - generated parser fixtures third
5. Descriptor-driven runtime suite:
   - lexer execution
   - parser execution
   - parse tree behavior
   - listeners and visitors
   - semantic predicate evaluation
   - left recursion

The first valuable milestone is not raw test count. It is catching mechanical
translation defects in stream, token, and parser-compatible behavior before
downstream adapters rely on this runtime.

---

## Downstream Wiring Sequence

### 1. Finish antlr4-kotlin release readiness

Validation:

```bash
./gradlew jvmTest --no-daemon --no-configuration-cache
./gradlew compileKotlinJvm compileKotlinAndroidNativeArm64 compileKotlinJs compileKotlinWasmJs --no-daemon --no-configuration-cache
./gradlew swiftExportSmokeTest --no-daemon --no-configuration-cache
./gradlew build --no-daemon --no-configuration-cache
```

Release readiness requires:

- generated Gradle and workflow files synchronized with `proc-macro-kotlin`
- property and TOML files synchronized with the generated template's expected
  keys
- every target still in the build gate
- Swift Export smoke test passing
- runtime tests covering streams, token buffering, and parser-compatible token
  flow

### 2. Add antlr4-kotlin to proc-macro-kotlin

Do not edit generated `proc-macro-kotlin/build.gradle.kts` by hand.

Expected change shape:

- add `antlr4-kotlin` to `gradle/libs.versions.toml`
- include it in the common main dependency bundle selected by
  `gradle.properties`
- keep generated build script and workflows in sync with the parent template

### 3. Add AntlrTokenAdapter to proc-macro-kotlin

Code shape:

```text
internal object AntlrTokenAdapter {
  fun tokenize(
    tokens: io.github.kotlinmania.antlr4.TokenStream,
    vocabulary: Vocabulary?,
    source: SourceInfo?
  ): TokenStreamParseOutcome
}
```

Expected implementation shape:

- collect and normalize ANTLR tokens
- share grouping and punctuation spacing logic with `KtTokenAdapter` where the
  local code shape supports it
- use internal literal helpers
- produce `TokenStream(TokenStreamData(...))`
- return `TokenStreamParseOutcome.Err` for unclosed delimiters, unsupported
  token spellings, and malformed literal spellings

### 4. Compare JetBrains and ANTLR normalization

Add tests in `proc-macro-kotlin` with equivalent Kotlin snippets:

- identifiers and keywords
- nested delimiter groups
- string, char, integer, and float literals
- multi-character punctuation
- safe casts and negated `in`/`is`
- hidden tokens and comments
- source span ranges

The comparison target is normalized `TokenTree` shape, not identical lexer
token IDs.

### 5. Restore proc-macro2-kotlin wrapper dispatch

Expected change shape:

- replace direct public wrapping of fallback internals with wrapper internals
- remove the invalid port-lint placeholder in `Wrapper.kt`
- map fallback and compiler variants through the same public API
- preserve fallback parsing as the default outside compiler mode
- let `forceFallback` and `unforceFallback` keep their documented behavior

Mapping table:

| proc-macro2 public | fallback internal | compiler internal |
| --- | --- | --- |
| `TokenStream` | `FallbackTokenStream` | `proc-macro-kotlin TokenStream` |
| `TokenTree.Group` | `FallbackGroup` | `proc-macro-kotlin Group` |
| `TokenTree.Ident` | `FallbackIdent` | `proc-macro-kotlin Ident` |
| `TokenTree.Punct` | `FallbackPunct` | `proc-macro-kotlin Punct` |
| `TokenTree.Literal` | `FallbackLiteral` | `proc-macro-kotlin Literal` |
| `Span` | `FallbackSpan` | `proc-macro-kotlin Span` |
| `LexError` | fallback parse error | proc-macro parse error |

Enum mapping:

| proc-macro2 | proc-macro-kotlin |
| --- | --- |
| `Delimiter.Parenthesis` | `Delimiter.PARENTHESIS` |
| `Delimiter.Brace` | `Delimiter.BRACE` |
| `Delimiter.Bracket` | `Delimiter.BRACKET` |
| `Delimiter.None` | `Delimiter.NONE` |
| `Spacing.Joint` | `Spacing.JOINT` |
| `Spacing.Alone` | `Spacing.ALONE` |

Required conversion APIs:

```text
TokenStream.fromProcMacro(input: proc-macro-kotlin TokenStream)
TokenStream.toProcMacro(): proc-macro-kotlin TokenStream
Span.fromProcMacro(input: proc-macro-kotlin Span)
Span.toProcMacro(): proc-macro-kotlin Span
```

These can be internal at first if macro entry functions live in the same
module, but the conversion shape needs to exist. `quote-kotlin`, `syn-kotlin`,
and `serde-kotlin` should continue to talk primarily to `proc-macro2-kotlin`.

### 6. Revalidate quote-kotlin against wrapper tokens

Expected change shape:

- keep `ToTokens` and `TokenStreamExt` as the public construction layer
- ensure `append`, `appendAll`, `appendSeparated`, and `appendTerminated`
  preserve wrapper backend consistency
- keep static string parsing through `proc-macro2 TokenStream.fromString`
- add tests that quote-style builders produce the same token tree shape in
  fallback mode and compiler mode

### 7. Revalidate syn-kotlin against wrapper tokens

Expected change shape:

- `TokenBuffer.new2` must buffer either wrapper backend without dropping spans
- `Cursor.ident`, `punct`, `literal`, `group`, `anyGroup`, and `tokenTree`
  must preserve group delimiter spans
- `parseMacroInput` should keep returning `ParseMacroSynResult`
- `SynError.toCompileError()` must emit tokens that can convert back through
  `proc-macro2-kotlin` into `proc-macro-kotlin`
- `DeriveInput`, `Attribute`, `Meta`, `Generics`, `Fields`, `Variant`, and
  `Type` are the first serde-facing Syn nodes to validate

### 8. Finish serde_derive entry points

When `proc-macro2-kotlin` can select the compiler variant and still preserve
fallback behavior, serde macro code can use the Rust-shaped public API without
knowing whether a token stream came from fallback parsing or the compiler-backed
proc-macro path. That API is the validation and parsing bridge, not the only
generation target.

Expected change shape:

- keep serde runtime traits in `serde-kotlin`
- keep translated derive internals under `serdederive`
- add thin macro entry functions that accept and return `proc-macro-kotlin
  TokenStream`
- parse through `syn-kotlin`
- lower through `Container.fromAst`
- build a language-neutral serde expansion model
- render Rust output through `quote-kotlin` builders for parity proof
- render Kotlin output through a Kotlin backend for product use
- return `SynError.intoCompileError()` on failure

First serde derive tests:

- named struct `Serialize`
- tuple struct `Serialize`
- unit struct `Serialize`
- named struct `Deserialize`
- `#[serde(rename = "...")]`
- duplicate serde attribute error
- unsupported union error
- span on a malformed serde attribute
- same semantic model rendered as Rust and Kotlin for a small named struct

---

## Done Criteria

This repository is ready for `proc-macro-kotlin` release wiring when:

- `./gradlew build --no-daemon --no-configuration-cache` passes.
- Android, Android Native, JVM, JS, Wasm, Apple, Linux, and Windows target
  compilation remain in the build gate.
- Swift Export smoke testing passes locally.
- Runtime tests cover character streams, token streams, ATN/DFA behavior, and
  parser-compatible token source flow.
- The ANTLR-to-proc-macro adapter contract is proven by tests.
- Generated build and workflow files remain synchronized with
  `proc-macro-kotlin`.
- `gradle.properties` and `gradle/libs.versions.toml` are synchronized with
  the generated build's expected keys and dependency bundle names.
