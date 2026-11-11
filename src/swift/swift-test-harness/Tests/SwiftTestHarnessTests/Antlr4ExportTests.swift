#if canImport(Testing)
import Testing
import Antlr4

@Suite("Antlr4 Swift Export Tests")
struct Antlr4ExportTests {
    @Test("ANTLRInputStream operations: LA, consume, seek, reset")
    func testANTLRInputStreamOperations() throws {
        let stream = ANTLRInputStream(input: "hello world")
        #expect(stream.index() == 0)
        #expect(stream.LA(i: 1) == 104) // 'h'
        
        stream.consume()
        #expect(stream.index() == 1)
        #expect(stream.LA(i: 1) == 101) // 'e'
        
        stream.seek(index: 6)
        #expect(stream.index() == 6)
        #expect(stream.LA(i: 1) == 119) // 'w'
        
        stream.reset()
        #expect(stream.index() == 0)
        #expect(stream.LA(i: 1) == 104) // 'h'
    }

    @Test("CommonToken properties and mutation")
    func testCommonTokenProperties() throws {
        let token = CommonToken(type: 10, text: "foobar")
        #expect(token.type == 10)
        #expect(token.text == "foobar")
        
        token.channel = 2
        #expect(token.channel == 2)
        
        token.line = 5
        #expect(token.line == 5)
    }

    @Test("IntStream companion constants")
    func testIntStreamConstants() throws {
        let eof = _ExportedKotlinPackages_io_github_kotlinmania_antlr4_IntStream_Companion.shared.EOF
        #expect(eof == -1)
    }

    @Test("VocabularyImpl and ListTokenSource bridging")
    func testVocabularyAndListTokenSource() throws {
        let vocab = VocabularyImpl(literalNames: ["", "'fun'", "'val'"], symbolicNames: ["", "FUN", "VAL"])
        #expect(vocab.maxTokenType == 2)
        #expect(vocab.getLiteralName(tokenType: 1) == "'fun'")
        #expect(vocab.getSymbolicName(tokenType: 2) == "VAL")
        #expect(vocab.getLiteralName(tokenType: 0) == nil)

        let t1 = CommonToken(type: 1, text: "fun")
        let t2 = CommonToken(type: 2, text: "val")
        let source = ListTokenSource(tokens: [t1, t2])
        #expect(source.nextToken()?.text == "fun")
        #expect(source.nextToken()?.text == "val")
        #expect(source.nextToken()?.type == -1)
    }

    @Test("Tooling PostScriptDocument and TestRig option parsing")
    func testToolingPostScriptAndTestRig() throws {
        let doc = io.github.kotlinmania.antlr4.tooling.gui.PostScriptDocument(fontName: "CourierNew", fontSize: 11)
        doc.boundingBox(w: 120, h: 60)
        doc.text(s: "expr(1)", x: 10.0, y: 20.0)
        let ps = doc.ps
        #expect(ps.contains("%!PS-Adobe-3.0 EPSF-3.0"))
        #expect(ps.contains("(expr\\(1\\)) show"))

        let rig = io.github.kotlinmania.antlr4.tooling.gui.TestRig(args: ["Kotlin", "kotlinFile", "-tokens", "-tree", "-SLL", "Sample.kt"])
        #expect(rig.grammarName == "Kotlin")
        #expect(rig.startRuleName == "kotlinFile")
        #expect(rig.showTokens == true)
        #expect(rig.printTree == true)
        #expect(rig.sll == true)
        #expect(rig.inputFiles == ["Sample.kt"])
    }

    @Test("Tooling UnicodeData, CharSupport, and EscapeSequenceParsing")
    func testToolingUnicodeAndEscapeParsing() throws {
        let lu = io.github.kotlinmania.antlr4.tooling.unicode.UnicodeData.shared.getPropertyCodePoints(propertyCodeOrAlias: "Lu")
        #expect(lu?.contains(el: 88) == true) // 'X'
        #expect(lu?.contains(el: 120) == false) // 'x'

        let lit = io.github.kotlinmania.antlr4.tooling.misc.CharSupport.shared.getANTLRCharLiteralForChar(c: 0x10FFFF)
        #expect(lit == "'\\u{10FFFF}'")

        let parsed = io.github.kotlinmania.antlr4.tooling.misc.EscapeSequenceParsing.shared.parseEscape(s: "\\p{Deseret}", startOff: 0)
        #expect(parsed.parseLength == 11)
        #expect(parsed.propertyIntervalSet.contains(el: 66560) == true)
    }

