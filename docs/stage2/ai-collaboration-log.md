# AI human collaboration log

Dev Parikh, 219957794. EECS 3311 Software Design, Stage 2.

Repository: https://github.com/devparikh28/EECS-3311-PROJECT---SPOTTER

## What this document is

Stage 2 asks for a representative log of the collaboration rather than a transcript, so this is one entry per working session, nine in all, covering ninety four commits between 28 September and 4 November 2026. Each entry says what was asked for, what the model produced, what I did with it, and what actually ended up in the repository.

The sessions are listed with the dates and commit ranges they correspond to, so any entry can be checked against the history. Where the model's first answer was wrong or was rejected, the entry says so, because those are the parts of this log that are worth reading.

The tool was Claude, used through Claude Code and the Claude desktop application. The model identifiers, the token counts and the money spent are in `ai-tools.md` alongside this file.

## A note on how the code was reviewed

I did not accept code because it compiled. The pattern across every session was the same: the model proposed, I read the proposal against the Stage 1 design, and anything that did not match the design or did not match how the rest of the system already worked went back. The tests are the record of that. Five hundred of them exist, and several were written because I disagreed with an assertion the model had made about its own code.

The clearest example is in session seven. The model wrote a test asserting that a failed validation message said "4 weeks" when the message the code produced said "4 were asked for". The test was wrong rather than the code. I made it fix the test rather than the message, because the message was the better wording.

The opposite also happened, in session eight. Two repository tests saved a macro target for a profile that did not exist, and passed only because the model had written the test to expect that. The foreign key was correct and the tests were wrong. I had it create the profile first and add a third test asserting that the database refuses an orphaned target, which is the behaviour I actually wanted guaranteed.

---

## Session 1, 28 September 2026

Commits `817e956` to `f680c19`, nineteen commits.

**Task.** Produce the Stage 1 design: features, class diagram, patterns, use cases and sequence diagrams.

**Asked for.** A design for an AI strength training and nutrition coach, built around the principle that the model proposes and deterministic code disposes.

**Produced.** The initial feature set, the five layer architecture, the pattern selection and the first diagrams.

**Reviewed and changed.** Four substantive redirections in this session, each visible as its own commit. The stack was switched to Java 21, JavaFX and LangChain4j (`a21a252`) rather than what was first proposed. The meet date was made optional and a training goal added (`fe76eae`), because a design that assumed every user was preparing for a competition excluded most lifters. The training split was made explicit and stable (`d82c9ca`). And the design was extended to work with any tracking application and with none at all (`68ae2c1`), which is what later became the generic CSV import path rather than a Hevy only one.

Three features were added by me after reading the first draft: F12 plan adherence and progression (`182cebb`), then F13 guided onboarding and F14 adopt an existing programme (`971ed28`). The first draft had eleven features and stopped at generating plans for lifters who already had training history, which left a new lifter with nothing and a lifter with an existing programme unable to bring it.

**Landed.** The fourteen feature design, eight patterns, and the diagrams, as submitted for Stage 1.

---

## Session 2, 30 September 2026

Commits `1f6e68e` to `0e15ec8`, fifteen commits.

**Task.** Turn the Stage 1 material into one submittable report.

**Produced.** The consolidated report, the title block, the appendices and PNG plus UXF exports of every diagram.

**Reviewed and changed.** Most of this session was me correcting the report rather than the design. The architecture diagram was redrawn as stacked layer bands (`f7efaa4`) because the first version implied dependencies between layers that the design does not have. The agent and tool counts were wrong and were corrected (`ceed30b`). A duplicated note about the optional meet date was removed (`4cbfddb`). The repository was flattened to README, gitignore and docs (`f88dc47`) because the generated artefacts had accumulated.

**Landed.** `docs/Spotter_Stage1_Report.md` and the diagram exports.

