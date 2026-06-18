import SwiftUI
import shared

/// Switches between sign-in and inbox based on whether there's an active account.
struct RootView: View {
    @StateObject private var activeAccount =
        FlowObserver<UserProfile?>(initial: AppDI.module.accountManager.getActiveAccount())

    var body: some View {
        Group {
            if activeAccount.value != nil {
                InboxView()
            } else {
                SignInView()
            }
        }
        .onAppear {
            activeAccount.start(AppDI.module.accountManager.activeAccountFlow)
        }
    }
}
