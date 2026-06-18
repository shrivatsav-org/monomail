import SwiftUI
import shared

/// Inbox list. Observes InboxViewModel.state; tap a row to open the thread.
/// NOTE: sealed-class casts (InboxStateSuccess/Loading/Error) assume SKIE names.
struct InboxView: View {
    private let viewModel = InboxViewModel(
        repository: AppDI.module.repository,
        contactProvider: AppDI.module.contactProvider,
        accountManager: AppDI.module.accountManager,
        settings: AppDI.module.settingsRepository
    )
    @StateObject private var state = FlowObserver<InboxState>(initial: InboxState.Loading())

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Inbox")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { viewModel.refresh(showLoader: true) } label: {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                }
        }
        .onAppear { state.start(viewModel.state) }
    }

    @ViewBuilder
    private var content: some View {
        if let success = state.value as? InboxStateSuccess {
            List {
                ForEach(success.threads, id: \.threadId) { thread in
                    NavigationLink(destination: EmailDetailView(threadId: thread.threadId)) {
                        ThreadRow(thread: thread)
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            viewModel.deleteThread(threadId: thread.threadId)
                        } label: { Label("Delete", systemImage: "trash") }
                        Button {
                            viewModel.archiveThread(threadId: thread.threadId)
                        } label: { Label("Archive", systemImage: "archivebox") }
                    }
                }
            }
            .listStyle(.plain)
            .refreshable { viewModel.refresh(showLoader: false) }
        } else if state.value is InboxStateError {
            Text((state.value as? InboxStateError)?.message ?? "Error")
                .foregroundColor(.red)
        } else {
            ProgressView()
        }
    }
}

private struct ThreadRow: View {
    let thread: EmailThread
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(thread.from).fontWeight(thread.isRead ? .regular : .bold)
                Spacer()
                if thread.isStarred { Image(systemName: "star.fill").foregroundColor(.yellow).font(.caption) }
            }
            Text(thread.subject).font(.subheadline).lineLimit(1)
            Text(thread.snippet).font(.footnote).foregroundColor(.secondary).lineLimit(1)
        }
        .padding(.vertical, 4)
    }
}
