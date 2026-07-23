## Plan Mode

- Make the plan extremely concise. Sacrifice grammar for the sake of concision.
- At the end of each plan, give me a list of unresolved questions to answer, if any.

## Verification

After implementing any new code, before marking it complete:
1. Run a full test `./gradlew pitest` and full suite must pass
2. Run a code coverage report `./gradlew jacocoTestReport` and check the generated report at `build/reports/jacoco/test/html/index.html` and where sensible attempt to reach
   - Line coverage ≥ 95%
3. Stop and get confirmation from user on any code that cannot reach that number

## Phase Execution Checklist

1. Create a branch with the pattern `claude/<description of plan>/<phase number>/<phase description>` before making any changes
2. At the end of each step commit the step with a concise git header followed by an empty line then the complete task description from the plan file

## Phase Completion Checklist

After implementing any planned phase, before marking it complete:

1. Run PIT mutation testing `./gradlew pitest` but try limit it's scope to changed files were possible with `./gradlew pitest -PpitestScope=com.rox.apu.*`
2. Check the generated report at `build/reports/pitest/index.html`
3. Do not consider the phase complete unless:
   - Line coverage is ≥ 90%
   - Mutation score is ≥ 80%
4. If thresholds are not met, write additional tests before proceeding
5. Push all commits to the phase branch
6. Create a pull request & seek manual approval to continue
7. Review each comment with user and seek approval before making changes
   1. Address code coverage changes flagged by Codecov
   2. Address coderabbitai comments by evaluating their "Prompt for AI Agents" section, commits should include a desciptive header, a blank line then the descriptive paragraph included in the "Prompt for AI Agents"
   3. Address any other comments made
