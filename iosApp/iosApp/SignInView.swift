import SwiftUI
import shared

/// Sign-in screen. Buttons drive the shared PKCE flow; the browser redirect is
/// handled inside the shared module via ASWebAuthenticationSession.
///
/// NOTE: the `SignInState` sealed-class casts below assume SKIE's Swift names.
/// Adjust to the generated interface if they differ.
struct SignInView: View {
    private let viewModel = SignInViewModel(authManager: AppDI.module.authManager)
    @StateObject private var state = FlowObserver<SignInState>(initial: SignInState.Idle())

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Text("MonoMail")
                .font(.largeTitle).fontWeight(.bold)
            Text("One inbox for Gmail and Outlook")
                .foregroundColor(.secondary)

            if state.value is SignInStateLoading {
                ProgressView().padding()
            } else {
                Button(action: { viewModel.signInGmail() }) {
                    Text("Sign in with Google")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

                Button(action: { viewModel.signInOutlook() }) {
                    Text("Sign in with Outlook")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }

            if let error = state.value as? SignInStateError {
                Text(error.message)
                    .foregroundColor(.red)
                    .font(.footnote)
            }
            Spacer()
        }
        .padding(32)
        .onAppear { state.start(viewModel.state) }
    }
}
