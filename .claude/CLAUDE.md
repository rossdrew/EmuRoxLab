## Plan Mode

- Make the plan extremely concise. Sacrifice grammar for the sake of concision.
- At the end of each plan, give me a list of unresolved questions to answer, if any.

## Verification

After implementing any new code, before marking it complete:
1. Run a full test `./gradlew pitest` and full suite must pass
2. Run a code coverage report `./gradlew jacocoTestReport` and check the generated report at `build/reports/jacoco/test/html/index.html` and where sensible attempt to reach
   - Line coverage ≥ 95%
3. Stop and get confirmation from user on any code that cannot reach that number

## Phase Completion Checklist

After implementing any planned phase, before marking it complete:

1. Run PIT mutation testing `./gradlew pitest`
2. Check the generated report at `build/reports/pitest/index.html`
3. Do not consider the phase complete unless:
   - Line coverage is ≥ 90%
   - Mutation score is ≥ 80%
4. If thresholds are not met, write additional tests before proceeding
