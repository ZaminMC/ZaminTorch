// The shared test fixtures (ML-002): EngineAwaits — the engine-condition
// awaits the test-deadline census's conversions ride. A testFixtures
// source set ships test utilities to THIS module's tests and to the
// downstream modules' test suites (protocol + bukkit take
// testImplementation(testFixtures(project(":zamin-core")))) without ever
// touching the production jar.
apply(plugin = "java-test-fixtures")

dependencies {
    api(project(":zamin-api"))
    // The region-compression pair's own codecs (WAVE RC — KigPaper 0214/0215
    // ported engine-native): lz4-java for the LZ4 block envelope (the
    // patch's own choice for its pure-Java fallback) and zstd-jni for the
    // ZSTD envelope — the patch's exact pinned versions, flowing into the
    // launcher's fat jar through the runtime classpath.
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("com.github.luben:zstd-jni:1.5.0-4")
}
