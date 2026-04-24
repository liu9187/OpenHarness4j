package io.openharness4j.examples.verification;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

final class ExplodingTool implements Tool {

    @Override
    public String name() {
        return "explode";
    }

    @Override
    public String description() {
        return "Always throws an exception.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        throw new IllegalStateException("planned example failure");
    }
}
