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
        // Custom URLSession(configuration:) sessions: inject the protocol via configuration.
        swizzle(URLSessionConfiguration.self,
                #selector(getter: URLSessionConfiguration.protocolClasses),
                #selector(URLSessionConfiguration.scout_protocolClasses))
        // URLSession.shared ignores protocols, so wrap its completion-handler dataTasks directly.
        swizzle(URLSession.self,
                NSSelectorFromString("dataTaskWithRequest:completionHandler:"),
                #selector(URLSession.scout_dataTask(with:completionHandler:)))
        swizzle(URLSession.self,
                NSSelectorFromString("dataTaskWithURL:completionHandler:"),
                #selector(URLSession.scout_dataTaskURL(with:completionHandler:)))
    }

    private static func swizzle(_ cls: AnyClass, _ original: Selector, _ replacement: Selector) {
        guard let o = class_getInstanceMethod(cls, original),
              let r = class_getInstanceMethod(cls, replacement) else { return }
        method_exchangeImplementations(o, r)
    }

    static func nowEpochNanos() -> Int64 { Int64(Date().timeIntervalSince1970 * 1_000_000_000) }

    static func record(request: URLRequest, response: URLResponse?, error: Error?, startEpochNanos: Int64) {
        guard let url = request.url else { return }
        if let host = url.host,
           let collectorHost = URL(string: ScoutEngine.shared.collectorEndpoint)?.host,
           host == collectorHost { return }
        let status = (response as? HTTPURLResponse)?.statusCode ?? (error == nil ? 0 : -1)
        ScoutEngine.shared.reportHttp(
            method: request.httpMethod ?? "GET",
            url: url.absoluteString,
            statusCode: Int64(status),
            responseSize: Int64(response?.expectedContentLength ?? -1),
            errorMessage: error?.localizedDescription,
            startEpochNanos: startEpochNanos,
            endEpochNanos: nowEpochNanos()
        )
    }
}

extension URLSession {
    @objc func scout_dataTask(with request: URLRequest, completionHandler: @escaping (Data?, URLResponse?, Error?) -> Void) -> URLSessionDataTask {
        // Only the shared session; custom sessions are covered by ScoutURLProtocol.
        guard self === URLSession.shared else {
            return self.scout_dataTask(with: request, completionHandler: completionHandler)
        }
        let start = HttpTracking.nowEpochNanos()
        return self.scout_dataTask(with: request, completionHandler: { data, response, error in
            HttpTracking.record(request: request, response: response, error: error, startEpochNanos: start)
            completionHandler(data, response, error)
        })
    }

    @objc func scout_dataTaskURL(with url: URL, completionHandler: @escaping (Data?, URLResponse?, Error?) -> Void) -> URLSessionDataTask {
        guard self === URLSession.shared else {
            return self.scout_dataTaskURL(with: url, completionHandler: completionHandler)
        }
        let request = URLRequest(url: url)
        let start = HttpTracking.nowEpochNanos()
        return self.scout_dataTaskURL(with: url, completionHandler: { data, response, error in
            HttpTracking.record(request: request, response: response, error: error, startEpochNanos: start)
            completionHandler(data, response, error)
        })
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
            responseSize: Int64(response?.expectedContentLength ?? -1),
            errorMessage: error?.localizedDescription,
            startEpochNanos: startEpochNanos,
            endEpochNanos: endEpochNanos
        )
    }
}
