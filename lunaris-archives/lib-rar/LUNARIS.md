# lib-rar in Lunaris Archives

- Origin: https://github.com/d4rken-org/butler, directory `lib-rar/`, merge commit 686c6c01d79bbdaae770b8a95659d46edd8fface (PR #331, 2026-10-21).
- Licence: GNU GPL version 3 (Butler). Per-file notices kept as upstream: libarchive RAR5 port (BSD-2-Clause, Copyright (c) 2018 Grzegorz Antoniak), BLAKE2 (Samuel Neves, CC0 / OpenSSL / Apache-2.0), RAR5 crypto after 7-Zip (Igor Pavlov, LGPL-2.1-or-later).
- Local changes (GPL-3.0 §5a):
  - 2026-11-16: own `build.gradle.kts` (Kotlin JVM, JVM 17, opt-in `kotlin.time.ExperimentalTime`, JUnit Jupiter only for tests; no Kover, no sweep, fuzz or benchmark tasks).
  - 2026-11-16: added `LunarisRarTimes.kt`.
  - 2026-11-16: removed the Jazzer fuzz tests and their seeds (no Jazzer dependency here).
  - 2026-11-16: five libarchive fixtures renamed from `test_read_format_rar5_*.rar` to `rar5_*.rar` (`arm_filter_on_window_boundary`, `bad_window_sz_in_mltarc_file`, `decode_number_out_of_bounds_read`, `invalid_hash_valid_htime_exfld`, `unpacked_size_exceeds_declared`), to keep every path in the repository within 117 characters; `Fixtures.kt` maps libarchive's names to the stored ones, so the tests and `manifest.json` are unchanged.
- Upstream files are otherwise unmodified; the app's file-size and dead-code rules do not apply to them.
- Check for upstream changes: `gh api "repos/d4rken-org/butler/commits?path=lib-rar"`.
