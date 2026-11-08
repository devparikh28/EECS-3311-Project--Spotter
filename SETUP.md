# Setup

Spotter needs Java 21 and Maven 3.9 or newer. No API key is needed for
the deterministic commands, the tests, or the dashboard with sample data.

## Build and test

```
mvn -B test
mvn -q package
```

The tests use a scripted model client, so they run with no network
access and no key.

## Run with sample data

```
java -jar target/spotter.jar seed
java -jar target/spotter.jar status
mvn javafx:run
```

`seed` loads a sample lifter, twenty sessions, recovery readings and
a food catalogue. To try the import paths use the files in
`sample-data/`.

## Add your own API key (optional)

```
cp .env.example .env
```

Open `.env` and set `LLM_API_KEY` to your own key. `.env` is
git ignored. Never put a key in a source file, report or commit.

## Guided tour

Press the ? button in the dashboard, or start it automatically:

```
SPOTTER_TOUR=1 mvn javafx:run
```

The tour only looks. It saves nothing and calls no model.

## Use a separate database

```
SPOTTER_DB_PATH=/tmp/spotter-try.db java -jar target/spotter.jar seed
```
