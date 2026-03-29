# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

**Read [`workflow.md`](workflow.md) before starting any task.** This project uses a strict multi-phase pipeline. Never write code without an approved plan.

### Product Definition (one-time setup)

`idea.md` → `vision.md` → `tech-stack.md` → `architecture.md` → `project-structure.md`

- **`idea.md`** — Raw product notes. User adds notes; Claude addresses them and updates the document. Don't implement.
- **`vision.md`** — Product description: core features, target users, key technical challenges. Derived from `idea.md`.
- **`tech-stack.md`** — Research and recommend tech stack options with trade-offs. Present options for the user to choose — don't make final decisions.
- **`architecture.md`** — System architecture: rendering pipeline, data model, storage layer, module boundaries. Include text diagrams and key data structures.
- **`project-structure.md`** — Folder structure with explanations. Then scaffold the actual directories with minimal boilerplate (empty files or module exports only — no real logic).

### Feature Development (repeating cycle)

`research.md` → `plan.md` & `TODO.md` → Annotate → Implementation

- **`research.md`** — Deep reading of relevant code/folders. Write detailed findings here before planning. Review surface for verifying understanding.
- **`plan.md`** — Detailed approach, code snippets, trade-offs. Iterated via annotation cycles.
- **`TODO.md`** — Granular task checklist and progress tracking. Linked from `plan.md`, not duplicated there. **Always include a "Manual Testing Checklist (Emulator)" section** with testable items for the current phase. This checklist is for the user to fill in manually — do not mark these items as complete.

During implementation, mark tasks complete in `TODO.md` and run typecheck continuously.

## Project Context

Drafty is a handwriting/note-taking app targeting Android (primary) with a future iOS port planned. Built with Kotlin Multiplatform (KMP). Full technical research and architecture details are in [`research.md`](research.md).

### Manual Testing on Emulator

```bash
# 1. List available AVDs
emulator -list-avds

# 2. Launch the emulator (use the Pixel_Tablet AVD)
emulator -avd Pixel_Tablet &

# 3. Wait for the emulator to boot, then install and run
./gradlew :androidApp:installDebug

# 4. Launch the app
~/Library/Android/sdk/platform-tools/adb shell am start -n com.drafty.android/.MainActivity
```

Note: `adb` is not on PATH by default — use the full path `~/Library/Android/sdk/platform-tools/adb`.
