# Tracknix UX refactor — shared brief

Goal: every screen follows the Tracknix design system strictly, and the app stops bombarding the user:
summaries first, details on demand, one obvious primary action per screen.

## Design system (non-negotiable)
- Read `C:\Users\thecr\.claude\skills\tracknix-design-system\` (SKILL.md, COMPONENTS.md, PATTERNS.md, TYPOGRAPHY.md, COLORS.md).
- Build UI ONLY from `ui/components/Tracknix.kt` and `ui/components/TracknixLayout.kt`:
  - Containers: `ShadowCard` (hard 4dp ink shadow, 1.5dp ink border, 4dp corners), `TxListGroup` + `TxListRow`, `TxCollapsible`, `TxMetricTile`.
  - Controls: `TxButton` (primary = `bg = Coral, textColor = ItemsBg`; at most ONE primary per screen), `TxIconButton`, `TxChip`, `TxSegmented`, `TxSwitch`, `TxTextField`, `TxProgress`, `TxBadge`.
  - Headers: `TxTopBar(title, subtitle, onBack, actions)` for every screen (tab roots: no onBack). Replace `ScreenTitle`, `BackTitle`, `LibBackTitle`, ad-hoc back-arrow rows.
  - Overlays: `TxDialog`, `TxDialogButtons`, `TxConfirmDialog`, `TxPromptDialog`, `TxActionSheet`, `TxMenu`/`TxMenuItem`.
  - Text: `SectionLabel`, `StatValue`, `TxDivider`, `EmptyState`.
- Forbidden in screens: Material `AlertDialog`, `TextButton`, `Button`, `OutlinedTextField`, `Card`, `NavigationBar`, raw `DropdownMenu`/`DropdownMenuItem`, elevation shadows, ad-hoc `.border(...)` boxes that imitate a card. Material `DatePicker`/`TimePicker` may stay (no Tracknix equivalent) but must sit inside `TxDialog` or use `DatePickerDialog` with `TxButton(fill = false)` confirm/dismiss and Tracknix colours (`DatePickerDefaults.colors(containerColor = ItemsBg, selectedDayContainerColor = Coral, todayDateBorderColor = Ink, ...)`, same idea for TimePicker).
- Typography: ExtraLight caps labels, Regular body, ExtraBold values. Use `MaterialTheme.typography.*`; no hard-coded font sizes.
- Colours from `ui/theme/Color.kt` only (`Ink`, `ItemsBg`, `SecondaryBg`, `Coral`, `Green`, `Amber`, `MetricColors.*`). Secondary text = `Ink.copy(alpha = 0.7f)`.
- Spacing: 16dp screen gutters, 12–16dp between cards, 8dp inside cards. Bottom spacer 24dp on scrolling screens.

## UX principles
1. **Summary first.** The top of a screen answers "how am I doing?" in one glance (hero numbers). Detail lives below or behind a tap.
2. **Progressive disclosure.** Source tags, override fields, history lists, explanations, long exercise lists → behind `TxCollapsible`, a dialog, or a "Show all" toggle. Default view shows ≤ 3–5 items per list.
3. **One primary action** per screen (coral). Other actions are plain `TxButton`, `TxIconButton`, or rows in a `TxListGroup`. Rarely-used actions go into an overflow (`TxIconButton(MoreVert)` → `TxActionSheet`).
4. **Group, don't stack.** Related links/settings in one `TxListGroup` rather than a column of separate cards/buttons.
5. **Fewer words.** Cut helper sentences that repeat what the UI shows; keep short captions only where they prevent mistakes.
6. **Empty states** use `EmptyState` with one clear next step.

## Hard constraints
- UI/layout only. Do NOT change data, repositories, DAOs, ViewModel logic, domain math, navigation routes or composable signatures that `ui/navigation/YoolNavHost.kt` calls (you may add optional params with defaults).
- Do NOT edit `ui/components/*`, `ui/theme/*`, `ui/navigation/*`. If you need a new shared piece, write it `private` in your own file and mention it in your report.
- Only edit files in your assigned area.
- All user-facing dates/times go through `ui/Format.kt` (`Fmt.date`, `Fmt.time`, ...). 12-hour time pickers.
- Must pass: `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest` (Windows: run via bash `./gradlew`).
- Small, focused conventional commits on your worktree branch (e.g. `refactor(today): ...`). End each commit message with:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Final report: list of commits, what changed per screen, anything you couldn't do.
