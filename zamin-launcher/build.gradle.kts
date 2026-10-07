dependencies {
    api(project(":zamin-api"))
    implementation(project(":zamin-core"))
    implementation(project(":zamin-protocol-v1_8_8"))
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Main-Class" to "net.zamin.launcher.ZaminLauncher")
    }
}

tasks.register<JavaExec>("runServer") {
    group = "zamin"
    description = "Starts a ZaminTorch development server."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.zamin.launcher.ZaminLauncher"
    workingDir = file("${rootProject.projectDir}/run")
    standardInput = System.`in`
}
