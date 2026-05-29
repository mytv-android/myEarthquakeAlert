# Project: myEarthQuakeAlert

Android earthquake alert app built with Jetpack Compose, supporting phones, tablets, and Android TV.

## Pre-commit Rules

- **Always run a comprehensive static check before committing.** This includes:
  1. Check all `values*/strings.xml` for duplicate string name attributes
  2. Check all `.kt` files for missing imports (especially `android.app.Notification`, `android.content.Intent`, etc.)
  3. Verify all `R.string.*`, `R.drawable.*` references resolve to existing resources
  4. Check for XML syntax errors and mismatched tags
- Do NOT commit if any build-breaking issue is found. Fix first, then commit.
