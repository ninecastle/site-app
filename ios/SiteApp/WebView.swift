import SwiftUI
import WebKit

/// 사이트를 주소창 없이 띄우는 웹뷰.
/// - 로그인/쿠키 유지 (기본 데이터 저장소)
/// - 왼쪽 가장자리에서 밀면 뒤로가기
/// - 아래로 당기면 새로고침
/// - 동영상 화면 안 재생 + 전체화면
/// - 새 창 링크도 앱 안에서, 전화·메일 등은 해당 앱으로
/// - 인터넷 끊김 안내 + 다시 시도
struct WebView: UIViewRepresentable {
    let url: URL

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.allowsPictureInPictureMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.preferences.isElementFullscreenEnabled = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.allowsBackForwardNavigationGestures = true
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear

        let refresh = UIRefreshControl()
        refresh.addTarget(context.coordinator, action: #selector(Coordinator.pullToRefresh(_:)), for: .valueChanged)
        webView.scrollView.refreshControl = refresh

        context.coordinator.webView = webView
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        weak var webView: WKWebView?

        @objc func pullToRefresh(_ sender: UIRefreshControl) {
            webView?.reload()
        }

        // 링크 처리: 웹 주소는 앱 안에서, 그 외(tel:, mailto:, 앱 링크)는 iOS 에 넘김
        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
            guard let url = navigationAction.request.url, let scheme = url.scheme?.lowercased() else {
                return .allow
            }
            if ["http", "https", "about", "blob", "data"].contains(scheme) {
                return .allow
            }
            await UIApplication.shared.open(url)
            return .cancel
        }

        // target="_blank" / window.open → 같은 화면에서 열기
        func webView(_ webView: WKWebView,
                     createWebViewWith configuration: WKWebViewConfiguration,
                     for navigationAction: WKNavigationAction,
                     windowFeatures: WKWindowFeatures) -> WKWebView? {
            if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
            }
            return nil
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.scrollView.refreshControl?.endRefreshing()
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            handle(error, in: webView)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            handle(error, in: webView)
        }

        private func handle(_ error: Error, in webView: WKWebView) {
            webView.scrollView.refreshControl?.endRefreshing()
            let nsError = error as NSError
            // 사용자가 다른 링크를 눌러 취소된 경우 등은 무시
            if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled { return }
            if nsError.domain == "WebKitErrorDomain" && nsError.code == 102 { return } // 프레임 로드 중단

            let failed = (nsError.userInfo[NSURLErrorFailingURLStringErrorKey] as? String)
                ?? webView.url?.absoluteString
                ?? SiteConfig.startURL.absoluteString
            let safe = failed.replacingOccurrences(of: "'", with: "%27")
                             .replacingOccurrences(of: "\"", with: "%22")
            let html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;
            justify-content:center;font-family:-apple-system,sans-serif;background:#111;color:#eee;text-align:center}
            button{margin-top:20px;padding:12px 28px;font-size:16px;border:0;border-radius:24px;
            background:#3b82f6;color:#fff}</style></head><body>
            <div style="font-size:20px;font-weight:600">연결할 수 없어요</div>
            <div style="margin-top:8px;color:#aaa">인터넷 연결을 확인해 주세요.</div>
            <button onclick="location.href='\(safe)'">다시 시도</button>
            </body></html>
            """
            webView.loadHTMLString(html, baseURL: nil)
        }
    }
}
