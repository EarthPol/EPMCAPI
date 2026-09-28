# Contributing

Build with Java 21 and an installed Maven 3.9 or later: `mvn -Dmaven.test.skip=true package`. No private Maven access, database credentials, or checked-in plugin binaries should be needed.

Keep pull requests focused. Explain the client-visible behavior and include a request/response example for API changes. Preserve existing successful response shapes unless a change is explicitly documented as a migration. Validate changes with compilation and focused manual checks. Do not add automated test infrastructure unless requested.

HTTP and database code must not directly read mutable game objects off-thread. Use `GameThread.read` to return detached values; keep database and LuckPerms storage waits on HTTP workers. Integrations must fail independently when their plugin or data source is absent. Do not claim Folia support without implementing and checking the relevant schedulers.

Use `src/main/resources/config.yml` only for safe defaults. Do not commit deployment settings, real credentials, generated POMs, IDE files, built JARs, or local dependency caches. Keep plugin API dependencies `provided`; keep EarthPol-specific reflection inside the integration adapter.

Preserve existing attribution. Do not copy source from another repository merely because it is publicly readable; check its license first.

Report reproducible bugs through the target repository's issue tracker once it is published. For sensitive reports, contact the EarthPol maintainers privately; a public vulnerability-reporting channel has not yet been configured for this new repository. Do not place credentials or private player data in public issues.
