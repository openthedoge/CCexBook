# CCexbook

An **offline, zero-permission** Android quiz app: import question banks from Excel / Word / plain text, practise sequentially, keep a wrong-answer book, take mock exams, tag and annotate questions, and back everything up locally.

This version is now final. The refactored version is currently and will remain in long-term beta.

> This project is a fork of **紫若灵刷题练习 v1.13.0** by **lssudi**, with many features added and parts of the code refactored. Copyright of the original functionality belongs to the original author; this repository only contains the fork's additions and changes.

---

## Features

### Importing banks
- Supports **xlsx / docx / txt / csv**
- Word tables with inline images are supported — images are extracted automatically and shown with the question
- Preview before import, detect question types, then fix types in bulk or one by one
- Built-in Word / Excel import templates (can be generated inside the app)

### Practising
- Types: single choice, multiple choice, true/false, fill-in-the-blank, short answer, sorting, other
- Modes: sequential, random, recite, focused practice, wrong-answer redo, favourites, mock exam
- **Continue practising** — resumes from the last position directly
- **Custom start question** — digits only; out-of-range input is clamped to the first / last question
- Answer sheet distinguishes “answered in this session” from “answered previously” and jumps to the current question
- Optional option shuffling, auto-advance on correct answers, haptics and sound feedback

### Managing questions
- **Append questions** — add questions to an existing bank from a file or pasted text; insert automatically by category or confirm each question and choose its position
- **Move question** — move a question to any position in any category from the editor
- **Delete question** — long-press a row in “Browse all questions”; the remaining questions are renumbered
- **Reset bank** — long-press a bank on the home screen and choose which parts to clear: practice records / wrong book / tags / favourites (overall statistics are unaffected)
- Question stem, options, answer, analysis, images and category are all editable

### Tags and notes
- **Multi-tag marking** — each bank has its own set (up to 10 tags); a question can carry several tags; the built-in “Other” tag cannot be renamed or deleted; tags support set-default / rename / delete
- Filter questions by **multiple tags**, combined with type and category filters
- **Notes** — after answering a question, a note area appears under the analysis; tap to write a note (cancel / save). Notes belong to the question: deleting the question or the bank removes them

### Statistics
- Home and statistics screens: total answered, accuracy, answered today, streak, last-7-days chart, accuracy per type, progress per bank
- **Overall statistics are a separate ledger** — deleting a bank or clearing practice records does not reset them; only “Clear statistics” in Settings (with double confirmation) can zero them

### Export and backup
- Export **Excel** (no images) or **Word table** (images embedded, editable and re-importable)
- Export an **exam paper** (Word, grouped by type, optional answer sheet), images embedded
- **Full backup / restore** — database and images packed into a zip
- Restore supports append or replace; a snapshot of the current data is kept automatically before replacing
- Older backups are migrated automatically on open (legacy flags become the “Suspect” tag)

### Appearance
- Theme: follow system / light / dark / pure black (AMOLED)
- 5 accent colours: Tech Blue, Chinese Red, Emerald Green, Vibrant Orange, Romantic Purple
- Font size: small / normal / large / extra large

---

## Data and privacy

- **No permissions at all** — no network access, no data collection
- Everything stays on the device: SQLite database plus images in app-private storage
- Uninstalling removes all data (back it up first)

---

## Building

Requirements:

| Item | Version |
|---|---|
| JDK | 17 |
| Gradle | 8.7 (wrapper included) |
| Android Gradle Plugin | 8.5.2 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # release build
./gradlew testDebugUnitTest  # unit tests (Robolectric, 204 cases)
```

> Signing reads from a local `keystore.properties`; the repository contains no keystore or passwords. Create your own before building or publishing.

Package name: `com.ziruoling.quiz.c` — it can be installed alongside the original `com.ziruoling.quiz`.

---

## Question bank format

An import sheet has 11 fixed columns:

| Type | Question | Option A | Option B | Option C | Option D | Option E | Option F | Answer | Analysis | Category |
|---|---|---|---|---|---|---|---|---|---|---|

- Type: 单选题 / 多选题 / 判断题 / 填空题 / 简答题 / 排序题 / 其它题
- True/false answers: 对 / 错 / √ / × / A / B are all accepted
- Multiple choice answers look like `ABD`; sorting answers are the correct letter order, e.g. `CAB`
- Separate multiple reference answers for fill-in questions with `||`
- A leading number in the stem (e.g. `5.`) is recognised as the original question number, shown as a badge in practice and written back on export
- Insert images directly into the “Question” cell

Text and CSV use the same layout, one question per line.

---

## Version history

| Version | Summary |
|---|---|
| fork-v1.0 – fork-v1.3 | Early forks of the original: save answer state, remove the AI feature, sharing, pure-black theme, document parsing, question editor, focused practice, answer sheet, four-way share menu, changelog page |
| v1.4.0 | Unified app name and version number after extensive changes (CC练习本 / 1.4.0) |
| v1.5.0 | Multi-tag system with tag filtering, faster bank queries and image handling, partial refactoring |
| v1.6.0 | Per-question notes |
| v1.6.1 | Notes appear only after answering; wording and changelog refinements |

For future updates, tap the home icon.

---

## Known limitations

- Notes are not searchable or exported; they only travel with the question and the backup
- Sorting and short-answer questions are not auto-graded
- Excel export contains no images (use Word export or a full backup for images)

---

## Credits

- Original author: **lssudi**
- Fork (secondary creation) by: **CC**

Issues and pull requests are welcome.