    @Test("Tooling GrammarAST, ErrorManager, and AttributeDict")
    func testToolingASTAndErrorManager() throws {
        let ruleType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.RULE
        let blockType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.BLOCK
        let altType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.ALT
        let ruleRefType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.RULE_REF
        let tokenRefType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.TOKEN_REF

        let ruleNode = io.github.kotlinmania.antlr4.tooling.ast.RuleAST(type: ruleType)
        ruleNode.addChild(t: io.github.kotlinmania.antlr4.tooling.ast.GrammarAST(t: CommonToken(type: ruleRefType, text: "expr")))
        let blockNode = io.github.kotlinmania.antlr4.tooling.ast.BlockAST(type: blockType)
        let altNode = io.github.kotlinmania.antlr4.tooling.ast.AltAST(type: altType)
        altNode.addChild(t: io.github.kotlinmania.antlr4.tooling.ast.TerminalAST(t: CommonToken(type: tokenRefType, text: "INT")))
        blockNode.addChild(t: altNode)
        ruleNode.addChild(t: blockNode)

        #expect(ruleNode.ruleName == "expr")
        #expect(ruleNode.isLexerRule == false)
        #expect(ruleNode.toStringTree() == "(RULE expr (BLOCK (ALT INT)))")

        let errMgr = io.github.kotlinmania.antlr4.tooling.tool.ErrorManager(tool: nil)
        let tok = CommonToken(type: tokenRefType, text: "BAD")
        tok.line = 7
        tok.charPositionInLine = 2
        let synMsg = io.github.kotlinmania.antlr4.tooling.tool.GrammarSyntaxMessage(
            errorType: .SYNTAX_ERROR,
            fileName: "Expr.g4",
            offendingToken: tok,
            detail: "missing ';'"
        )
        #expect(errMgr.formatMessage(msg: synMsg) == "error(50): Expr.g4:7:2: syntax error: missing ';'")

        let predefined = io.github.kotlinmania.antlr4.tooling.tool.AttributeDict.Companion.shared.predefinedTokenDict
        #expect(predefined.size() == 7)
        #expect(predefined.get(name: "text")?.name == "text")
    }

    @Test("Tooling ANTLRParser, ScopeParser, and ActionSplitter")
    func testToolingGrammarParserScopeAndActionSplitter() throws {
        let gStream = ANTLRInputStream(input: "grammar Expr; expr : expr '+' INT | INT ; INT : [0-9]+ ;")
        let gLexer = io.github.kotlinmania.antlr4.tooling.parse.ANTLRLexer(input: gStream)
        let gTokens = CommonTokenStream(tokenSource: gLexer)
        let gParser = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParser(input: gTokens)
        let spec = gParser.grammarSpec()
        #expect(spec.tree.grammarName == "Expr")
        #expect(
            spec.tree.toStringTree() ==
                "(COMBINED_GRAMMAR Expr (RULES (RULE expr (BLOCK (ALT expr '+' INT) (ALT INT))) (RULE INT (BLOCK (ALT (+ (BLOCK (ALT [0-9]))))))))"
        )

        let attrs = io.github.kotlinmania.antlr4.tooling.parse.ScopeParser.shared.parseTypedArgList(
            action: nil,
            s: "x:Int = 10, name:String",
            g: nil
        )
        #expect(attrs.size() == 2)
        #expect(attrs.get(name: "x")?.type == "Int")
        #expect(attrs.get(name: "x")?.initValue == "10")
        #expect(attrs.get(name: "name")?.type == "String")

