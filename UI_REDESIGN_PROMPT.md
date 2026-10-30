# PRODUCTION-GRADE UI/UX REDESIGN SPECIFICATION & PROMPT
## Android Jetpack Compose • Material 3 • Senior Design Engineering Overhaul

---

> **TARGET CODEBASE**: `Smart Attendance` (`com.smartattendance.app`)  
> **TECH STACK**: Kotlin 2.0+ • Jetpack Compose (BOM 2025.04.01) • Material 3 • Navigation Compose 2.8.9  
> **OBJECTIVE**: Refactor the entire visual and interaction layer of the app to match top-tier commercial applications (**Linear, Stripe Dashboard, Apple HIG, GitHub Mobile, Google Lens**). Eliminate all signs of generic "AI vibe coding" while strictly locking the existing color palette and business logic.

---

## 1. CORE EXECUTIVE MANDATES (NON-NEGOTIABLE)

1. **ZERO LOGIC MUTATION (100% BEHAVIORAL PRESERVATION)**:
   - Do **NOT** modify or delete any ViewModel (`AppViewModel`, `ScannerViewModel`), Repository (`LocalAttendanceRepository`, `RemoteAttendanceRepository`), Room Entity/DAO (`AttendanceDatabase`, `Entities.kt`), Supabase client (`SupabaseApi`), domain rules (`AttendanceRules.kt`), or CSV handlers (`StudentCsvImporter`, `CsvExporter`).
   - Every function signature, callback (`onClick`, `navigate`, `onSelect`), route parameter (`session/{id}`, `class/{id}`, `create?classId=...`), and state contract (`AppState`, `Snapshot`, `ScanState`) must remain untouched.
   - Refactoring is strictly confined to Composable layout trees, modifiers, typography, elevation, drawing canvas, and micro-interaction animations.

2. **COLOR PALETTE LOCK (IDENTICAL TONAL IDENTITY)**:
   - Preserve the exact brand colors defined in `Theme.kt`:
     - **Light Theme**:
       - `primary`: `#173756` (Deep Slate Navy)
       - `primaryContainer`: `#DCE8F4` (Ice Blue)
       - `secondary`: `#007E87` (Pine Teal)
       - `secondaryContainer`: `#D5F4EF` (Soft Mint)
       - `tertiary`: `#956600` (Warm Bronze/Amber)
       - `background`: `#F4F7FB` (Cool Professional Canvas)
       - `surface`: `#FDFEFF` (Crisp Paper White)
       - `surfaceVariant`: `#E8EEF5` (Subtle Card Surface)
       - `outline`: `#778899` (Slate Grey Border)
     - **Dark Theme**:
       - `primary`: `#9DCBF2`
       - `primaryContainer`: `#204160`
       - `secondary`: `#65D8CB`
       - `secondaryContainer`: `#084E50`
       - `background`: `#101B29` (Deep Obsidian Blue)
       - `surface`: `#172536` (Navy Slate Surface)
       - `surfaceVariant`: `#243549`
     - **Semantic Status Accents**:
       - Present / Active: `#087B69` (Emerald)
       - Late / Scheduled: `#9B690B` (Amber)
       - Absent / Inactive: MaterialTheme error `#B3261E` / `#F2B8B5`
   - You may only calibrate alpha/opacities (e.g. `1px` hairlines with `0.08f`–`0.15f` alpha) to achieve modern optical depth.

3. **SYSTEMATIC ERADICATION OF "AI VIBE CODE"**:
   - **No Monolithic InfoCard Stacking**: Replace the uniform `InfoCard` everywhere with purpose-built component architectures (grouped inset containers, hero metric tiles, badge chips, structured student list items).
   - **No Blanket 20dp/24dp Pill Radii**: Implement mathematically nested corner radii (`outer_radius - padding = inner_radius`). Standardize on `12.dp` for cards, `8.dp` for controls, and `4.dp` for nested chips.
   - **No Flat Static Scanner Box**: Replace the amateur 2dp green box with a professional camera HUD: animated laser sweep beam, darkened outer aperture mask, sleek corner reticles, and floating status pill.
   - **No Clunky Demo Banner**: Replace the full-width harsh bar with a refined glassmorphic pill badge integrated seamlessly into the navigation header.

