(ns codescene-lite.api.jobs
  "HTTP handlers for /api/jobs — async job polling."
  (:require [codescene-lite.store.edn-store :as store]))

(defn get-job [store]
  (fn [{{:keys [id]} :path-params}]
    (if-let [job (store/get-job store id)]
      {:status 200 :body (dissoc job :options)} ; don't leak full options to client
      {:status 404 :body {:error (str "Job not found: " id)}})))
