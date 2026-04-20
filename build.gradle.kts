import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.architectury.plugin.ArchitectPluginExtension
import groovy.json.StringEscapeUtils
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

plugins {
    java
    id("maven-publish")
    id("com.teamresourceful.resourcefulgradle") version "0.0.+"
    id("dev.architectury.loom") version "1.14.473" apply false
    id("architectury-plugin") version "3.5.166"
    id("com.gradleup.shadow") version "9.4.1" apply false
}

architectury {
    val minecraftVersion: String by project
    minecraft = minecraftVersion
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "architectury-plugin")

    val minecraftVersion: String by project
    val modLoader = project.name
    val modId = rootProject.name
    val isCommon = modLoader == rootProject.projects.common.name

    base {
        archivesName = "$modId-$modLoader-$minecraftVersion"
    }

    configure<LoomGradleExtensionAPI> {
        silentMojangMappingsLicense()
    }

    repositories {
        maven(url = "https://maven.teamresourceful.com/repository/maven-public/")
        maven(url = "https://maven.neoforged.net/releases/")
    }

    val minecraft by configurations.getting
    val mappings by configurations.getting
    val modApi by configurations.getting

    dependencies {
        val resourcefulLibVersion: String by project

        minecraft("::$minecraftVersion")

        @Suppress("UnstableApiUsage")
        mappings(project.the<LoomGradleExtensionAPI>().layered {
            val parchmentVersion: String by project

            officialMojangMappings()

            parchment("org.parchmentmc.data:parchment-1.21.11:${parchmentVersion}")
        })

        modApi("com.teamresourceful.resourcefullib:resourcefullib-$modLoader-1.21.11:$resourcefulLibVersion")
    }

    java {
        withSourcesJar()
    }

    tasks {
        jar {
            archiveClassifier = "dev"
        }

        named<RemapJarTask>("remapJar") {
            archiveClassifier = null
        }

        processResources {
            val version = project.version
            inputs.property("version", project.version)

            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            filesMatching(listOf("META-INF/neoforge.mods.toml", "fabric.mod.json")) {
                expand("version" to version)
            }
        }
    }

    if (!isCommon) {
        apply(plugin = "com.gradleup.shadow")
        configure<ArchitectPluginExtension> {
            platformSetupLoomIde()
        }

        val shadowCommon by configurations.creating {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        tasks {
            val shadowJar by getting(ShadowJar::class) {
                archiveClassifier = "dev-shadow"
                configurations = listOf(shadowCommon)

                exclude(".cache/**") // Remove datagen cache from jar.
                exclude("**/handcrafted/datagen/**") // Remove data gen code from jar.
            }

            val remapJar by getting(RemapJarTask::class) {
                dependsOn("shadowJar")
                inputFile = shadowJar.archiveFile
            }
        }
    } else {
        sourceSets {
            main {
                resources.srcDir("src/main/generated/resources")
            }
        }
    }

    idea {
        module {
            excludeDirs.add(file("run"))
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                artifactId = "$modId-$modLoader-$minecraftVersion"
                from(components["java"])

                pom {
                    name = "Handcrafted $modLoader"
                    url = "https://github.com/terrarium-earth/$modId"

                    scm {
                        connection = "git:https://github.com/terrarium-earth/$modId.git"
                        developerConnection = "git:https://github.com/terrarium-earth/$modId.git"
                        url = "https://github.com/terrarium-earth/$modId"
                    }

                    licenses {
                        license {
                            name = "ARR"
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                setUrl("https://maven.resourcefulbees.com/repository/terrarium/")
                credentials {
                    username = System.getenv("MAVEN_USER")
                    password = System.getenv("MAVEN_PASS")
                }
            }
        }
    }
}

resourcefulGradle {
    templates {
        register("embed") {
            val minecraftVersion: String by project
            val version: String by project
            val changelog: String = file("changelog.md").readText(Charsets.UTF_8)
            val fabricLink: String? = System.getenv("FABRIC_RELEASE_URL")
            val forgeLink: String? = System.getenv("FORGE_RELEASE_URL")

            source = file("templates/embed.json.template")

            // thanks gradle
            @Suppress("UNCHECKED_CAST")
            injectedValues.putAll(
                mapOf(
                    "minecraft" to minecraftVersion,
                    "version" to version,
                    "changelog" to StringEscapeUtils.escapeJava(changelog),
                    "fabric_link" to fabricLink,
                    "forge_link" to forgeLink,
                ) as Map<String, Any>
            )
        }
    }
}
