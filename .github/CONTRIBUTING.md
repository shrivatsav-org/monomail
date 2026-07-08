# Contributing to MonoMail

Thanks for considering contributing. Keep it simple — this is a solo project at heart, and the goal is maintainable code, not process.

## Quick Start

```bash
# Fork the repo, then:
git clone https://github.com/YOUR_USERNAME/monomail.git
cd monomail
./gradlew assembleGithubDebug   # builds without Google Client ID
```

See the [README](../README.md) for full setup instructions (MSAL, Google Cloud project, etc.).

## Code Style

- **Kotlin** — follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html). The project uses ktlint (run `./gradlew ktlintCheck`).
- **Compose** — one file per screen: `{ScreenName}Screen.kt` + `{ScreenName}ViewModel.kt`. Keep composables at the top, ViewModel at the bottom. State hoisting, single source of truth.
- **Package structure** — match the existing layout in `app/src/main/java/com/shrivatsav/monomail/`. See [CLAUDE.md](../CLAUDE.md) for the full map.
- **Naming** — `camelCase` for functions/vals, `PascalCase` for classes/composables. No Hungarian notation.

## Pull Requests

1. **Branch from `main`**, use a short descriptive name: `fix/scroll-retention`, `feat/pgp-keys`.
2. **One change per PR.** Bug fix, feature, refactor — keep it focused.
3. **Write a good title.** Prefix with the type: `fix:`, `feat:`, `refactor:`, `chore:`. The body should explain *what* and *why*, not *how*.
4. **No WIP PRs** — open a draft if you want early feedback, convert to ready when done.

### Before submitting

- Build: `./gradlew assembleGithubDebug` should pass.
- Tests: `./gradlew test` should pass. Add tests for new logic — no need to test every Compose composable, but data/repository/ViewModel logic should have coverage.
- Existing tests must not break.

## Commit Messages

```
type(scope): short summary (max 72 chars)

Longer explanation if needed. Wrap at 72 chars.
```

Types: `fix`, `feat`, `refactor`, `chore`, `docs`, `test`, `style`. Keep `scope` lowercase: `inbox`, `detail`, `compose`, `settings`, `auth`, `data`, `push`, etc.

## Questions

Open a [Discussion](https://github.com/shrivatsav-0/monomail/discussions) for questions or ideas before writing code. Avoids wasted effort.
