;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.knowledge-loss
  "Knowledge loss risk analysis: identifies files with concentrated knowledge (bus factor = 1).

   These are files where:
   - Knowledge is concentrated in few developers (low fragmentation)
   - One person dominates (high ownership)
   - The file is actively maintained (frequent changes)

   Formula: risk_score = (1 - fragmentation) * ownership * log(revisions)

   This helps identify knowledge silos and succession planning needs."
  (:require [code-maat.dataset.dataset :as ds]
            [code-maat.analysis.effort :as effort]
            [code-maat.analysis.math :as math]))

(defn- calculate-risk-score
  "Calculate knowledge loss risk score.

   - concentration: 1 - fragmentation (0 to 1, higher = more concentrated)
   - ownership: percentage owned by main dev (0 to 1)
   - revisions: number of changes

   Returns 0 for files with < 2 revisions (log not meaningful)"
  [concentration ownership revisions]
  (if (< revisions 2)
    0.0
    (* concentration
       ownership
       (Math/log (double revisions)))))

(defn- contributed-revs
  [[_author added _total]]
  added)

(defn- as-author-fractals
  [[_ ai nc]]
  (Math/pow (/ ai (double nc)) 2))

(defn- calculate-fragmentation
  "Calculate fragmentation value (0 = one author, 1 = many authors)"
  [author-revs]
  (let [[_author _added total-revs] (first author-revs)
        fv1 (reduce + (map as-author-fractals author-revs))
        fv (math/ratio->centi-float-precision (- 1 fv1))]
    fv))

(defn- sum-revs-by-author
  "Group revisions by author for an entity"
  [changes]
  (let [total-revs (ds/-nrows changes)
        author-groups (ds/-group-by :author changes)]
    (for [[group-entry author-changes] author-groups
          :let [author (:author group-entry)
                author-revs (ds/-nrows author-changes)]]
      [author author-revs total-revs])))

(defn- entity-knowledge-metrics
  "Calculate knowledge metrics for a single entity"
  [[entity-group changes]]
  (let [entity (:entity entity-group)
        total-revs (ds/-nrows changes)
        author-revs (sum-revs-by-author changes)

        ;; Sort by contribution to find main dev
        sorted-contribs (sort-by contributed-revs > author-revs)
        [main-dev main-dev-revs _total] (first sorted-contribs)
        ownership (math/ratio->centi-float-precision (/ main-dev-revs (double total-revs)))

        ;; Calculate fragmentation (knowledge spread)
        fragmentation (calculate-fragmentation author-revs)
        concentration (math/ratio->centi-float-precision (- 1 fragmentation))

        ;; Calculate risk score
        risk-score (math/ratio->centi-float-precision
                    (calculate-risk-score concentration ownership total-revs))]

    [entity main-dev total-revs fragmentation concentration ownership risk-score]))

(defn by-risk
  "Analyze knowledge loss risk for each file.

   Returns dataset with columns:
   - entity: file path
   - main-dev: primary developer
   - n-revs: number of revisions
   - fragmentation: knowledge spread (0=concentrated, 1=distributed)
   - concentration: inverse of fragmentation (0=distributed, 1=concentrated)
   - ownership: % of work by main dev
   - risk-score: composite risk metric

   Files are sorted by risk-score in descending order (highest risk first).
   High risk = one person knows the code + file is actively changed."
  [ds options]
  (->>
   ds
   (ds/-group-by :entity)
   (map entity-knowledge-metrics)
   (ds/-dataset [:entity :main-dev :n-revs :fragmentation :concentration :ownership :risk-score])
   (ds/-order-by :risk-score :desc)))
