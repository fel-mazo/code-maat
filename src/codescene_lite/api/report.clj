(ns codescene-lite.api.report
  "Combined full-repository analysis report.
   Runs all key analyses in sequence and returns a unified structure
   that the frontend renders as a single-page CTO dashboard."
  (:require [codescene-lite.store.edn-store :as store]
            [codescene-lite.domain.job :as job]
            [codescene-lite.engine.git-log :as git-log]
            [codescene-lite.engine.runner :as runner]
            [codescene-lite.engine.technical-debt :as td]))

(defn- safe-run
  "Run a code-maat analysis, returning {:error msg :columns [] :rows []} on failure."
  [log-path analysis vcs opts]
  (try
    (runner/run-analysis log-path (merge {:analysis analysis :version-control vcs} opts))
    (catch Exception e
      {:error (.getMessage e) :columns [] :rows []})))

(defn run-report!
  "Execute all analyses for a repo and return a merged report map."
  [repo {:keys [from-date to-date bug-prefixes refactor-prefixes]}]
  (let [vcs         (or (:vcs repo) "git2")
        log-text    (git-log/generate-log {:path      (:path repo)
                                           :from-date from-date
                                           :to-date   to-date})
        log-path    (git-log/write-log-to-temp-file! log-text)
        engine-opts {:from-date from-date :to-date to-date}]
    {:summary        (safe-run log-path "summary"        vcs engine-opts)
     :revisions      (safe-run log-path "revisions"      vcs engine-opts)
     :hotspots       (safe-run log-path "hotspots"       vcs engine-opts)
     :knowledge-loss (safe-run log-path "knowledge-loss" vcs engine-opts)
     :coupling       (safe-run log-path "coupling"       vcs engine-opts)
     :code-health    (safe-run log-path "code-health"    vcs engine-opts)
     :abs-churn      (safe-run log-path "abs-churn"      vcs engine-opts)
     :technical-debt (try
                       (td/analyze! {:path              (:path repo)
                                     :from-date         from-date
                                     :to-date           to-date
                                     :bug-prefixes      bug-prefixes
                                     :refactor-prefixes refactor-prefixes})
                       (catch Exception e
                         {:error    (.getMessage e)
                          :summary  {}
                          :columns  []
                          :rows     []}))}))

(defn run-report [store job-queue]
  (fn [{{:keys [id]} :path-params body :body-params}]
    (let [r (store/get-repo store id)]
      (if (nil? r)
        {:status 404 :body {:error (str "Repository not found: " id)}}
        (let [{:keys [from-date to-date skip-cache
                      bug-prefixes refactor-prefixes]} body
              final-bug-pfx    (or (seq bug-prefixes)    (:bug-prefixes r))
              final-refact-pfx (or (seq refactor-prefixes) (:refactor-prefixes r))
              cache-key        (str "report"
                                    (when from-date (str "|from=" from-date))
                                    (when to-date   (str "|to=" to-date)))
              cached           (when-not skip-cache
                                 (store/get-result store id cache-key))]
          (if cached
            {:status 200 :body (assoc cached :from-cache true)}
            (let [j (job/create id "report" {})]
              (store/save-job! store j)
              (.submit job-queue
                       ^Runnable
                       (fn []
                         (store/update-job! store (:id j) job/running)
                         (try
                           (store/update-job! store (:id j) job/set-phase :analyzing)
                           (let [report (run-report!
                                         r {:from-date         from-date
                                            :to-date           to-date
                                            :bug-prefixes      final-bug-pfx
                                            :refactor-prefixes final-refact-pfx})
                                 result (assoc report
                                               :repo-id   id
                                               :cached-at (str (java.time.Instant/now)))]
                             (store/save-result! store id cache-key result)
                             (store/update-job! store (:id j) job/done result))
                           (catch Exception e
                             (store/update-job! store (:id j)
                                                job/failed (.getMessage e))))))
              {:status 202 :body {:job-id (:id j)}})))))))
