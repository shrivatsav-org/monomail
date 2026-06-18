import SwiftUI
import WebKit
import shared

/// Thread detail. Renders each message's HTML body in a WKWebView (the iOS-native
/// equivalent of the Android WebView).
struct EmailDetailView: View {
    let threadId: String
    private let viewModel: EmailDetailViewModel
    @StateObject private var state = FlowObserver<EmailDetailState>(initial: EmailDetailState.Loading())

    init(threadId: String) {
        self.threadId = threadId
        self.viewModel = EmailDetailViewModel(repository: AppDI.module.repository, threadId: threadId)
    }

    var body: some View {
        Group {
            if let success = state.value as? EmailDetailStateSuccess {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(success.emails, id: \.id) { email in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(email.from).fontWeight(.semibold)
                                Text(email.to).font(.caption).foregroundColor(.secondary)
                                HTMLView(html: email.body)
                                    .frame(minHeight: 200)
                            }
                            Divider()
                        }
                    }
                    .padding()
                }
            } else if state.value is EmailDetailStateError {
                Text((state.value as? EmailDetailStateError)?.message ?? "Error").foregroundColor(.red)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Conversation")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { viewModel.toggleStar() } label: { Image(systemName: "star") }
            }
        }
        .onAppear { state.start(viewModel.state) }
    }
}

/// Minimal WKWebView wrapper for rendering email HTML.
private struct HTMLView: UIViewRepresentable {
    let html: String

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false
        webView.isOpaque = false
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let wrapped = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>body{font-family:-apple-system;font-size:15px;margin:0;padding:0;word-wrap:break-word}img{max-width:100%;height:auto}</style>
        </head><body>\(html)</body></html>
        """
        webView.loadHTMLString(wrapped, baseURL: nil)
    }
}
