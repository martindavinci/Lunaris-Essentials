# Sora Editor + TextMate (tm4e) + oniguruma (joni/jcodings): grammars/themes are loaded from
# assets and resolved partly via reflection — keep these libraries intact.
-keep class io.github.rosemoe.** { *; }
-dontwarn io.github.rosemoe.**
-keep class org.eclipse.tm4e.** { *; }
-dontwarn org.eclipse.tm4e.**
-keep class org.joni.** { *; }
-dontwarn org.joni.**
-keep class org.jcodings.** { *; }
-dontwarn org.jcodings.**

# tm4e plist/yaml parsing pulls SnakeYAML on some setups.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
-keep class org.snakeyaml.** { *; }
-dontwarn org.snakeyaml.**

# commonmark (Markdown preview) + its GFM extensions.
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**
