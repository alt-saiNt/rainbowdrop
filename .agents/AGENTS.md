# Antigravity Workspace Rules

## 1. Loop Execution Limit (Guardrails)
- The agent must monitor build and compiler execution status.
- If a compilation or test execution command fails **2 consecutive times**, the agent must immediately halt all automated loops, stop invoking repair tools, and present the build log to the user for human-in-the-loop guidance. Do not attempt a third automated repair attempt without explicit user consent.

## 2. Model Routing Guidelines
- For basic structuring, boilerplate generation, git commands, and standard layout files, prioritize using **Gemini Flash** models to conserve computational quota.
- Switch to high-reasoning **Gemini Pro/Ultra** models only when solving complex algorithms, concurrency/threading bugs, database locks, or multi-file logical refactoring.
