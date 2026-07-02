import XCTest
import Scout
import KSCrashRecording
@testable import ScoutKit

// Verifies the native-crash and ANR pipelines end-to-end on iOS. A KSCrash user-reported
// exception writes a real crash report to the store (without terminating), which drainPending
// turns into a `native_crash` span; a hard main-thread block past the watchdog threshold
// produces an `anr`. Reads SCOUT_TEST_ENDPOINT / SCOUT_TEST_TOKEN from the environment.
final class CrashTests: XCTestCase {
    func testCrashAndAnrPipeline() throws {
        let env = ProcessInfo.processInfo.environment
        let endpoint = env["SCOUT_TEST_ENDPOINT"] ?? "https://your-collector.example/otlp"
        let token = env["SCOUT_TEST_TOKEN"] ?? "<YOUR_SCOUT_INGEST_TOKEN>"
        try XCTSkipIf(token.hasPrefix("<"), "Set SCOUT_TEST_ENDPOINT / SCOUT_TEST_TOKEN to run the crash test")

        Scout.start(
            serviceName: "ios-crash-test",
            endpoint: endpoint,
            environment: "test",
            headers: ["Authorization": "Bearer \(token)"],
            sessionSampleRate: 100.0,
            enableCrashReporting: true,
            enableHttpTracking: false,
            enableScreenTracking: false,
            anrThresholdMs: 2000
        )

        // 1) Native crash: KSCrash writes a real report to its store without terminating.
        KSCrash.shared.reportUserException(
            "ScoutTestCrash",
            reason: "verify native crash pipeline",
            language: "swift",
            lineOfCode: nil,
            stackTrace: ["0 ScoutKitTests testCrashAndAnrPipeline"],
            logAllThreads: true,
            terminateProgram: false
        )
        ScoutCrashReporter.drainPending()

        // 2) ANR: hard-block the main thread past the 2s watchdog threshold (below KSCrash's 5s
        //    deadlock monitor so only our watchdog fires).
        Thread.sleep(forTimeInterval: 3.0)

        // 3) Flush via a soft wait (spins the runloop so the watchdog doesn't re-fire).
        let flush = expectation(description: "flush")
        DispatchQueue.global().asyncAfter(deadline: .now() + 25) { flush.fulfill() }
        wait(for: [flush], timeout: 30)
    }
}
