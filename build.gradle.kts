import org.gradle.api.plugins.JavaPluginExtension

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
        testLogging {
            events("failed", "skipped")
            showStackTraces = true
            showExceptions = true
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
