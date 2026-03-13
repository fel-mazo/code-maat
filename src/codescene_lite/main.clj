(ns codescene-lite.main
  "Production entry point — starts the Integrant system from config.edn."
  (:require [integrant.core :as ig]
            [codescene-lite.system :as system])
  (:gen-class))

(defn -main [& _args]
  (println "Starting codescene-lite...")
  (let [cfg (system/config)]
    (ig/init cfg))
  (println "codescene-lite running at http://localhost:3000")
  @(promise)) ; block forever
