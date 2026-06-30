import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "testImplementation"(platform(catalog.findLibrary("junit-bom").get()))
        "testImplementation"(catalog.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(catalog.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
    }
}
