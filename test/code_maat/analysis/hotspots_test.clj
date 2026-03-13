;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.hotspots-test
  (:require [code-maat.analysis.hotspots :as hotspots]
            [incanter.core :as incanter])
  (:use clojure.test))

(def test-churn-data
  "Test data with churn metrics for hotspot analysis"
  [{:entity "FileA.clj" :rev "r1" :author "dev1" :loc-added "100" :loc-deleted "10"}
   {:entity "FileA.clj" :rev "r2" :author "dev2" :loc-added "50"  :loc-deleted "20"}
   {:entity "FileA.clj" :rev "r3" :author "dev1" :loc-added "75"  :loc-deleted "5"}
   {:entity "FileB.clj" :rev "r1" :author "dev3" :loc-added "200" :loc-deleted "0"}
   {:entity "FileB.clj" :rev "r2" :author "dev3" :loc-added "50"  :loc-deleted "10"}
   {:entity "FileC.clj" :rev "r1" :author "dev2" :loc-added "10"  :loc-deleted "5"}])

(def test-churn-ds (incanter/to-dataset test-churn-data))

(defn content-of [ds]
  (:rows (incanter/sel ds :rows :all)))

(deftest calculates-hotspot-scores
  (let [result (hotspots/by-score test-churn-ds {})
        rows (content-of result)]
    ;; Should have 3 entities
    (is (= 3 (count rows)))

    ;; FileA: 3 revisions, 225 lines (100+50+75)
    ;; score = sqrt(225) * log(3) = 15 * 1.099 ≈ 16.48
    (let [file-a (first (filter #(= "FileA.clj" (:entity %)) rows))]
      (is (= 3 (:n-revs file-a)))
      (is (= 225 (:code-size file-a)))
      (is (> (:hotspot-score file-a) 16.0))
      (is (< (:hotspot-score file-a) 17.0)))

    ;; FileB: 2 revisions, 250 lines (200+50)
    ;; score = sqrt(250) * log(2) = 15.81 * 0.693 ≈ 10.95
    (let [file-b (first (filter #(= "FileB.clj" (:entity %)) rows))]
      (is (= 2 (:n-revs file-b)))
      (is (= 250 (:code-size file-b)))
      (is (> (:hotspot-score file-b) 10.0))
      (is (< (:hotspot-score file-b) 12.0)))

    ;; FileC: 1 revision, 10 lines
    ;; score = 0 (< 2 revisions)
    (let [file-c (first (filter #(= "FileC.clj" (:entity %)) rows))]
      (is (= 1 (:n-revs file-c)))
      (is (= 10 (:code-size file-c)))
      (is (= 0.0 (:hotspot-score file-c))))))

(deftest sorts-by-hotspot-score-descending
  (let [result (hotspots/by-score test-churn-ds {})
        rows (content-of result)
        entities (map :entity rows)]
    ;; FileA should be first (highest score), FileB second, FileC last (score=0)
    (is (= "FileA.clj" (first entities)))
    (is (= "FileB.clj" (second entities)))
    (is (= "FileC.clj" (last entities)))))

(deftest handles-binary-files
  (let [binary-data [{:entity "image.png" :rev "r1" :author "dev1" :loc-added "-" :loc-deleted "-"}
                     {:entity "image.png" :rev "r2" :author "dev1" :loc-added "100" :loc-deleted "0"}]
        binary-ds (incanter/to-dataset binary-data)
        result (hotspots/by-score binary-ds {})
        rows (content-of result)]
    ;; Binary files marked with "-" should be treated as 0 lines
    (is (= 1 (count rows)))
    (let [file (first rows)]
      (is (= 2 (:n-revs file)))
      (is (= 100 (:code-size file))))))

(deftest throws-on-missing-churn-data
  (let [no-churn-data [{:entity "File.clj" :rev "r1" :author "dev1"}]
        no-churn-ds (incanter/to-dataset no-churn-data)]
    (is (thrown? IllegalArgumentException
                 (hotspots/by-score no-churn-ds {})))))
