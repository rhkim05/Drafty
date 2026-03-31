# Project Structure

> Linked from [CLAUDE.md](../CLAUDE.md)

```
tablet-note-app/
├── index.js                       # App entry point, registers TabletNoteApp component
├── App.tsx                        # Mounts <Navigation />
├── android/
│   ├── build.gradle               # AGP 8.1.1, compileSdk 34, androidx pins
│   ├── gradle.properties          # Hermes, new arch flags (committed; JDK path goes in ~/.gradle/gradle.properties)
│   └── app/
│       └── src/main/java/com/tabletnoteapp/
│           ├── MainActivity.kt            # S-Pen button key intercept (KEYCODE_STYLUS_BUTTON_PRIMARY)
│           ├── MainApplication.kt         # RN app entry, registers CanvasPackage
│           ├── canvas/                    # Pure drawing engine (no RN dependency)
│           │   ├── DrawingCanvas.kt       # Custom View: touch events + rendering (blank notes)
│           │   ├── PdfDrawingView.kt      # Custom View: PDF rendering + drawing overlay
│           │   ├── ColorGradientView.kt   # Native HSV color picker gradient view
│           │   ├── models/Stroke.kt       # Stroke, StrokeStyle, ToolType enum, UndoAction sealed class
│           │   ├── models/Point.kt        # Point (x, y, pressure)
│           │   └── utils/BezierSmoother.kt
│           └── reactbridge/               # RN <-> Kotlin bridge
│               ├── CanvasViewManager.kt
│               ├── CanvasModule.kt
│               ├── CanvasPackage.kt
│               ├── PdfCanvasViewManager.kt
│               ├── PdfCanvasModule.kt
│               └── ColorGradientViewManager.kt
└── src/
    ├── screens/
    │   ├── HomeScreen.tsx         # Note grid, PDF import button
    │   ├── PdfViewerScreen.tsx    # PDF viewer + canvas overlay
    │   └── NoteEditorScreen.tsx   # Blank drawing canvas screen
    ├── store/
    │   ├── useNotebookStore.ts    # Notes list + categories with AsyncStorage persistence
    │   ├── useToolStore.ts        # Active tool state (pen/eraser/select), undo/redo, color/thickness
    │   ├── useSettingsStore.ts    # S-Pen button action mapping, persisted via AsyncStorage
    │   └── useEditorStore.ts      # (stub) Current page, zoom
    ├── navigation/
    │   └── index.tsx              # NavigationContainer + RootStackParamList
    ├── styles/
    │   └── theme.ts               # Light/dark palettes + useTheme() hook
    ├── native/                    # Bridge wrappers
    │   ├── CanvasView.tsx         # requireNativeComponent wrapper with forwardRef
    │   ├── CanvasModule.ts        # undo/redo/clear/getStrokes/loadStrokes
    │   ├── PdfCanvasView.tsx      # requireNativeComponent wrapper for PdfDrawingView
    │   ├── PdfCanvasModule.ts     # undo/redo/clear/getStrokes/loadStrokes/scrollToPage
    │   └── ColorGradientView.tsx  # Native HSV gradient picker with batched event coalescing
    ├── components/
    │   ├── Sidebar.tsx            # Collapsible left sidebar: categories, settings modal, S-Pen action mapping
    │   ├── Toolbar.tsx            # Full toolbar: tool switching, undo/redo, color/thickness
    │   ├── ColorPickerPanel.tsx   # HSV color picker using ColorGradientView + preset swatches
    │   ├── ThicknessSlider.tsx    # PanResponder-based slider for pen/eraser thickness
    │   └── ThumbnailStrip.tsx     # PDF page thumbnail strip using react-native-pdf-thumbnail
    └── types/
        ├── noteTypes.ts           # Note, NoteType (includes optional categoryId, drawingUri)
        ├── categoryTypes.ts       # Category interface + BUILT_IN_CATEGORIES (all/pdfs/notes)
        └── canvasTypes.ts         # PenColor, ToolMode, StrokeStyle
```
