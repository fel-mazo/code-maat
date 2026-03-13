(ns code-maat.app.cmd-line-test
    (:require [clojure.test :refer [deftest is testing]]
              [clojure.tools.cli :as cli]
              [code-maat.cmd-line :refer [cli-options]]))


(deftest test-argument-parsing
         (testing "simple cmd line parsing"
                  (let [args ["-l some_file.log"]
                        parsed-options (cli/parse-opts args cli-options)]

                       (is (nil? (:errors parsed-options))))))
