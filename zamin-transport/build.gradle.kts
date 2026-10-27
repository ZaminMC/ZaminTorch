// The version-independent outbound transport (architecture §35): the common
// layer every protocol implementation shares — encoded-payload lifetime,
// outbound queueing, write batching, flush policy, transport metrics.
//
// The module boundary IS the §35.6 invariant: nothing here imports a
// protocol-version type or an engine type. Only Netty and the JDK. Minecraft
// version controls what is encoded; this module moves the result efficiently.

dependencies {
    implementation("io.netty:netty-buffer:4.2.1.Final")
    implementation("io.netty:netty-transport:4.2.1.Final")
}

// §35.13's debug/test-build visibility row, tightened: the transport's OWN
// tests run the leak detector at `paranoid` — every buffer sampled — because
// the queue's ownership law IS its contract (a leaked ticket is a law
// violation, not a sampled accident).
tasks.withType<Test>().configureEach {
    systemProperty("io.netty.leakDetection.level", "paranoid")
}