---

## 2. BENCHMARK COMPARISON: CURRENT VS. INDUSTRY STANDARD

| Area | Current Implementation (AI Vibe Flaws) | Senior Design Standard (Linear / Apple / Stripe) |
| :--- | :--- | :--- |
| **Page Layout** | Flat `Column` with `verticalScroll()` and generic `spacedBy(16.dp)` on all screens. | Scaffold with pinned glassmorphic headers, sticky actions, balanced whitespace, and grouped inset sections. |
| **Typography** | Default Compose `Typography()`, uncalibrated line heights, no tabular numerals. | Custom `Typography` with tight tracking (`-0.02em`) on headers, medium weight (500) for labels, and `FontFeatureSettings("tnum")` for all codes, counts, and times. |
| **Lists & Cards** | Every entity (student, class, session, stat) is wrapped in the exact same `InfoCard(shape=20.dp)`. | Grouped container pattern (iOS/Linear style): single elevated card housing multiple items separated by hairline dividers with edge-to-edge touch ripples. |
| **Camera Scanner** | Static black preview box with raw 2dp green outline and static text. | Viewfinder with dimmed overlay mask, pulsing animated corner brackets (reticles), oscillating vertical laser line, and floating pill HUD. |
| **Statistics** | 4 cramped cards + generic 8dp progress bar. | Stacked segmented distribution bar with percentage indicators, large bold tabular metric counters, and clean contextual subtitles. |
| **Transitions** | Abrupt instant route switching; no interactive touch feedback. | Fluid Compose NavHost enter/exit transitions (slide + fade), `animateContentSize` on expandable lists, and spring-scale press micro-interactions. |

---

## 3. DESIGN TOKENS & TYPOGRAPHY SYSTEM (`Theme.kt` & `Components.kt`)

### Typographic Specifications (Jetpack Compose)
Elevate the typography scale with intentional tracking, line-height ratios, and tabular figures for numerical data:

```kotlin
// In ui/theme/Type.kt or Theme.kt
val TabularNumbers = "tnum"

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.03).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.015).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.04.sp // Eyebrow uppercase tracking
    )
)
```

### Elevation & Border Tokens
- **Hairline Border**: `BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))`
- **Card Corner Radius**: `RoundedCornerShape(14.dp)` for top-level cards, `RoundedCornerShape(8.dp)` for nested controls.
- **Surface Layering**:
  - `Background`: `#F4F7FB` (Light) / `#101B29` (Dark)
  - `Surface Level 1 (Card)`: `#FDFEFF` (Light) / `#172536` (Dark) + hairline outline
  - `Surface Level 2 (Nested / Active)`: `#F0F4F9` (Light) / `#1E3046` (Dark)

---

## 4. SCREEN-BY-SCREEN ARCHITECTURAL REFACTORING

### Screen 1: App Shell & Global Navigation (`AppNavigation.kt`)
1. **Refined Top App Bar**:
   - Apply a clean hairline bottom divider: `Modifier.drawBehind { drawLine(color = dividerColor, strokeWidth = 1f, ...) }`.
   - Embed the Environment Mode (Demo vs Online) directly into the App Bar as a chic status badge:
     - Demo Mode: A pill tag `[● DEMO CỤC BỘ]` in `secondary` color with 12% alpha container and 6dp pulsating dot.
     - Online Mode: A subtle user avatar / email chip.
   - Remove the clumsy full-width `Surface` strip below the TopAppBar.
2. **Modern Bottom Navigation Bar**:
   - Reduce height to 64.dp with subtle icon pill indicator (`NavigationBarItemDefaults.colors` with low-opacity container).
   - Use subtle scale transition when switching tabs.
