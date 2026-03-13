;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.hotspots
  "Hotspot analysis: identifies files that are both complex/large AND frequently changed.
   These represent the highest-risk areas of technical debt in your codebase.

   Formula: hotspot_score = sqrt(code_size) * log(revisions)

   - code_size: total lines added (proxy for complexity)
   - revisions: number of times the file has been modified"
  (:require [code-maat.dataset.dataset :as ds]
            [incanter.core :as incanter]))

(defn- as-int
  "Binaries are given as a dash. Ensure these are replaced by zeros."
  [v]
  (Integer/parseInt
   (if (= "-" v) "0" v)))

(defn- total-added-lines
  "Calculate total lines added for an entity across all revisions"
  [ds]
  (reduce + (map as-int (ds/-select-by :loc-added ds))))

(defn- calculate-hotspot-score
  "Calculate hotspot score using CodeScene's formula:
   score = sqrt(code_size) * log(revisions)

   Returns 0 for entities with < 2 revisions (log not meaningful)"
  [code-size revisions]
  (if (< revisions 2)
    0.0
    (* (Math/sqrt (double code-size))
       (Math/log (double revisions)))))

(defn- entity-metrics
  "Extract hotspot metrics for a single entity group"
  [[entity-group changes]]
  (let [entity (:entity entity-group)
        revisions (count (distinct (ds/-select-by :rev changes)))
        code-size (total-added-lines changes)
        score (calculate-hotspot-score code-size revisions)]
    [entity revisions code-size score]))

(defn by-score
  "Analyze files by hotspot score (combines change frequency with code size).

   Returns dataset with columns:
   - entity: file path
   - n-revs: number of revisions (change frequency)
   - code-size: total lines added (proxy for complexity)
   - hotspot-score: composite risk metric

   Files are sorted by hotspot-score in descending order (highest risk first)."
  [ds _options]
  ;; Check if we have the necessary churn data
  (let [columns (set (incanter/col-names ds))]
    (when (not (columns :loc-added))
      (throw
       (IllegalArgumentException.
        (str "hotspots analysis: the given VCS data doesn't contain modification metrics. "
             "Use git2 log format with --numstat to include line change data.")))))

  (->>
   ds
   (ds/-group-by :entity)
   (map entity-metrics)
   (ds/-dataset [:entity :n-revs :code-size :hotspot-score])
   (ds/-order-by :hotspot-score :desc)))
