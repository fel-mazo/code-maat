(ns codescene-lite.engine.technical-debt
  "Analyzes git commit history to quantify technical debt.
   Classifies commits as bug-fixes, refactoring, or features based on
   configurable prefix patterns.  Returns per-file debt metrics and
   repository-level summary statistics."
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]))

(def default-bug-prefixes
  ["fix" "bug" "hotfix" "bugfix" "patch" "revert" "issue" "defect" "error"])

(def default-refactor-prefixes
  ["refactor" "chore" "cleanup" "clean" "debt" "improve" "perf" "simplif" "restructur"])

(defn- run-git! [repo-path args]
  (let [result (apply sh/sh (concat ["git"] args [:dir repo-path]))]
    (if (not= 0 (:exit result))
      (throw (ex-info "git command failed"
                      {:args args :stderr (:err result)}))
      (:out result))))

(defn- prefix-match?
  "Returns true if text starts with the prefix (case-insensitive).
   Also recognises conventional-commit forms like 'fix(scope):' and 'fix!'."
  [text prefix]
  (let [t (str/lower-case (str/trim (str text)))
        p (str/lower-case (str prefix))]
    (or (str/starts-with? t (str p " "))
        (str/starts-with? t (str p ":"))
        (str/starts-with? t (str p "("))
        (str/starts-with? t (str p "!")))))

(defn- classify-commit [subject bug-prefixes refactor-prefixes]
  (let [s (str/trim (or subject ""))]
    (cond
      (some (partial prefix-match? s) bug-prefixes)      :bug
      (some (partial prefix-match? s) refactor-prefixes) :refactor
      :else                                               :feature)))

(defn- parse-subject-log
  "Parse 'git log --format=%H|%s' lines into classified commit maps."
  [log-text bug-pfx refactor-pfx]
  (->> (str/split-lines log-text)
       (filter seq)
       (map (fn [line]
              (let [idx     (str/index-of line "|")
                    hash    (if idx (subs line 0 idx) line)
                    subject (if idx (subs line (inc idx)) "")]
                {:hash    hash
                 :subject subject
                 :type    (classify-commit subject bug-pfx refactor-pfx)})))))

(defn- parse-numstat-log
  "Parse 'git log --numstat --format=%H' output.
   Returns map of commit-hash -> #{file-paths}."
  [log-text]
  (:result
   (reduce
    (fn [{:keys [current-hash result]} line]
      (cond
        (str/blank? line)
        {:current-hash current-hash :result result}

        ;; numstat line: <added>\t<deleted>\t<file>  (binary files show -\t-\t<file>)
        (re-matches #"^[\d-]+\t[\d-]+\t.+" line)
        (if current-hash
          (let [parts (str/split line #"\t" 3)
                file  (nth parts 2 nil)]
            {:current-hash current-hash
             :result       (if file
                             (update result current-hash (fnil conj #{}) file)
                             {:current-hash current-hash :result result})})
          {:current-hash nil :result result})

        ;; Must be a commit hash line
        :else
        {:current-hash (str/trim line) :result result}))
    {:current-hash nil :result {}}
    (str/split-lines log-text))))

(defn analyze!
  "Run technical debt analysis on a git repository.

   Options:
     :path              (required) Absolute path to the git repository
     :from-date         (optional) ISO date string e.g. '2023-01-01'
     :to-date           (optional) ISO date string
     :bug-prefixes      (optional) List of commit-subject prefixes → bug fix
     :refactor-prefixes (optional) List of commit-subject prefixes → refactoring

   Returns:
     {:summary  {:total-commits :bug-commits :refactor-commits :feature-commits
                 :bug-pct :refactor-pct :feature-pct :overall-debt-score}
      :columns  [\"entity\" \"total-commits\" \"bug-commits\" \"refactor-commits\"
                 \"feature-commits\" \"debt-score\" \"refactor-score\"]
      :rows     [[entity total bug refact feat debt-pct refact-pct] ...]}"
  [{:keys [path from-date to-date bug-prefixes refactor-prefixes]}]
  (let [bug-pfx    (or (seq bug-prefixes) default-bug-prefixes)
        refact-pfx (or (seq refactor-prefixes) default-refactor-prefixes)
        date-args  (cond-> []
                     from-date (conj (str "--after=" from-date))
                     to-date   (conj (str "--before=" to-date)))

        subject-log (run-git! path (concat ["log" "--all" "--format=%H|%s"] date-args))
        commits     (parse-subject-log subject-log bug-pfx refact-pfx)
        type-map    (into {} (map (fn [c] [(:hash c) (:type c)]) commits))

        numstat-log (run-git! path (concat ["log" "--all" "--numstat" "--format=%H"] date-args))
        file-map    (parse-numstat-log numstat-log)

        ;; Build per-file counters
        file-stats
        (reduce (fn [acc [hash files]]
                  (let [t (get type-map hash :feature)]
                    (reduce (fn [a file]
                              (let [e (or (get a file)
                                          {:entity    file :total    0 :bugs 0
                                           :refactors 0    :features 0})]
                                (assoc a file
                                       (-> e
                                           (update :total inc)
                                           (update ({:bug      :bugs
                                                     :refactor :refactors
                                                     :feature  :features} t :features)
                                                   inc)))))
                            acc
                            files)))
                {}
                file-map)

        safe-pct (fn [n d] (if (zero? d) 0.0 (* 100.0 (/ (double n) d))))

        files-scored
        (->> (vals file-stats)
             (map (fn [{:keys [total bugs refactors] :as f}]
                    (assoc f
                           :debt-score     (safe-pct bugs total)
                           :refactor-score (safe-pct refactors total))))
             (sort-by (comp - :debt-score))
             (take 200))

        total-c  (count commits)
        bug-c    (count (filter #(= :bug (:type %)) commits))
        refact-c (count (filter #(= :refactor (:type %)) commits))
        feat-c   (count (filter #(= :feature (:type %)) commits))

        columns ["entity" "total-commits" "bug-commits" "refactor-commits"
                 "feature-commits" "debt-score" "refactor-score"]
        rows    (mapv (fn [{:keys [entity total bugs refactors features
                                   debt-score refactor-score]}]
                        [entity total bugs refactors features
                         (Math/round (double debt-score))
                         (Math/round (double refactor-score))])
                      files-scored)]

    {:summary {:total-commits      total-c
               :bug-commits        bug-c
               :refactor-commits   refact-c
               :feature-commits    feat-c
               :bug-pct            (safe-pct bug-c total-c)
               :refactor-pct       (safe-pct refact-c total-c)
               :feature-pct        (safe-pct feat-c total-c)
               :overall-debt-score (safe-pct bug-c total-c)}
     :columns columns
     :rows    rows}))
