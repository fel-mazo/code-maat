(ns codescene-lite.ui.views.app
  (:require
   [re-frame.core :as rf]
   [codescene-lite.ui.subs :as subs]
   [codescene-lite.ui.events :as events]
   [codescene-lite.ui.views.repos :as repos-view]
   [codescene-lite.ui.views.repo-detail :as repo-detail-view]
   [codescene-lite.ui.views.results.result-view :as result-view]))

(defn- repo-sidebar-item [repo active-repo-id]
  (let [active? (= (:id repo) active-repo-id)]
    [:li
     {:key (:id repo)}
     [:div
      {:class    (str "sidebar-nav-item" (when active? " active"))
       :on-click #(rf/dispatch [::events/select-repo (:id repo)])}
      [:span.repo-dot]
      [:span.repo-name (:name repo)]]]))

(defn- sidebar []
  (let [repos           @(rf/subscribe [::subs/repos])
        active-repo-id  @(rf/subscribe [::subs/active-repo-id])]
    [:nav.sidebar
     [:div.sidebar-header
      [:span.sidebar-logo "code" [:span "scene"] "-lite"]]

     [:div.sidebar-section-title "Repositories"]

     [:ul.sidebar-nav
      (for [repo (sort-by :name repos)]
        (repo-sidebar-item repo active-repo-id))]

     [:div.sidebar-add-btn
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [::events/open-add-repo-form])
        :style    {:width "100%"}}
       "+ Add Repo"]]]))

(defn- main-area []
  (let [view @(rf/subscribe [::subs/active-view])]
    [:main.main-content
     (case view
       :repos        [repos-view/repos-page]
       :repo-detail  [repo-detail-view/repo-detail-page]
       :results      [result-view/result-page]
       ;; fallback
       [repos-view/repos-page])]))

(defn- flash-banner []
  (when-let [{:keys [level message]} @(rf/subscribe [::subs/flash])]
    [:div {:class (str "flash-banner flash-" (name level))}
     [:span message]
     [:button.btn.btn-ghost
      {:on-click #(rf/dispatch [::events/clear-flash])}
      "×"]]))

(defn root []
  [:div.app-layout
   [sidebar]
   [main-area]
   [flash-banner]])
