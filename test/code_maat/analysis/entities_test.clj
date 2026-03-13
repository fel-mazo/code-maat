;;; Copyright (C) 2013 Adam Tornhill
;;;
;;; Distributed under the GNU General Public License v3.0,
;;; see http://www.gnu.org/licenses/gpl.html

(ns code-maat.analysis.entities-test
  (:require [code-maat.analysis.entities :as entities]
            [code-maat.analysis.test-data :as test-data]
            [clojure.test :refer [deftest is]]))

(deftest deduces-all-modified-entities
  (is (= (into #{} (entities/all test-data/vcsd))
         #{"A" "B"})))

(deftest sorts-entities-on-number-of-revisions
  (is (= (test-data/content-of (entities/by-revision
                                test-data/vcsd
                                test-data/options-with-low-thresholds))
         [{:n-revs 3 :entity "A"}
          {:n-revs 1, :entity "B"}])))

(deftest calculates-revisions-of-specific-entites
  (let [rg (entities/by-revision
            test-data/vcsd
            test-data/options-with-low-thresholds)]
    (is (= (entities/revisions-of "A" rg)
           3))
    (is (= (entities/revisions-of "B" rg)
           1))))