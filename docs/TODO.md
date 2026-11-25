# ThoughtWeave – TODO

Open tasks. Remove an item (or move it to CLAUDE.md as a decision) once it is done.

## App
- [ ] **AI agreement legal review.** After review, remove `STATUS: DRAFT` from both agreement assets and fill in
      the provider, contact and governing-law placeholders (see CLAUDE.md, Consent).

## Exhibition panel (`docs/05_캡스톤디자인_전시회_판넬_ThoughtWeave.pptx`)
Built from `docs/05_캡스톤디자인_전시회_판넬.pptx` (2026-11-18). Screens are Korean emulator captures of sample
memos and schedules seeded into the database; diagrams are PowerPoint shapes. Updated 2026-11-25 with the team
photo (`docs/images/20261125_103559.jpg`), the S02-C fact check, the 6 GB RAM requirement and the S23 Ultra timings.
The filled panel and the photo are gitignored and kept only locally (user decision, 2026-11-25); the panel commit
of 2026-11-18 (45bbce7, no photo) stays in history.
- [ ] **Keep in sync.** Retake the S01–S04 captures after the UI rework, and update the test count (244 on
      2026-11-25), the benchmark figure (188/209 facts) and the timings (CLAUDE.md, Inference) if they change.

## Voice dictation (V1 and V2 done 2026-11-18, checked on the S23 Ultra 2026-11-25; V3 dropped)
- [ ] **Microphone in the legal texts.** Mention microphone use in the agreement at the legal review
      (bump `CURRENT_AGREEMENT_VERSION`), and in the Play Data safety form (audio processed on device, not collected).

## UI (added 2026-11-18)
- [ ] **UI rework.** Rework the screens' look and layout (scope to be decided with the user before starting).
