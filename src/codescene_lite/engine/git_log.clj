(ns codescene-lite.engine.git-log
  "Generates git log text by running a subprocess in the target repository.
   Returns the log as a string suitable for code-maat's git2 parser."
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]))

;; The git log format expected by code-maat's git2 parser.
;; --%h: abbreviated commit hash
;; --%cd: commit date (formatted by --date=short → YYYY-MM-DD)
;; --%cn: committer name
;; --numstat: lines added/deleted per file
(def ^:private git-log-format
  "--pretty=format:--%h--%cd--%cn")

(defn generate-log
  "Runs git log in the given repo directory and returns the output as a string.

   Options:
     :path       (required) Absolute path to the git repository
     :from-date  (optional) ISO date string e.g. \"2023-01-01\" (--after)
     :to-date    (optional) ISO date string e.g. \"2024-01-01\" (--before)
     :branch     (optional) Branch/ref to log (default: all branches via --all)

   Returns the git log as a string, or throws ex-info on failure."
  [{:keys [path from-date to-date branch] :or {branch nil}}]
  (let [base-cmd ["git" "log"
                  "--all"
                  "-M" "-C"
                  "--numstat"
                  "--date=short"
                  git-log-format]
        cmd      (cond-> base-cmd
                   from-date (conj (str "--after=" from-date))
                   to-date   (conj (str "--before=" to-date))
                   branch    (conj branch))
        result   (apply sh/sh (concat cmd [:dir path]))]
    (when (not= 0 (:exit result))
      (throw (ex-info "git log failed"
                      {:path   path
                       :stderr (:err result)
                       :exit   (:exit result)})))
    (:out result)))

(defn write-log-to-temp-file!
  "Writes the given log string to a temporary file and returns its path.
   The temp file is deleted on JVM exit."
  [log-text]
  (let [tmp (java.io.File/createTempFile "codemaat-" ".log")]
    (.deleteOnExit tmp)
    (spit tmp log-text)
    (.getAbsolutePath tmp)))
