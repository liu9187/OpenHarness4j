package io.openharness4j.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSkillLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAnthropicStyleSkillMarkdown() throws Exception {
        Path skill = tempDir.resolve("research-helper.md");
        Files.writeString(skill, """
                ---
                name: Research Helper
                description: Summarize research notes.
                requiredTools:
                  - search
                ---
                Use concise bullet points.
                """);

        SkillDefinition definition = new MarkdownSkillLoader().load(skill);

        assertEquals("research_helper", definition.id());
        assertEquals("0.1.0", definition.version());
        assertEquals("Research Helper", definition.name());
        assertTrue(definition.requiredTools().contains("search"));
        assertEquals(1, definition.workflow().size());
        assertEquals(SkillStepType.LLM, definition.workflow().get(0).type());
        assertTrue(definition.workflow().get(0).prompt().contains("concise"));
    }

    @Test
    void loadsWorkflowFromFrontMatter() {
        SkillDefinition definition = new MarkdownSkillLoader().load("""
                ---
                id: echo_skill
                version: 1.0.0
                workflow:
                  - name: echo_step
                    type: tool
                    tool: echo
                    args:
                      text: hello
                ---
                Body is optional when workflow is explicit.
                """, "SKILL.md");

        assertEquals("echo_skill", definition.id());
        assertEquals("1.0.0", definition.version());
        assertEquals(1, definition.workflow().size());
        assertEquals(SkillStepType.TOOL, definition.workflow().get(0).type());
        assertEquals("echo", definition.workflow().get(0).tool());
    }
}
