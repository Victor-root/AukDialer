# Translating Rivo

Thank you for helping translate Rivo into your language.

## Where translations live

- Source strings: [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml)
- Translations: `app/src/main/res/values-<locale>/strings.xml`
  (for example `values-pl-rPL/strings.xml` for Polish, `values-de-rDE/strings.xml`
  for German)

Any string not yet translated for a locale automatically falls back to the
English source, so a partial translation is fine and will never break the app.

## Contributing a translation

1. Copy `app/src/main/res/values/strings.xml` to
   `app/src/main/res/values-<locale>/strings.xml`.
2. Translate the text inside each `<string>` element. Leave the `name`
   attributes unchanged and keep any format placeholders (`%1$s`, `%d`, `\n`,
   `\'`) intact.
3. Do not translate strings marked `translatable="false"`.
4. Open a pull request.

New locales are picked up automatically. `generateLocaleConfig` is enabled, so
the language appears in the system per-app language picker without any extra
wiring.
