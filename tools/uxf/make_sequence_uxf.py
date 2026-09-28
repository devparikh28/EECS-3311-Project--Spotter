#!/usr/bin/env python3
"""Generate UMLet sequence diagrams (UMLSequenceAllInOne) into diagrams/uxf/.

Each diagram is one UMLSequenceAllInOne element whose panel_attributes hold
UMLet's sequence text syntax, so the diagram stays editable as text in UMLet.
"""

import os
import re
from puml_to_uxf import write_uxf

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "diagrams", "uxf")

SD01 = """title=SD01 Import CSV Data (F02, F03 / UC02, UC04, UC15)
obj=Lifter~lifter ACTOR
obj=LogPanel~ui
obj=CoachController~ctrl
obj=CsvImportService~imp
obj=DataSourceFactory~fac
obj=HevyCsvAdapter~ad
obj=ExerciseCatalog~cat
obj=WorkoutRepository~repo
obj=EventBus~bus

lifter->>>ui : onImportHevyClicked(path); on=ui
ui->>>ctrl : importWorkouts(path); on=ctrl
ctrl->>>imp : importFile(path, HEVY); on=imp
imp->>>fac : detect(path)
fac.>imp : SourceKind.HEVY
imp->>>fac : create(HEVY)
fac.>imp : HevyCsvAdapter
imp->>>ad : read(path); on=ad
ad.>imp : ImportBatch; off=ad
combinedFragment=alt~f1 imp cat
imp:[exercise name known]
imp->>>cat : byName(name)
cat.>imp : Exercise
..=f1
imp:[unknown name, UC15]
imp->>>cat : closestMatches(name, 3)
cat.>imp : candidates
imp->>>ui : promptMapping(name, candidates)
ui.>imp : chosen mapping
imp->>>cat : addAlias(name, exercise)
--=f1
imp->>>imp : dedupe(batch)
imp->>>repo : save(sessions)
repo.>imp : saved count
imp.>ctrl : ImportReport; off=imp
ctrl->>>bus : publish(DATA_IMPORTED)
bus->>>ui : onEvent(e)
ctrl.>ui : ImportReport; off=ctrl
ui.>lifter : imported, skipped and failed rows; off=ui
"""

SD02 = """title=SD02 View Strength Analytics (F04 / UC05)
obj=Lifter~lifter ACTOR
obj=AnalyticsPanel~ui
obj=CoachController~ctrl
obj=WorkoutRepository~repo
obj=StrengthAnalytics~an
obj=RPEChart~chart

lifter->>>ui : onLiftSelected(SQUAT); on=ui
ui->>>ctrl : getAnalytics(SQUAT); on=ctrl
ctrl->>>repo : findByLift(SQUAT, since)
repo.>ctrl : List<WorkoutSession>
combinedFragment=alt~f1 ctrl chart
ctrl:[history empty]
ctrl.>ui : AnalyticsSummary.empty()
ui.>lifter : empty state, prompt to import
..=f1
ctrl:[history exists]
ctrl->>>an : e1rmSeries(SQUAT, history); on=an
combinedFragment=loop~l1 an chart
an:[for each SetEntry]
an->>>chart : percentOf1RM(reps, rpe)
chart.>an : percentage
an->>>an : computeE1RM(set) or exclude invalid RPE
--=l1
an.>ctrl : List<Point>; off=an
ctrl->>>an : weeklyTonnage(SQUAT, history)
an.>ctrl : List<Point>
ctrl->>>an : bestSet(SQUAT, history)
an.>ctrl : SetEntry
ctrl.>ui : AnalyticsSummary
ui.>lifter : chart, figures, data quality warnings
--=f1
"""

