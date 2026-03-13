(ns codescene-lite.api.analysis
  "HTTP handlers for /api/repos/:id/analyze and /api/analyses."
  (:require [codescene-lite.store.edn-store :as store]
            [codescene-lite.domain.repository :as repo]
            [codescene-lite.domain.job :as job]
            [codescene-lite.engine.git-log :as git-log]
            [codescene-lite.engine.runner :as runner]
            [codescene-lite.engine.metadata :as meta]))

;; ── Analysis metadata endpoint ────────────────────────────────────────────

(defn list-analyses [_]
  (fn [_request]
    {:status 200 :body meta/analysis-metadata}))

;; ── Synchronous analysis ──────────────────────────────────────────────────

(defn- run-sync!
  "Runs the analysis synchronously and returns the result map.
   Also caches the result in the store."
  [store repo analysis-name engine-opts cache-key]
  (let [log-text   (git-log/generate-log
                    {:path      (:path repo)
                     :from-date (:from-date engine-opts)
                     :to-date   (:to-date engine-opts)})
        log-path   (git-log/write-log-to-temp-file! log-text)
        result     (runner/run-analysis log-path
                                        (merge {:analysis        analysis-name
                                                :version-control (:vcs repo)}
                                               engine-opts))
        cached     (assoc result
                          :analysis   analysis-name
                          :repo-id    (:id repo)
                          :cached-at  (str (java.time.Instant/now)))]
    (store/save-result! store (:id repo) cache-key cached)
    cached))

;; ── Asynchronous analysis (via job queue) ────────────────────────────────

(defn- submit-async!
  "Submits the analysis to the job queue and returns the job-id immediately."
  [store job-queue repo analysis-name engine-opts cache-key]
  (let [j (job/create (:id repo) analysis-name engine-opts)]
    (store/save-job! store j)
    (.submit job-queue
             ^Runnable
             (fn []
               (store/update-job! store (:id j) job/running)
               (try
                 (store/update-job! store (:id j) job/set-phase :generating-log)
                 (let [log-text (git-log/generate-log
                                 {:path      (:path repo)
                                  :from-date (:from-date engine-opts)
                                  :to-date   (:to-date engine-opts)})
                       log-path (git-log/write-log-to-temp-file! log-text)
                       _        (store/update-job! store (:id j) job/set-phase :analyzing)
                       result   (runner/run-analysis log-path
                                                     (merge {:analysis        analysis-name
                                                             :version-control (:vcs repo)}
                                                            engine-opts))
                       cached   (assoc result
                                       :analysis  analysis-name
                                       :repo-id   (:id repo)
                                       :cached-at (str (java.time.Instant/now)))]
                   (store/save-result! store (:id repo) cache-key cached)
                   (store/update-job! store (:id j) job/done cached))
                 (catch Exception e
                   (store/update-job! store (:id j) job/failed (.getMessage e))))))
    {:job-id (:id j)}))

;; ── Main handler ─────────────────────────────────────────────────────────

(defn run-analysis [store job-queue]
  (fn [{{:keys [id]} :path-params
        {:keys [analysis from-date to-date skip-cache]
         :as   body}  :body-params}]
    (let [r (store/get-repo store id)]
      (cond
        (nil? r)
        {:status 404 :body {:error (str "Repository not found: " id)}}

        (nil? (get meta/analysis-metadata analysis))
        {:status 400 :body {:error (str "Unknown analysis: " analysis
                                        ". Valid: " (sort (keys meta/analysis-metadata)))}}

        :else
        (let [engine-opts (-> (dissoc body :analysis :from-date :to-date :skip-cache)
                              (assoc :from-date from-date :to-date to-date))
              cache-key   (repo/cache-key analysis engine-opts)
              cached      (when-not skip-cache
                            (store/get-result store id cache-key))]
          (if cached
            {:status 200 :body (assoc cached :from-cache true)}
            (if (meta/async? analysis)
              {:status 202 :body (submit-async! store job-queue r analysis engine-opts cache-key)}
              (try
                {:status 200 :body (run-sync! store r analysis engine-opts cache-key)}
                (catch Exception e
                  {:status 500 :body {:error (.getMessage e)}})))))))))
