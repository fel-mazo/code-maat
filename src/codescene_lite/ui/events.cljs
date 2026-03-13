(ns codescene-lite.ui.events
  (:require
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [codescene-lite.ui.db :as db]))

;; ── Helpers ─────────────────────────────────────────────────────────────

(defn- xhrio-get [uri on-success on-failure]
  {:method          :get
   :uri             uri
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      on-success
   :on-failure      on-failure})

(defn- xhrio-post [uri body on-success on-failure]
  {:method          :post
   :uri             uri
   :params          body
   :format          (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      on-success
   :on-failure      on-failure})

(defn- xhrio-delete [uri on-success on-failure]
  {:method          :delete
   :uri             uri
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success      on-success
   :on-failure      on-failure})

;; ── App Init ─────────────────────────────────────────────────────────────

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-fx
 ::init
 (fn [{:keys [db]} _]
   {:db (or db db/default-db)
    :fx [[:dispatch [::load-repos]]
         [:dispatch [::load-analyses-meta]]]}))

;; ── Repos ────────────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::load-repos
 (fn [_ _]
   {:http-xhrio (xhrio-get "/api/repos"
                           [::repos-loaded]
                           [::http-error])}))

(rf/reg-event-db
 ::repos-loaded
 (fn [db [_ repos]]
   (let [repos-map (into {} (map (fn [r] [(:id r) r]) repos))]
     (assoc db :repos repos-map))))

;; ── Analyses Meta ────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::load-analyses-meta
 (fn [_ _]
   {:http-xhrio (xhrio-get "/api/analyses"
                           [::analyses-meta-loaded]
                           [::http-error])}))

(rf/reg-event-db
 ::analyses-meta-loaded
 (fn [db [_ analyses]]
   (let [meta-map (into {} (map (fn [[k v]] [k (assoc v :name k)]) analyses))]
     (assoc db :analyses-meta meta-map))))

;; ── Add Repo Form ─────────────────────────────────────────────────────────

(rf/reg-event-db
 ::open-add-repo-form
 (fn [db _]
   (assoc-in db [:ui :add-repo-form :open?] true)))

(rf/reg-event-db
 ::close-add-repo-form
 (fn [db _]
   (assoc-in db [:ui :add-repo-form]
             {:open? false :name "" :path "" :vcs "git2" :error nil})))

(rf/reg-event-db
 ::update-add-repo-field
 (fn [db [_ field value]]
   (assoc-in db [:ui :add-repo-form field] value)))

(rf/reg-event-fx
 ::submit-add-repo
 (fn [{:keys [db]} _]
   (let [form (get-in db [:ui :add-repo-form])
         {:keys [name path vcs]} form]
     (if (or (empty? name) (empty? path))
       {:db (assoc-in db [:ui :add-repo-form :error] "Name and path are required.")}
       {:http-xhrio (xhrio-post "/api/repos"
                                {:name name :path path :vcs vcs}
                                [::add-repo-success]
                                [::add-repo-failure])}))))

(rf/reg-event-fx
 ::add-repo-success
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:ui :add-repo-form]
                  {:open? false :name "" :path "" :vcs "git2" :error nil})
    :fx [[:dispatch [::load-repos]]]}))

(rf/reg-event-db
 ::add-repo-failure
 (fn [db [_ response]]
   (let [msg (or (get-in response [:response :error])
                 "Failed to add repo. Check the path and try again.")]
     (assoc-in db [:ui :add-repo-form :error] msg))))

;; ── Delete Repo ───────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::delete-repo
 (fn [_ [_ repo-id]]
   {:http-xhrio (xhrio-delete (str "/api/repos/" repo-id)
                              [::delete-repo-success repo-id]
                              [::http-error])}))