SD03 = """title=SD03 Generate Training Block (F05 / UC06, including the UC14 agent loop)
obj=Lifter~lifter ACTOR
obj=PlanPanel~ui
obj=CoachController~ctrl
obj=BlockGenerationAgent~agent
obj=ToolManager~tools
obj=PromptBuilder~prompt
obj=ClaudeClient~llm
obj=ResponseParser~parser
obj=PlanValidator~val
obj=BlockRepository~repo
obj=Claude API~api ACTOR

lifter->>>ui : onGenerateClicked(weeks = 6); on=ui
ui->>>ctrl : generateBlock(6); on=ctrl
ctrl->>>agent : run(AgentRequest); on=agent
agent->>>agent : createToolset() Factory Method
agent->>>agent : gatherContext(req)
agent->>>prompt : system, context, schema, build()
prompt.>agent : List<Message>
combinedFragment=loop~l1 agent api
agent:[at most maxToolRounds]
agent->>>llm : complete(msgs, tools, system); on=llm
llm->>>api : messages.create(...)
api.>llm : response with tool_use
llm.>agent : LLMResponse(toolCalls); off=llm
agent->>>tools : execute(call); on=tools
tools.>agent : ToolResult (e1rms, exercises, or error); off=tools
--=l1
agent->>>llm : complete(msgs + results, tools, system)
llm->>>api : messages.create(...)
api.>llm : final JSON block
llm.>agent : LLMResponse(content)
agent->>>parser : toBlock(json, catalog)
parser.>agent : TrainingBlock
agent->>>val : validateBlock(block, ctx, strategy)
val.>agent : ValidationReport
combinedFragment=alt~f1 agent repo
agent:[report valid]
agent.>ctrl : AgentResult(success, block); off=agent
ctrl->>>repo : save(block)
ctrl.>ui : AgentResult(success)
ui.>lifter : block displayed week by week
..=f1
agent:[invalid, one retry]
agent->>>llm : complete(retry with validation errors)
llm.>agent : corrected JSON
agent->>>val : validateBlock(...)
..=f1
agent:[still invalid or API timeout]
agent.>ctrl : AgentResult(success = false, errors)
ctrl.>ui : errors
ui.>lifter : error banner, previous block unchanged
--=f1
off=ctrl; off=ui
"""

SD04 = """title=SD04 Substitute Unavailable Exercise (F06 / UC08, UC07)
obj=Lifter~lifter ACTOR
obj=PlanPanel~ui
obj=CoachController~ctrl
obj=SubstitutionAgent~agent
obj=ExerciseDBTool~tool
obj=ClaudeClient~llm
obj=PlanValidator~val
obj=PlanEditHistory~hist
obj=SwapExerciseCommand~cmd
obj=TrainingBlock~block

lifter->>>ui : onSubstituteClicked(prescriptionId); on=ui
ui->>>ctrl : suggestSubstitute(id); on=ctrl
ctrl->>>agent : run(AgentRequest); on=agent
agent->>>tool : execute(pattern, muscles, equipment)
tool.>agent : candidate exercises
combinedFragment=alt~f1 agent block
agent:[no candidates]
agent.>ctrl : AgentResult(success = false)
ctrl.>ui : no automatic substitute
ui.>lifter : swap manually through UC07
..=f1
agent:[candidates exist]
ref=agent llm :UC14 agent loop, see SD03
agent->>>val : validateSubstitution(chosen, candidates)
val.>agent : ValidationReport
agent.>ctrl : AgentResult(exercise, rationale); off=agent
ctrl.>ui : suggestion and rationale
ui.>lifter : proposed substitute
lifter->>>ui : accept()
ui->>>ctrl : applyEdit(SwapExerciseCommand)
ctrl->>>hist : execute(cmd); on=hist
hist->>>cmd : execute(); on=cmd
cmd->>>block : setExercise(prescriptionId, newExercise)
cmd->>>cmd : store previous exercise for undo; off=cmd
hist->>>hist : push(cmd); off=hist
ctrl.>ui : plan updated
ui.>lifter : Undo available
--=f1
off=ctrl; off=ui
"""

