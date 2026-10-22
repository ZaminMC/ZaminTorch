// The shared test fixtures (ML-002): EngineAwaits — the engine-condition
// awaits the test-deadline census's conversions ride. A testFixtures
// source set ships test utilities to THIS module's tests and to the
// downstream modules' test suites (protocol + bukkit take
// testImplementation(testFixtures(project(":zamin-core")))) without ever
// touching the production jar.
apply(plugin = "java-test-fixtures")

dependencies {
    api(project(":zamin-api"))
}
