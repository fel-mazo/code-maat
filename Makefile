.PHONY: help deps repl test lint format clean dist server cljs-watch cljs-build cljs-release

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

deps: ## Download all dependencies
	clj -P
	clj -P -M:dev
	clj -P -M:test

repl: ## Start a development REPL (with nREPL for editor connection)
	clj -M:dev -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"

test: ## Run all tests
	clj -M:test

test-watch: ## Run tests in watch mode
	clj -M:test --watch

server: ## Start the web server (production mode)
	clj -M:server

lint: ## Lint with clj-kondo
	clj-kondo --lint src test

format: ## Check formatting with cljfmt
	clj -M:dev -m cljfmt.main check src test

format-fix: ## Fix formatting with cljfmt
	clj -M:dev -m cljfmt.main fix src test

clean: ## Remove build artifacts
	clj -T:build clean
	rm -rf .cpcache

dist: ## Build production uberjar
	clj -T:build uber

cljs-watch: ## Start ClojureScript hot-reload dev server (port 9630)
	npx shadow-cljs watch app

cljs-build: ## Compile ClojureScript for development
	npx shadow-cljs compile app

cljs-release: ## Compile ClojureScript for production (optimized)
	npx shadow-cljs release app
