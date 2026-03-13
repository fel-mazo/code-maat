(ns codescene-lite.ui.views.repos
  (:require
   [re-frame.core :as rf]
   [codescene-lite.ui.subs :as subs]
   [codescene-lite.ui.events :as events]))

(defn- repo-card [repo]
  [:div.repo-card
   {:key      (:id repo)
    :on-click #(rf/dispatch [::events/select-repo (:id repo)])
    :style    {:cursor "pointer"}}
   [:div.repo-card-name (:name repo)]
   [:div.repo-card-path (:path repo)]
   [:div.repo-card-footer
    [:span.badge.badge-vcs "git"]
    [:button.btn.btn-danger.btn-sm
     {:on-click (fn [e]
                  (.stopPropagation e)
                  (when (js/confirm (str "Delete repo \"" (:name repo) "\"?"))
                    (rf/dispatch [::events/delete-repo (:id repo)])))}
     "Delete"]]])

(defn- add-repo-form []
  (let [form @(rf/subscribe [::subs/add-repo-form])]
    [:div.form-panel
     [:div.form-panel-title "Add Git Repository"]

     (when (:error form)
       [:div.alert.alert-error (:error form)])

     [:div.form-row
      [:div.form-group
       [:label.form-label "Name"]
       [:input.form-control
        {:type        "text"
         :placeholder "my-project"
         :value       (:name form)
         :on-change   #(rf/dispatch [::events/update-add-repo-field
                                     :name (-> % .-target .-value)])
         :on-key-down (fn [e]
                        (when (= "Enter" (.-key e))
                          (rf/dispatch [::events/submit-add-repo])))}]]
      [:div.form-group
       [:label.form-label "Path"]
       [:input.form-control
        {:type        "text"
         :placeholder "/absolute/path/to/repo"
         :value       (:path form)
         :on-change   #(rf/dispatch [::events/update-add-repo-field
                                     :path (-> % .-target .-value)])
         :on-key-down (fn [e]
                        (when (= "Enter" (.-key e))
                          (rf/dispatch [::events/submit-add-repo])))}]]]

     [:div.form-actions
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [::events/submit-add-repo])}
       "Add Repository"]
      [:button.btn.btn-secondary
       {:on-click #(rf/dispatch [::events/close-add-repo-form])}
       "Cancel"]]]))

(defn- discover-panel []
  (let [discovered @(rf/subscribe [::subs/discovered-repos])]
    [:div.form-panel
     [:div.form-panel-title "Discovered Repositories"]
     (cond
       (= discovered :loading)
       [:div.loading-spinner "Scanning /repos..."]

       (empty? discovered)
       [:p {:style {:color "var(--text-muted)"}}
        "No new git repositories found under /repos."]

       :else
       [:div
        [:p {:style {:color "var(--text-muted)" :margin-bottom "0.75rem"}}
         (str (count discovered) " repo(s) found — click Add to register:")]
        (for [{:keys [name path] :as repo} discovered]
          [:div.discover-row {:key path}
           [:div.discover-row-info
            [:span.discover-name name]
            [:span.discover-path path]]
           [:button.btn.btn-primary.btn-sm
            {:on-click #(rf/dispatch [::events/add-discovered-repo repo])}
            "Add"]])])]))

(defn repos-page []
  (let [repos      @(rf/subscribe [::subs/repos])
        form       @(rf/subscribe [::subs/add-repo-form])
        discovered @(rf/subscribe [::subs/discovered-repos])]
    [:div
     [:div.main-header
      [:h1 "Repositories"]
      [:div {:style {:display "flex" :gap "0.5rem"}}
       [:button.btn.btn-secondary
        {:on-click #(rf/dispatch [::events/discover-repos])}
        "Discover"]
       [:button.btn.btn-primary
        {:on-click #(rf/dispatch [::events/open-add-repo-form])}
        "+ Add Repo"]]]

     [:div.main-body
      (when (or (:open? form) discovered)
        [:div {:style {:margin-bottom "1.5rem"}}
         (when discovered [discover-panel])
         (when (:open? form) [add-repo-form])])

      (if (empty? repos)
        [:div.empty-state
         [:div.empty-state-icon "📂"]
         [:h2 "No repositories yet"]
         [:p "Click Discover to find git repos in your mounted volumes, or add one manually."]
         [:div {:style {:display "flex" :gap "0.75rem" :justify-content "center"}}
          [:button.btn.btn-secondary
           {:on-click #(rf/dispatch [::events/discover-repos])}
           "Discover Repos"]
          [:button.btn.btn-primary
           {:on-click #(rf/dispatch [::events/open-add-repo-form])}
           "+ Add Manually"]]]
        [:div.repos-grid
         (for [repo (sort-by :name repos)]
           (repo-card repo))])]]))
