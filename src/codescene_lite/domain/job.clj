(ns codescene-lite.domain.job
  "Job entity — state machine for async analyses."
  (:import [java.util UUID]))

(defn create
  "Create a new job in :queued state."
  [repo-id analysis-name options]
  {:id          (str (UUID/randomUUID))
   :repo-id     repo-id
   :analysis    analysis-name
   :options     options
   :status      :queued
   :created-at  (str (java.time.Instant/now))
   :result      nil
   :error       nil})

(defn running [job]
  (assoc job :status :running :started-at (str (java.time.Instant/now))))

(defn done [job result]
  (assoc job :status :done :result result :finished-at (str (java.time.Instant/now))))

(defn failed [job error-msg]
  (assoc job :status :error :error error-msg :finished-at (str (java.time.Instant/now))))
