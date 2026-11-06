# Stage 2: important design changes

Dev Parikh, 219957794
EECS 3311 Software Design, Fall 2026
Project: Spotter

This records where the Stage 2 implementation
departs from the Stage 1 design, and why. Every
entry was written at the time the decision was
made, and each one names the commit that carried
it so the claim can be checked against the code
rather than taken on trust.

Changes fall into three kinds. Some are design
decisions that only became visible once the code
existed. Some are corrections forced by real
data, which is a category Stage 1 could not have
anticipated because Stage 1 had no data. The rest
are constraints imposed from outside, such as a
library or a model that changed under the
project.

---

## Summary

| # | Original design | Change | Reason |
|---|---|---|---|
| 1 | One `PlanValidator` gates every agent | Added `ProfileValidator` as a separate class | Judging loads, meals, explanations and profiles in one class is four responsibilities sharing a name |
| 2 | Agent produces a `LifterProfile` | Agent produces a `ProfileDraft` | A profile fills in defaults, so an unanswered question becomes an invented fact |
| 3 | Exercise names resolved at parse time only | Loads also checked through a tool before the model answers | Rejection teaches one attempt at a time; a lookup means it never has to be told |
| 4 | `MAX_TOOL_ROUNDS` 4, `MAX_RETRIES` 1 | 6 and 2 | Both limits were chosen against a test catalogue of two exercises |
| 5 | Model client built once per tier | Built per tier and reply length | A cached model fixed the reply length for every agent after the first |
| 6 | `Range.contains` exact | Allows 0.05 kg | A bound of 66.04 printed as 66.0 and refused 66.0 |
| 7 | Limitations avoid movement patterns | Also avoid named exercises | The domain supported both since Stage 1; the schema did not |
| 8 | Conversation persisted | Conversation held in memory | A stored conversation is a transcript, a different feature with different consequences |
| 9 | Memory notes free form | Notes refused when written in the coach's voice | A coach that remembers its own advice as the lifter's words cannot be corrected |
| 10 | Model identifiers fixed in config defaults | Updated twice, now pinned with an explanation | The identifiers written in Stage 1 were retired before Stage 2 called them |
| 11 | `spotter.db` beside the working directory | Unchanged, but `--env` added throughout | Running the GUI and the CLI against different databases was too easy |
| 12 | Validation called from the command layer after the agent returns | Validation moved inside the agents | A rejected proposal has to go back to the model with the reason attached, and the retry loop can only live where the conversation lives |
| 13 | Competition lift load checks apply to any exercise mapped to a lift | Known limitation, fix identified, not yet made | A close grip bench is held to competition bench loads, which is wrong |
| 14 | Undo stack, lifetime unspecified | Held in the window, bounded at twenty edits | The case it exists for happens inside one sitting, and a persisted history is a feature nobody asked for |
| 15 | Panels refresh every time they are opened | Panels refresh when something they display has changed | Walking the navigation column re-queried the database for figures nothing had touched |
| 16 | Commands print prose | Eight commands also take `--json` | Stage 3 drives the application from a harness, and scraping prose would make every wording change a breaking change |
| 17 | `CoachController` wraps every agent, one method each | Facade covers the repositories and deterministic services only | The agents take different inputs, so a method per agent would be a second interface kept in step by hand |
| 18 | F13 as an eight question adaptive interview | A single paragraph read into a draft | Eight adaptive questions is eight model calls and eight screens for the same outcome |
| 19 | No check that a prescribed load was above zero | A movement needing equipment is refused at zero load | A real run prescribed Romanian deadlifts at 0.0 kg and the gate passed it |
| 20 | Import, profile editing and a light theme existed only as commands or not at all | The window gained an Import screen, an editable profile form and a theme toggle | Stage 1 put import buttons in the GUI; a lifter should not need a terminal to bring in their data |

---

## 1. A second validator rather than a larger one

**Commit** `8b5affc`

Stage 1 described `PlanValidator` as the gate, in
the singular, and the first three agents fitted
that description. By the time onboarding was
built the class was judging training loads, meal
portions and the figures quoted in an
explanation, and a profile check would have been
a fourth unrelated thing behind one name.