SD05 = """title=SD05 Adjust Session for Poor Recovery (F07 / UC09)
obj=Lifter~lifter ACTOR
obj=PlanPanel~ui
obj=CoachController~ctrl
obj=RecoveryRuleEngine~rules
obj=RecoveryRepository~rrepo
obj=AdjustmentAgent~agent
obj=ClaudeClient~llm
obj=PlanValidator~val
obj=PlanEditHistory~hist
obj=EventBus~bus

ctrl->>>rrepo : findByDate(sessionDate); on=ctrl
rrepo.>ctrl : RecoveryDay
ctrl->>>rules : evaluate(day)
combinedFragment=alt~f1 rules bus
rules:[score < 33 or sleep < 6.0]
rules.>ctrl : RecoveryFlag(reasons)
ctrl->>>bus : publish(RECOVERY_FLAGGED)
bus->>>ui : onEvent(e)
ui.>lifter : warning badge on session
..=f1
rules:[data missing or within thresholds]
rules.>ctrl : no flag
--=f1
lifter->>>ui : onAdjustClicked(sessionId); on=ui
ui->>>ctrl : adjustSession(sessionId)
ctrl->>>agent : run(AgentRequest); on=agent
ref=agent llm :UC14 agent loop, see SD03
agent->>>val : validateAdjustment(adjusted, original)
val.>agent : no load increases, competition lifts kept
combinedFragment=alt~f2 agent hist
agent:[valid]
agent.>ctrl : AgentResult(adjustedSession, rationale); off=agent
..=f2
agent:[agent or API failure]
ctrl->>>rules : fallbackAdjustment(session)
rules.>ctrl : sets reduced by one third
--=f2
ctrl.>ui : original and adjusted side by side
ui.>lifter : review adjustment
combinedFragment=alt~f3 lifter hist
lifter:[accepts]
lifter->>>ui : accept()
ui->>>ctrl : applyEdit(ApplyAdjustmentCommand)
ctrl->>>hist : execute(cmd)
ctrl->>>bus : publish(PLAN_UPDATED)
bus->>>ui : onEvent(e)
ui.>lifter : session updated with adjustment note
..=f3
lifter:[rejects]
ui.>lifter : original session kept
--=f3
off=ctrl; off=ui
"""

SD06 = """title=SD06 Plan a Heavy Single: Meet Attempts or Test Day (F08 / UC10)
obj=Lifter~lifter ACTOR
obj=MeetPanel~ui
obj=CoachController~ctrl
obj=StrengthAnalytics~an
obj=AttemptCalculator~calc
obj=AttemptRationaleAgent~agent
obj=ClaudeClient~llm
obj=PlanValidator~val

lifter->>>ui : onCalculateClicked(); on=ui
ui->>>ctrl : calculateAttempts(); on=ctrl
ctrl->>>an : currentE1RMs(history)
an.>ctrl : Map<Lift, double>
combinedFragment=loop~l1 ctrl calc
ctrl:[for each main lift]
combinedFragment=alt~f1 ctrl calc
ctrl:[profile has a meet date]
ctrl->>>calc : calculateMeetAttempts(lift, e1rm)
calc.>ctrl : MeetAttempts(opener, second, third)
..=f1
ctrl:[no meet date]
ctrl->>>calc : projectTestDay(lift, e1rm)
calc.>ctrl : TestDayPlan(warmups, topSingle)
--=f1
--=l1
ctrl->>>agent : run(AgentRequest(numbers, trend)); on=agent
ref=agent llm :UC14 agent loop, see SD03
agent->>>val : validateRationale(text, numbers)
val.>agent : every quoted number must match the calculator
combinedFragment=alt~f2 agent ui
agent:[rationale consistent]
agent.>ctrl : AgentResult(rationale); off=agent
ctrl.>ui : numbers and rationale
ui.>lifter : attempts or test day plan with explanation
..=f2
agent:[contradicts calculator or agent failed]
agent.>ctrl : AgentResult(success = false)
ctrl.>ui : numbers only
ui.>lifter : shown without a rationale
--=f2
lifter->>>ui : editAttempt(lift, value)
ui->>>ctrl : saveManualAttempt(lift, value)
ctrl.>ui : stored and marked manual; off=ctrl
off=ui
"""

