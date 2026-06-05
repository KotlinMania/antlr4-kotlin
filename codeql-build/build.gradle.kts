import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity

plugins {
    base
}

val codeqlKotlinVersion = providers.gradleProperty("codeql.kotlin.version").getOrElse("2.3.21")
val codeqlLanguageVersion =
    providers
        .gradleProperty("kotlin.languageVersion")
        .getOrElse(codeqlKotlinVersion.split('.').take(2).joinToString("."))
val codeqlApiVersion = providers.gradleProperty("kotlin.apiVersion").getOrElse(codeqlLanguageVersion)
val jvmToolchainVersion = providers.gradleProperty("jvm.toolchain").getOrElse("21")
val codeqlKotlinSourceSetNames =
    providers
        .gradleProperty("project.codeql.kotlinSourceSets")
        .getOrElse("commonMain,jvmMain")
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
val codeqlKotlinCommonSourceSetNames =
    providers
        .gradleProperty("project.codeql.kotlinCommonSourceSets")
        .getOrElse("commonMain")
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
val commonOptIns =
    listOf(
        "kotlin.time.ExperimentalTime",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        "kotlin.ExperimentalUnsignedTypes",
    )
val defaultCodeqlSourceClasspath =
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib:$codeqlKotlinVersion",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
    ).joinToString(",")

val codeqlKotlinc by configurations.creating
val codeqlSourceClasspath by configurations.creating
val codeqlAndroidAar by configurations.creating

dependencies {
    add("codeqlKotlinc", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$codeqlKotlinVersion")

    providers
        .gradleProperty("project.dependencies.codeqlSourceClasspath")
        .getOrElse(defaultCodeqlSourceClasspath)
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { add("codeqlSourceClasspath", it) }

    providers
        .gradleProperty("project.dependencies.codeqlAndroidAar")
        .getOrElse("")
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { add("codeqlAndroidAar", it) }
}

tasks.register<JavaExec>("codeqlCompileJvm") {
    description =
        "Compile ${codeqlKotlinSourceSetNames.joinToString(",")} Kotlin sources " +
            "with kotlinc $codeqlKotlinVersion for CodeQL Java/Kotlin extraction."
    group = "verification"
    classpath(codeqlKotlinc)
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

    val sourceRoot = layout.projectDirectory.dir("..")
    val outDir = layout.buildDirectory.dir("classes/kotlin/codeql-jvm")
    val aarExtractDir = layout.buildDirectory.dir("codeql/android-aar")
    val commonSources =
        files(
            codeqlKotlinCommonSourceSetNames.map { sourceSetName ->
                fileTree(sourceRoot.dir("src/$sourceSetName/kotlin")) { include("**/*.kt") }
            },
        )
    val sources =
        files(
            codeqlKotlinSourceSetNames.map { sourceSetName ->
                fileTree(sourceRoot.dir("src/$sourceSetName/kotlin")) { include("**/*.kt") }
            },
        )

    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(commonSources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(codeqlSourceClasspath).withNormalizer(ClasspathNormalizer::class.java)
    inputs.files(codeqlAndroidAar).withNormalizer(ClasspathNormalizer::class.java)
    outputs.dir(outDir)
    outputs.dir(aarExtractDir)

    doFirst {
        outDir.get().asFile.mkdirs()
        val extractedJars =
            codeqlAndroidAar.resolve().mapNotNull { aar ->
                val extractTarget = aarExtractDir.get().asFile.resolve(aar.nameWithoutExtension)
                extractTarget.mkdirs()
                copy {
                    from(zipTree(aar))
                    include("classes.jar")
                    into(extractTarget)
                }
                extractTarget.resolve("classes.jar").takeIf { it.exists() }
            }
        val fullClasspath =
            (codeqlSourceClasspath.resolve() + extractedJars)
                .joinToString(File.pathSeparator) { it.absolutePath }
        val commonSourceFiles = commonSources.files.toMutableList()
        require(commonSourceFiles.isNotEmpty()) {
            "project.codeql.kotlinCommonSourceSets must resolve to at least one Kotlin source file"
        }
        val sourceFiles = sources.files.toMutableList()
        require(sourceFiles.isNotEmpty()) {
            "project.codeql.kotlinSourceSets must resolve to at least one Kotlin source file"
        }
        args =
            listOf(
                "-d",
                outDir.get().asFile.absolutePath,
                "-classpath",
                fullClasspath,
                "-jvm-target",
                jvmToolchainVersion,
                "-no-stdlib",
                "-no-reflect",
                "-language-version",
                codeqlLanguageVersion,
                "-api-version",
                codeqlApiVersion,
                "-Xmulti-platform",
                "-Xcommon-sources=${commonSourceFiles.joinToString(",") { it.absolutePath }}",
                "-Xexpect-actual-classes",
            ) + commonOptIns.flatMap { listOf("-opt-in", it) } + sourceFiles.map { it.absolutePath }
    }
}
