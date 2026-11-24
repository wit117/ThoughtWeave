# ThoughtWeave – TODO

Open tasks. Remove an item (or move it to CLAUDE.md as a decision) once it is done.

## App
- [ ] **AI agreement legal review.** After review, remove `STATUS: DRAFT` from both agreement assets and fill in
      the provider, contact and governing-law placeholders (see CLAUDE.md, Consent).

## Exhibition panel (`docs/05_캡스톤디자인_전시회_판넬_ThoughtWeave.pptx`)
Built from `docs/05_캡스톤디자인_전시회_판넬.pptx` (2026-11-18). Screens are Korean emulator captures of sample
memos and schedules seeded into the database; diagrams are PowerPoint shapes.
- [ ] **AI times.** Add the PER-04/05 targets and measurements (CLAUDE.md, Inference; requirements document).
- [ ] **Keep in sync.** Retake the S01–S04 captures after the UI rework or dark mode, and update the test count
      (224 on 2026-11-18) and the benchmark figure (188/209 facts) if they change.

## Voice dictation (V1 and V2 done 2026-11-18; V3 dropped)
- [ ] **Real-device check.** Dictate in Korean and English on the Galaxy S23. The emulator's on-device recognizer
      runs but hears no voice, so the flows were tested with a scripted recognizer. Check on the S23: a long dictation
      with pauses (segmented session, no piece inserted twice), the 10 s silence end, the S06 language states and a
      download. Check also that recognition works without the `<queries>` entry; remove it if it is not needed.
- [ ] **Microphone in the legal texts.** Mention microphone use in the agreement at the legal review
      (bump `CURRENT_AGREEMENT_VERSION`), and in the Play Data safety form (audio processed on device, not collected).

## UI (added 2026-11-18)
- [ ] **UI rework.** Rework the screens' look and layout (scope to be decided with the user before starting).
- [ ] **Dark mode.** Support the system dark theme on every screen, including the S05 legal texts and dialogs.

## Phase 6 (on hold since 2026-11-11)
- [ ] **Start Phase 6** when the hold is lifted: Galaxy S23 GPU and performance measurements, full flow tests,
      offline, themes, accessibility, Korean pass.
