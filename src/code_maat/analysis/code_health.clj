;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.code-health
  "Code health score: composite metric combining multiple quality factors.

   Health score ranges from 0 (critical) to 100 (excellent):
   - Size & Change Frequency (hotspot risk)
   - Knowledge Distribution (bus factor)
   - Coupling (architectural quality)

   This provides a single 'north star' metric for code quality
   and helps prioritize refactoring efforts."
  (:require [code-maat.dataset.dataset :as ds]
            [code-maat.analysis.hotspots :as hotspots]
            [code-maat.analysis.knowledge-loss :as knowledge-loss]
            [code-maat.analysis.sum-of-coupling :as soc]
            [code-maat.analysis.math :as math]
            [incanter.core :as incanter]))

(defn- normalize-to-0-100
  "Normalize a value to 0-100 scale given min and max observed values.
   Higher input = lower health (inverted scale)"
  [value min-val max-val]
  (if (= min-val max-val)
    50.0  ; if all values are the same, return neutral score
    (let [normalized (/ (- value min-val) (- max-val min-val))
          ;; Invert: high risk = low health
          health-score (* (- 1.0 normalized) 100.0)]
      health-score)))

(defn- safe-get
  "Safely get a value from a map, returning 0 if not found"
  [m k]
  (or (get m k) 0.0))

(defn- calculate-health-components
  "Calculate individual health component scores (0-100 scale)"
  [entity-metrics hotspot-stats knowledge-stats coupling-stats]
  (let [entity (:entity entity-metrics)

        ;; Hotspot component (40% weight) - size × change frequency
        hotspot-score (safe-get hotspot-stats entity)
        hotspot-health (normalize-to-0-100
                         hotspot-score
                         (:min-hotspot hotspot-stats)
                         (:max-hotspot hotspot-stats))

        ;; Knowledge component (30% weight) - bus factor risk
        knowledge-risk (safe-get knowledge-stats entity)
        knowledge-health (normalize-to-0-100
                           knowledge-risk
                           (:min-knowledge knowledge-stats)
                           (:max-knowledge knowledge-stats))

        ;; Coupling component (30% weight) - architectural quality
        coupling-score (safe-get coupling-stats entity)
        coupling-health (normalize-to-0-100
                          coupling-score
                          (:min-coupling coupling-stats)
                          (:max-coupling coupling-stats))

        ;; Weighted average (configurable weights)
        health-score (math/ratio->centi-float-precision
                       (+ (* 0.40 hotspot-health)
                          (* 0.30 knowledge-health)
                          (* 0.30 coupling-health)))]

    {:entity entity
     :health-score health-score
     :hotspot-health hotspot-health
     :knowledge-health knowledge-health
     :coupling-health coupling-health}))

(defn- extract-metric-map
  "Convert analysis result to map of entity -> score"
  [ds entity-col score-col]
  (let [rows (ds/-select-by :all ds)]
    (into {}
      (map (fn [row]
             [(incanter/$ entity-col row)
              (incanter/$ score-col row)])
        rows))))

(defn- calculate-stats
  "Calculate min/max for a metric map"
  [metric-map]
  (let [values (vals metric-map)]
    (if (empty? values)
      {:min 0.0 :max 1.0}  ; default stats if no data
      {:min (apply min values)
       :max (apply max values)})))

(defn- run-component-analyses
  "Run all component analyses and extract metrics"
  [ds options]
  (try
    ;; Run component analyses
    (let [hotspots-ds (hotspots/by-score ds options)
          knowledge-ds (knowledge-loss/by-risk ds options)
          coupling-ds (try
                        (soc/by-degree ds options)
                        (catch Exception _
                          ;; If coupling fails (no coupling data), return empty dataset with no rows
                          (incanter/to-dataset [])))

          ;; Extract metric maps
          hotspot-map (extract-metric-map hotspots-ds :entity :hotspot-score)
          knowledge-map (extract-metric-map knowledge-ds :entity :risk-score)
          coupling-map (extract-metric-map coupling-ds :entity :soc)

          ;; Calculate statistics for normalization
          hotspot-stats (merge (calculate-stats hotspot-map)
                          {:min-hotspot (:min (calculate-stats hotspot-map))
                           :max-hotspot (:max (calculate-stats hotspot-map))})
          knowledge-stats (merge (calculate-stats knowledge-map)
                            {:min-knowledge (:min (calculate-stats knowledge-map))
                             :max-knowledge (:max (calculate-stats knowledge-map))})
          coupling-stats (merge (calculate-stats coupling-map)
                           {:min-coupling (:min (calculate-stats coupling-map))
                            :max-coupling (:max (calculate-stats coupling-map))})]

      {:hotspot-map hotspot-map
       :knowledge-map knowledge-map
       :coupling-map coupling-map
       :hotspot-stats (merge hotspot-stats hotspot-map)
       :knowledge-stats (merge knowledge-stats knowledge-map)
       :coupling-stats (merge coupling-stats coupling-map)})
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
   - coupling-health: architectural quality component (0-100)

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
  (let [analyses (run-component-analyses ds options)
        hotspot-stats (:hotspot-stats analyses)
        knowledge-stats (:knowledge-stats analyses)
        coupling-stats (:coupling-stats analyses)

        ;; Get all unique entities
        all-entities (distinct (ds/-select-by :entity ds))

        ;; Calculate health for each entity
        health-metrics (map (fn [entity]
                              (calculate-health-components
                                {:entity entity}
                                hotspot-stats
                                knowledge-stats
                                coupling-stats))
                         all-entities)]

    (->>
      health-metrics
      (ds/-dataset [:entity :health-score :hotspot-health :knowledge-health :coupling-health])
      (ds/-order-by :health-score :asc))))  ; worst health first
