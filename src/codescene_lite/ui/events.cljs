(ns codescene-lite.ui.events
  (:require
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [codescene-lite.ui.db :as db]))

;; ── Helpers ─────────────────────────────────────────────────────────────────

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

(defn- xhrio-put [uri body on-success on-failure]
  {:method          :put
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

;; ── App Init ─────────────────────────────────────────────────────────────────

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-fx
 ::init
 (fn [{:keys [db]} _]
   {:db (or db db/default-db)
    :fx [[:dispatch [::load-repos]]]}))

;; ── Repos ────────────────────────────────────────────────────────────────────

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

;; ── Add Repo Form ─────────────────────────────────────────────────────────────

(rf/reg-event-db
 ::open-add-repo-form
 (fn [db _]
   (assoc-in db [:ui :add-repo-form :open?] true)))

(rf/reg-event-db
 ::close-add-repo-form
 (fn [db _]
   (assoc-in db [:ui :add-repo-form]
             {:open? false :name "" :path "" :error nil})))

(rf/reg-event-db
 ::update-add-repo-field
 (fn [db [_ field value]]
   (assoc-in db [:ui :add-repo-form field] value)))

(rf/reg-event-fx
 ::submit-add-repo
 (fn [{:keys [db]} _]
   (let [{:keys [name path]} (get-in db [:ui :add-repo-form])]
     (if (or (empty? name) (empty? path))
       {:db (assoc-in db [:ui :add-repo-form :error] "Name and path are required.")}
       {:http-xhrio (xhrio-post "/api/repos"
                                {:name name :path path}
                                [::add-repo-success]
                                [::add-repo-failure])}))))

(rf/reg-event-fx
 ::add-repo-success
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:ui :add-repo-form]
                  {:open? false :name "" :path "" :error nil})
    :fx [[:dispatch [::load-repos]]]}))

(rf/reg-event-db
 ::add-repo-failure
 (fn [db [_ response]]
   (let [msg (or (get-in response [:response :error])
                 "Failed to add repo. Check the path and try again.")]
     (assoc-in db [:ui :add-repo-form :error] msg))))

;; ── Delete Repo ───────────────────────────────────────────────────────────────

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
                    (update :reports dissoc repo-id))]
     (if (= (:active-repo-id new-db) repo-id)
       {:db (-> new-db
                (assoc :active-repo-id nil)
                (assoc-in [:ui :view] :repos))}
       {:db new-db}))))

;; ── Select Repo ───────────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::select-repo
 (fn [{:keys [db]} [_ repo-id]]
   {:db (-> db
            (assoc :active-repo-id repo-id)
            (assoc-in [:ui :view] :dashboard)
            (assoc-in [:ui :active-tab] :overview))
    :fx [[:dispatch [::cancel-polls]]
         [:dispatch [::load-cached-report repo-id]]]}))

(rf/reg-event-fx
 ::load-cached-report
 (fn [{:keys [db]} [_ repo-id]]
   (let [existing (get-in db [:reports repo-id])]
     (when (nil? existing)
       {:http-xhrio (xhrio-get (str "/api/repos/" repo-id "/results/report")
                               [::cached-report-loaded repo-id]
                               [::cached-report-not-found repo-id])}))))

(rf/reg-event-db
 ::cached-report-loaded
 (fn [db [_ repo-id report]]
   (assoc-in db [:reports repo-id]
             {:status :loaded :data report :cached-at (:cached-at report)})))

(rf/reg-event-db
 ::cached-report-not-found
 (fn [db [_ repo-id _]]
   ;; 404 just means no cached report yet — that's fine
   (if (get-in db [:reports repo-id])
     db
     (assoc-in db [:reports repo-id] {:status :none}))))

;; ── Repo Config (branch prefixes) ─────────────────────────────────────────────

(rf/reg-event-db
 ::toggle-config-panel
 (fn [db _]
   (update-in db [:ui :config-open] not)))

(rf/reg-event-fx
 ::save-repo-config
 (fn [_ [_ repo-id config]]
   {:http-xhrio (xhrio-put (str "/api/repos/" repo-id)
                           config
                           [::save-repo-config-success repo-id]
                           [::http-error])}))

(rf/reg-event-fx
 ::save-repo-config-success
 (fn [{:keys [db]} [_ repo-id updated]]
   {:db (assoc-in db [:repos repo-id] updated)
    :fx [[:dispatch [::set-flash :success "Configuration saved."]]]}))