        let splitter = io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter(
            input: ANTLRInputStream(input: "$expr.text + $a::count"),
            delegate: io.github.kotlinmania.antlr4.tooling.semantics.BlankActionSplitterListener(),
            initialLine: 1,
            initialCharPositionInLine: 0
        )
        let chunks = splitter.getActionTokens()
        #expect(chunks.count == 3)
        #expect(chunks[0].type == io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter.Companion.shared.QUALIFIED_ATTR)
        #expect(chunks[2].type == io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter.Companion.shared.NONLOCAL_ATTR)
    }

    @Test("Tooling Grammar, LeftRecursion, GrammarParserInterpreter, DOTGenerator, and Interpreter")
    func testToolingGrammarLeftRecursionInterpreterAndDOTGenerator() throws {
        let combined = io.github.kotlinmania.antlr4.tooling.tool.Grammar(
            grammarText: """
            grammar Calc;
            expr : expr '*' expr
                 | expr '+' expr
                 | INT
                 ;
            INT : [0-9]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
            """
        )
        let lexerGrammar = try #require(combined.implicitLexer)
        let lexInterp = lexerGrammar.createLexerInterpreter(input: ANTLRInputStream(input: "1 + 2 * 3"))
        let tokenStream = CommonTokenStream(tokenSource: lexInterp)
        let parserInterp = combined.createGrammarParserInterpreter(tokenStream: tokenStream)
        let exprRule = try #require(combined.getRule(name: "expr"))
        let tree = try #require(parserInterp.parse(startRuleIndex: exprRule.index))
        #expect(tree.toStringTree(parser: parserInterp) == "(expr:2 (expr:3 1) + (expr:1 (expr:3 2) * (expr:3 3)))")

        let dotGen = io.github.kotlinmania.antlr4.tooling.tool.DOTGenerator(grammar: combined)
        let startState = combined.getATN().ruleToStartState[Int(exprRule.index)]
        let dot = dotGen.getDOT(startState: startState)
        #expect(dot?.contains("digraph ATN") == true)

        let guiInterp = io.github.kotlinmania.antlr4.tooling.gui.Interpreter(args: ["Calc.g4", "expr", "-tree"])
        let info = guiInterp.interpGrammarText(
            grammarText: """
            grammar Calc;
            expr : expr '+' INT | INT ;
            INT : [0-9]+ ;
            WS : [ \\t]+ -> skip ;
            """,
            lexerGrammarText: nil,
            charStream: ANTLRInputStream(input: "10 + 20"),
            enableProfile: false
        )
        #expect(info != nil)
        #expect(guiInterp.lastRenderedTree == "(expr:1 (expr:2 10) + 20)")
    }
}
#elseif canImport(XCTest)
import XCTest
import Antlr4

final class Antlr4ExportTests: XCTestCase {
    func testANTLRInputStreamOperations() throws {
        let stream = ANTLRInputStream(input: "hello world")
        XCTAssertEqual(stream.index(), 0)
        XCTAssertEqual(stream.LA(i: 1), 104)
        
        stream.consume()
        XCTAssertEqual(stream.index(), 1)
        XCTAssertEqual(stream.LA(i: 1), 101)
        
        stream.seek(index: 6)
        XCTAssertEqual(stream.index(), 6)
        XCTAssertEqual(stream.LA(i: 1), 119)
        
        stream.reset()
        XCTAssertEqual(stream.index(), 0)
        XCTAssertEqual(stream.LA(i: 1), 104)
    }

    func testCommonTokenProperties() throws {
        let token = CommonToken(type: 10, text: "foobar")
        XCTAssertEqual(token.type, 10)
        XCTAssertEqual(token.text, "foobar")
        
        token.channel = 2
        XCTAssertEqual(token.channel, 2)
        
        token.line = 5
        XCTAssertEqual(token.line, 5)
    }

    func testIntStreamConstants() throws {
        let eof = _ExportedKotlinPackages_io_github_kotlinmania_antlr4_IntStream_Companion.shared.EOF
        XCTAssertEqual(eof, -1)
    }

    func testVocabularyAndListTokenSource() throws {
        let vocab = VocabularyImpl(literalNames: ["", "'fun'", "'val'"], symbolicNames: ["", "FUN", "VAL"])
        XCTAssertEqual(vocab.maxTokenType, 2)
        XCTAssertEqual(vocab.getLiteralName(tokenType: 1), "'fun'")
        XCTAssertEqual(vocab.getSymbolicName(tokenType: 2), "VAL")
        XCTAssertNil(vocab.getLiteralName(tokenType: 0))

        let t1 = CommonToken(type: 1, text: "fun")
        let t2 = CommonToken(type: 2, text: "val")
        let source = ListTokenSource(tokens: [t1, t2])
        XCTAssertEqual(source.nextToken()?.text, "fun")
        XCTAssertEqual(source.nextToken()?.text, "val")
        XCTAssertEqual(source.nextToken()?.type, -1)
    }

