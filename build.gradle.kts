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
