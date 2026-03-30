# Project Instructions — Drafty

> These are the working rules and workflow for developing the Drafty app using Claude Code. Follow these instructions at all times.

---

## Start of the Project

1. **idea.md** — Brainstorm how to develop the app.
2. **plan.md** — Make a general plan, stating what features are needed, how to implement those features, etc.
3. **tech-stack.md** — State tech stacks for each feature or for the overall project.
4. **architecture.md** — Design the overall system architecture for this app.
5. **project-structure.md** — Update every time when the folder or file structure of the project changes.

---

## Code Implementation Workflow

For each new feature, follow these steps **in order**:

1. **research.md** — Write when building a new feature. Read the folder in depth, understand how it works deeply, what it does and all its specificities. When that's done, write a detailed report of learnings and findings in `research.md`.

2. **plan.md** — Write when building a new feature `<featureName>: featureDescription`. Write a detailed `plan.md` document outlining how to implement the feature.

3. **TODO.md** — Add a detailed todo list of the `plan.md` to this file, with all the phases and individual tasks necessary to complete the plan. **Do not implement code yet until this step.** Link this document and `plan.md`.

4. **Implementation** — Implement the plan. When a task or phase is done, mark todos in `TODO.md` as completed.

5. **issues.md** — Record and write a report of any issues or errors happening during implementation, so that you can always refer to it. State why the issue happens, and how you tried to fix it. Link this document from other files. Examples:
   - In `TODO.md`: *"Got `<error>` while doing this step. Refer to [issues.md line 59](./issues.md#L59) for details."*
   - In a code file like `Canvas.kt`: *"There was an issue which is `<description>`. Refer to [issues.md line 193](./issues.md#L193) for details."*

---

## General Rules

- **Always log issues:** State every problem and issue encountered while working on the project to `issues.md`. After fixing it, reference the line number of `issues.md` from the file you edited, and vice versa.

- **Always update docs when appropriate:**
  - `project-structure.md` — after revising any folder structure.
  - `tech-stack.md` — after switching to a different tech stack for a feature (e.g., discovered a better/more efficient option later).
  - `plan.md` — when scope or approach changes.
  - Any other doc that becomes stale.

- **Split large files:** If any `.md` file gets too many lines, separate it into phases (e.g., `plan-phase1.md`, `plan-phase2.md`). **Exceptions — never split these:**
  - `idea.md`
  - `issues.md`
  - `project-structure.md`

- **Cross-reference everything:** All documentation files should link to each other where relevant. Code files should reference `issues.md` when workarounds or fixes are applied.

---

## App Identity

- **App name:** Drafty
- **Package name:** `com.drafty.*`
- **Database:** `drafty.db`
- **Philosophy:** Writing should feel effortless. Simple by design. Minimal at its core. Always a work in progress — and that's okay.
