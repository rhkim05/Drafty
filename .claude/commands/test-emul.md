# Test & Deploy to Android Emulator

Run TypeScript type checking first. If it passes, build and deploy to the Android emulator.

## Steps

1. **TypeScript type check** — Run `npx tsc --noEmit` in the project root. If this fails, stop here and show the type errors. Do not proceed to the build step.

2. **Check if Metro bundler is running** — Check if a process is already listening on port 8081 (e.g., `lsof -i :8081`). If Metro is not running, start it in the background with `npx react-native start` and wait a few seconds for it to be ready before proceeding.

3. **Build & deploy** — Run `npx react-native run-android` to build and deploy the app to the connected emulator/device.

Report the result of each step as you go.