    func testToolingPostScriptAndTestRig() throws {
        let doc = io.github.kotlinmania.antlr4.tooling.gui.PostScriptDocument(fontName: "CourierNew", fontSize: 11)
        doc.boundingBox(w: 120, h: 60)
        doc.text(s: "expr(1)", x: 10.0, y: 20.0)
        let ps = doc.ps
        XCTAssertTrue(ps.contains("%!PS-Adobe-3.0 EPSF-3.0"))
        XCTAssertTrue(ps.contains("(expr\\(1\\)) show"))

        let rig = io.github.kotlinmania.antlr4.tooling.gui.TestRig(args: ["Kotlin", "kotlinFile", "-tokens", "-tree", "-SLL", "Sample.kt"])
        XCTAssertEqual(rig.grammarName, "Kotlin")
        XCTAssertEqual(rig.startRuleName, "kotlinFile")
        XCTAssertTrue(rig.showTokens)
        XCTAssertTrue(rig.printTree)
        XCTAssertTrue(rig.sll)
        XCTAssertEqual(rig.inputFiles, ["Sample.kt"])
    }

    func testToolingUnicodeAndEscapeParsing() throws {
        let lu = io.github.kotlinmania.antlr4.tooling.unicode.UnicodeData.shared.getPropertyCodePoints(propertyCodeOrAlias: "Lu")
        XCTAssertEqual(lu?.contains(el: 88), true)
        XCTAssertEqual(lu?.contains(el: 120), false)

        let lit = io.github.kotlinmania.antlr4.tooling.misc.CharSupport.shared.getANTLRCharLiteralForChar(c: 0x10FFFF)
        XCTAssertEqual(lit, "'\\u{10FFFF}'")

        let parsed = io.github.kotlinmania.antlr4.tooling.misc.EscapeSequenceParsing.shared.parseEscape(s: "\\p{Deseret}", startOff: 0)
        XCTAssertEqual(parsed.parseLength, 11)
        XCTAssertTrue(parsed.propertyIntervalSet.contains(el: 66560))
    }

    func testToolingASTAndErrorManager() throws {
        let ruleType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.RULE
        let blockType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.BLOCK
        let altType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.ALT
        let ruleRefType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.RULE_REF
        let tokenRefType = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes.shared.TOKEN_REF

        let ruleNode = io.github.kotlinmania.antlr4.tooling.ast.RuleAST(type: ruleType)
        ruleNode.addChild(t: io.github.kotlinmania.antlr4.tooling.ast.GrammarAST(t: CommonToken(type: ruleRefType, text: "expr")))
        let blockNode = io.github.kotlinmania.antlr4.tooling.ast.BlockAST(type: blockType)
        let altNode = io.github.kotlinmania.antlr4.tooling.ast.AltAST(type: altType)
        altNode.addChild(t: io.github.kotlinmania.antlr4.tooling.ast.TerminalAST(t: CommonToken(type: tokenRefType, text: "INT")))
        blockNode.addChild(t: altNode)
        ruleNode.addChild(t: blockNode)

        XCTAssertEqual(ruleNode.ruleName, "expr")
        XCTAssertFalse(ruleNode.isLexerRule)
        XCTAssertEqual(ruleNode.toStringTree(), "(RULE expr (BLOCK (ALT INT)))")

        let errMgr = io.github.kotlinmania.antlr4.tooling.tool.ErrorManager(tool: nil)
        let tok = CommonToken(type: tokenRefType, text: "BAD")
        tok.line = 7
        tok.charPositionInLine = 2
        let synMsg = io.github.kotlinmania.antlr4.tooling.tool.GrammarSyntaxMessage(
            errorType: .SYNTAX_ERROR,
            fileName: "Expr.g4",
            offendingToken: tok,
            detail: "missing ';'"
        )
        XCTAssertEqual(errMgr.formatMessage(msg: synMsg), "error(50): Expr.g4:7:2: syntax error: missing ';'")

        let predefined = io.github.kotlinmania.antlr4.tooling.tool.AttributeDict.Companion.shared.predefinedTokenDict
        XCTAssertEqual(predefined.size(), 7)
        XCTAssertEqual(predefined.get(name: "text")?.name, "text")
    }

