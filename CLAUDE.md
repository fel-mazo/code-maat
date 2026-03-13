# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Code Maat is a VCS analysis tool that mines git/svn/hg/p4/tfs history to produce behavioral metrics (coupling, churn, author hotspots, etc.). This repo has two distinct layers:

- **`src/code_maat/`** — the original CLI engine (largely stable; avoid modifying)
- **`src/codescene_lite/`** — a modern web application wrapping the engine, with a ClojureScript SPA frontend

## Commands

```bash
# Development REPL (Clojure backend)
make repl          # starts nREPL; then call (start) in REPL to launch server on :7777

# ClojureScript frontend (separate terminal)
npx shadow-cljs watch app    # hot-reload dev server; compiles to resources/public/js/

# Tests
make test          # run all tests (Kaocha)
make test-watch    # watch mode

# Run a single test namespace
clj -M:test --focus <namespace>

# Lint / format
make lint
make format-fix

# Production build
make dist          # produces target/codescene-lite-0.1.0-standalone.jar
```

**Important:** Always access the app via port 7777 (Ring server). The shadow-cljs DevTools port (9630) does not proxy API calls.

## Dev REPL Helpers (`dev/user.clj`)

After `make repl`, the `user` ns is auto-loaded:
```clojure
(start)    ; start web server
(stop)     ; stop web server
(restart)  ; reload namespaces and restart

; Run analysis directly without the UI:
(analyze! "/path/to/repo" "revisions")
(analyze! "/path/to/repo" "coupling" {:min-coupling 40})
```

Portal data inspector opens automatically in the browser when the REPL starts.

## Architecture

### Backend (`codescene_lite/`)

Request flow:
1. **Reitit router** (`router.clj`) — routes + Ring middleware stack
2. **API handlers** (`api/repos.clj`, `api/analysis.clj`) — validate with Malli, enqueue jobs
3. **Engine adapter** (`engine/runner.clj`) — generates git log (`engine/git_log.clj`), calls `code-maat.app.app/run`, converts Incanter dataset → Clojure maps
4. **EDN store** (`store/edn_store.clj`) — atom-backed file persistence under `data/` (repos.edn, jobs.edn, results/)

Component lifecycle is managed by **Integrant** (`system.clj`). Config lives in `resources/config.edn`. The data directory can be overridden with `-Dcodescene.data-dir=/path`.

### Frontend (`codescene_lite/ui/`)

Re-Frame (Redux-style) ClojureScript SPA:
- `db.cljs` — app state shape
- `events.cljs` — event handlers (mutations + HTTP calls via `http-fx`)
- `subs.cljs` — derived state subscriptions
- `views/` — Reagent components; `views/results/` contains charts (Recharts/D3) and tables (TanStack)

### Supported Analyses

`revisions`, `authors`, `coupling`, `soc`, `churn`, `age`, `effort`, `communication`, `summary`, `identity`

## Testing

Tests use **Kaocha** (`tests.edn`). Test files are under `test/` and mirror `src/` namespaces. The engine tests in `test/code_maat/` are the most comprehensive; web layer tests are in `test/codescene_lite/`.

## Docker

```bash
docker build -t code-maat-app .
docker compose up --build                # run the app
docker compose --profile dev up          # also starts nREPL on :7888 for remote connection
```

The multi-stage Dockerfile builds ClojureScript + uberjar in a builder stage, then runs in a JRE-only runtime image.
