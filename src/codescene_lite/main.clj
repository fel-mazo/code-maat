(ns codescene-lite.main
  "Production entry point — starts the Integrant system from config.edn."
  (:require [integrant.core :as ig]
            [codescene-lite.system :as system])
  (:gen-class))

(defn -main [& _args]
  (println "Starting codescene-lite...")
  (let [cfg     (system/config)
        ;; Allow overriding data-dir via system property for Docker deployments:
        ;; -Dcodescene.data-dir=/data
        data-dir (System/getProperty "codescene.data-dir")
        cfg     (cond-> cfg
                  data-dir (assoc-in [:codescene-lite/store :data-dir] data-dir))]
    (ig/init cfg))
  (println "codescene-lite running at http://localhost:7777")
  @(promise)) ; block forever
