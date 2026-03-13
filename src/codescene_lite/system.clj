(ns codescene-lite.system
  "Integrant system — component lifecycle definitions.
   Each ig/init-key method starts a component; ig/halt-key stops it."
  (:require [integrant.core :as ig]
            [org.httpkit.server :as httpkit]
            [clojure.java.io :as io]
            [codescene-lite.store.edn-store :as edn-store]
            [codescene-lite.router :as router])
  (:import [java.util.concurrent Executors]))

(defn config
  "Load the Integrant system config from resources/config.edn."
  []
  (-> (io/resource "config.edn")
      slurp
      (ig/read-string)))

;; ── EDN Store ─────────────────────────────────────────────────────────────

(defmethod ig/init-key :codescene-lite/store [_ opts]
  (println "Starting EDN store in:" (:data-dir opts))
  (edn-store/create-store opts))

(defmethod ig/halt-key! :codescene-lite/store [_ _store]
  (println "Stopping EDN store"))

;; ── Job Queue ─────────────────────────────────────────────────────────────

(defmethod ig/init-key :codescene-lite/job-queue [_ {:keys [max-threads]}]
  (println "Starting job queue with" max-threads "threads")
  (Executors/newFixedThreadPool max-threads))

(defmethod ig/halt-key! :codescene-lite/job-queue [_ executor]
  (println "Shutting down job queue")
  (.shutdown executor))

;; ── Router ────────────────────────────────────────────────────────────────

(defmethod ig/init-key :codescene-lite/router [_ {:keys [store job-queue]}]
  (println "Creating router")
  (router/create-handler store job-queue))

(defmethod ig/halt-key! :codescene-lite/router [_ _]
  nil)

;; ── Http-kit Server ───────────────────────────────────────────────────────

(defmethod ig/init-key :codescene-lite/server [_ {:keys [port host join? handler]}]
  (println (str "Starting Http-kit server on " host ":" port))
  (httpkit/run-server handler {:port port :ip host :join? (boolean join?)}))

(defmethod ig/halt-key! :codescene-lite/server [_ stop-fn]
  (println "Stopping Http-kit server")
  (when stop-fn (stop-fn)))
