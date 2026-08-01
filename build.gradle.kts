import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.134"
}

val minecraft_version: String by project
val minecraft_version_range: String by project
val neo_version: String by project
val neo_version_range: String by project
val loader_version_range: String by project
val parchment_mappings_version: String by project
val parchment_minecraft_version: String by project
val mod_id: String by project
val mod_name: String by project
val mod_license: String by project
val mod_version: String by project
val mod_group_id: String by project
val mod_authors: String by project
val mod_description: String by project
val irons_version: String by project
val curios_version: String by project
val geckolib_version: String by project
val irons_lib_version: String by project
val playeranimator_version: String by project
val simply_swords_version: String by project
val fzzy_config_version: String by project
val kotlin_for_forge_version: String by project
val simply_tooltips_version: String by project
val architectury_version: String by project

version = mod_version
group = mod_group_id

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://api.modrinth.com/maven") }
}

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

val gameTest = sourceSets.create("gameTest")

neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
        register("gameTestServer") {
            type = "gameTestServer"
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(mod_id) {
            sourceSet(sourceSets.main.get())
            sourceSet(gameTest)
        }
    }

    addModdingDependenciesTo(gameTest)
}

gameTest.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
gameTest.runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath

dependencies {
    // Iron's Spells 'n Spellbooks is a HARD dependency at runtime (see neoforge.mods.toml): this mod
    // stores its tier state in Iron's `upgrade_data` component and its `upgrade_orb_type` datapack
    // registry. compileOnly because the pack always ships Iron's - we never bundle it.
    compileOnly("maven.modrinth:irons-spells-n-spellbooks:$irons_version")

    // The production pack supplies these jars. Gradle development runs do not, so declare the
    // exact pack closure for runGameTestServer instead of making shared CI know this mod's graph.
    runtimeOnly("maven.modrinth:irons-spells-n-spellbooks:$irons_version")
    runtimeOnly("maven.modrinth:curios:$curios_version")
    runtimeOnly("maven.modrinth:geckolib:$geckolib_version")
    runtimeOnly("maven.modrinth:irons-lib:$irons_lib_version")
    runtimeOnly("maven.modrinth:playeranimator:$playeranimator_version")
    runtimeOnly("maven.modrinth:simply-swords:$simply_swords_version")
    runtimeOnly("maven.modrinth:fzzy-config:$fzzy_config_version")
    runtimeOnly("maven.modrinth:kotlin-for-forge:$kotlin_for_forge_version")
    runtimeOnly("maven.modrinth:simply-tooltips:$simply_tooltips_version")
    runtimeOnly("maven.modrinth:architectury-api:$architectury_version")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version" to neo_version,
        "neo_version_range" to neo_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)
