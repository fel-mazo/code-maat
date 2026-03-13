(ns codescene-lite.domain.repository
  "Repository entity — creation, validation, and cache-key generation."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util UUID]))

(def default-bug-prefixes
  ["fix" "bug" "hotfix" "bugfix" "patch" "revert" "issue" "defect" "error"])

(def default-refactor-prefixes
  ["refactor" "chore" "cleanup" "clean" "debt" "improve" "perf" "simplif" "restructur"])

(defn create
  "Build a new repository map from user-supplied fields.
   Always uses git2 VCS format."
  [{:keys [name path description bug-prefixes refactor-prefixes]
    :or   {description ""}}]
  {:id                 (str (UUID/randomUUID))
   :name               name
   :path               path
   :vcs                "git2"
   :description        (or description "")
   :bug-prefixes       (or (seq bug-prefixes) default-bug-prefixes)
   :refactor-prefixes  (or (seq refactor-prefixes) default-refactor-prefixes)
   :created-at         (str (java.time.Instant/now))})

(defn valid? [{:keys [name path]}]
  (and (seq name) (seq path)))

(defn path-exists? [{:keys [path]}]
  (.exists (io/file path)))

(defn cache-key
  "Derives a cache key for a given analysis + options combination.
   Nil and empty-string values are excluded."
  [analysis-name options]
  (let [relevant (->> (dissoc options :analysis :version-control :log-file)
                      (remove (fn [[_ v]] (or (nil? v) (= "" v))))
                      (into {}))]
    (if (empty? relevant)
      analysis-name
      (let [pairs (->> relevant
                       (sort-by key)
                       (map (fn [[k v]] (str (name k) "=" v)))
                       (str/join "|"))]
        (str analysis-name "|" pairs)))))
