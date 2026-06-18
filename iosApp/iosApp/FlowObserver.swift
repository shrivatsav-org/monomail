import Foundation
import shared

/// Bridges a Kotlin StateFlow into a SwiftUI-observable value.
///
/// Assumes SKIE is enabled, which makes a Kotlin `StateFlow<T>` conform to
/// Swift `AsyncSequence` (Element == T). Without SKIE, replace the `for await`
/// loop with a manual `Kotlinx_coroutines_coreFlow.collect(...)` call.
@MainActor
final class FlowObserver<T>: ObservableObject {
    @Published var value: T
    private var task: Task<Void, Never>?

    init(initial: T) {
        self.value = initial
    }

    /// Start collecting `sequence`, publishing every emission on the main actor.
    func start<S: AsyncSequence>(_ sequence: S) where S.Element == T {
        task?.cancel()
        task = Task { [weak self] in
            do {
                for try await element in sequence {
                    self?.value = element
                }
            } catch {
                // flow cancelled / failed; ignore
            }
        }
    }

    deinit { task?.cancel() }
}