**Carried forward.** I recorded a warning to myself at the end of this session, in the Stage 2 plan: running a diagram generator overwrites hand edits without warning, so pick one workflow and stay in it. That warning turned out to matter, because the class diagram has since drifted from the code and is on the outstanding list.

---

## Session 3, 21 and 22 October 2026

Commits `1b2cf81` to `8705805`, five commits.

**Task.** Slice 0 of the build plan: the skeleton.

**Asked for.** Maven, the package layout, the SQLite schema, the repository interface, configuration loading and a command line interface that runs and does nothing.

**Produced.** All of it, close to the plan.

**Reviewed and changed.** One decision was mine and is recorded as design change 11. Configuration loading was first written to read a file from a fixed location. I had it take `--env` on every command instead, because the tests needed to point at a temporary database and a fixed path would have meant the test suite writing into the working directory. That decision is why `JsonOutputTest` can run fifteen commands against a temporary database today without touching anything real.

Configuration loading was also made to fail safely rather than throwing (`162a0be`), so that a missing key produces a command that explains what is missing rather than a stack trace.

**Landed.** The Maven build, the schema, `Repository<T>`, `SpotterConfig`, the picocli interface and `StatusCommand`, with tests for configuration loading (`2c9ac7e`).

---

## Session 4, 25 and 29 October 2026

Commits `e583b5b` to `f4f6e0e`, sixteen commits.

**Task.** Slices 1 and 2: the domain, then the infrastructure that feeds it.

**Asked for.** The entities and the pure calculations, with no model and no database involved, then the CSV adapters.

**Produced.** `StrengthAnalytics`, `RPEChart`, `AttemptCalculator`, `MacroTracker`, `RecoveryRuleEngine`, the training block structure, and then the Hevy and Whoop adapters.

**Reviewed and changed.** This is the session where real data overruled the design, twice, and both commits say so in their own messages. `dd73edb` is titled "Read real Hevy exports, and fix what real data exposed": the adapter was written against the format as documented and broke on an actual export. `f4f6e0e` fixed a crash on re importing the same file, which only appeared because I imported the same export twice rather than once.

I also rejected the first version of the recovery rules. It evaluated readings and produced an adjustment directly. I had it split so that `RecoveryRuleEngine` decides whether a day is flagged and a separate deterministic fallback decides what to do about it (`95c997f`), because the decision to reduce training has to belong to the rule engine rather than to the model, and keeping them in one method made that impossible to enforce later. The Today screen still disables its Ease off button when nothing is flagged for exactly this reason.

**Landed.** The domain layer with its tests, the three adapters, `DataSourceFactory`, the exercise catalogue, workout storage and `ImportCommand`.

---

## Session 5, 29 October 2026

Commits `3e15fdf` to `8687a15`, four commits.

**Task.** Slice 3: the agent loop, proven against a scripted client with no API calls at all.

**Asked for.** `CoachAgent` as a Template Method, the prompt builder, the response parser, the validator, the tool manager and the traces, all unit testable without a network.

**Produced.** The agent core, the Claude client with its caching and recording decorators, the progression strategies and their factory, the parser and the validator.

**Reviewed and changed.** Two decisions here are recorded as design changes 1 and 12.

The first: the validator was initially one class validating everything, including the onboarding profile draft. I had the profile validation split into `ProfileValidator`, because a draft profile and a training plan have nothing in common except that both come from a model, and one class validating both would have been a class with two unrelated reasons to change.

The second, and the more important one: validation was first called from the command layer, after the agent returned. I moved it inside the agents. The reason is that a rejected proposal has to go back to the model with the reason attached so it can correct itself, and that retry loop can only live where the conversation lives. This is why `CoachAgent.run()` is final: the retry and validation sequence is the part no agent is allowed to reimplement.

**Landed.** `CoachAgent`, `PromptBuilder`, `ResponseParser`, `PlanValidator`, `ProfileValidator`, `ToolManager`, `AgentTrace`, `ClaudeClient` and the two decorators.