3. **Animated NavHost Transitions**:
   - Provide silky screen transitions across all routes:
     ```kotlin
     NavHost(
         navController = nav,
         startDestination = "dashboard",
         enterTransition = { fadeIn(tween(200)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
         exitTransition = { fadeOut(tween(150)) },
         popEnterTransition = { fadeIn(tween(200)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) },
         popExitTransition = { fadeOut(tween(150)) }
     )
     ```

### Screen 2: Dashboard Overview (`DashboardScreen.kt`)
1. **Hero Gradient Card**:
   - Replace the flat blue rectangle with a polished Linear-style hero card:
     - Gradient fill using `Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.88f)))`.
     - Fine top-edge tactile highlight (`1.dp` border highlight).
     - Clean metric typography: Session count in `displaySmall`, participation stats with a visual mini circular progress or segmented gauge.
     - Quick "Quét thẻ sinh viên" primary CTA button with tactile elevation and leading scanner icon.
2. **Metric Summary Grid**:
   - Render 2 concise metric tiles side-by-side (`Lớp phụ trách`, `Hồ sơ sinh viên`) with large numbers in bold tabular font, subtle category labels, and faint secondary container accents.
3. **Grouped Section Views**:
   - Group recent classes and today's sessions into cohesive inset card containers with dividers between rows, instead of disconnected standalone cards.

### Screen 3: Attendance Scanner HUD (`ScannerScreen.kt`)
1. **Aperture Mask with Frosted Vignette**:
   - Darken the periphery around the central scanning target window (`260.dp x 180.dp`) with `Color.Black.copy(alpha = 0.55f)` to focus user vision.
2. **Precision Corner Reticles**:
   - Draw four sleek L-shaped corner brackets (length: 22.dp, stroke: 3.dp, radius: 8.dp) in `Color(0xFF007E87)` or bright mint `#65D8CB`.
3. **Animated Laser Sweep**:
   - Integrate an `InfiniteTransition` with a smooth oscillating vertical scan line:
     ```kotlin
     val infiniteTransition = rememberInfiniteTransition(label = "laser")
     val laserOffset by infiniteTransition.animateFloat(
         initialValue = 0f, targetValue = 1f,
         animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
     )
     ```
   - Render the laser as a gradient brush fading towards transparent edges.
4. **Live Floating Status Pill**:
   - Over the camera preview, display a floating pill at the top: `[🟢 Camera sẵn sàng]` with flashlight toggle button at the top-right.
5. **Instant Feedback Banner (Result Sheet)**:
   - When a student is scanned, show a tactile bottom sheet / floating card with:
     - Instant checkmark animation (Scale + Spring).
     - Student Name in semibold, `MASV` in tabular font, and dynamic status pill (`CÓ MẶT` / `ĐI MUỘN`).
     - Haptic feedback and subtle success tone.

### Screen 4: Session Attendance & Roster Management (`AttendanceScreen.kt`)
1. **Sticky Header & Session Stats**:
   - Prominent session status pill (`ĐANG MỞ`, `ĐÃ ĐÓNG`) paired with start/end time formatted cleanly.
   - Enhanced `Stats` component:
     - A multi-segmented progress bar (stacked horizontal bar showing proportions of Present [Green], Late [Amber], Excused [Blue], Absent [Red]).
     - 4 metric pills with counts and percentage tags.
2. **Search & Filter Ergonomics**:
   - Unified search bar with a horizontal scrollable row of `FilterChip` components (`Tất cả`, `Có mặt`, `Đi muộn`, `Vắng`, `Có phép`) replacing the clunky dropdown menu.
3. **Student Roster List**:
   - Format student rows into a clean, scannable table/list view:
     - Left: Student avatar circle (initials or person icon) in soft neutral tint.
     - Center: Student Name (Medium weight) + `MASV` (Tabular font, secondary color).
     - Right: Timestamp tag (e.g. `08:14`) + Status badge.
   - Tapping opens the adjustment modal with smooth `ModalBottomSheet` or animated dialog.

### Screen 5: Classes & Class Details (`ClassScreens.kt`)
1. **Class Directory**:
   - Display classes as structured cards featuring class code (bold uppercase), subject name, student count tag, and attendance completion bar.