SD07 = """title=SD07 Get Meal Suggestion (F10 / UC12)
obj=Lifter~lifter ACTOR
obj=NutritionPanel~ui
obj=CoachController~ctrl
obj=MacroTracker~macros
obj=MealSuggestionAgent~agent
obj=FoodDBTool~tool
obj=FoodCatalog~cat
obj=ClaudeClient~llm
obj=ResponseParser~parser
obj=PlanValidator~val

lifter->>>ui : onSuggestMealClicked(); on=ui
ui->>>ctrl : suggestMeal(); on=ctrl
ctrl->>>macros : getRemaining(today)
macros.>ctrl : MacroTotals(remaining)
combinedFragment=alt~f1 ctrl val
ctrl:[remaining calories near zero]
ctrl.>ui : targets already met
ui.>lifter : no meal needed
..=f1
ctrl:[macros remain]
ctrl->>>agent : run(AgentRequest(remaining, constraints)); on=agent
agent->>>tool : execute(constraints, maxCalories)
tool->>>cat : search(dietaryConstraints, maxCalories)
cat.>tool : permitted FoodItems
tool.>agent : ToolResult(foods)
ref=agent llm :UC14 agent loop, see SD03
agent->>>parser : toMeal(json, foodCatalog)
parser.>agent : MealSuggestion, nutrition from the catalog
agent->>>val : validateMeal(meal, constraints, remaining)
val.>agent : ValidationReport
combinedFragment=alt~f2 agent ui
agent:[valid]
agent.>ctrl : AgentResult(meal); off=agent
ctrl.>ui : meal with full nutrition breakdown
ui.>lifter : suggestion shown
lifter->>>ui : logThisMeal()
ui->>>ctrl : logIntake(entries)
..=f2
agent:[non permitted item or totals exceed remaining]
agent->>>llm : retry with validation errors
llm.>agent : corrected meal
..=f2
agent:[no foods fit]
agent.>ctrl : AgentResult(success = false, explanation)
ctrl.>ui : explanation of the shortfall
ui.>lifter : no meal invented
--=f2
--=f1
off=ctrl; off=ui
"""

SD08 = """title=SD08 Chat with Coach (F11 / UC13)
obj=Lifter~lifter ACTOR
obj=ChatPanel~ui
obj=CoachController~ctrl
obj=ChatAgent~agent
obj=MemoryManager~mem
obj=ConversationHistory~hist
obj=ToolManager~tools
obj=ClaudeClient~llm
obj=Claude API~api ACTOR

lifter->>>ui : onSendClicked(question); on=ui
ui->>>ctrl : chat(message); on=ctrl
ctrl->>>agent : run(AgentRequest(message)); on=agent
agent->>>mem : recall(message, k = 5); on=mem
mem->>>hist : recent(20)
hist.>mem : List<Message>
mem.>agent : relevant snippets; off=mem
combinedFragment=loop~l1 agent api
agent:[at most maxToolRounds]
agent->>>llm : complete(msgs, tools, system)
llm->>>api : messages.create(...)
api.>llm : tool_use request
llm.>agent : LLMResponse(toolCalls)
agent->>>tools : execute(call)
tools.>agent : ToolResult from AnalyticsTool, PlanLookupTool or RecoveryLookupTool
--=l1
agent->>>llm : complete(msgs + results, tools, system)
llm->>>api : messages.create(...)
api.>llm : final answer
llm.>agent : LLMResponse(content)
combinedFragment=alt~f1 agent ui
agent:[question ambiguous or data unavailable]
agent.>ctrl : AgentResult(clarifying question or honest gap)
..=f1
agent:[answer grounded in tool data]
agent->>>mem : remember(userMessage, reply)
mem->>>hist : append(m)
mem->>>mem : summarizeOlderTurns() if above maxTurns
agent.>ctrl : AgentResult(answer); off=agent
--=f1
ctrl.>ui : answer; off=ctrl
ui.>lifter : reply shown in chat; off=ui
"""

SD09 = """title=SD09 Save Profile and Log Intake (F01, F09 / UC01, UC11)
obj=Lifter~lifter ACTOR
obj=ProfilePanel~pui
obj=NutritionPanel~nui
obj=CoachController~ctrl
obj=ProfileRepository~prepo
obj=FoodCatalog~cat
obj=MacroTracker~macros
obj=IntakeRepository~irepo
obj=EventBus~bus

lifter->>>pui : onSaveClicked(); on=pui
pui->>>ctrl : saveProfile(profile); on=ctrl
ctrl->>>ctrl : validate(required fields, goal, meet date if given)
combinedFragment=alt~f1 ctrl bus
ctrl:[valid]
ctrl->>>prepo : save(profile)
prepo.>ctrl : LifterProfile
ctrl->>>bus : publish(PROFILE_UPDATED)
bus->>>nui : onEvent(e)
ctrl.>pui : saved
pui.>lifter : confirmation
..=f1
ctrl:[missing field or past meet date]
ctrl.>pui : ValidationReport(errors)
pui.>lifter : fields highlighted, nothing saved
--=f1
off=pui
lifter->>>nui : onLogIntakeClicked(food, grams); on=nui
nui->>>ctrl : logIntake(FoodEntry)
ctrl->>>cat : byName(food)
combinedFragment=alt~f2 ctrl irepo
ctrl:[known food]
cat.>ctrl : FoodItem
..=f2
ctrl:[unknown food]
ctrl.>nui : request macros per 100 g
lifter->>>nui : enter values
nui->>>ctrl : addFood(FoodItem)
ctrl->>>cat : add(item)
--=f2
combinedFragment=alt~f3 ctrl bus
ctrl:[grams within range]
ctrl->>>macros : recordIntake(today, entry); on=macros
macros->>>irepo : save(intake)
macros.>ctrl : MacroTotals(consumed)
ctrl->>>macros : getRemaining(today)
macros.>ctrl : MacroTotals(remaining)
ctrl->>>macros : weeklyAdherence(weekStart)
macros.>ctrl : percentage, days with no entries excluded; off=macros
ctrl->>>bus : publish(INTAKE_LOGGED)
ctrl.>nui : remaining macros and adherence
nui.>lifter : updated totals
..=f3
ctrl:[grams zero, negative or implausible]
ctrl.>nui : rejected with message
nui.>lifter : entry not saved
--=f3
off=ctrl; off=nui
"""

