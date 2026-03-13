(ns code-maat.end-to-end.churn-scenario-test
  (:require [code-maat.app.app :as app]
            [clojure.test :refer [deftest is]]))

;;; This module contains end-to-end tests running the whole app
;;; from front-end to back-end with respect to code churn.

(def ^:const git-log-file "./test/code_maat/end_to_end/simple_git.txt")

(defn- run-with-str-output [log-file options]
  (with-out-str
    (app/run log-file options)))

(deftest calculates-absolute-churn
  (is (= (run-with-str-output git-log-file
                              {:version-control "git"
                               :analysis "abs-churn"})
         "date,added,deleted,commits\n2013-02-07,18,2,1\n2013-02-08,4,6,1\n")))

(deftest calculates-churn-by-author
  (is (= (run-with-str-output git-log-file
                              {:version-control "git"
                               :analysis "author-churn"})
         "author,added,deleted,commits\nAPT,4,6,1\nXYZ,18,2,1\n")))

(deftest calculates-churn-by-entity
  (is (= (run-with-str-output git-log-file
                              {:version-control "git"
                               :analysis "entity-churn"})
         "entity,added,deleted,commits\n/Infrastrucure/Network/Connection.cs,19,4,2\n/Presentation/Status/ClientPresenter.cs,3,4,1\n")))

(deftest calculates-ownership-by-churn
  (is (= (run-with-str-output git-log-file
                              {:version-control "git"
                               :analysis "entity-ownership"})
         "entity,author,added,deleted\n/Infrastrucure/Network/Connection.cs,APT,1,2\n/Infrastrucure/Network/Connection.cs,XYZ,18,2\n/Presentation/Status/ClientPresenter.cs,APT,3,4\n")))
