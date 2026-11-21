import { readFileSync, readdirSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

// Spec: specs/002-colorful-theme/spec.md
// Tests run from frontend/ (npm test), so resolve paths from the working directory.
const root = process.cwd();
const css = readFileSync(`${root}/src/styles.css`, 'utf8');
const componentDir = `${root}/src/components`;

/** Collect every static class name used in component JSX. */
function componentClassNames(): Set<string> {
  const names = new Set<string>();
  for (const file of readdirSync(componentDir).filter((f) => f.endsWith('.tsx'))) {
    const source = readFileSync(`${componentDir}/${file}`, 'utf8');
    for (const match of source.matchAll(/className="([^"]+)"/g)) {
      match[1].split(/\s+/).forEach((name) => names.add(name));
    }
  }
  return names;
}

function hasRule(selector: string): boolean {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`\\.${escaped}(?![\\w-])`).test(css);
}

describe('colorful theme (FR-001, FR-002)', () => {
  it('defines the palette as custom properties on :root', () => {
    const required = ['--caramel', '--berry', '--ocean', '--mint', '--violet', '--ink', '--danger'];
    for (const variable of required) {
      expect(css).toMatch(new RegExp(`${variable}:\\s*#[0-9a-f]{3,8};`, 'i'));
    }
  });

  it('has a rule for every static class name used by the components', () => {
    const missing = [...componentClassNames()].filter((name) => !hasRule(name));
    expect(missing).toEqual([]);
  });

  it('styles both speaker roles with distinct accent colors (US1)', () => {
    expect(css).toMatch(/\.message--user\s*{[^}]*--ocean|\.message--user\s*{[^}]*#0ea5e9/s);
    expect(css).toMatch(/\.message--assistant\s*{[^}]*caramel|\.message--assistant\s*{[^}]*#f59e0b/s);
  });

  it('colors the typing indicator and failure text (US2)', () => {
    expect(css).toMatch(/\.message__typing\s*{[^}]*color:\s*var\(--berry\)/s);
    expect(css).toMatch(/\.message__failed\s*{[^}]*color:\s*var\(--danger\)/s);
    expect(css).toContain('@keyframes pulse');
  });

  it('keeps the disabled button state without hover lift (US3)', () => {
    expect(css).toMatch(/button:disabled\s*{[^}]*opacity:\s*0\.5/s);
    expect(css).toMatch(/button:hover:not\(:disabled\)/);
  });
});