`ProfileValidator` is a separate class because
nothing `PlanValidator` knows helps with a
profile. The gate as a concept survives; the
single class does not. The remaining
inconsistency is honest and recorded here: the
meal and rationale checks are still methods on
`PlanValidator` and would be better extracted the
same way, which was not done for lack of time
rather than because it is right.

## 2. A draft, not a profile

**Commit** `8b5affc`

Onboarding was designed to read a paragraph and
produce a `LifterProfile`. The problem only
became visible once `LifterProfile` existed: it
fills in sensible defaults, so a lifter who never
said how often they train silently becomes one
who trains four days a week.

`ProfileDraft` can be incomplete and knows what
is missing, phrased as questions a person could
be asked. `toProfile` refuses to build from an
incomplete draft. The difference matters because
an invented training frequency would shape every
plan the lifter is ever given, and they would
have no way of knowing a number they never
supplied was driving their training.

## 3. Letting the model ask instead of guessing

**Commit** `b5e09b2`

Stage 1 said the model proposes and deterministic
code disposes, and the validator was the only
place that line was drawn. The first real four
week generation showed what that costs. The model
produced three sound plans and had every one
refused for loads outside the allowed range,
nearly always too light, because it was guessing
what an RPE implies, which is a lookup table it
cannot see. The gate was the first time it found
out.

`LoadRangeTool` lets it ask before proposing, and
reads the same `ProgressionStrategy` the
validator checks against, so the tool and the
gate cannot disagree. The design principle is
unchanged; what changed is the realisation that a
gate which only refuses is a slow teacher.

## 4. Limits set against a test catalogue

**Commits** `b707614`, `883b485`

`MAX_TOOL_ROUNDS` was four and `MAX_RETRIES` was
one. Both were chosen while the only catalogue in
existence was a test fixture holding two
exercises. Against a real catalogue, writing a
block for three lifts means asking about three
lifts and then about the movements available for
each, which is six exchanges before any thinking
starts. One retry assumes the first answer is
nearly right, which is true for choosing a
substitute and false for writing four weeks of
training.

They are now six and two, and both are still hard
limits rather than loops, because an agent that
argues until something passes is how a bill is
run up without anyone noticing.

Raising the retry limit broke twenty four tests
that had queued exactly two replies. That was the
right failure: they were pinned to the old limit
rather than to the behaviour. `ScriptedLLMClient`
gained `alwaysReply`, and the tests that count
calls now state the expected count as
`MAX_RETRIES + 1`.

## 5. The reply length bug

**Commit** `b5e09b2`

`ClaudeClient` cached a model per tier, and the
library fixes the maximum reply length when the
model is built rather than per call. So the first
agent to run decided the reply length for every
agent after it. Block generation asked for 16384
tokens, silently received the configured 4096,
and produced JSON cut off partway through, which
the parser then reported as an unreadable reply.

This is worth recording as more than a bug. For
two attempts in a row the failure was presented
as the model being unreadable, when the model had
done nothing wrong and the ceiling was ours. A
system that blames the model for its own limits
is a system whose error messages cannot be
trusted, and the trace was what made it visible.

## 6. Slack at the range boundary

**Commit** `b5e09b2`

`Range.contains` was exact. A computed bound of
66.04 kg printed as 66.0, the model proposed
66.0, and the rejection message read that the
load must be between 66.0 and 76.7. A refusal
nobody can see the sense of is worse than a
hundredth of a kilo of slack, so `contains` now
allows 0.05 kg, which is well under the lightest
plate anyone owns.

## 7. Limitations that name exercises

**Commit** `0d362b5`

`Limitation` has carried both `avoidPatterns` and
`avoidExercises` since Stage 1, but the schema
had a `limitation_pattern` table and no
equivalent for exercises, so half the domain
could not be stored. Found while writing the
profile repository, fixed by adding
`limitation_exercise`.

## 8. Conversations are not stored

**Commit** `e6109f9`

Stage 1 listed conversation history alongside the
other persisted entities. On reflection it does
not belong there. A conversation matters until
the window closes, and anything in it worth
keeping becomes a memory note instead. Writing
every exchange to the database would be a
transcript, which is a different feature with
different privacy consequences and one nobody
asked for.

