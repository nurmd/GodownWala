---
name: CementTrack Godown Manager
colors:
  surface: '#f4faff'
  surface-dim: '#cfdce4'
  surface-bright: '#f4faff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#e9f6fd'
  surface-container: '#e3f0f8'
  surface-container-high: '#ddeaf2'
  surface-container-highest: '#d7e4ec'
  on-surface: '#111d23'
  on-surface-variant: '#574235'
  inverse-surface: '#263238'
  inverse-on-surface: '#e6f3fb'
  outline: '#8b7263'
  outline-variant: '#dec1af'
  surface-tint: '#964900'
  primary: '#964900'
  on-primary: '#ffffff'
  primary-container: '#f57c00'
  on-primary-container: '#572800'
  inverse-primary: '#ffb786'
  secondary: '#506169'
  on-secondary: '#ffffff'
  secondary-container: '#d1e2ec'
  on-secondary-container: '#55656d'
  tertiary: '#1b6d24'
  on-tertiary: '#ffffff'
  tertiary-container: '#5faf5d'
  on-tertiary-container: '#003f0b'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdcc6'
  primary-fixed-dim: '#ffb786'
  on-primary-fixed: '#311300'
  on-primary-fixed-variant: '#723600'
  secondary-fixed: '#d4e5ef'
  secondary-fixed-dim: '#b8c9d3'
  on-secondary-fixed: '#0d1e25'
  on-secondary-fixed-variant: '#394951'
  tertiary-fixed: '#a3f69c'
  tertiary-fixed-dim: '#88d982'
  on-tertiary-fixed: '#002204'
  on-tertiary-fixed-variant: '#005312'
  background: '#f4faff'
  on-background: '#111d23'
  surface-variant: '#d7e4ec'
typography:
  headline-xl:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
  receipt-code:
    fontFamily: Space Mono
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  metric-counter:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '800'
    lineHeight: 44px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  margin: 1rem
  margin-lg: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers an unapologetic, industrial-grade operational experience built specifically for warehouse logistics, dispatch docks, and raw godown environments. The design language balances extreme functional clarity with heavy-duty ergonomics, engineered for floor supervisors and forklift operators interacting with ruggedized mobile terminals under harsh warehouse lighting or glare.

The visual style merges utilitarian industrial efficiency with high-contrast tactical minimalism. Rather than relying on purely ornamental design patterns, the interface utilizes concrete-inspired card surfaces, deliberate structural divisions, high-visibility safety markers, and generous interaction targets optimized for single-handed use and gloved operation. Every screen prioritizes immediate data scanning, instantaneous state confirmation, and physical warehouse workflows such as rapid barcode auditing, stock-in verification, and bill-of-lading printing.

## Colors

The color palette is built strictly around operational safety, high readability, and instantaneous state recognition across the godown floor.

- **Primary (`#F57C00` - Safety Amber/Orange):** Serves as the primary operational trigger. Reserved for critical interactive actions (e.g., dispatch confirmations, barcode scanning activations, primary CTA buttons) and high-priority alerts.
- **Secondary (`#37474F` - Heavy Slate):** Represents structural containment and structural UI components, such as app headers, navigation chrome, prominent badges, and segmented tab tracks.
- **Tertiary (`#2E7D32` - Stock-In Emerald):** Dedicated functional color for all incoming inventory, positive balance updates, verified bag counts, and completed gate-ins.
- **Stock-Out Crimson (`#C62828`):** Functional paired alert color strictly indicating outgoing dispatch, deducted inventory, damaged bags, or system errors.
- **Neutral Surface & Concrete Palette:**
  - Base canvas: `#F5F7F8` (Concrete Off-White) to eliminate glare common with pure `#FFFFFF` under industrial halide lighting.
  - Surface cards: `#FFFFFF` for sharp contrast against the concrete base.
  - Borders & dividers: `#CFD8DC` and `#ECEFF1` for clear structural segmentation.
  - Primary text: `#263238` (Deep Slate Charcoal) ensuring maximum accessibility compliance (exceeding WCAG AAA standards).
  - Secondary text: `#546E7A` for metadata, SKU subtext, and timestamp tags.

## Typography

Typography is set in **Inter** to ensure maximum glyph differentiation, rigid horizontal stability, and flawless legibility at varying arm lengths and angles.

- **Headlines & Counters:** Rendered with heavy weights (`600` to `800`) to highlight stock quantities, bay numbers, and truck registrations at a glance.
- **Body & Labels:** Engineered with open aperture heights for legibility through smudged protective glass screens.
- **Monospace Extension (`Space Mono`):** Applied exclusively for thermal receipt print previews, digital bill-of-lading manifests, SKU serial tags, and hardware scanner payload dumps.
- **Tabular Figures:** All numeric displays (stock balances, weights in metric tons, bay allocations) enforce monospaced numerals (`tnum`) to prevent layout jitter during rapid inventory updates.

## Layout & Spacing