SD10 = """title=SD10 Review Adherence and Progress the Plan (F12 / UC16)
obj=Lifter~lifter ACTOR
obj=PlanPanel~ui
obj=CoachController~ctrl
obj=PlanAdherence~adh
obj=ProgressionAgent~agent
obj=AdherenceTool~tool
obj=ClaudeClient~llm
obj=PlanValidator~val
obj=PlanEditHistory~hist
obj=TrainingBlock~block

lifter->>>ui : onReviewWeekClicked(week); on=ui
ui->>>ctrl : reviewAdherence(week); on=ctrl
ctrl->>>adh : compare(block, logged, from, to); on=adh
adh->>>adh : matchSession and verdictFor each prescription
adh.>ctrl : AdherenceReport(completionRate, entries); off=adh
ctrl.>ui : report
ui.>lifter : prescribed against actual, per exercise
combinedFragment=alt~f1 lifter block
lifter:[shortfall, or asks to progress]
lifter->>>ui : onProgressPlanClicked()
ui->>>ctrl : progressPlan(week)
ctrl->>>agent : run(AgentRequest(block, report)); on=agent
agent->>>tool : execute(week)
tool.>agent : adherence summary
ref=agent llm :UC14 agent loop, see SD03
agent->>>val : validateProgression(revised, original, report)
val.>agent : loads advance only where the verdict allows
agent.>ctrl : AgentResult(revised weeks, rationale); off=agent
ctrl.>ui : proposed changes with reasons
lifter->>>ui : accept()
ui->>>ctrl : applyEdit(ApplyProgressionCommand)
ctrl->>>hist : execute(cmd)
hist->>>block : replaceWeeks(revised)
ctrl.>ui : plan updated
ui.>lifter : remaining weeks revised, Undo available
..=f1
lifter:[no shortfall, no request]
ctrl.>ui : report only
--=f1
off=ctrl; off=ui
"""

SD11 = """title=SD11 Complete Guided Onboarding (F13 / UC18)
obj=Lifter~lifter ACTOR
obj=OnboardingPanel~ui
obj=CoachController~ctrl
obj=OnboardingAgent~agent
obj=ClaudeClient~llm
obj=ProfileDraft~draft
obj=PlanValidator~val
obj=StartingStrength~start
obj=ProfileRepository~repo

lifter->>>ui : start onboarding; on=ui
ui->>>ctrl : runOnboarding(); on=ctrl
ctrl->>>agent : run(AgentRequest); on=agent
combinedFragment=loop~l1 agent draft
agent:[until required answers collected, at most 8]
ref=agent llm :UC14 agent loop, see SD03
agent.>ui : next question
lifter->>>ui : answer
ui->>>agent : answer
agent->>>draft : record(answer)
--=l1
agent->>>val : validateProfileDraft(draft)
val.>agent : required fields present, no contradictions
combinedFragment=alt~f1 agent start
agent:[draft complete]
agent.>ctrl : AgentResult(ProfileDraft); off=agent
ctrl.>ui : draft profile for review
lifter->>>ui : confirm or correct
ui->>>ctrl : saveProfile(draft.toProfile())
ctrl->>>repo : save(profile)
ctrl->>>start : needsCalibration(history, lift)
start.>ctrl : stated maxes or a calibration week
ctrl.>ui : concrete first step
..=f1
agent:[required answers still missing]
agent.>ctrl : AgentResult(draft, missingRequired)
ctrl.>ui : fall back to the prefilled profile form
--=f1
off=ctrl; off=ui
"""

