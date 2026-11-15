---
name: Tsumo
colors:
  surface: '#faf9f5'
  surface-dim: '#dbdad6'
  surface-bright: '#faf9f5'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f4f0'
  surface-container: '#efeeea'
  surface-container-high: '#e9e8e4'
  surface-container-highest: '#e3e2df'
  on-surface: '#1b1c1a'
  on-surface-variant: '#50453d'
  inverse-surface: '#2f312e'
  inverse-on-surface: '#f2f1ed'
  outline: '#82756b'
  outline-variant: '#d4c3b9'
  surface-tint: '#7b5739'
  primary: '#291200'
  on-primary: '#ffffff'
  primary-container: '#42260c'
  on-primary-container: '#b68b6a'
  inverse-primary: '#ecbd98'
  secondary: '#7e562e'
  on-secondary: '#ffffff'
  secondary-container: '#fdc796'
  on-secondary-container: '#79512a'
  tertiary: '#001c21'
  on-tertiary: '#ffffff'
  tertiary-container: '#043239'
  on-tertiary-container: '#749ba3'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdcc2'
  primary-fixed-dim: '#ecbd98'
  on-primary-fixed: '#2e1501'
  on-primary-fixed-variant: '#604024'
  secondary-fixed: '#ffdcbf'
  secondary-fixed-dim: '#f1bc8c'
  on-secondary-fixed: '#2d1600'
  on-secondary-fixed-variant: '#633f19'
  tertiary-fixed: '#c1e9f2'
  tertiary-fixed-dim: '#a5cdd6'
  on-tertiary-fixed: '#001f24'
  on-tertiary-fixed-variant: '#254c54'
  background: '#faf9f5'
  on-background: '#1b1c1a'
  surface-variant: '#e3e2df'
typography:
  display:
    fontFamily: EB Garamond
    fontSize: 3rem
    fontWeight: '400'
    lineHeight: 3.5rem
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: EB Garamond
    fontSize: 2.25rem
    fontWeight: '400'
    lineHeight: 2.75rem
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: EB Garamond
    fontSize: 1.75rem
    fontWeight: '400'
    lineHeight: 2.25rem
  headline-md:
    fontFamily: EB Garamond
    fontSize: 1.5rem
    fontWeight: '400'
    lineHeight: 2rem
  headline-sm:
    fontFamily: EB Garamond
    fontSize: 1.25rem
    fontWeight: '400'
    lineHeight: 1.75rem
  body-lg:
    fontFamily: Manrope
    fontSize: 1.125rem
    fontWeight: '400'
    lineHeight: 1.75rem
  body-md:
    fontFamily: Manrope
    fontSize: 0.9375rem
    fontWeight: '400'
    lineHeight: 1.5rem
  body-sm:
    fontFamily: Manrope
    fontSize: 0.8125rem
    fontWeight: '400'
    lineHeight: 1.25rem
  label-md:
    fontFamily: Manrope
    fontSize: 0.8125rem
    fontWeight: '600'
    lineHeight: 1.125rem
    letterSpacing: 0.06em
  label-sm:
    fontFamily: Manrope
    fontSize: 0.6875rem
    fontWeight: '500'
    lineHeight: 1rem
    letterSpacing: 0.08em
spacing:
  gutter: 1.5rem
  margin: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

The design system embodies an editorial archive aesthetic, built around the quiet dignity of printed literature and physical book collecting. Designed for discerning readers, scholars, and digital bibliophiles, the interface strips away customary mobile scaffolding—eliminating rigid card wrappers, heavy solid backgrounds, icons, and noisy graphical decorations—in favor of an open canvas where book cover art serves as the primary visual resonance.

The emotional tone is contemplative, tactile, and restrained. By juxtaposing classical serif typography with razor-sharp geometric sans-serif metadata, the experience mirrors walking through a private gallery or rare manuscript library. Every interaction prioritizes white space, visual balance, and uncompromised focus on cover aesthetics.

## Colors

The palette is derived from aged vellum, rich printing inks, and deep walnut timber. 

- **Primary (`#42260c`):** Deep umber ink. Used for high-emphasis headlines, active text labels, and structural typography.
- **Secondary (`#8c6239`):** Burnished leather. Applied to secondary metadata, publication years, author attribution, and quiet focus states.
- **Neutral Canvas (`#fcfbf7`):** Warm unbleached paper. Provides an expansive, non-distracting field across all screens, allowing book cover plates to project naturally without harsh contrast.