The layout is optimized for high-density handheld screens operating within dynamic warehouse logistics workflows.

- **Grid Architecture:** Employs a 4-column fluid mobile grid with `1rem` gutters and outer canvas margins. Multi-column data layouts collapse cleanly into vertical stacks on viewports narrower than 360px.
- **Touch Target Disciplines:** All primary tap targets require a minimum height of `48px` (ideally `56px` for primary stock entry triggers) to guarantee accurate input while moving or wearing work gloves.
- **Rhythm & Padding:** Component interiors conform strictly to an 8px base rhythm (`space-xs` = 4px, `space-sm` = 8px, `space-md` = 16px, `space-lg` = 24px, `space-xl` = 32px).
- **Scanning Zone Sticky Anchorage:** Action zones (such as continuous camera feed toggles, manual quantity nudgers, and print dispatch triggers) are pinned to the bottom 88px of the viewport for comfortable thumb reach.

## Elevation & Depth

This system intentionally rejects ambient drop shadows and low-contrast soft blurs in favor of crisp, structural boundaries and low-contrast functional outlines that thrive in bright, harsh lighting conditions.

- **Level 0 (Floor Canvas):** Flat concrete off-white (`#F5F7F8`) base.
- **Level 1 (Card & Module Layer):** Pure white (`#FFFFFF`) surface framed by a 1px solid border (`#CFD8DC`). No drop shadow is applied; contrast is achieved strictly through surface boundaries.
- **Level 2 (Active Focus & Scanners):** Highlighted card surfaces bordered with a 2px stroke in primary safety amber (`#F57C00`) or deep slate (`#37474F`), supplemented with a subtle 2px hard offset shadow (`rgba(38, 50, 56, 0.12)`).
- **Level 3 (Modal Sheets & Thermal Receipt Slips):** Overlays sit atop an opaque scrim (`rgba(38, 50, 56, 0.60)`), framed with a 1px slate divider and an 8px elevation drop shadow (`0 8px 24px rgba(38, 50, 56, 0.20)`).

## Shapes

The design system enforces a soft, mechanical radius of **`1`** (`0.25rem` / `4px` base radius), delivering an engineered, machine-tooled profile consistent with heavy equipment and industrial controls.

- **Containers & Cards:** Styled with a `4px` corner radius. Sharp enough to convey precision, softened slightly to prevent visual fatigue.
- **Badges & Barcode Frames:** Use a tight `2px` to `4px` corner radius. Fully circular or pill-shaped containers are strictly avoided, as rounded pills waste valuable horizontal space when displaying long SKU and vehicle numbers.
- **Buttons & Large Touch Inputs:** Follow the system base radius (`4px`), providing a defined geometric footprint for touch and click states.

## Components

### Action Buttons
- **Primary Industrial Trigger:** Minimum `52px` height. Solid `#F57C00` fill with bold white typography (`#FFFFFF`). Text is uppercase with slight letter-spacing (`0.5px`).
- **Secondary Action:** Minimum `48px` height. Solid `#37474F` with `#FFFFFF` text, or transparent background with a 2px `#37474F` solid outline.
- **Directional Stock Actions:** 
  - **Stock-In Button:** Emerald green (`#2E7D32`) background with incoming arrow glyph.
  - **Stock-Out Button:** Deep amber/crimson (`#C62828`) background with outgoing arrow glyph.

### Cards & Tile Modules
- **Concrete Inventory Card:** `#FFFFFF` background encased in a 1px `#CFD8DC` border. Uses a thick 4px vertical accent stripe on the left edge denoting material status:
  - Green (`#2E7D32`) for In-Stock/Accepted.
  - Orange (`#F57C00`) for In-Transit/Allocated.
  - Red (`#C62828`) for Depleted/Damaged/Quarantined.
- **Data Densities:** Displays prominent bag count/tonnage metric in `metric-counter` font size next to the SKU code.

### Barcode & QR Badges
- Encapsulated in high-contrast rectangular containers with `#ECEFF1` background, 1px dashed `#78909C` border, and accompanied by the raw alphanumeric string in `Space Mono`.

### Inputs & Number Steppers
- **Godown Quick-Steppers:** Large dual-button increment counters (`-` and `+`) with a minimum `56px` square footprint flanking an oversized numeric input box to enable immediate bag tally adjustments without pulling up software keyboards.
- **Text Entry:** Light gray interior (`#ECEFF1`) with a bottom 2px border that transitions to Safety Amber (`#F57C00`) on active focus.

### Thermal Receipt Preview Slip
- Distinctive off-white container (`#FAFAFA`) utilizing monospaced typesetting (`Space Mono`).
- Features a serrated zig-zag cut edge pattern on the top and bottom borders generated via CSS clip-path mask.
- Integrates a receipt-cut micro-interaction: upon tapping "Issue Gate Pass" or "Print Manifest", a 200ms haptic tap triggers an animated scissor/slice hairline transition across the perforation, confirming the document dispatch.