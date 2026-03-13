;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.code-health
  "Code health score: composite metric combining multiple quality factors.

   Health score ranges from 0 (critical) to 100 (excellent):
   - Size & Change Frequency (hotspot risk)
   - Knowledge Distribution (bus factor)

   This provides a single 'north star' metric for code quality
   and helps prioritize refactoring efforts."
  (:require [code-maat.dataset.dataset :as ds]
            [code-maat.analysis.hotspots :as hotspots]
            [code-maat.analysis.knowledge-loss :as knowledge-loss]
            [code-maat.analysis.math :as math]
            [incanter.core :as incanter]))

(defn- normalize-to-0-100
  "Normalize a value to 0-100 scale given min and max observed values.
   Higher input = lower health (inverted scale)"
  [value min-val max-val]
  (if (= min-val max-val)
    100.0  ; if all values are the same, no relative risk signal -> healthy by default
    (let [normalized (/ (- value min-val) (- max-val min-val))
          ;; Invert: high risk = low health
          health-score (* (- 1.0 normalized) 100.0)]
      health-score)))

(defn- safe-get
  "Safely get a value from a map, returning 0 if not found"
  [m k]
  (or (get m k) 0.0))

(defn- extract-metric-map
  "Convert analysis result to map of entity -> score"
  [ds entity-col score-col]
  (let [rows (:rows (incanter/sel ds :rows :all))]
    (into {}
          (map (fn [row]
                 [(get row entity-col)
                  (get row score-col)])
               rows))))

(defn- stats-for
  "Calculate min/max for a metric map, returning prefixed keys"
  [metric-map prefix]
  (let [values (vals metric-map)]
    (if (empty? values)
      {(keyword (str "min-" prefix)) 0.0 (keyword (str "max-" prefix)) 0.0}
      {(keyword (str "min-" prefix)) (apply min values)
       (keyword (str "max-" prefix)) (apply max values)})))

(defn- calculate-health-components
  "Calculate individual health component scores (0-100 scale)"
  [entity hotspot-map hotspot-stats knowledge-map knowledge-stats]
  (let [;; Hotspot component (60% weight) - size x change frequency
        hotspot-score (safe-get hotspot-map entity)
        hotspot-health (normalize-to-0-100
                        hotspot-score
                        (:min-hotspot hotspot-stats)
                        (:max-hotspot hotspot-stats))

        ;; Knowledge component (40% weight) - bus factor risk
        knowledge-risk (safe-get knowledge-map entity)
        knowledge-health (normalize-to-0-100
                          knowledge-risk
                          (:min-knowledge knowledge-stats)
                          (:max-knowledge knowledge-stats))

        ;; Weighted average: 60% hotspot + 40% knowledge
        health-score (math/ratio->centi-float-precision
                      (+ (* 0.60 hotspot-health)
                         (* 0.40 knowledge-health)))]

    {:entity entity
     :health-score health-score
     :hotspot-health hotspot-health
     :knowledge-health knowledge-health
     :coupling-health 100.0}))

(defn- run-component-analyses
  "Run hotspot and knowledge-loss analyses and extract metrics"
  [ds options]
  (try
    (let [hotspots-ds (hotspots/by-score ds options)
          knowledge-ds (knowledge-loss/by-risk ds options)

          hotspot-map (extract-metric-map hotspots-ds :entity :hotspot-score)
          knowledge-map (extract-metric-map knowledge-ds :entity :risk-score)

          hotspot-stats (stats-for hotspot-map "hotspot")
          knowledge-stats (stats-for knowledge-map "knowledge")]

      {:hotspot-map hotspot-map
       :hotspot-stats hotspot-stats
       :knowledge-map knowledge-map
       :knowledge-stats knowledge-stats})
    (catch Exception e
      (throw (ex-info "Failed to run component analyses for health score"
                      {:cause (.getMessage e)})))))

(defn by-score
  "Calculate code health score for each file.

   Returns dataset with columns:
   - entity: file path
   - health-score: composite 0-100 score (100=excellent, 0=critical)
   - hotspot-health: size/change frequency component (0-100)
   - knowledge-health: bus factor component (0-100)
   - coupling-health: architectural quality component (0-100, always 100)

   Health categories:
   - 80-100: Healthy (green)
   - 50-80:  Needs attention (yellow)
   - 0-50:   Critical (red)

   Files are sorted by health-score ascending (worst health first)."
  [ds options]
  ;; Check if we have the necessary data
  (let [columns (set (incanter/col-names ds))]
    (when (not (columns :loc-added))
      (throw
       (IllegalArgumentException.
        (str "code-health analysis: requires churn data (--numstat in git log). "
             "Use git2 log format with --numstat to include line change data.")))))

  ;; Run component analyses and calculate health
  (let [{:keys [hotspot-map hotspot-stats knowledge-map knowledge-stats]}
        (run-component-analyses ds options)

        ;; Get all unique entities
        all-entities (distinct (map :entity (:rows (incanter/sel ds :rows :all))))

        ;; Calculate health for each entity
        health-metrics (map (fn [entity]
                              (calculate-health-components
                               entity
                               hotspot-map hotspot-stats
                               knowledge-map knowledge-stats))
                            all-entities)]

    (->>
     health-metrics
     (ds/-dataset [:entity :health-score :hotspot-health :knowledge-health :coupling-health])
     (ds/-order-by :health-score :asc))))  ; worst health first
