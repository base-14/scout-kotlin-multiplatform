import Foundation
import ObjectiveC.runtime
import Scout

/// HTTP capture via a pass-through `URLProtocol`. Times each request and forwards a
/// `http.request` span (method, url, status) into the engine.
///
/// `URLProtocol.registerClass` alone only covers `NSURLConnection`. To cover
/// `URLSession(configuration:)` sessions we swizzle `URLSessionConfiguration.protocolClasses`
/// to inject our protocol into every configuration. Note: `URLSession.shared` ignores custom
/// protocols entirely (Apple limitation) — covering it would require swizzling `URLSession`'s
/// `dataTask` methods, a heavier follow-up.
enum HttpTracking {
    private static var installed = false

    static func install() {
        guard !installed else { return }
        installed = true
        URLProtocol.registerClass(ScoutURLProtocol.self)
        guard
            let original = class_getInstanceMethod(URLSessionConfiguration.self, #selector(getter: URLSessionConfiguration.protocolClasses)),
            let replacement = class_getInstanceMethod(URLSessionConfiguration.self, #selector(URLSessionConfiguration.scout_protocolClasses))
        else { return }
        method_exchangeImplementations(original, replacement)
    }
}

extension URLSessionConfiguration {
    @objc func scout_protocolClasses() -> [AnyClass]? {
        var classes = self.scout_protocolClasses() ?? [] // original getter (swapped)
        if !classes.contains(where: { $0 == ScoutURLProtocol.self }) {
            classes.insert(ScoutURLProtocol.self, at: 0)
        }
        return classes
    }
}

final class ScoutURLProtocol: URLProtocol, URLSessionDataDelegate {
    private static let handledKey = "ScoutURLProtocolHandled"

    private var session: URLSession?
    private var dataTask: URLSessionDataTask?
    private var startEpochNanos: Int64 = 0
    private var response: URLResponse?

    private static func nowEpochNanos() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1_000_000_000)
    }

    override class func canInit(with request: URLRequest) -> Bool {
        // Avoid recursion: don't re-handle a request we've already tagged.
        if URLProtocol.property(forKey: handledKey, in: request) != nil { return false }
        guard let url = request.url, let scheme = url.scheme?.lowercased() else { return false }
        if scheme != "http" && scheme != "https" { return false }
        // Never instrument the SDK's own telemetry uploads to the collector.
        if let host = url.host,
           let collectorHost = URL(string: ScoutEngine.shared.collectorEndpoint)?.host,
           host == collectorHost {
            return false
        }
        return true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let mutable = (request as NSURLRequest).mutableCopy() as? NSMutableURLRequest else { return }
        ScoutURLProtocol.setProperty(true, forKey: ScoutURLProtocol.handledKey, in: mutable)
        startEpochNanos = ScoutURLProtocol.nowEpochNanos()
        let config = URLSessionConfiguration.default
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        dataTask = session?.dataTask(with: mutable as URLRequest)
        dataTask?.resume()
    }

    override func stopLoading() {
        dataTask?.cancel()
        session?.invalidateAndCancel()
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        self.response = response
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        client?.urlProtocol(self, didLoad: data)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        emit(endEpochNanos: ScoutURLProtocol.nowEpochNanos(), error: error)
        if let error = error {
            client?.urlProtocol(self, didFailWithError: error)
        } else {
            client?.urlProtocolDidFinishLoading(self)
        }
    }

    private func emit(endEpochNanos: Int64, error: Error?) {
        let method = request.httpMethod ?? "GET"
        let url = request.url?.absoluteString ?? ""
        let status = (response as? HTTPURLResponse)?.statusCode ?? (error == nil ? 0 : -1)
        ScoutEngine.shared.reportHttp(
            method: method,
            url: url,
            statusCode: Int64(status),
            startEpochNanos: startEpochNanos,
            endEpochNanos: endEpochNanos
        )
    }
}
