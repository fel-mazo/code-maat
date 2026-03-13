(ns codescene-lite.ui.views.results.result-view
  (:require
   [re-frame.core :as rf]
   [codescene-lite.ui.subs :as subs]
   [codescene-lite.ui.events :as events]
   [codescene-lite.ui.views.results.table :as table]
   [codescene-lite.ui.views.results.charts :as charts]))

(defn- loading-view [phase]
  [:div.loading-state
   [:div.spinner.spinner-lg]
   [:span (case phase
            :generating-log "Generating git log..."
            :analyzing      "Analyzing..."
            "Running analysis...")]])

(defn- error-view [msg]
  [:div.main-body
   [:div.alert.alert-error
    [:strong "Analysis error: "] msg]])

(defn- format-date [s]
  (when (and s (not= s "null"))
    (try
      (.toLocaleString (js/Date. s))
      (catch :default _ s))))

(defn- result-content [analysis-name analysis-meta result-data]
  (let [{:keys [columns rows]} result-data
        viz-type (keyword (or (:viz-type analysis-meta) :bar-chart))]
    [:div
     ;; Chart section (only if we have data)
     (when (and (seq columns) (seq rows))
       [charts/render-chart viz-type {:columns columns :rows rows}])

     ;; Table section
     (if (and (seq columns) (seq rows))
       [table/data-table {:columns columns :rows rows}]
       [:div.empty-state
        [:p "No results returned for this analysis."]])]))

(defn result-page []
  (let [repo          @(rf/subscribe [::subs/active-repo])
        analysis-name @(rf/subscribe [::subs/selected-analysis])
        analyses-meta @(rf/subscribe [::subs/analyses-meta])
        result        (when (and repo analysis-name)
                        @(rf/subscribe [::subs/result (:id repo) analysis-name]))
        analysis-meta (get analyses-meta analysis-name)]

    (if-not (and repo analysis-name)
      [:div.main-body
       [:div.empty-state
        [:p "Select a repo and run an analysis to see results."]]]
      [:div
       [:div.main-header
        [:div
         [:div.results-title analysis-name]
         (when (:cached-at result)
           [:div.results-meta
            "Cached: " (format-date (:cached-at result))])]
        [:div.results-actions
         [:button.btn.btn-secondary
          {:on-click #(rf/dispatch [::events/set-view :repo-detail])}
          "← Back"]
         [:button.btn.btn-primary
          {:on-click #(rf/dispatch [::events/run-analysis (:id repo) analysis-name])
           :disabled (= :loading (:status result))}
          (if (= :loading (:status result)) "Running..." "Re-run")]]]

       [:div.main-body
        (cond
          (nil? result)
          [:div.loading-state
           [:div.spinner]
           [:span "Loading..."]]

          (= :loading (:status result))
          [loading-view nil]

          (= :running (:status result))
          [loading-view (:phase result)]

          (= :error (:status result))
          [error-view (:error result)]

          (= :cached (:status result))
          ;; We have a cache entry but no data yet — fetch it
          ;; Use dispatch-later to avoid dispatching during render
          (do
            (js/setTimeout #(rf/dispatch [::events/load-result (:id repo) analysis-name]) 0)
            [loading-view nil])

          (and (= :loaded (:status result)) (:data result))
          [result-content analysis-name analysis-meta (:data result)]

          :else
          [:div.empty-state
           [:p "No data available. Try running the analysis."]])]])))
