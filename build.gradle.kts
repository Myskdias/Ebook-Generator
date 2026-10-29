plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.myskdias"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

javafx {
    version = "25.0.4"
    modules("javafx.controls", "javafx.web")
}

application {
    mainClass = "com.myskdias.ebookgenerator.Main"
}

val epubcheck by configurations.creating

dependencies {
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    epubcheck("org.w3c:epubcheck:5.3.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("generateSample") {
    group = "verification"
    description = "Create an EPUB from the two local HTML fixtures."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(
        "--title", "Example Novel",
        "--author", "Demo Author",
        "--language", "en",
        "--output", layout.buildDirectory.file("samples/demo.epub").get().asFile.path,
        "src/test/resources/chapters/chapter-01.html",
        "src/test/resources/chapters/chapter-02.html"
    )
}

tasks.register<JavaExec>("epubcheckSample") {
    group = "verification"
    description = "Validate the generated sample with the official EPUBCheck CLI."
    dependsOn("generateSample")
    classpath = epubcheck
    mainClass.set("com.adobe.epubcheck.tool.Checker")
    args(layout.buildDirectory.file("samples/demo.epub").get().asFile.path)
}

tasks.register<JavaExec>("parserEditor") {
    group = "application"
    description = "Open the desktop app (EPUB generation and visual parser profiles)."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.myskdias.ebookgenerator.desktop.VisualParserLauncher")
}
