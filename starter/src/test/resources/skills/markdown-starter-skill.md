---
id: markdown_starter_skill
version: 0.1.0
name: Markdown Starter Skill
requiredTools:
  - echo
workflow:
  - name: echo_text
    type: tool
    tool: echo
    args:
      text: "{{text}}"
---
Use the echo tool with the provided text.