;; ── Run Full Report ───────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::run-report
 (fn [{:keys [db]} [_ repo-id]]
   (let [filters    (get-in db [:ui :filters])
         skip-cache (get-in db [:ui :skip-cache])
         repo       (get-in db [:repos repo-id])
         payload    (cond-> {}
                      (not-empty (:from filters))       (assoc :from-date (:from filters))
                      (not-empty (:to filters))         (assoc :to-date (:to filters))
                      (:bug-prefixes repo)              (assoc :bug-prefixes (:bug-prefixes repo))
                      (:refactor-prefixes repo)         (assoc :refactor-prefixes (:refactor-prefixes repo))
                      skip-cache                        (assoc :skip-cache true))]
     {:db         (assoc-in db [:reports repo-id] {:status :running :phase :analyzing})
      :http-xhrio (xhrio-post (str "/api/repos/" repo-id "/report")
                              payload
                              [::run-report-response repo-id]
                              [::run-report-error repo-id])})))

(rf/reg-event-fx
 ::run-report-response
 (fn [{:keys [db]} [_ repo-id response]]
   (if-let [job-id (:job-id response)]
     (let [token (str (random-uuid))]
       {:db (-> db
                (assoc-in [:reports repo-id] {:status :running :phase :queued})
                (assoc-in [:jobs job-id] {:status :queued})
                (assoc-in [:active-polls job-id] token))
        :fx [[:dispatch [::poll-report-job job-id repo-id token]]]})
     ;; Sync result (from cache)
     {:db (assoc-in db [:reports repo-id]
                    {:status    :loaded
                     :data      response
                     :cached-at (:cached-at response)})})))

(rf/reg-event-db
 ::run-report-error
 (fn [db [_ repo-id response]]
   (let [msg (or (get-in response [:response :error]) "Report failed.")]
     (assoc-in db [:reports repo-id] {:status :error :error msg}))))

;; ── Report Job Polling ────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::poll-report-job
 (fn [_ [_ job-id repo-id token]]
   {:http-xhrio (xhrio-get (str "/api/jobs/" job-id)
                           [::poll-report-job-response job-id repo-id token]
                           [::poll-report-job-error job-id repo-id])}))

(rf/reg-event-fx
 ::poll-report-job-response
 (fn [{:keys [db]} [_ job-id repo-id token response]]
   (when (= token (get-in db [:active-polls job-id]))
     (let [status (keyword (:status response))]
       (cond
         (= status :done)
         {:db (-> db
                  (assoc-in [:reports repo-id]
                            {:status    :loaded
                             :data      (:result response)
                             :cached-at (str (js/Date.))})
                  (assoc-in [:jobs job-id] {:status :done})
                  (update :active-polls dissoc job-id))}

         (= status :error)
         {:db (-> db
                  (assoc-in [:reports repo-id]
                            {:status :error :error (:error response)})
                  (assoc-in [:jobs job-id] {:status :error})
                  (update :active-polls dissoc job-id))}

         :else
         {:db (-> db
                  (assoc-in [:jobs job-id] {:status status})
                  (assoc-in [:reports repo-id :phase] (keyword (:phase response))))
          :dispatch-later [{:ms       2000
                            :dispatch [::poll-report-job job-id repo-id token]}]})))))

(rf/reg-event-db
 ::cancel-polls
 (fn [db _]
   (assoc db :active-polls {})))

(rf/reg-event-fx
 ::poll-report-job-error
 (fn [{:keys [db]} [_ job-id repo-id _]]
   {:db (-> db
            (assoc-in [:jobs job-id] {:status :error})
            (assoc-in [:reports repo-id]
                      {:status :error :error "Job polling failed."}))}))

;; ── Filters ───────────────────────────────────────────────────────────────────

(rf/reg-event-db
 ::update-filter
 (fn [db [_ field value]]
   (assoc-in db [:ui :filters field] value)))

(rf/reg-event-db
 ::toggle-skip-cache
 (fn [db _]
   (update-in db [:ui :skip-cache] not)))

;; ── Tab Navigation ────────────────────────────────────────────────────────────

(rf/reg-event-db
 ::set-tab
 (fn [db [_ tab]]
   (assoc-in db [:ui :active-tab] tab)))

;; ── Navigation ───────────────────────────────────────────────────────────────

(rf/reg-event-fx
 ::set-view
 (fn [{:keys [db]} [_ view]]
   {:db (assoc-in db [:ui :view] view)
    :fx [[:dispatch [::cancel-polls]]]}))

;; ── Discover Repos ────────────────────────────────────────────────────────────

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
                            {:name name :path path}
                            [::add-repo-success]
                            [::add-repo-failure])}))

;; ── Flash messages ────────────────────────────────────────────────────────────

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

(rf/reg-event-db
 ::set-flash
 (fn [db [_ level message]]
   (assoc-in db [:ui :flash] {:level level :message message})))
