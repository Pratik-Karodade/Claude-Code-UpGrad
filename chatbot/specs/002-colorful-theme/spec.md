# Feature Specification: Colorful Chat Theme

**Feature Branch**: none (changes made on `master`)

**Status**: Implemented

**Scope**: Visual styling of the frontend only, in `frontend/src/styles.css`. No behavior, API, or
markup changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Distinguish Speakers at a Glance (Priority: P1)

A customer scanning the chat can tell their own messages from the assistant's by color alone.

**Acceptance Scenarios**:

1. **Given** a conversation with both roles, **When** it renders, **Then** user messages use the
   sky-blue style with a blue accent and assistant messages use the warm yellow style with an
   amber accent.
2. **Given** a message label, **When** it renders, **Then** the speaker name ("You" or
   "Caramel AI Support") is shown in the matching accent color.

### User Story 2 - Clear States for Streaming and Failures (Priority: P2)

A customer can see when a reply is still arriving and when something failed.

**Acceptance Scenarios**:

1. **Given** a streaming assistant reply, **When** it renders, **Then** the typing indicator is
   pink and pulses.
2. **Given** a failed reply or an error banner, **When** it renders, **Then** it uses the rose/red
   danger colors and keeps readable text contrast.

### User Story 3 - Branded Header and Controls (Priority: P3)

**Acceptance Scenarios**:

1. **Given** the app loads, **When** the header renders, **Then** it shows a caramel → pink →
   violet gradient bar with white title text.
2. **Given** an enabled button, **When** the pointer hovers it, **Then** it lifts slightly and
   brightens. **Given** a disabled button, **Then** it is dimmed with no hover effect.
3. **Given** the message textarea, **When** it has focus, **Then** it shows a violet focus ring.

## Requirements *(mandatory)*

- **FR-001**: The palette MUST be defined as CSS custom properties on `:root`.
- **FR-002**: Every class name used by the components MUST have a matching rule in the stylesheet.
- **FR-003**: Styling MUST NOT change class names, DOM structure, or ARIA attributes, so existing
  tests keep passing.
- **FR-004**: The typing indicator animation MUST respect the existing visible text; it only
  changes opacity.

## Success Criteria *(mandatory)*

- **SC-001**: `npm test` in `frontend/` passes, including the theme spec in
  `frontend/tests/theme.test.ts`.
- **SC-002**: Visual review on http://localhost:5173 shows distinct user and assistant colors,
  the gradient header, and the pulsing typing indicator.
