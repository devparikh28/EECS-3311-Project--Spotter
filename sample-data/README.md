# Sample data

Three small synthetic files, enough to reproduce every import path in the application without needing a real export from anybody's account. No real person's training or physiological data appears here.

`spotter seed` is the other way in and needs no files at all: it loads twenty sessions and a food catalogue straight into the database, which is what the screens use when there is nothing else.

## hevy-export-sample.csv

A workout export in Hevy's format: 20 data rows, 7 sessions across three weeks, in pounds.

```
spotter import hevy-export-sample.csv
spotter import hevy-export-sample.csv --show-skipped
```

Two rows are deliberately unreadable, one with no rep count and one with the weight written as `heavy`. They are skipped and reported by line number while the other eighteen import, which is the behaviour that matters: one bad row in a real export should not lose the rest. Running the import twice shows the duplicate detection.

## whoop-export-sample.csv

A recovery export in Whoop's physiological cycles format: 12 rows, 11 complete days.

```
spotter import whoop-export-sample.csv
spotter recovery
```

The last row is a cycle still in progress with empty fields, and it is skipped rather than stored as a day of zeros. Day six has a recovery score of 29 and under five hours of sleep, which the rule engine flags, so `spotter adjust` has something to work with afterwards.

## programme-sample.csv

A three week programme in a layout nobody agreed on, for the spreadsheet route of F14.

```
spotter adopt programme-sample.csv --csv \
  --set week=Week --set day=Day --set exercise=Lift \
  --set sets=Sets --set reps=Reps --set weight=Weight --set rpe=RPE
```

The column names are deliberately not the ones Spotter expects, because that is the whole point of the mapping: a lifter says which column means what, once, and `--save-as` means they are never asked again. Adopting this involves no model at all.
