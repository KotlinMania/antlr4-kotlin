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
}
#endif