The history is also capped, for a practical
reason: every turn is sent to the model again on
the next message, so an uncapped history is a
bill that grows with the conversation and
eventually a request too large to send.

## 9. The memory gate

**Commit** `e6109f9`

Stage 1 described a `MemoryManager` without
saying what it was for. Building it answered the
question. The rule that earns its place refuses
any note written in the coach's own voice,
because a model that records its own suggestion
as though the lifter had said it will, three
conversations later, be certain they asked for
something they never mentioned, and nothing
downstream can detect that once the note is
stored.

## 10. Model identifiers

**Commits** `9849c0d`, `8b3776a`

The identifiers written into the configuration
defaults during Stage 1 were
`claude-3-5-haiku-20241022` and
`claude-3-5-sonnet-20241022`. Both were retired
before the project ever called a real model, in
February 2026 and October 2025 respectively.
Nothing noticed, because every test runs against
`ScriptedLLMClient` and the first real call was
made in November.

The replacements chosen first,
`claude-haiku-5-5` and `claude-sonnet-5-5`,
failed differently: they return a thinking
content block that the pinned LangChain4j version
does not know how to deserialise. The defaults
are now `claude-haiku-4-5-20251001` and
`claude-sonnet-4-6`, with a comment in
`.env.example` explaining why, so the next person
to look does not simply bump them and get a parse
error with no explanation.

The proper fix is to replace `ClaudeClient` with
direct calls to the Messages API, which is one
class, because nothing else in the project
imports LangChain4j. That was a Stage 1 decision
justified on exactly these grounds, and this is
the first time the claim has been tested rather
than asserted. It has not been done, because
configuration solved the problem and the time was
better spent elsewhere.

## 11. Configuration file on every command

**Commit** `04dd527` and throughout

Every command takes `--env`. The database path
lives in that file, so the GUI and the CLI read
the same configuration and therefore the same
database. Without it, running the dashboard from
one directory and a command from another produces
two databases and a great deal of confusion about
why an import did not appear.

## 12. Validation moved into the agents

**Commits** `31f4e98`, `28b71b7`, `606712b`

Stage 1 put every check on `PlanValidator`:
`validateSubstitution`, `validateProgression` and
the rest. In the implementation the substitution
and adjustment checks live in their own agents
instead, as `validate` overrides on the template.

The reason is that the template already demands a
validate step from every agent, so a check that
belongs to exactly one agent has a natural home
there, and putting it on a shared class would
mean passing that agent's context into a class
with no other use for it. The checks that stayed
on `PlanValidator` are the ones a second caller
could reasonably want: block loads, meal limits,
and the figures in an attempt explanation.

The gate is in the same place conceptually, which
is between the model and the lifter. It is simply
not all in one class, and the mapping document
records exactly where each check now lives.

## 13. Known limitation: competition lift variations

**Found** 3 November, not yet fixed

`PlanValidator` checks a load whenever the
exercise maps to a competition lift. The Hevy
import maps variations onto the same lift, so
`Bench Press - Close Grip (Barbell)` is held to
competition bench loads. In the first successful
generation the model proposed 57.5 kg for a close
grip set, which is sensible, and was refused and
pushed to 71.5 kg, which is not. The model was
right and the gate was wrong.

The fix is to check loads only for the
competition movement itself and leave variations
to judgement, as accessories already are. It is
recorded rather than made because it touches how
the catalogue marks competition lifts, which also
feeds the estimated maxima in
`StrengthAnalytics`, and a change with two
consumers deserves more care than the last week
of a project allows.

## 14. Undo kept in the window, not the database

Stage 1 described an undo stack over plan edits
and said nothing about how long it lives. The
implementation keeps it in the window: a
`PlanEditHistory` held by the Today screen,
bounded at twenty edits, lost when the
application closes.

The alternative was a table of applied commands
with enough detail to reverse each one, which
would survive a restart. It was not built,
because the case the feature exists for happens
inside one sitting. A lifter eases off a session
in the morning and wants it back in the evening;
nobody has asked to reverse a change made three
weeks ago, and the plan itself is the record of
what was decided.

The cost of that choice is honest and small.
`spotter review --keep` still routes its save
through `ApplyProgressionCommand`, which gives
one place that rebuilds a block and one
description of what changed, but it has no stack
to offer because the process exits. Persisting
the history is the obvious next step if undo
across restarts is ever wanted.

