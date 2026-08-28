#if canImport(Testing)
import Testing
import Antlr4

@Suite("Antlr4 Swift Export Tests")
struct Antlr4ExportTests {
    @Test("Antlr4 swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "Antlr4 swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Antlr4

final class Antlr4ExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Antlr4 swift module imported cleanly")
    }
}
#endif
