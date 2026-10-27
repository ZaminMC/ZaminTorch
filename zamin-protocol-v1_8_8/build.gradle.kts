dependencies {
    implementation(project(":zamin-api"))
    implementation(project(":zamin-core"))
    // The version-independent outbound transport (§35): the queue-and-flush
    // stage every protocol implementation shares. This module stays the
    // version-specific codec; the transport moves the result.
    implementation(project(":zamin-transport"))
    // The shared engine-condition awaits (ML-002) ride zamin-core's test
    // fixtures — see docs/TEST_DEADLINE_CENSUS.md.
    testImplementation(testFixtures(project(":zamin-core")))
    implementation("io.netty:netty-handler:4.2.1.Final")
    implementation("io.netty:netty-codec:4.2.1.Final")
    implementation("io.netty:netty-transport:4.2.1.Final")
}
