# AI tools and LLM models used

Dev Parikh, 219957794. EECS 3311 Software Design, Stage 2.

This file covers Stage 2 deliverables 3 and 4: the tools used to build Spotter, and the models those tools used. The session by session account of what each tool produced and what I did with it is in `ai-collaboration-log.md`.

There are two separate things to report and they are easy to conflate, so they are kept apart here. One is the AI used to write the software. The other is the AI the software itself calls when it runs, which is the subject of the project rather than a development tool.

## 1. AI used to build the software

| Tool | Model | Purpose | Example tasks |
|---|---|---|---|
| Claude Code, in the Claude desktop application | Claude Opus 5 configured for the session; see the note below | Writing, reviewing and debugging the implementation, and drafting the Stage 1 and Stage 2 documents | Scaffolded the Maven project and SQLite schema; wrote the domain calculations and their tests; wrote the seven agents against the `CoachAgent` template; built the eight JavaFX panels; added the Command and Observer patterns; wrote `JsonOutput` and its fifteen tests; drafted `design-changes.md` and `feature-to-implementation.md` |

**On the model identifier.** The session is configured for `claude-opus-5`. The tool does not expose the model serving any individual request, and the runtime may substitute a different model mid session, so the configured identifier is what can be stated accurately and the serving model for a given request cannot. Stage 2 asks that this be said plainly rather than guessed at, so it is said here: the configured model is `claude-opus-5` and no per request identifier is available.

No other AI tool was used. No code generation service, no autocomplete model, and no second assistant.

### Cost of the development tool

Billed through a Claude subscription rather than per token, so there is no token count or per task figure to report for this part. The subscription cost for the period is the only meaningful number and is a flat monthly charge rather than anything attributable to this project.

`TO FILL IN: the subscription tier and monthly cost, if the marker wants it.`

## 2. AI the software calls when it runs

Spotter calls the Anthropic API directly through LangChain4j 0.35.0. Exactly one class in the project imports that library, `agent/ClaudeClient`, which is the Strategy implementation behind the `LLMClient` interface. Everything else in the project talks to the interface, which is why the whole agent layer can be tested with `ScriptedLLMClient` and no network at all.

Two models are configured, and which one is used is decided per agent rather than globally.

| Tier | Model identifier | Used by | Why |
|---|---|---|---|
| Ordinary | `claude-haiku-4-5-20251001` | `SubstitutionAgent` (F06), `AdjustmentAgent` (F07), `AttemptRationaleAgent` (F08), `MealSuggestionAgent` (F10), `OnboardingAgent` (F13) | These agents pick from a filtered list or write one checked sentence. The answer is validated against deterministic code either way, so a more capable model buys nothing |
| Reasoning | `claude-sonnet-4-6` | `BlockGenerationAgent` (F05), `ChatAgent` (F11), `ProgressionAgent` (F12), `ProgrammeParserAgent` (F14) | These write or rewrite a whole training block, or interpret prose. F05 generates four weeks of training under constraints it has to satisfy simultaneously, and F14 reads a programme written in whatever form a lifter had it in |

Both identifiers are set in `.env` and documented in `.env.example`. Neither is hard coded.

**Why these identifiers and not newer ones.** Both are pinned deliberately and the reason is in `.env.example` next to the values. The 5 series models emit a `thinking` content block that LangChain4j 0.35.0 cannot parse, so a call to one fails before the reply reaches the parser. The fix is a client library upgrade rather than a model change, and it was not worth doing in the final week of the project. This is recorded as design change 10.

**An earlier error worth recording here.** The first identifiers configured were `claude-3-5-haiku-20241022` and `claude-3-5-sonnet-20241022`, which were suggested from the assistant's own training rather than looked up. Both had been retired, in February 2026 and October 2025 respectively, so every call failed. They were replaced only after checking the current model list. The lesson applies to anything a model states as fact about the world outside the code.

### Tokens and money

Spotter reports what every run cost, through `presentation/support/UsageReport`, which reads the token counts out of `AgentTrace` and prices them from the rates in `.env`. Any command run with `--trace` prints the figure, and `--json` reports the token counts under `trace.usage`.

The reported figure is an upper bound rather than an exact cost, and deliberately so. A single pair of rates is configured, 3 and 15 dollars per million input and output tokens, which are the reasoning model's published rates. The ordinary model is cheaper, at 1 and 5, so any run that used the ordinary tier is reported as costing more than it did. The reasoning is written into `.env.example`: a cost that overstates and says so is worth more than one that looks precise and is not.

Measured usage:

| What | Calls | Cost |
|---|---|---|
| Session 7, the first live run of the agents, including every failed call | 9 | roughly 24 cents, measured |
| Everything else | see note | `TO FILL IN` |

Token totals across the whole project cannot be reported from the repository, because responses are not persisted. `RecordingLLMClient` exists for that and was not switched on during the live sessions, which in hindsight it should have been. The account total is available in the Anthropic console under usage, and that figure is the one to quote.

`TO FILL IN: the total API spend and token count from the Anthropic console, for the period 2 November onward.`

### Why the cost is as low as it is

Worth stating, because a project built around an API is expected to be expensive and this one is not.

Most of what Spotter does involves no model at all. The analytics, the estimated maxima, the RPE chart, the attempt calculation, the macro arithmetic, the recovery rules and the adherence figures are all deterministic code, and every one of them works with no API key configured. `spotter analytics`, `spotter attempts` and `spotter review` run without a key and say so in their output.

Where a model is involved, it proposes and deterministic code disposes. The model never writes to the database directly. It returns a proposal, `PlanValidator` checks it, and a rejected proposal goes back with the reason attached. That loop costs a second call when it triggers, which is cheaper than the alternative of a plan nobody checked.

The architecture also supports not calling the model at all. `CachingLLMClient` and `RecordingLLMClient` are Decorators over `LLMClient`, so a response can be recorded once and replayed. `ScriptedLLMClient` is a third Strategy implementation, and it is what the entire test suite uses: five hundred tests run with no network and no key.

## 3. What the AI did not do unchecked

The Stage 2 instruction asks that AI generated code be understood, inspected, integrated and tested rather than accepted because it compiles. The record of that is in `ai-collaboration-log.md` session by session, but the short version belongs here.

Five hundred tests exist, and several were written because I disagreed with an assertion the assistant had made about its own code. Two repository tests were saving a macro target for a profile that did not exist and passing because the test had been written to expect it; the foreign key was right and the tests were wrong, and a third test was added asserting that the database refuses an orphaned target. One test asserted a validation message that the code did not produce; the test was fixed rather than the message, because the message was the better wording.

Three bugs in the project's own code were found only by running it against the live API: an agent that ran out of tool rounds and blamed the model for our own limit, a client that cached models by name while fixing the token limit at build time and so silently truncated long replies, and a range boundary that refused a load of 66.0 kg against a range printing as 66.0 to 76.7. All three are in `design-changes.md`.

One assistant mistake is recorded in the collaboration log rather than hidden: it stated that it had edited `.env` when it had not, and the same error recurred until the edit was actually made. The output has to be inspected, including the claim that something was done.
