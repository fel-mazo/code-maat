(ns user
  "REPL development utilities.
   Load this namespace automatically when starting the :dev alias.

   Usage:
     (start)    - start the web server
     (stop)     - stop the web server
     (restart)  - reload namespaces and restart
     (analyze! \"/path/to/repo\" \"revisions\")  - run analysis from REPL"
  (:require [integrant.repl :as ig-repl]
            [integrant.repl.state :as ig-state]
            [clojure.tools.namespace.repl :as repl]))

;; ── Portal data inspector ──────────────────────────────────────────────────
(defonce portal-instance
  (try
    (require 'portal.api)
    (let [open (resolve 'portal.api/open)
          submit (resolve 'portal.api/submit)]
      (let [p (open {:launcher :browser})]
        (add-tap submit)
        p))
    (catch Exception _ nil)))

;; ── Integrant system lifecycle ─────────────────────────────────────────────
(ig-repl/set-prep!
  (fn []
    (require '[codescene-lite.system :as system])
    ((resolve 'codescene-lite.system/config))))

(defn start
  "Start the codescene-lite web server."
  []
  (ig-repl/go))

(defn stop
  "Stop the web server."
  []
  (ig-repl/halt))

(defn restart
  "Reload changed namespaces and restart the web server."
  []
  (ig-repl/reset))

;; ── REPL analysis conveniences ────────────────────────────────────────────
(defn analyze!
  "Run a code-maat analysis on a local git repo directly from the REPL.
   Useful for quick exploration without the UI.

   Examples:
     (analyze! \"/path/to/repo\" \"revisions\")
     (analyze! \"/path/to/repo\" \"coupling\" {:min-coupling 40})"
  ([repo-path analysis-name]
   (analyze! repo-path analysis-name {}))
  ([repo-path analysis-name opts]
   (require '[codescene-lite.engine.git-log :as git-log]
            '[codescene-lite.engine.runner :as runner])
   (let [log-file (-> (git-log/generate-log {:path repo-path})
                      (java.io.File.))]
     (runner/run-analysis (.getPath log-file)
                          (merge {:analysis analysis-name
                                  :version-control "git2"}
                                 opts)))))
