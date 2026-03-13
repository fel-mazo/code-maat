;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.knowledge-loss-test
  (:require [code-maat.analysis.knowledge-loss :as knowledge-loss]
            [incanter.core :as incanter])
  (:use clojure.test))

(def test-data
  "Test data with varying knowledge distribution patterns"
  [;; FileA: High risk - one developer dominates, many changes
   {:entity "FileA.clj" :rev "r1" :author "alice"}
   {:entity "FileA.clj" :rev "r2" :author "alice"}
   {:entity "FileA.clj" :rev "r3" :author "alice"}
   {:entity "FileA.clj" :rev "r4" :author "alice"}
   {:entity "FileA.clj" :rev "r5" :author "alice"}
   {:entity "FileA.clj" :rev "r6" :author "bob"}   ; only 1 change by bob

   ;; FileB: Medium risk - knowledge more evenly distributed
   {:entity "FileB.clj" :rev "r1" :author "alice"}
   {:entity "FileB.clj" :rev "r2" :author "alice"}
   {:entity "FileB.clj" :rev "r3" :author "bob"}
   {:entity "FileB.clj" :rev "r4" :author "bob"}
   {:entity "FileB.clj" :rev "r5" :author "charlie"}
   {:entity "FileB.clj" :rev "r6" :author "charlie"}

   ;; FileC: Low risk - single change only
   {:entity "FileC.clj" :rev "r1" :author "alice"}

   ;; FileD: Low risk - knowledge well distributed
   {:entity "FileD.clj" :rev "r1" :author "alice"}
   {:entity "FileD.clj" :rev "r2" :author "bob"}
   {:entity "FileD.clj" :rev "r3" :author "charlie"}
   {:entity "FileD.clj" :rev "r4" :author "dave"}
   {:entity "FileD.clj" :rev "r5" :author "eve"}])

(def test-ds (incanter/to-dataset test-data))

(defn content-of [ds]
  (:rows (incanter/sel ds :rows :all)))

(deftest identifies-high-risk-files
  (let [result (knowledge-loss/by-risk test-ds {})
        rows (content-of result)
        file-a (first (filter #(= "FileA.clj" (:entity %)) rows))]

    ;; FileA should have highest risk
    (is (= "FileA.clj" (:entity (first rows))))

    ;; FileA: 6 revisions, alice did 5/6 = 83% ownership
    (is (= "alice" (:main-dev file-a)))
    (is (= 6 (:n-revs file-a)))
    (is (> (:ownership file-a) 0.80))
    (is (< (:ownership file-a) 0.85))

    ;; Low fragmentation (concentrated knowledge)
    (is (< (:fragmentation file-a) 0.30))

    ;; High concentration (inverse of fragmentation)
    (is (> (:concentration file-a) 0.70))

    ;; Should have highest risk score
    (is (> (:risk-score file-a) 1.0))))

(deftest calculates-medium-risk-for-distributed-knowledge
  (let [result (knowledge-loss/by-risk test-ds {})
        rows (content-of result)
        file-b (first (filter #(= "FileB.clj" (:entity %)) rows))]

    ;; FileB: evenly distributed (alice 2, bob 2, charlie 2)
    (is (= 6 (:n-revs file-b)))

    ;; Main dev should be one of them (all equal, so first alphabetically or by count)
    (is (contains? #{"alice" "bob" "charlie"} (:main-dev file-b)))

    ;; Ownership should be ~33%
    (is (> (:ownership file-b) 0.30))
    (is (< (:ownership file-b) 0.35))

    ;; Higher fragmentation (distributed knowledge)
    (is (> (:fragmentation file-b) 0.60))

    ;; Lower concentration
    (is (< (:concentration file-b) 0.40))

    ;; Risk score should be moderate (less than FileA)
    (let [file-a (first (filter #(= "FileA.clj" (:entity %)) rows))]
      (is (< (:risk-score file-b) (:risk-score file-a))))))

(deftest assigns-zero-risk-to-single-revision-files
  (let [result (knowledge-loss/by-risk test-ds {})
        rows (content-of result)
        file-c (first (filter #(= "FileC.clj" (:entity %)) rows))]

    ;; FileC: only 1 revision
    (is (= 1 (:n-revs file-c)))
    (is (= 0.0 (:risk-score file-c)))))

(deftest identifies-well-distributed-knowledge
  (let [result (knowledge-loss/by-risk test-ds {})
        rows (content-of result)
        file-d (first (filter #(= "FileD.clj" (:entity %)) rows))]

    ;; FileD: 5 developers, 5 revisions (perfect distribution)
    (is (= 5 (:n-revs file-d)))

    ;; Each person owns 20%
    (is (> (:ownership file-d) 0.18))
    (is (< (:ownership file-d) 0.22))

    ;; High fragmentation (well distributed)
    (is (> (:fragmentation file-d) 0.70))

    ;; Low concentration
    (is (< (:concentration file-d) 0.30))

    ;; Should have low risk (lowest of multi-revision files)
    (let [file-a (first (filter #(= "FileA.clj" (:entity %)) rows))
          file-b (first (filter #(= "FileB.clj" (:entity %)) rows))]
      (is (< (:risk-score file-d) (:risk-score file-b)))
      (is (< (:risk-score file-d) (:risk-score file-a))))))

(deftest sorts-by-risk-descending
  (let [result (knowledge-loss/by-risk test-ds {})
        rows (content-of result)
        risk-scores (map :risk-score rows)]

    ;; Should be sorted by risk score descending
    (is (= risk-scores (sort > risk-scores)))))
