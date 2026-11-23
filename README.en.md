# CC Exercise Book (CCexbook)

[简体中文](./README.md) | **English**

An **offline-only, zero-permission** Android quiz/practice app: supports importing question banks from Excel / Word / text files, sequential practice, wrong-answer book, mock exams, tags and notes, data backup, and lets you write notes for each question.

> This project is a derivative work based on **紫若林刷题练习 v1.13.0** (original author: **lssudi**). Following the original author's open attitude, it has undergone extensive feature additions and refactoring. Copyright for the original features belongs to the original author; this repository only contains the modifications after forking.

---
**This is already a qualified local quiz/practice app. The next few update plans will start in mid-2027; it is currently in hibernation (typing is tiring and cold in winter). In the meantime, bugs in the current version will continue to be fixed. If you have better requirements or suggestions, feel free to let me know.**

## Features

### Question Bank Import
- Supports **xlsx / docx / txt / csv** formats
- Word tables with embedded images can be imported directly; images are automatically extracted and displayed with the questions
- Preview before import, detect question types, and assign question types in bulk or one by one
- Built-in Word / Excel import templates (can be generated and exported in-app)

### Practice
- Question types: single choice, multiple choice, true/false, fill-in-the-blank, short answer, sorting, other
- Modes: sequential practice, random practice, study mode, targeted practice, wrong-answer retry, favorite practice, mock exam
- **Resume practice**: pick up directly from your last progress, no need to reselect each time
- **Custom starting question number**: accepts only numbers; out-of-range values automatically jump to the first / last question
- Answer sheet: distinguishes "answered this session" from "answered historically", and auto-locates the current question
- Option order can be randomized; auto-advance to the next question on correct answers; vibration and sound can be toggled

### Question Management
- **Append questions**: insert questions into an existing question bank, via file or direct paste; can be auto-inserted by category, or confirmed one by one with a specified position
- **Move questions**: in the editor, move a question to any position in any category
- **Delete questions**: long-press in "Browse All Questions" to delete; question order automatically shifts forward afterward
- **Reset question bank**: long-press a question bank on the home page to selectively clear practice records / wrong-answer book / tags / favorites (overall statistics are unaffected)
- Question stem, options, answers, explanations, images, and categories are all editable

### Tags and Notes
- **Multi-tag system**: each question bank has its own independent set of tags (up to 10); a question can carry multiple tags; the built-in "Other" tag cannot be deleted or modified; supports "set as default / rename / delete"
- Within a question bank, filter by **multiple selected tags**, combined with question type and category filters
- **Notes**: after answering a question, a note area appears below the explanation; tap to write a note for the current question (cancel / save); notes follow the question and disappear when the question or question bank is deleted

### Statistics
- Home page and statistics page: cumulative questions answered, accuracy, questions answered today, consecutive check-in days, last 7 days bar chart, accuracy by question type, progress by question bank
- **Overall statistics are an independent ledger**: deleting question banks or clearing practice records will not reset them; only "Clear Statistics" in settings (with double confirmation) can zero them

### Export and Backup
- Export to **Excel** (no images) or **Word tables** (images embedded; text can be edited and imported back into the question bank)
- Export **exam papers** (Word, grouped by question type, optionally with answers and explanations), with images embedded
- **Full backup / restore**: database + images packaged as a zip
- Restore supports "append import" and "overwrite replace"; before overwriting, a "snapshot of original data" is automatically saved
- Compatible with old-version backups: automatic migration on open (old marks are automatically converted to the "Questionable" tag)

### Appearance
- Theme: follow system / light / dark / pure black (AMOLED)
- 5 theme color schemes: Tech Blue, China Red, Emerald Green, Vibrant Orange, Romantic Purple
- Font size: small / standard / large / extra large

---

## Data and Privacy

- **No permissions at all**; no internet access, no data collection
- All data is stored locally: SQLite database + images in the app's private directory
- Uninstalling deletes all data (please back up on your own)

---

## Build

Environment requirements:

| Item | Version |
|---|---|
| JDK | 17 |
| Gradle | 8.7 (wrapper included in the repo) |
| Android Gradle Plugin | 8.5.2 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |

```bash
./gradlew assembleDebug      # Debug build
./gradlew assembleRelease    # Release build
./gradlew testDebugUnitTest  # Unit tests (Robolectric, 180+ cases)
```

The app package name is `com.ziruoling.quiz.c`, so it can be installed alongside the original `com.ziruoling.quiz`.

---

## Question Bank Format

Import tables have 11 columns, with a fixed column order:

| Question Type | Question | Option A | Option B | Option C | Option D | Option E | Option F | Answer | Explanation | Category |
|---|---|---|---|---|---|---|---|---|---|---|

- Question type: Single Choice / Multiple Choice / True-False / Fill-in-the-Blank / Short Answer / Sorting / Other
- True/false answers: 对 / 错 / √ / × / A / B are all acceptable
- Multiple-choice answers such as `ABD`; for sorting questions, enter the correct sequence of letters (e.g. `CAB`)
- Separate multiple reference answers for fill-in-the-blank with `||`
- A leading number in the question stem (e.g. `5.`) is recognized as the "original question number", displayed as a corner badge on the practice page and written back to the start of the line on export
- Images can be inserted directly into the "Question" cell

Text and CSV formats work the same way: one question per line.

---

## Versions

| Version | Description |
|---|---|
| fork-v1.0 ~ fork-v1.3 | Early changes based on the original: save answered state, remove AI, sharing, pure black theme, document parsing, question editing, targeted practice, answer sheet, choose one of four sharing options, changelog page |
| v1.4.0 | Due to too many changes, unified the app name and version number (CC Exercise Book / 1.4.0) |
| v1.5.0 | Multi-tag system, tag filtering, question bank query and image display optimization, partial code refactoring |
| v1.6.0 | Added question notes |
| v1.6.1 | Notes now only shown after answering; copy and log revisions |
| v1.7-1.9 | Further feature improvements and modifications, overall UI changes |

---

## Known Limitations

- **Multilingual support is incomplete**: the current UI is fixed in Chinese; English resources and the translation framework have been reserved (see `开发备注-多语言方案.md`)
- Notes are not included in search or export; they are only saved with the question and in backups
- Sorting and short-answer questions do not participate in automatic grading
- Exported Excel does not contain images (use Word export or full backup if you need images)

---

## Acknowledgements

- Original author: **lssudi**
- Modifications in this project: **CC**

If you find this useful, feel free to submit an Issue or PR.
