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

## Development Workflow

1. **Start backend REPL:** `make repl` then `(start)` — server runs on http://localhost:7777
2. **Start frontend watcher (separate terminal):** `npx shadow-cljs watch app` — hot-reload on save
3. **Access app:** http://localhost:7777 (NOT port 9630 — that's shadow-cljs DevTools only)
4. **REPL-driven analysis:** Use `(analyze! "/path/to/repo" "revisions")` to test engine directly

**Note:** shadow-cljs watches ClojureScript files and compiles to `resources/public/js/`. The Ring server serves these static files. Always test via port 7777 to ensure API calls work.

## Architecture

### Backend (`codescene_lite/`)

Request flow:
1. **Reitit router** (`router.clj`) — routes + Ring middleware stack
2. **API handlers** (`api/repos.clj`, `api/analysis.clj`, `api/jobs.clj`) — validate with Malli, enqueue jobs
3. **Engine adapter** (`engine/runner.clj`) — generates git log (`engine/git_log.clj`), calls `code-maat.app.app/analyze`, converts Incanter dataset → Clojure maps
4. **EDN store** (`store/edn_store.clj`) — atom-backed file persistence under `data/` (repos.edn, results/)

**Data persistence:**
- `data/repos.edn` — registered repositories (path, name, metadata)
- `data/results/<repo-id>.edn` — cached analysis results per repository
- Jobs are ephemeral (in-memory only) and tracked via a separate jobs atom

Component lifecycle is managed by **Integrant** (`system.clj`). Config lives in `resources/config.edn`. The data directory can be overridden with `-Dcodescene.data-dir=/path` (useful for Docker volumes).

### Frontend (`codescene_lite/ui/`)

Re-Frame (Redux-style) ClojureScript SPA:
- `db.cljs` — app state shape
- `events.cljs` — event handlers (mutations + HTTP calls via `http-fx`)
- `subs.cljs` — derived state subscriptions
- `views/` — Reagent components; `views/results/` contains charts (Recharts/D3) and tables (TanStack)

### Supported Analyses

**Core metrics:** `revisions`, `authors`, `summary`, `identity`
**Coupling:** `coupling`, `soc`, `communication`
**Churn:** `abs-churn`, `author-churn`, `entity-churn`, `entity-ownership`, `main-dev`, `refactoring-main-dev`
**Effort:** `entity-effort`, `main-dev-by-revs`, `fragmentation`
**Other:** `age`, `messages`

Analysis metadata lives in `engine/metadata.clj`. Some analyses (coupling, entity-effort, fragmentation, communication) are marked as async and run in a background thread pool.

### Code Maat Engine (`src/code_maat/`)

The engine is the stable core that performs VCS log parsing and analysis. It follows a pipeline architecture:

1. **Parsers** (`parsers/`) — convert VCS log files to sequences of modification maps
   - Supports: `git`, `git2` (preferred), `svn`, `hg`, `p4`, `tfs`
   - git2 format: `git log --all --numstat --date=short --pretty=format:'--%h--%ad--%cn' --no-renames`

2. **Analysis** (`analysis/`) — process modification sequences into metrics datasets
   - Each analysis function takes an Incanter dataset and returns a dataset
   - All supported analyses are registered in `app/app.clj` `supported-analysis` map

3. **Aggregation** (optional) — group files into architectural layers via regex patterns (`app/grouper.clj`)

**Important:** Avoid modifying the engine unless fixing bugs. The web layer should adapt to the engine's interface, not vice versa.

## Code Formatting

This project uses **cljfmt** with 2-space indentation (matching IntelliJ/Cursive "Default to Only Indent" style). Configuration in `.cljfmt.edn` sets `:indents ^:replace {#".*" [[:inner 0]]}` to apply consistent indentation to all forms. Run `make format-fix` before committing.

## Testing

Tests use **Kaocha** (`tests.edn`). Test files are under `test/` and mirror `src/` namespaces. The engine tests in `test/code_maat/` are the most comprehensive; web layer tests are in `test/codescene_lite/`.

## Docker

```bash
docker build -t code-maat-app .
docker compose up --build                # run the app
docker compose --profile dev up          # also starts nREPL on :7888 for remote connection
```

The multi-stage Dockerfile builds ClojureScript + uberjar in a builder stage, then runs in a JRE-only runtime image.