---

## Session 6, 2 November 2026

Commits `8efc6b0` to `2c31026`, twenty seven commits. The longest session.

**Task.** Slices 4, 5 and 6: the first real agent, then the graphical interface, then the remaining agents.

**Asked for.** `BlockGenerationAgent` end to end, then the JavaFX dashboard, then the other agents now that the template existed.

**Produced.** Seven agents, eight dashboard panels, the memory layer, nutrition storage and the cost report.

**Reviewed and changed.** One thing I pushed back on, and two I was argued into.

The limits in the validator were first set against the test catalogue rather than against anything real, which is design change 4. Twelve sets, thirty reps, seven sessions a week: numbers chosen because the tests passed with them. I had them reset against what a plan for an actual lifter can contain, and the reasoning written down, because a limit nobody can explain is a limit nobody can change safely.

The two memory decisions went the other way, and this entry says so because a log that credits the human for everything is not worth reading. I had assumed the chat coach would persist the conversation, which is what a chat feature normally does. The assistant proposed not storing it: persist facts the lifter states and nothing else, through `MemoryManager`, on the argument that storing a conversation means storing everything the model said, including whatever it got wrong, and then reading it back later as though it were true. I accepted that, and it is design change 8.

The memory gate, design change 9, came from the same proposal and I accepted it for the same reason: the thing writing to memory is the same thing that might hallucinate the fact, so what gets remembered has to be gated rather than taken at face value.

Both were good calls and neither was mine. What I did do was make them explicit in the design changes log rather than leaving them as implementation detail, which is why they can be checked now.

**Landed.** `BlockGenerationAgent`, `SubstitutionAgent`, `AdjustmentAgent`, `AttemptRationaleAgent`, `MealSuggestionAgent`, `OnboardingAgent`, `ChatAgent`, the eight panels, `MemoryManager` and the usage report.

---

## Session 7, 2 November 2026

Commits `d007d15` to `e641653`, six commits. The first session against the real API.

**Task.** Make the agents work against the live model rather than against prepared replies.

**Asked for.** An API key configured, and the agents run for real.

**What went wrong, in order.** This session is the most useful one in this log, because almost everything in it was a correction.

The configured model identifiers were retired models. `claude-3-5-haiku-20241022` was retired in February 2026 and `claude-3-5-sonnet-20241022` in October 2025. The model had suggested identifiers from its own training rather than checking. I made it look up the current ones and fix the configuration (`3ebec0f`), and that is design change 10.

Replacing them with current models then produced an unparseable reply, because the client library version in use cannot read the thinking content block the newer models emit. Fixed by pinning model versions the client can actually read, with the reason written into `.env.example` so the next person does not undo it (`2e8ae26`).

Then a real mistake on the model's part, which I want recorded because it is the one that cost me the most time. It told me it had edited `.env` to the pinned models when it had not made the edit at all. I ran the command again and hit the identical error. When I pointed this out it apologised and made the edit for real. The lesson I took from it is the one the course instruction is making: the output has to be inspected, including the claim that something was done.

Three genuine bugs in our own code were found by running it for real. An agent that ran out of tool rounds returned an empty string and reported it as an unreadable reply from the model, blaming the model for our own limit; fixed by having it make one more call with no tools offered (`7f4bcdd`). The client cached models by name but fixed the token limit at build time, so a four week block generation silently got the default limit and was truncated; fixed by caching per name and limit (`e641653`, design change 5). And a load of 66.0 kg was refused against a range printing as 66.0 to 76.7, because the true bound was 66.04; fixed with a tolerance at the range boundary (design change 6).

One change came from watching the model fail rather than from a bug. It kept prescribing loads outside the allowed range because it was guessing at what the range was. I had a `LoadRangeTool` added so it can ask instead (`e641653`, design change 3). It then generated a four week block, was refused for four close grip bench loads, called `allowed_load`, corrected itself and passed.

