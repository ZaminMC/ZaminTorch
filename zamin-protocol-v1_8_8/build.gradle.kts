dependencies {
    implementation(project(":zamin-api"))
    implementation(project(":zamin-core"))
    // The shared engine-condition awaits (ML-002) ride zamin-core's test
    // fixtures — see docs/TEST_DEADLINE_CENSUS.md.
    testImplementation(testFixtures(project(":zamin-core")))
    implementation("io.netty:netty-handler:4.2.1.Final")
    implementation("io.netty:netty-codec:4.2.1.Final")
    implementation("io.netty:netty-transport:4.2.1.Final")
}
