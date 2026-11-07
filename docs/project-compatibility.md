# Project compatibility

Status: `.lynx` and `.radiant` use the same native serializer. API 36 instrumentation verifies native `.radiant` save/reopen and 137/143 BPM synthetic FLP imports followed by a `.radiant` save/reopen with 137 BPM preserved. API 29 and API 36 instrumentation drive Android's CreateDocument and OpenDocument pickers for both `.lynx` and `.radiant`, read each selected `content://` URI back, and load the bytes through the native project loader. A manual API 36 SAF flow also created, reopened, and resaved `Untitled.radiant`; DocumentsUI imported the 27-byte fixture and the saved provider document reopened at 137 BPM. Representative FLP compatibility remains outstanding.

## Existing formats

- `.lynx`: versioned JSON project (`format` is `lynx-project`, current version 1); serialized through `ProjectFile::toJson/fromJson` and saved via a temporary file before replacement.
- `.flp`: parsed by `src/flp/Flp.cpp` and imported by `src/core/FlpImport.cpp`. Import can translate supported native plugin state and can keep unresolved foreign-plugin state. User-specific FL factory data and samples may still be missing.
- Project packages: ZIP operations in `src/engine/ProjectPackage.cpp` assume local `juce::File` paths.

## Android coverage still needed

- Test `.lynx` and `.radiant` read/write interchange across desktop and Android and preserve schema/version fields. Android SAF round trips now cover both extensions, but the synthetic empty-project files do not cover populated projects or desktop interchange.
- Test SAF open/save/import with revoked grants, provider failures, interrupted copy, and multiple providers.
- Stage URI-backed samples into app-private cache where APIs need random access; sample-folder permissions and missing-media relinking remain incomplete.
- Keep plugin IDs, raw state, routing, and automation when no Android plugin exists.
- Test atomic/recoverable saves, autosave, and recovery across process death and provider failure.

Native version 1 `.radiant` serialization and reload are verified. Android's staged FLP path is verified only with an empty project and a one-event tempo fixture. Real FLPs with patterns, instruments, effects, automation, plugin state, and samples; packaged project ZIP behavior; large files; recovery across process death; and cross-version desktop interchange are not yet validated on Android.