Subtle ink washes at low opacities (e.g., `#42260c` at 6% or 12%) provide hair-thin hairline rules when visual separation is structurally unavoidable.

## Typography

Typography establishes the architectural framework of the design system. 

- **EB Garamond** acts as the literary voice: scholarly, graceful, and historically grounded. It is reserved for prominent titles, literary excerpts, and the brand mark, which is displayed in an expressive, italicized cut of EB Garamond.
- **Manrope** provides a technical, clean counterweight for metadata, release dates, file attributes, status indications, and interactive text triggers.

All labels and years beneath posters use uppercase tracking to enhance legibility at small sizes without demanding high visual weight. Icons and emoji are strictly prohibited; status and navigation cues rely entirely on precise typographic phrasing.

## Layout & Spacing

The canvas follows a fluid vertical-scroll model anchored by consistent margin channels.

- **Mobile Viewports (<600dp):** Single or 2-column poster grid with `1.5rem` margins and `1.5rem` gutters.
- **Large Viewports (≥600dp):** Fluid 3- to 4-column poster grid with `2.5rem` margins and `2rem` gutters.

Vertical rhythm relies on generous unallocated paper space rather than visible structural partitions. Poster art must be anchored with fixed aspect ratios (typically 2:3) to maintain rhythmic alignment. Metadata blocks (e.g., release year and format) rest precisely `0.5rem` below the poster plate, keeping the association intuitive and unified.

## Elevation & Depth

This design system avoids simulated Z-axis elevation, skeuomorphic drop shadows, and synthetic blur materials. 

Visual hierarchy is communicated purely through planar relationships:
- **Canvas Plane:** The baseline warm paper ground (`#fcfbf7`).
- **Media Plane:** High-resolution book cover posters resting directly flush on the canvas ground without drop shadows, border outlines, or rounded masks.
- **Hairline Accentuation:** When section separation is necessary, use a single 0.5px hairline rule using `#42260c` at 12% opacity.
- **Focus & States:** Interactive focus states are denoted strictly by shifts in typographic color (e.g., transitioning from muted `#8c6239` to deep `#42260c`) or an understated underline, preserving absolute flatness.

## Shapes

The shape philosophy is strictly geometric and unrounded (`roundedness: 0`). 

Every element retains sharp 90-degree corners, echoing traditional cut paper sheets, printed plates, book boards, and architectural drafting margins. No pills, rounded buttons, or circular indicators exist within the interface. Inputs, interactive text zones, and poster image frames preserve absolute planar geometry.

## Components

### Search Field
The search experience is borderless and seamless with the canvas. It contains no enclosed input box, background pill, or magnifying glass icon. The component consists solely of an empty text area with an EB Garamond placeholder ("Search by title, author, or catalog...") rendered in `#8c6239` at 60% opacity. Tapping the field reveals a blinking, razor-thin caret in `#42260c`. A subtle 0.5px baseline rule in `#42260c` (15% opacity) grounds the field.

### Book Poster Item
The core component of the interface. Displays a large, unrounded vertical plate (2:3 aspect ratio). No shadows or borders enclose the artwork. Immediately beneath the image, metadata is typeset horizontally in Manrope:
- **Primary Line:** Book title in `headline-sm` or `body-md` (bold).
- **Secondary Line:** Publication year in `label-sm` using `#8c6239`, accompanied by author name separated by standard typographic spacing.

### Buttons & Interactive Text
Traditional button containers (solid rectangles, pills, framed boxes) are banned. Actions are rendered as clean typographic triggers in `label-md` or `body-md`.
- **Primary Action (e.g., Download):** Deep umber `#42260c` text with a 1px borderless bottom underline.
- **Secondary Action (e.g., Details, Cancel):** `#8c6239` text with no underline, brightening to `#42260c` on press.

### Chips & Filters
Horizontal text filters set in `label-sm`. The inactive state is rendered in `#8c6239` (muted); the active selection is rendered in `#42260c` with a tight, crisp baseline rule directly beneath the text string. No enclosing capsules or tinted backgrounds.

### Selection Controls (Checkbox & Radio)
Replaced with typographic toggles or stark geometric markers:
- **Checkbox:** A crisp, sharp 14px square frame (1px solid `#42260c`) containing a solid black center fill when checked.
- **Radio:** A sharp 14px outer square with an inner 6px square center when selected. No rounded corners.

### Lists & Index Views
Catalog lists discard card containers. Items are arranged sequentially separated only by generous whitespace (`space-md`) and an optional 0.5px hairline in low-opacity umber. Each row prioritizes title, year, and file size in clean tabular alignment.