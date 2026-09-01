# Room schemas

The Room Gradle plugin configures this directory as the KSP schema location. Versioned JSON files are inputs to `MigrationTestHelper` and must be committed when the database version changes.

Schema generation is currently blocked because the concurrent account-schema work still declares `exportSchema = false` in `AppDatabase`. Historical schemas are also absent and cannot be reconstructed safely from current entities. Full migration-chain tests remain blocked until the account-schema owner enables export and exact historical schemas are recovered from their corresponding releases; do not synthesize them or change migrations to work around the missing history.