**Landed.** Working live agents, at a measured cost of roughly twenty four cents for nine calls. Six design changes.

---

## Session 8, 3 November 2026

Commits `ef87e39` to `6f6b421`, eight commits.

**Task.** Write the design changes log and the feature to implementation mapping, then close whatever the mapping exposed.

**Produced.** `design-changes.md` with thirteen entries and `feature-to-implementation.md` covering the features.

**Reviewed and corrected.** This session contains the correction I am most glad I made.

The first mapping said the design had eleven features plus three added in Stage 2. That was wrong, and I said so: Stage 1 had fourteen features. The model had assumed the three features I added late in session one were Stage 2 additions rather than part of the submitted design. I then told it to check against what was actually pushed to GitHub rather than against its own notes. It read the submitted Stage 1 report, confirmed fourteen features and eight patterns including Decorator, and corrected the mapping (`ca2c2db`, `b4485e8`).

Reading the real feature specifications then exposed three gaps that the mapping had described as complete, which is exactly what a mapping is for. Those were closed in `604076e`.

I also reversed a decision. F14, adopting an existing programme, had been dropped, and I asked why. The reason given was that it was the hardest feature and the schedule was tight. I judged that there was enough time, said every feature should be included, and then set the governing instruction for the rest of the project: everything from Stage 1 gets built and nothing is left out. F14 landed the same day (`4957fa0`), F12's revision half landed the same day (`0c78fc0`), and F02's missing halves the same day (`6f6b421`).

**Landed.** Both documents, the three closed gaps, and three features completed.

---

## Session 9, 4 November 2026

Commits `8b18e6e` to `d8fe005`, four commits.

**Task.** Finish everything still unbuilt from Stage 1.

**Asked for.** F14's remaining import routes, then the Command pattern, then Observer, then the JSON output for the Stage 3 harness.

**Produced.** All four, with tests, taking the suite from 462 to 500.

**Reviewed and changed.** Two of these I asked to be built as real behaviour rather than as pattern exercises, and the results differ because of it.

The Command pattern is tied to an actual use: the undo stack exists for the case in the Stage 1 walkthrough, where a lifter eases off a session in the morning and wants it back in the evening. The commands return a new block rather than mutating the one they were given, which makes undo a matter of keeping the old value rather than of reversing arithmetic.

Observer changed existing behaviour rather than adding structure beside it. Panels used to refresh on every visit. They now refresh when something they display has actually changed. And when I asked for the full event set, the model argued back that two of the five events I expected would have no publisher, because logging a set and importing recovery readings happen in a separate process that a bus inside the window cannot hear. I accepted that. The enum has three values and the reason is in the documentation, which is the right outcome: an event bus with events nobody fires would have been the pattern for its own sake that the lectures warn about.

**Landed.** F14 complete, Command, Observer, `--json` on eight commands, design changes 14, 15 and 16, and five hundred passing tests.

---

## What I would do differently

Three things.

I should have kept this log as the work happened rather than reconstructing it from ninety four commits. The Stage 2 plan I wrote on 30 September says exactly that, in those words, and I did not follow it. The reconstruction held up: the commit messages are detailed and the design changes were recorded at the time, so going back through the earlier sessions I could confirm every decision in them but one, where the assistant had written up the memory design as my call and it was in fact its proposal that I accepted. That entry is corrected. But it took a session to write, and one misattribution out of six is a reminder that this is not a document anyone should be reconstructing.

I should have run against the real API earlier. Session seven found six real problems in under two hours, three of them in our own code and three in assumptions about the model. Every one of them would have been cheaper to find in session five than in session seven.

And I should have checked the submitted Stage 1 report against the implementation sooner. Doing it in session eight found three features described as complete that were not, and found that four statuses in the mapping were more generous than the code deserved. A week earlier that would have been a schedule correction rather than a scramble.
