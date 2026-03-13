(ns codescene-lite.engine.runner
  "Thin adapter between the codescene-lite web layer and the code-maat engine.
   Calls app/analyze, then serialises the Incanter dataset to plain Clojure data."
  (:require [code-maat.app.app :as app]
            [incanter.core :as incanter]))

(defn dataset->result
  "Converts an Incanter dataset to a serialisable map.
   Returns {:columns [\"col1\" \"col2\" ...] :rows [[v1 v2 ...] ...]}"
  [ds]
  {:columns (mapv name (incanter/col-names ds))
   :rows    (incanter/to-list ds)})

(defn run-analysis
  "Run a code-maat analysis on a log file and return the result as plain data.

   Arguments:
     log-file-path — absolute path to a git log text file
     options       — map matching code-maat's option keys:
                       :analysis         (required) e.g. \"revisions\"
                       :version-control  (required) e.g. \"git2\"
                       :min-revs, :min-coupling, etc. (optional)

   Returns {:columns [...] :rows [[...] ...]} on success.
   Throws ex-info wrapping any IllegalArgumentException from the engine."
  [log-file-path options]
  (try
    (-> (app/analyze log-file-path options)
        dataset->result)
    (catch IllegalArgumentException e
      (throw (ex-info (.getMessage e)
                      {:analysis (:analysis options)
                       :cause    e})))))
