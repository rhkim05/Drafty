# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

**Read [`workflow.md`](workflow.md) before starting any task.** This project uses a strict Research → Plan → Annotate → Implement pipeline. Never write code without an approved plan.

Pipeline: [`research.md`](research.md) → [`plan.md`](plan.md) & [`TODO.md`](TODO.md) → Implementation.

- **`research.md`** — Write findings here before planning. Review surface for verifying understanding.
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
