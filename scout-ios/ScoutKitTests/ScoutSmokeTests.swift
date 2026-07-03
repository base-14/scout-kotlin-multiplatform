import XCTest
import Scout
@testable import ScoutKit

// Live end-to-end smoke test. Fill SCOUT_TEST_ENDPOINT / SCOUT_TEST_TOKEN (env or below) with a
// real collector before running; skipped if unset. Verifies data reaches the collector on iOS.
final class ScoutSmokeTests: XCTestCase {
    func testEmitsToCollector() throws {
        let env = ProcessInfo.processInfo.environment
        let endpoint = env["SCOUT_TEST_ENDPOINT"] ?? "https://your-collector.example/otlp"
        let token = env["SCOUT_TEST_TOKEN"] ?? "<YOUR_SCOUT_INGEST_TOKEN>"
        try XCTSkipIf(token.hasPrefix("<"), "Set SCOUT_TEST_ENDPOINT / SCOUT_TEST_TOKEN to run the live smoke test")

        Scout.start(
            serviceName: "ios-smoke-test",
            endpoint: endpoint,
            environment: "test",
            headers: ["Authorization": "Bearer \(token)"],
            sessionSampleRate: 100.0,
            enableCrashReporting: false
        )
        Scout.setScreen("SmokeHome")
        Scout.logInfo("ios smoke test log")
        Scout.logEvent("ios_smoke_event")
        Scout.setUser("ios-tester-1")
        Scout.addBreadcrumb(type: "test", message: "smoke breadcrumb")
        Scout.reportError(NSError(domain: "SmokeDomain", code: 42, userInfo: [NSLocalizedDescriptionKey: "handled smoke error"]))
        Scout.setScreen("SmokeDetail")

        let exp = expectation(description: "http")
        // Auto-capture path: a plain custom session (no manual protocol injection) should be
        // instrumented by the protocolClasses swizzle installed in Scout.start.
        let session = URLSession(configuration: .default)
        session.dataTask(with: URL(string: "https://example.com")!) { _, _, _ in
            exp.fulfill()
        }.resume()
        wait(for: [exp], timeout: 30)

        // Let the batch span/log exporters flush to the collector.
        Thread.sleep(forTimeInterval: 25)
    }
}