## 15. Panels refresh on a change, not on a visit

The dashboard used to refresh a panel every time
it was opened. That was correct and wasteful: an
import on one screen does change the numbers on
another, but walking down the navigation column
re-queried the database for figures nothing had
touched, and a panel that had just finished
drawing threw the result away the moment the
lifter came back to it.

With the event bus in place the rule is narrower.
A panel names the kinds of change it displays. A
panel that saves something publishes rather than
refreshing itself. The shell refreshes the panel
on screen immediately and marks the others stale,
and a stale panel redraws the next time it is
opened.

Stage 1 put this behaviour on a `BasePanel`
superclass. It is on the `Panel` interface
instead, as a default `changedBy()` method, so a
screen does not have to inherit from anything in
order to be heard.

Two cases needed deciding. A panel that names no
event is refreshed on every visit, as before,
because the shell cannot reason about it:
Analytics is computed from logged sets, which
arrive from `spotter log` in a separate process
that this bus cannot hear. And a listener that
throws does not stop the others from being told,
because one broken panel should not silently
freeze the rest of the window.

## 16. Machine readable output on every command
    that answers a question

Stage 3 runs Spotter from a harness rather than
by reading a terminal, so eight commands now take
`--json` and print one object instead of prose:
plan, substitute, adjust, meal, attempts, review,
adopt and checkin.

The shape is the same for all of them, which is
the point. `command` names the command, `ok` says
whether it produced what was asked for, `value`
carries the result and `trace` carries what the
agent did. A harness reads `ok`, not the wording,
so improving a sentence is no longer a breaking
change.

Three decisions are worth recording.

A refusal is a successful run that produced
nothing, so it prints `ok` false with a reason
rather than failing. The exit code is unchanged
from the prose path, which means a harness can
use either signal.

`value` can appear alongside a failure, and that
is deliberate. When the coach refuses to explain
a set of meet attempts, the attempts themselves
were calculated rather than generated and still
stand, so they are reported with `ok` false
beside them.

Domain objects are mapped by hand in `JsonOutput`
rather than handed to Jackson. They carry
Optionals and no annotations, but the real reason
is that the wire shape is a contract Stage 3
depends on: it should change when someone decides
to change it, not when someone renames a field.

The trace reports counts always and the steps
only with `--trace`. The steps are the most
useful part of a trace for a person to read and
the least useful to assert on.

## 17. A narrower Facade

Stage 1 drew `CoachController` with about
twenty five methods, one for each thing the
application can do, including one per agent:
`generateBlock`, `suggestSubstitute`,
`adjustSession`, `suggestMeal`, `progressPlan`,
`chat`, `adoptProgramme`. Every screen and every
command would have gone through it.

The implemented Facade is narrower. It gives one
way to reach the repositories, the catalogues and
the deterministic services, and it does not wrap
the agents at all. A screen or a command builds
the agent it needs and calls `run()` on it.

The reason is that the agents do not have a
common shape. `BlockGenerationAgent` needs a
profile, a catalogue, a history, a progression
strategy, a start date and a number of weeks.
`AttemptRationaleAgent` needs a set of calculated
attempts and an estimated maximum. A method per
agent on the Facade would have been a second
interface carrying the same parameters, kept in
step with the first by hand, and every new agent
would have meant editing the Facade as well as
writing the agent. That is the change the Open
Closed Principle is about.

What the Facade still earns its place for is the
part that is uniform: there is exactly one way to
get a repository, one `importFile`, one
`getAnalytics`, one `currentE1RMs`. Nothing else
in the application opens a database connection or
constructs a repository, which is what the
pattern was wanted for.

The cost is that the presentation layer knows the
agent constructors. That is visible in
`PlanPanel` and `PlanCommand`, which both build a
`BlockGenerationAgent`. `GuiContext` and
`ModelAccess` exist to keep that from being
repeated: the shared dependencies are assembled
once and handed to whoever needs them.

## 18. An interview became a paragraph

Stage 1 designed F13 as an interview: up to eight
questions, asked one at a time, each adapting to
what had already been answered. The
implementation reads a single paragraph and
reports what it understood alongside what the
lifter has not said.