2. **Class Details**:
   - Top overview banner with class capacity and historical attendance average.
   - Clean tabbed or segmented view switching between `Buổi học` (Sessions) and `Danh sách sinh viên` (Student Roster).

### Screen 6: Reports & Export (`ReportsScreen.kt`)
1. **Date Range & Filter Bar**:
   - Modern connected input fields with clear icons and calendar quick-picks.
2. **Visual Attendance Breakdown**:
   - Multi-segment progress visualizer with legend chips.
   - Clean CSV Export button with prominent download icon and file format indicator (`UTF-8 BOM CSV`).
3. **Student Attendance Ranking**:
   - List students ordered by attendance rate with tabular percentages (`95.5%`, `80.0%`).

### Screen 7: Login & Authentication (`LoginScreen.kt`)
1. **Refined Branding Header**:
   - Perfectly centered app logo with subtle ambient glow container.
   - Modern typography: App title in `headlineMedium`, elegant tagline in `secondary` color.
2. **Form Layout**:
   - Input fields with standardized 44.dp height, hairline borders, and smooth focus-ring animation.
   - Clear visual separation between the production login form and demo quick-access buttons.
3. **Demo Quick-Access Cards**:
   - Display demo roles (`Quản trị viên`, `GV Minh Anh`, `GV Hoàng Nam`) as clean horizontal action cards with distinct role icons and description tags.

---

## 5. REUSABLE ATOMIC COMPONENTS UPGRADE (`Components.kt`)

Replace the generic helpers with these senior-grade implementations:

1. **`GroupedCard` & `ListItem`**:
   - A container with `RoundedCornerShape(14.dp)`, hairline border, and surface color.
   - Internal items have ripple feedback and are separated by `HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = outline.copy(alpha = 0.08f))`.
2. **`StatusBadge`**:
   - Sleek pill container (`shape = RoundedCornerShape(6.dp)`) with 12% background tint of the semantic color.
   - Contains a 5.dp circular dot indicator followed by text in `labelSmall` with semibold weight.
3. **`SegmentedStatsBar`**:
   - A single horizontal bar with rounded ends, showing the exact ratio of Present / Late / Excused / Absent with smooth animated width transitions (`animateFloatAsState`).
4. **`ModernSearchField`**:
   - Height: 44.dp.
   - Pill or 12.dp radius shape with leading search icon, trailing clear `IconButton`, and subtle background tint (`surfaceVariant.copy(alpha = 0.5f)`).

---

## 6. STEP-BY-STEP IMPLEMENTATION & VERIFICATION INSTRUCTIONS

When applying this prompt to the project:

1. **Phase 1: Token & Theme Enhancement**:
   - Update `Theme.kt` and `Components.kt` with the new typography, border tokens, and atomic components (`StatusBadge`, `GroupedCard`, `SegmentedStatsBar`, `ModernSearchField`).
   - Run compilation check: `.\gradlew.bat :app:assembleDebug` to ensure all existing callers compile.

2. **Phase 2: App Shell & Global Chrome**:
   - Update `AppNavigation.kt` to integrate the refined TopBar with the inline Demo pill, modern NavigationBar, and NavHost transition animations.

3. **Phase 3: High-Impact Screens**:
   - Refactor `DashboardScreen.kt` (Hero gradient, metric tiles, grouped lists).
   - Refactor `ScannerScreen.kt` (Camera viewfinder HUD, animated laser sweep, corner reticles, result banner).
   - Refactor `AttendanceScreen.kt` (Segmented stats, filter chips, student roster table).

4. **Phase 4: Supporting Screens**:
   - Refactor `ClassScreens.kt`, `ReportsScreen.kt`, `LoginScreen.kt`, `SettingsScreen.kt`, and `AdminScreen.kt`.

5. **Phase 5: Verification & QA**:
   - Verify that unit tests continue to pass: `.\gradlew.bat :app:testDebugUnitTest`.
   - Verify that all database queries, Supabase authentication, and CSV export logic function identically.
   - Verify visual hierarchy in both **Light** and **Dark** modes.
