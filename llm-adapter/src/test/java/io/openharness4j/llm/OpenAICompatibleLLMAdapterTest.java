package io.openharness4j.llm;

import io.openharness4j.api.LLMResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAICompatibleLLMAdapterTest {

    @Test
    void parsesToolCallResponse() {
        OpenAICompatibleLLMAdapter adapter = new OpenAICompatibleLLMAdapter(
                "http://localhost:11434/v1/chat/completions",
                null,
                "test-model"
        );
        String responseBody = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "echo",
                              "arguments": "{\\"text\\":\\"hello\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15
                  }
                }
                """;

        LLMResponse response = adapter.parseResponse(responseBody);

        assertTrue(response.hasToolCalls());
        assertEquals("echo", response.effectiveToolCalls().get(0).name());
        assertEquals("hello", response.effectiveToolCalls().get(0).args().get("text"));
        assertEquals(15, response.usage().totalTokens());
    }
}