The interview is the expensive version of the
same thing. Eight adaptive questions is eight
model calls and eight screens. A paragraph is one
call and one screen.

The adaptive part existed so that a lifter who
left something out would be asked about exactly
that. The implementation gets there differently:
`ProfileDraft.missingRequired()` lists what is
still unknown, and the Profile screen shows that
list beside what was understood. The lifter fills
the gaps in their own order rather than being
walked through a sequence.

Nothing is guessed. `ProfileDraft` is not a
`LifterProfile` and does not fill in defaults, so
an unanswered question stays unanswered rather
than quietly becoming an invented fact. That is
design change 2, and this change depends on it:
reading a paragraph into a profile directly would
have been worse than the interview, not better.

The cost is that a lifter who writes very little
gets a draft with several gaps at once rather
than being guided through them. That is a worse
experience for someone who wanted to be
interviewed, and a better one for everybody else.

## 19. A load of zero is a load nobody can lift

Found by watching a real run, not by a test,
while recording the demonstration videos.

The model was asked for a two week block and left
the load off three accessories. The parser read a
missing load as zero, `ExercisePrescription`
accepted it because zero is not negative, and
`PlanValidator` never looked. The plan told the
lifter to perform Romanian deadlifts for three
sets of eight at 0.0 kg.

The next run, four weeks of the same lifts,
filled every load in correctly. So this is not a
bad model. It is non deterministic output meeting
a gap in the gate, which is the category this
whole design exists to catch, and the gap was
open in the one place the videos point at.

The obvious fix, refusing any load of zero, is
wrong. A push up is prescribed at zero and that
is correct. The rule has to be about the
equipment rather than the number: a movement that
requires equipment and is prescribed at no load
is refused, with the equipment named so the model
knows what it forgot, and the agent corrects
itself the way it already does for a load outside
the allowed range. A movement requiring no
equipment still passes at zero.

Two tests cover the two halves, because a rule
with an exception needs both sides pinned or the
exception quietly becomes the rule.

What this says about the project is worth
recording honestly. The claim the whole design
rests on is that no model output reaches the
lifter unchecked. That claim was not quite true
for six days, and the thing that found it was
running the application for an audience rather
than any amount of test writing. The first live
session in November found six problems in two
hours. This is the seventh, and it was found the
same way.

## 20. The window can import, edit a profile and change theme

Stage 1 placed the import buttons on a Log screen
of the GUI. Stage 2 built import first as a
command, and for most of the project the window
could only read what a terminal had put in the
database. A lifter who does not use a terminal had
no way in, which defeats the purpose of building
the window.

Three things were added, all on top of what
existed rather than beside it.

An Import screen takes a CSV through a file
dialog. Hevy and Whoop exports are recognised from
their headers and need one click. Anything else is
shown as a column mapping, pre filled by the same
`ColumnMapping.suggestFor` the command line uses,
and can be saved under a name. It calls the same
`CoachController` methods as `spotter import`, so
there is one import and two ways to reach it, and
the result reports sessions added, duplicates and
skipped rows by line number exactly as the command
does.

The Profile screen gained a form. Describing
yourself in a paragraph, design change 18, is the
way in, but it is the wrong way to change one
number. The form shows every stored field and
saves under the profile's own id rather than
creating a second profile. Limitations stay as
free text because a form field would lose the
nuance the onboarding agent keeps.

The stylesheet gained a light theme. Every colour
was already a named variable, so the light theme
is the same names with different values under a
`light` style class on the root, and a toggle in
the navigation column switches it and remembers
the choice in the user's preferences store. No
panel knows which theme is showing. Doing this
exposed twelve hard coded colours in the
stylesheet, which became variables; a theme that
only changes the colours that happened to be named
is not a theme.

The Observer pattern needed one more value. The
note on `SpotterEvent` explained why there were
only three: logging and importing happened in a
separate process where the bus could not hear them.
The Import screen is now a publisher inside the
window, so `TRAINING_CHANGED` exists and the
dashboard refreshes when an import finishes. The
earlier argument against adding it was correct for
the code as it stood and stopped being correct when
the code changed, which is the reason that comment
recorded its reasoning rather than only its
conclusion.
