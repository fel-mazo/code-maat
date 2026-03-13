(ns codescene-lite.ui.views.repo-detail
  (:require
   [re-frame.core :as rf]
   [codescene-lite.ui.subs :as subs]
   [codescene-lite.ui.events :as events]
   [codescene-lite.ui.views.analysis-config :as config-view]))

(defn- viz-type-badge [viz-type]
  [:span.badge.badge-viz (name (or viz-type :table))])

(defn- status->badge-class [status]
  (case status
    :cached  "badge-done"
    :loaded  "badge-done"
    :loading "badge-running"
    :error   "badge-error"
    :running "badge-running"
    ""))

(defn- status->label [status]
  (case status
    :cached  "cached"
    :loaded  "done"
    :loading "loading"
    :error   "error"
    :running "running"
    "?"))

;; analysis-card is a Reagent component (function returning hiccup)
;; so subscriptions deref'd inside are properly reactive.
(defn- analysis-card [repo-id analysis selected-name]
  (let [{:keys [name description viz-type]} analysis
        result    @(rf/subscribe [::subs/result repo-id name])
        status    (:status result)
        selected? (= name selected-name)]
    [:div.analysis-card
     {:key      name
      :class    (when selected? "selected")
      :on-click #(rf/dispatch [::events/select-analysis name])}
     [:div.analysis-card-name name]
     [:div.analysis-card-desc (or description "No description available.")]
     [:div.analysis-card-footer
      [viz-type-badge viz-type]
      [:div {:style {:display "flex" :gap "0.5rem" :align-items "center"}}
       (when status
         [:span.badge
          {:class (status->badge-class status)}
          (status->label status)])
       [:button.btn.btn-primary.btn-sm
        {:on-click (fn [e]
                     (.stopPropagation e)
                     (rf/dispatch [::events/select-analysis name])
                     (rf/dispatch [::events/run-analysis repo-id name]))}
        (if (= status :loading) "Running..." "Run")]]]]))

(defn repo-detail-page []
  (let [repo           @(rf/subscribe [::subs/active-repo])
        analyses       @(rf/subscribe [::subs/analyses-list])
        selected-name  @(rf/subscribe [::subs/selected-analysis])]
    (if-not repo
      [:div.main-body [:div.empty-state [:p "No repository selected."]]]
      [:div
       [:div.main-header
        [:div
         [:h1 (:name repo)]
         [:div {:style {:font-size "0.8rem" :color "var(--text-muted)" :margin-top "0.2rem"
                        :font-family "var(--font-mono)"}}
          (:path repo)]]
        [:span.badge.badge-vcs (:vcs repo)]]

       [:div.main-body
        (when selected-name
          [config-view/analysis-config-panel])

        [:div {:style {:margin-bottom "1rem"}}
         [:strong {:style {:font-size "0.85rem" :color "var(--text-secondary)"}}
          (str (count analyses) " available analyses — click a card to configure, or Run directly")]]

        (if (empty? analyses)
          [:div.loading-state
           [:div.spinner]
           [:span "Loading analyses..."]]
          [:div.analysis-grid
           (doall
            (for [analysis (sort-by :name analyses)]
              (analysis-card (:id repo) analysis selected-name)))])]])))
