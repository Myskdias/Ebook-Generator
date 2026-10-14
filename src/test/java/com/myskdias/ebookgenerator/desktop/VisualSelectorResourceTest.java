package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VisualSelectorResourceTest {
    @Test void shipsSelectorScriptWithApplicationResources() throws Exception {
        try (var in = VisualParserApp.class.getResourceAsStream("selector.js")) {
            assertNotNull(in, "Visual DOM selector must be included in the application JAR");
            String js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(js.contains("window.__ebookPicker"));
            assertTrue(js.contains("takeSelection"));
            assertTrue(js.contains("selectParent"));
        }
    }
}
