# Project Plan

RainbowDrop: A stylistic photo-to-coloring-canvas app. Users can pick a photo, apply a filter, and color it on an interactive canvas with Standard or Mystery modes. Features include bucket-fill with color highlighting, freestyle brush, pinch-to-zoom/pan, undo/redo, Room DB persistence, and time-lapse export via FFmpeg-Kit. Minimal, clean M3 UI with a horizontal tool carousel. Responsive for phone and tablet.

## Project Brief

# RainbowDrop Project Brief

RainbowDrop is a stylistic photo-to-coloring-canvas application that transforms personal photos into interactive digital coloring pages. Users can apply various artistic filters and use a suite of coloring tools to bring their images to life in a vibrant, Material 3-inspired interface.

## Features

* **Stylized Photo Conversion:** Import photos and apply artistic filters (Pop Art, Comic Book, Watercolor, Vector Poster, Ink Sketch, Charcoal, Tattoo Flash) to generate a clean, colorable canvas.
* **Interactive Coloring Engine:** A responsive canvas featuring bucket-fill with color highlighting (checkerboard pattern), freestyle brushing, and intuitive pinch-to-zoom/pan controls.
* **Dual Coloring Modes:** Support for "Standard Mode" (visible outlines) and "Mystery Mode" (hidden outlines).
* **Project Persistence & History:** Local storage of coloring projects using Room DB, allowing users to save progress and utilize robust undo/redo functionality.
* **Time-Lapse Export:** Capability to generate and export a time-lapse video of the coloring process using FFmpeg-Kit.

## High-Level Technical Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose with Material Design 3
* **Navigation:** Jetpack Navigation Compose
* **Adaptive Layouts:** Compose Material Adaptive library for phone and tablet support.
* **Asynchronicity:** Kotlin Coroutines and Flow
* **Persistence:** Room Database
* **Media Processing:** FFmpeg-Kit (for time-lapse generation) and Photo Picker (PickVisualMedia API) for image input.
* **Image Processing:** Custom shaders or bitmap manipulation for filters.

## Implementation Steps
**Total Duration:** 44m 1s

### Task_1_Setup_Navigation_Import: Configure Material 3 theme (Light/Dark), implement Edge-to-Edge display, and set up Jetpack Navigation. Create the Home screen with Photo Picker (PickVisualMedia) integration to select images for the coloring process.
- **Status:** COMPLETED
- **Updates:** Material 3 theme, Edge-to-Edge display, and Navigation 3 are set up. Home screen with Photo Picker is implemented. Adaptive icon generated. Project builds successfully.
- **Acceptance Criteria:**
  - App launches with M3 theme and Edge-to-Edge display
  - Navigation between Home and Editor screens works
  - Photo Picker returns a valid image URI
  - Project builds successfully
- **Duration:** 37m 15s

### Task_2_ImageProcessing_ColoringEngine: Implement image processing filters (e.g., Ink Sketch, Vector Poster) and the interactive coloring canvas. Support pinch-to-zoom, pan, bucket-fill with color highlighting, and freestyle brush functionality.
- **Status:** COMPLETED
- **Updates:** Image processing filters (7 types) and the interactive coloring engine (zoom, pan, bucket-fill, brush) are implemented. Editor screen with tool carousel and filter selection is functional. Project builds successfully.
- **Acceptance Criteria:**
  - Photo is correctly processed into a colorable canvas
  - Filter selection carousel updates the preview
  - Pinch-to-zoom and pan work smoothly on the canvas
  - Bucket-fill and freestyle brush are functional
- **Duration:** 6m 46s
~~~~
### Task_3_Persistence_TimeLapse: Integrate Room Database for project persistence, including saving progress and undo/redo history. Implement time-lapse video export using FFmpeg-Kit.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Coloring projects are saved and loaded from Room DB
  - Undo/Redo functionality correctly updates canvas state
  - Time-lapse video of the coloring process is generated and exported successfully
  - Exported video is playable
- **StartTime:** 2026-06-27 00:46:19 CDT

### Task_4_Polish_Adaptive_Verify: Refine UI for Material 3 fidelity and implement adaptive layouts for phone and tablet. Generate an adaptive app icon and perform a final Run and Verify to ensure stability and requirement alignment.
- **Status:** PENDING
- **Acceptance Criteria:**
  - UI is responsive and adaptive on both phone and tablet
  - Adaptive app icon is implemented and visible
  - App stability verified (no crashes)
  - Build passes and all existing tests pass

