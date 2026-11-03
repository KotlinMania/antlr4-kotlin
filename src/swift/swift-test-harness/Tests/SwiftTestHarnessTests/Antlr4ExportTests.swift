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
}
#endif