(rf/reg-event-fx
 ::delete-repo-success
 (fn [{:keys [db]} [_ repo-id _response]]
   (let [new-db (-> db
                    (update :repos dissoc repo-id)
                    (update :results (fn [r]
                                       (into {} (remove #(= repo-id (first (key %))) r)))))]
     (if (= (:active-repo-id new-db) repo-id)
       {:db (-> new-db
                (assoc :active-repo-id nil)
                (assoc-in [:ui :view] :repos))}
       {:db new-db}))))

;; ── Select Repo ───────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::select-repo
 (fn [{:keys [db]} [_ repo-id]]
   {:db (-> db
            (assoc :active-repo-id repo-id)
            (assoc-in [:ui :view] :repo-detail)
            (assoc-in [:ui :selected-analysis] nil)
            (assoc-in [:ui :analysis-options] {})
            (assoc-in [:ui :date-range] {:from "" :to ""})
            (assoc-in [:ui :skip-cache] false))
    :fx [[:dispatch [::cancel-polls]]
         [:dispatch [::load-cached-results repo-id]]]}))

(rf/reg-event-fx
 ::load-cached-results
 (fn [_ [_ repo-id]]
   {:http-xhrio (xhrio-get (str "/api/repos/" repo-id "/results")
                           [::cached-results-loaded repo-id]
                           [::http-error])}))

(rf/reg-event-db
 ::cached-results-loaded
 (fn [db [_ repo-id results]]
   ;; results is a list of {analysis-name cached-at} entries
   (reduce (fn [d r]
             (let [k [repo-id (:analysis-name r)]]
               (if (get-in d [:results k])
                 d
                 (assoc-in d [:results k]
                           {:status :cached :cached-at (:cached-at r) :data nil}))))
           db
           results)))

;; ── Analysis Selection ────────────────────────────────────────────────────

(rf/reg-event-db
 ::select-analysis
 (fn [db [_ analysis-name]]
   (-> db
       (assoc-in [:ui :selected-analysis] analysis-name)
       (assoc-in [:ui :analysis-options] {}))))

(rf/reg-event-db
 ::update-option
 (fn [db [_ option-key value]]
   (assoc-in db [:ui :analysis-options option-key] value)))

(rf/reg-event-db
 ::update-date-range
 (fn [db [_ field value]]
   (assoc-in db [:ui :date-range field] value)))

(rf/reg-event-db
 ::toggle-skip-cache
 (fn [db _]
   (update-in db [:ui :skip-cache] not)))

;; ── Run Analysis ──────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::run-analysis
 (fn [{:keys [db]} [_ repo-id analysis-name]]
   (let [options     (get-in db [:ui :analysis-options])
         date-range  (get-in db [:ui :date-range])
         from-date   (not-empty (:from date-range))
         to-date     (not-empty (:to date-range))
         skip-cache  (get-in db [:ui :skip-cache])
         payload     (cond-> {:analysis analysis-name}
                       from-date    (assoc :from-date from-date)
                       to-date      (assoc :to-date to-date)
                       (seq options) (merge options)
                       skip-cache   (assoc :skip-cache true))
         result-key  [repo-id analysis-name]]
     {:db         (assoc-in db [:results result-key] {:status :loading :data nil})
      :http-xhrio (xhrio-post (str "/api/repos/" repo-id "/analyze")
                              payload
                              [::run-analysis-response repo-id analysis-name]
                              [::run-analysis-error repo-id analysis-name])})))

(rf/reg-event-fx
 ::run-analysis-response
 (fn [{:keys [db]} [_ repo-id analysis-name response]]
   (let [result-key [repo-id analysis-name]]
     (if-let [job-id (:job-id response)]
       ;; Async: got a job-id, start polling
       (let [token (str (random-uuid))]
         {:db (-> db
                  (assoc-in [:results result-key] {:status :running :data nil})
                  (assoc-in [:jobs job-id] {:status :queued})
                  (assoc-in [:active-polls job-id] token))
          :fx [[:dispatch [::poll-job job-id repo-id analysis-name token]]]})
       ;; Sync: got result directly
       {:db (assoc-in db [:results result-key]
                      {:status :loaded
                       :cached-at (str (js/Date.))
                       :data response})
        :fx [[:dispatch [::navigate-to-result repo-id analysis-name]]]}))))

(rf/reg-event-db
 ::run-analysis-error
 (fn [db [_ repo-id analysis-name response]]
   (let [result-key [repo-id analysis-name]
         msg (or (get-in response [:response :error]) "Analysis failed.")]
     (assoc-in db [:results result-key] {:status :error :error msg :data nil}))))

;; ── Job Polling ────────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::poll-job
 (fn [_ [_ job-id repo-id analysis-name token]]
   {:http-xhrio (xhrio-get (str "/api/jobs/" job-id)
                           [::poll-job-response job-id repo-id analysis-name token]
                           [::poll-job-error job-id repo-id analysis-name])}))

(rf/reg-event-fx
 ::poll-job-response
 (fn [{:keys [db]} [_ job-id repo-id analysis-name token response]]
   (when (= token (get-in db [:active-polls job-id]))
     (let [status     (keyword (:status response))
           result-key [repo-id analysis-name]]
       (cond
         (= status :done)
         {:db (-> db
                  (assoc-in [:jobs job-id] {:status :done :result (:result response)})
                  (assoc-in [:results result-key]
                            {:status    :loaded
                             :cached-at (str (js/Date.))
                             :data      (:result response)})
                  (update :active-polls dissoc job-id))
          :fx [[:dispatch [::navigate-to-result repo-id analysis-name]]]}

         (= status :error)
         {:db (-> db
                  (assoc-in [:jobs job-id] {:status :error :error (:error response)})
                  (assoc-in [:results result-key]
                            {:status :error :error (:error response) :data nil})
                  (update :active-polls dissoc job-id))}

         :else
         {:db (-> db
                  (assoc-in [:jobs job-id] {:status status})
                  (assoc-in [:results result-key :phase] (keyword (:phase response))))
          :dispatch-later [{:ms 2000
                            :dispatch [::poll-job job-id repo-id analysis-name token]}]})))))

(rf/reg-event-db
 ::cancel-polls
 (fn [db _]
   (assoc db :active-polls {})))

(rf/reg-event-fx
 ::poll-job-error
 (fn [{:keys [db]} [_ job-id repo-id analysis-name _response]]
   (let [result-key [repo-id analysis-name]]
     {:db (-> db
              (assoc-in [:jobs job-id] {:status :error})
              (assoc-in [:results result-key]
                        {:status :error :error "Job polling failed." :data nil}))})))

;; ── Load Cached Result ─────────────────────────────────────────────────────

(rf/reg-event-fx
 ::load-result
 (fn [{:keys [db]} [_ repo-id analysis-name]]
   (let [result-key [repo-id analysis-name]]
     {:db         (assoc-in db [:results result-key] {:status :loading :data nil})
      :http-xhrio (xhrio-get (str "/api/repos/" repo-id "/results/" analysis-name)
                             [::analysis-result-loaded repo-id analysis-name]
                             [::analysis-result-error repo-id analysis-name])})))

(rf/reg-event-db
 ::analysis-result-loaded
 (fn [db [_ repo-id analysis-name response]]
   (let [result-key [repo-id analysis-name]]
     (assoc-in db [:results result-key]
               {:status    :loaded
                :cached-at (:cached-at response)
                :data      response}))))

(rf/reg-event-db
 ::analysis-result-error
 (fn [db [_ repo-id analysis-name _response]]
   (let [result-key [repo-id analysis-name]]
     (assoc-in db [:results result-key]
               {:status :error :error "Failed to load result." :data nil}))))

;; ── Navigation ─────────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::navigate-to-result
 (fn [{:keys [db]} [_ repo-id analysis-name]]
   (let [result-key  [repo-id analysis-name]
         needs-load? (= :cached (get-in db [:results result-key :status]))]
     {:db (-> db
              (assoc-in [:ui :view] :results)
              (assoc-in [:ui :selected-analysis] analysis-name)
              (assoc :active-repo-id repo-id))
      :fx (when needs-load?
            [[:dispatch [::load-result repo-id analysis-name]]])})))

(rf/reg-event-fx
 ::set-view
 (fn [{:keys [db]} [_ view]]
   {:db (assoc-in db [:ui :view] view)
    :fx [[:dispatch [::cancel-polls]]]}))

;; ── Discover Repos ─────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::discover-repos
 (fn [{:keys [db]} _]
   {:db         (assoc db :discovered-repos :loading)
    :http-xhrio (xhrio-get "/api/repos-discover"
                           [::discovered-repos-loaded]
                           [::http-error])}))

(rf/reg-event-db
 ::discovered-repos-loaded
 (fn [db [_ repos]]
   (assoc db :discovered-repos repos)))

(rf/reg-event-fx
 ::add-discovered-repo
 (fn [_ [_ {:keys [name path]}]]
   {:http-xhrio (xhrio-post "/api/repos"
                            {:name name :path path :vcs "git2"}
                            [::add-repo-success]
                            [::add-repo-failure])}))

;; ── HTTP error fallback ────────────────────────────────────────────────────

(rf/reg-event-db
 ::http-error
 (fn [db [_ response]]
   (let [msg (or (get-in response [:response :error])
                 (str "Request failed (" (:status response) ")"))]
     (assoc-in db [:ui :flash] {:level :error :message msg}))))

(rf/reg-event-db
 ::clear-flash
 (fn [db _]
   (assoc-in db [:ui :flash] nil)))
