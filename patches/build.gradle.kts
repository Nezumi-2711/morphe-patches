group = "app.nezumi.patches"

patches {
    about {
        name = "Nezumi Patches"
        description = "Community patches for Zalo, compatible with Morphe"
        source = "https://github.com/Nezumi-2711/morphe-patches"
        author = "Nezumi-2711"
        contact = "https://github.com/Nezumi-2711/morphe-patches/issues"
        website = "https://github.com/Nezumi-2711/morphe-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
