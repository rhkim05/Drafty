# Workflow

This project follows a strict pipeline where planning and execution are always separated. Never write code until a written plan has been reviewed and approved.

## Phase 1: Research

Before touching any code, deeply read and understand the relevant part of the codebase. Write findings into a persistent `research.md` file — never just summarize verbally in chat. Read folders, modules, and flows in depth. Surface-level skimming is not acceptable. Document how things work, their specificities, edge cases, and potential issues.

Two forms of research:

1. **Codebase research** — Read existing code in depth, understand how it works, what it does, and all its specificities. Write a detailed report of learnings and findings.
2. **Feature research** — Read the idea/feature description in depth, understand how it should work, what it should do, and all its specificities. Write a detailed report.

The research file is a review surface for the human to verify understanding before planning begins. The most expensive failure mode is code that works in isolation but breaks the surrounding system — ignoring an existing layer, duplicating logic, or violating conventions. Research prevents this.

## Phase 2: Planning

After research is reviewed, write a detailed `plan.md` for the task. The plan must include:

- Detailed explanation of the approach
- Code snippets showing actual changes
- File paths that will be modified
- Considerations and trade-offs

Use the project's own `.md` plan files, not built-in plan mode. These persist as real artifacts, are editable in any editor, and serve as shared mutable state between the human and Claude. When the human shares code from open-source repos or existing project patterns, use them as concrete references rather than designing from scratch.

### TODO List

After the plan, add a detailed `TODO.md` with all phases and individual tasks necessary to complete the plan. **Don't implement yet.**

When the human adds notes to `TODO.md` (e.g., flagging errors), address why the issues happen and how to fix them in the document. **Don't implement yet.**

## Phase 3: Annotation Cycle

This is where the human adds the most value, and it repeats 1-6 times:

1. Claude writes `plan.md`
2. Human reviews and adds inline notes directly into the document
3. Human sends Claude back with something like "I added notes, address them and update the plan, **don't implement yet**"
4. Claude updates the plan
5. Cycle repeats until the human is satisfied

The **"don't implement yet"** guard is mandatory. Without it, Claude will jump to code prematurely. Implementation does not begin until the human explicitly says so.

Inline notes can be anything: correcting assumptions, rejecting approaches, adding constraints, providing domain knowledge, redirecting entire sections, or even just two words like "not optional."

## Phase 4: Implementation

When the plan is fully approved, execute everything in the plan. When done with a task or phase, mark it as completed in `TODO.md`. **Do not stop until all tasks and phases are completed.**

Rules during implementation:

- Do not add unnecessary comments or jsdocs
- Do not use `any` or `unknown` types
- Continuously run typecheck to make sure no new issues are introduced
- Implementation should be boring and mechanical — all creative decisions were already made during annotation

The human's role shifts from architect to supervisor. Corrections become short and direct: "you didn't implement the X function", "this should be in module A not module B, move it", or just "wider" or "still cropped." For visual/UI work, the human may attach screenshots. For pattern consistency, the human will reference existing code like "this view should look exactly like the existing X view, same layout, same spacing, same interaction patterns."

When something goes wrong, don't patch a bad approach. Revert git changes and re-scope with a narrower focus.

## Role Boundaries

The human makes all judgment calls; Claude handles mechanical execution. The human will:

- Cherry-pick from proposals — accepting, modifying, skipping, or overriding each item individually
- Trim scope to prevent creep
- Protect existing interfaces with hard constraints
- Override technical choices when they have a specific preference

## Session Continuity

Run research, planning, and implementation in a single long session. By the time implementation starts, Claude has built deep understanding through research and annotation cycles. The `plan.md` document survives context compaction and Claude can be pointed back to it at any time.

**In one sentence:** Research deeply, write a plan, annotate the plan until it's right, then execute the whole thing without stopping, checking types along the way with TODO.md.
