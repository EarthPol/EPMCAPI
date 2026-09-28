# Contributing

Build with Java 21 and an installed Maven 3.9 or later: `mvn -Dmaven.test.skip=true package`. No private Maven access, database credentials, or checked-in plugin binaries should be needed.

Keep pull requests focused. Explain the client-visible behavior and include a request/response example for API changes. Preserve existing successful response shapes unless a change is explicitly documented as a migration. Validate changes with compilation and focused manual checks. Do not add automated test infrastructure unless requested.

Use `GameThread.read` for global/plugin metadata on Paper and Folia, and `GameThread.at` for block/inventory reads on the region owning a fixed location. Return detached values to HTTP workers; only those workers may wait for scheduled reads, database queries, or LuckPerms storage. Entity state must use the entity scheduler if added in future. Integrations must fail independently when their plugin or data source is absent and require Folia-compatible providers when running Folia. Compilation and scheduler checks do not replace live integration testing.

Use `src/main/resources/config.yml` only for safe defaults. Do not commit deployment settings, real credentials, generated POMs, IDE files, built JARs, or local dependency caches. Keep plugin API dependencies `provided`; keep EarthPol-specific reflection inside the integration adapter.

Preserve existing attribution. Do not copy source from another repository merely because it is publicly readable; check its license first.

Report reproducible bugs through the target repository's issue tracker once it is published. For sensitive reports, contact the EarthPol maintainers privately; a public vulnerability-reporting channel has not yet been configured for this new repository. Do not place credentials or private player data in public issues.
