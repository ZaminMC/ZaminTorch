import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testretry.TestRetryTaskExtension

plugins {
    // The census-governed selective test retry (CI Phase A): retried
    // IN-TASK, once, only for the explicitly documented load-flake
    // classes. Declared here, applied to every subproject below.
    id("org.gradle.test-retry") version "1.6.6" apply false
}

// The root aggregator applies no Java plugin: it carries no sources, and an
// unconfigured compile task would bind to the daemon JVM instead of the
// Java 21 toolchain (a JRE-only host would then fail on capability checks).

allprojects {
    group = "net.zamin"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "org.gradle.test-retry")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all,-processing")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // §35.13's debug/test-build visibility row: leaked reference-counted
        // buffers must be VISIBLE in the test builds — Netty's leak detector
        // at `advanced` samples with the acquisition stack trace (the
        // zamin-transport module's own tests tighten this to `paranoid`,
        // every buffer sampled, since the queue's ownership law IS its
        // contract).
        systemProperty("io.netty.leakDetection.level", "advanced")
        // CI Phase A: 3 forks on the 4-vCPU runner — the suite is tick-bound
        // (waits on the 20 Hz engine clock), not CPU-bound, and each fork
        // is a separate JVM, so per-test engines, @TempDir worlds and
        // ephemeral ports are isolated by construction. zamin-bukkit
        // overrides this to 1 below (its harness shares ONE engine).
        maxParallelForks = 3
        maxHeapSize = "1g"
        testLogging {
            events("failed", "skipped")
            showStackTraces = true
            showExceptions = true
        }
        // CI Phase A: the selective, census-governed retry (replaces the
        // workflow's blind whole-build re-run, which doubled failing runs
        // to 50-56 minutes and still failed). ONLY the three documented
        // load-flake classes are retry-eligible (docs/TEST_DEADLINE_CENSUS.md
        // owns the list — a new member needs a diagnosed load-flake
        // justification there first; new failures never become eligible
        // automatically). One retry per failed test, in the same task
        // execution; a test that fails again fails the run, and more than
        // maxFailures failing tests disables retrying entirely so real
        // breakage is never retried away.
        extensions.configure<TestRetryTaskExtension> {
            maxRetries.set(1)
            maxFailures.set(20)
            filter {
                includeClasses.add("*LifecycleEventAcceptanceTest")
                includeClasses.add("*HopperSystemTest")
                includeClasses.add("*HopperWindowAcceptanceTest")
            }
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
