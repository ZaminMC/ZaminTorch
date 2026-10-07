plugins {
    // Auto-provision the Java 21 toolchain when no local JDK matches (standard
    // Gradle resolver; keeps `./gradlew build` self-sufficient on fresh machines).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ZaminTorch"

include("zamin-api")
include("zamin-core")
include("zamin-protocol-v1_8_8")
include("zamin-launcher")