SD12 = """title=SD12 Adopt an Existing Programme (F14 / UC19)
obj=Lifter~lifter ACTOR
obj=PlanPanel~ui
obj=CoachController~ctrl
obj=ProgrammeImportService~imp
obj=ProgrammeParserAgent~agent
obj=ClaudeClient~llm
obj=ExerciseCatalog~cat
obj=PlanValidator~val
obj=BlockRepository~repo
obj=EventBus~bus

lifter->>>ui : onAdoptProgrammeClicked(source); on=ui
ui->>>ctrl : adoptProgramme(source, kind); on=ctrl
combinedFragment=alt~f1 ctrl cat
ctrl:[CSV file]
ctrl->>>imp : fromCsv(path, mapping); on=imp
imp->>>cat : byName(exercise)
imp.>ctrl : TrainingBlock(origin = ADOPTED); off=imp
..=f1
ctrl:[manual entry]
ctrl->>>imp : fromManualEntry(weeks)
imp.>ctrl : TrainingBlock(origin = ADOPTED)
..=f1
ctrl:[pasted text]
ctrl->>>agent : run(AgentRequest(text)); on=agent
ref=agent llm :UC14 agent loop, see SD03
agent->>>cat : byName and closestMatches per movement
agent.>ctrl : TrainingBlock(origin = ADOPTED); off=agent
--=f1
ctrl->>>val : validateAdoptedBlock(block, profile)
val.>ctrl : exercises exist, sessions fit training days, loads plausible
combinedFragment=alt~f2 ctrl bus
ctrl:[valid]
ctrl->>>repo : save(block)
ctrl->>>bus : publish(PLAN_UPDATED)
bus->>>ui : onEvent(e)
ui.>lifter : adherence, substitution and recovery adjustment now apply
..=f2
ctrl:[unresolved exercises or implausible loads]
ctrl.>ui : the specific problems
ui.>lifter : map the unknown movements or correct the loads
--=f2
off=ctrl; off=ui
"""

DIAGRAMS = [
    ("SD01-import-csv", SD01),
    ("SD02-analytics", SD02),
    ("SD03-generate-block", SD03),
    ("SD04-substitute-exercise", SD04),
    ("SD05-recovery-adjustment", SD05),
    ("SD06-heavy-single", SD06),
    ("SD07-meal-suggestion", SD07),
    ("SD08-coach-chat", SD08),
    ("SD09-profile-and-macros", SD09),
    ("SD10-adherence-progression", SD10),
    ("SD11-onboarding", SD11),
    ("SD12-adopt-programme", SD12),
]


SELF_MSG = re.compile(r"^(\s*)(\w+)(->>>|\.>|->)\2(\s*)(:|;|$)")


def normalise(spec):
    """UMLet requires a duration on self messages (a->>>a +1 : text)."""
    out = []
    for line in spec.splitlines():
        m = SELF_MSG.match(line)
        if m:
            indent, obj, arrow, _, tail = m.groups()
            rest = line[m.end(4):]
            line = "%s%s%s%s +1 %s" % (indent, obj, arrow, obj, rest)
        out.append(line)
    return "\n".join(out)


def size_for(spec):
    lifelines = sum(1 for line in spec.splitlines() if line.startswith("obj="))
    steps = sum(1 for line in spec.splitlines()
                if line and not line.startswith(("title=", "obj=", "autoTick")))
    width = max(600, lifelines * 150)
    height = max(400, steps * 34 + 140)
    return width, height


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, spec in DIAGRAMS:
        w, h = size_for(spec)
        path = os.path.join(OUT, "%s.uxf" % name)
        write_uxf(path, [("UMLSequenceAllInOne", (20, 20, w, h), normalise(spec.strip()), "")])
        print("%-28s %4dx%-4d -> %s" % (name, w, h, os.path.relpath(path, ROOT)))


if __name__ == "__main__":
    main()
