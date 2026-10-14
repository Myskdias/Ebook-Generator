package com.myskdias.ebookgenerator.desktop;

import javafx.application.Application;

/** Separate launcher so the Gradle JavaExec task can launch JavaFX from the classpath. */
public final class VisualParserLauncher {
    private VisualParserLauncher() { }

    public static void main(String[] args) {
        Application.launch(VisualParserApp.class, args);
    }
}
