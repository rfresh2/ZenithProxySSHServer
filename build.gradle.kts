plugins {
    id("zenithproxy.plugin.dev") version "1.0.1-SNAPSHOT"
}

group = property("maven_group") as String
version = property("plugin_version") as String
val mc = property("mc") as String
val pluginId = property("plugin_id") as String

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        // variables in your BuildConstants.java template class
        "version" to project.version,
        "mc_version" to mc,
        "plugin_id" to pluginId,
        "maven_group" to group as String,
    )
    // the minimum supported java version for users of your plugin
    javaReleaseVersion = JavaLanguageVersion.of(21)
}

repositories {
    maven("https://maven.2b2t.vc/snapshots") {
        description = "ZenithProxy Prereleases"
    }
    maven("https://maven.2b2t.vc/releases") {
        description = "ZenithProxy Releases"
    }
    maven("https://maven.2b2t.vc/remote") {
        description = "Dependencies used by ZenithProxy"
    }
}

dependencies {
    zenithProxy("com.zenith:ZenithProxy:$mc-SNAPSHOT")
    shade("org.jline:jline-remote-ssh:4.3.1") {
        isTransitive = false
    }
    val sshdVersion = "2.19.0"
    shade("org.apache.sshd:sshd-core:$sshdVersion") {
        isTransitive = false
    }
    shade("org.apache.sshd:sshd-common:$sshdVersion") {
        isTransitive = false
    }
    shade("org.apache.sshd:sshd-netty:$sshdVersion") {
        isTransitive = false
    }
}

tasks {
    shadowJar {
        /**
         * relocate shaded dependencies to avoid conflicts with other plugins
         * transitive dependencies should also be relocated or removed (with exclude)
         * build and examine your plugin jar contents to check
         * https://gradleup.com/shadow/configuration/relocation/
         */
        val basePackage = "${project.group}.shadow"
//        relocate("org.apache", "$basePackage.org.apache")
        relocate("org.jline.builtins.ssh", "$basePackage.org.jline.builtins.ssh")
        exclude("META-INF/jline/**", "META-INF/maven/**", "META-INF/services/org.jline**", "META-INF/services/reactor**")

        /**
         * remove unneeded transitive dependencies
         * https://gradleup.com/shadow/configuration/dependencies/#filtering-dependencies
         */
        dependencies {
            exclude(dependency("org.slf4j:.*:.*"))
            exclude(dependency("io.netty:.*:.*"))
        }
    }
}
