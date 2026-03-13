;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.code-health-test
  (:require [code-maat.analysis.code-health :as code-health]
            [incanter.core :as incanter]
            [clojure.test :refer [deftest is]]))

(def test-data
  "Test data representing files with varying health characteristics"
  [;; FileA: Poor health - large, frequently changed, one developer
   {:entity "FileA.clj" :rev "r1" :author "alice" :loc-added "500" :loc-deleted "10"}
   {:entity "FileA.clj" :rev "r2" :author "alice" :loc-added "300" :loc-deleted "20"}
   {:entity "FileA.clj" :rev "r3" :author "alice" :loc-added "200" :loc-deleted "15"}
   {:entity "FileA.clj" :rev "r4" :author "alice" :loc-added "100" :loc-deleted "5"}
   {:entity "FileA.clj" :rev "r5" :author "bob" :loc-added "50" :loc-deleted "0"}

   ;; FileB: Better health - smaller, fewer changes, distributed knowledge
   {:entity "FileB.clj" :rev "r1" :author "alice" :loc-added "50" :loc-deleted "5"}
   {:entity "FileB.clj" :rev "r2" :author "bob" :loc-added "40" :loc-deleted "10"}
   {:entity "FileB.clj" :rev "r3" :author "charlie" :loc-added "30" :loc-deleted "5"}

   ;; FileC: Best health - small, infrequent changes, well distributed
   {:entity "FileC.clj" :rev "r1" :author "alice" :loc-added "20" :loc-deleted "0"}
   {:entity "FileC.clj" :rev "r2" :author "bob" :loc-added "15" :loc-deleted "0"}])

(def test-ds (incanter/to-dataset test-data))

(defn content-of [ds]
  (:rows (incanter/sel ds :rows :all)))

(deftest calculates-health-scores
  (let [result (code-health/by-score test-ds {})
        rows (content-of result)]

    ;; Should have 3 entities
    (is (= 3 (count rows)))

    ;; All should have health scores
    (doseq [row rows]
      (is (contains? row :health-score))
      (is (>= (:health-score row) 0))
      (is (<= (:health-score row) 100)))))

(deftest includes-component-scores
  (let [result (code-health/by-score test-ds {})
        rows (content-of result)
        file-a (first (filter #(= "FileA.clj" (:entity %)) rows))]

    ;; Should have all component scores
    (is (contains? file-a :hotspot-health))
    (is (contains? file-a :knowledge-health))
    (is (contains? file-a :coupling-health))

    ;; All components should be 0-100
    (is (>= (:hotspot-health file-a) 0))
    (is (<= (:hotspot-health file-a) 100))
    (is (>= (:knowledge-health file-a) 0))
    (is (<= (:knowledge-health file-a) 100))
    (is (>= (:coupling-health file-a) 0))
    (is (<= (:coupling-health file-a) 100))))

(deftest worst-health-file-comes-first
  (let [result (code-health/by-score test-ds {})
        rows (content-of result)
        first-file (:entity (first rows))
        last-file (:entity (last rows))]

    ;; FileA (worst health) should come first
    (is (= "FileA.clj" first-file))

    ;; FileC (best health) should come last
    (is (= "FileC.clj" last-file))))

(deftest health-scores-reflect-risk-factors
  (let [result (code-health/by-score test-ds {})
        rows (content-of result)
        file-a (first (filter #(= "FileA.clj" (:entity %)) rows))
        file-c (first (filter #(= "FileC.clj" (:entity %)) rows))]

    ;; FileA (high risk) must be strictly worse than FileC (low risk)
    (is (< (:health-score file-a) (:health-score file-c)))))

(deftest requires-churn-data
  (let [no-churn-data [{:entity "File.clj" :rev "r1" :author "dev1"}]
        no-churn-ds (incanter/to-dataset no-churn-data)]

    (is (thrown? IllegalArgumentException
                 (code-health/by-score no-churn-ds {})))))