    func testToolingGrammarParserScopeAndActionSplitter() throws {
        let gStream = ANTLRInputStream(input: "grammar Expr; expr : expr '+' INT | INT ; INT : [0-9]+ ;")
        let gLexer = io.github.kotlinmania.antlr4.tooling.parse.ANTLRLexer(input: gStream)
        let gTokens = CommonTokenStream(tokenSource: gLexer)
        let gParser = io.github.kotlinmania.antlr4.tooling.parse.ANTLRParser(input: gTokens)
        let spec = gParser.grammarSpec()
        XCTAssertEqual(spec.tree.grammarName, "Expr")
        XCTAssertEqual(
            spec.tree.toStringTree(),
            "(COMBINED_GRAMMAR Expr (RULES (RULE expr (BLOCK (ALT expr '+' INT) (ALT INT))) (RULE INT (BLOCK (ALT (+ (BLOCK (ALT [0-9]))))))))"
        )

        let attrs = io.github.kotlinmania.antlr4.tooling.parse.ScopeParser.shared.parseTypedArgList(
            action: nil,
            s: "x:Int = 10, name:String",
            g: nil
        )
        XCTAssertEqual(attrs.size(), 2)
        XCTAssertEqual(attrs.get(name: "x")?.type, "Int")
        XCTAssertEqual(attrs.get(name: "x")?.initValue, "10")
        XCTAssertEqual(attrs.get(name: "name")?.type, "String")

        let splitter = io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter(
            input: ANTLRInputStream(input: "$expr.text + $a::count"),
            delegate: io.github.kotlinmania.antlr4.tooling.semantics.BlankActionSplitterListener(),
            initialLine: 1,
            initialCharPositionInLine: 0
        )
        let chunks = splitter.getActionTokens()
        XCTAssertEqual(chunks.count, 3)
        XCTAssertEqual(chunks[0].type, io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter.Companion.shared.QUALIFIED_ATTR)
        XCTAssertEqual(chunks[2].type, io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter.Companion.shared.NONLOCAL_ATTR)
    }

    func testToolingGrammarLeftRecursionInterpreterAndDOTGenerator() throws {
        let combined = io.github.kotlinmania.antlr4.tooling.tool.Grammar(
            grammarText: """
            grammar Calc;
            expr : expr '*' expr
                 | expr '+' expr
                 | INT
                 ;
            INT : [0-9]+ ;
            WS : [ \\t\\r\\n]+ -> skip ;
            """
        )
        let lexerGrammar = try XCTUnwrap(combined.implicitLexer)
        let lexInterp = lexerGrammar.createLexerInterpreter(input: ANTLRInputStream(input: "1 + 2 * 3"))
        let tokenStream = CommonTokenStream(tokenSource: lexInterp)
        let parserInterp = combined.createGrammarParserInterpreter(tokenStream: tokenStream)
        let exprRule = try XCTUnwrap(combined.getRule(name: "expr"))
        let tree = try XCTUnwrap(parserInterp.parse(startRuleIndex: exprRule.index))
        XCTAssertEqual(tree.toStringTree(parser: parserInterp), "(expr:2 (expr:3 1) + (expr:1 (expr:3 2) * (expr:3 3)))")

        let dotGen = io.github.kotlinmania.antlr4.tooling.tool.DOTGenerator(grammar: combined)
        let startState = combined.getATN().ruleToStartState[Int(exprRule.index)]
        let dot = dotGen.getDOT(startState: startState)
        XCTAssertEqual(dot?.contains("digraph ATN"), true)

        let guiInterp = io.github.kotlinmania.antlr4.tooling.gui.Interpreter(args: ["Calc.g4", "expr", "-tree"])
        let info = guiInterp.interpGrammarText(
            grammarText: """
            grammar Calc;
            expr : expr '+' INT | INT ;
            INT : [0-9]+ ;
            WS : [ \\t]+ -> skip ;
            """,
            lexerGrammarText: nil,
            charStream: ANTLRInputStream(input: "10 + 20"),
            enableProfile: false
        )
        XCTAssertNotNil(info)
        XCTAssertEqual(guiInterp.lastRenderedTree, "(expr:1 (expr:2 10) + 20)")
    }
}
#endif
