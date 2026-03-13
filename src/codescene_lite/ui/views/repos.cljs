(ns codescene-lite.ui.views.repos
  (:require
   [re-frame.core :as rf]
   [codescene-lite.ui.subs :as subs]
   [codescene-lite.ui.events :as events]))

(defn- vcs-badge [vcs]
  [:span.badge.badge-vcs (str vcs)])

(defn- repo-card [repo]
  [:div.repo-card
   {:key      (:id repo)
    :on-click #(rf/dispatch [::events/select-repo (:id repo)])
    :style    {:cursor "pointer"}}
   [:div.repo-card-name (:name repo)]
   [:div.repo-card-path (:path repo)]
   [:div.repo-card-footer
    [vcs-badge (:vcs repo)]
    [:button.btn.btn-danger.btn-sm
     {:on-click (fn [e]
                  (.stopPropagation e)
                  (when (js/confirm (str "Delete repo \"" (:name repo) "\"?"))
                    (rf/dispatch [::events/delete-repo (:id repo)])))}
     "Delete"]]])

(defn- add-repo-form []
  (let [form @(rf/subscribe [::subs/add-repo-form])]
    [:div.form-panel
     [:div.form-panel-title "Add Repository"]

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
                                     :name (-> % .-target .-value)])}]]
      [:div.form-group
       [:label.form-label "VCS"]
       [:select.form-control
        {:value     (:vcs form)
         :on-change #(rf/dispatch [::events/update-add-repo-field
                                   :vcs (-> % .-target .-value)])}
        [:option {:value "git2"} "git2 (default)"]
        [:option {:value "git"}  "git (legacy)"]
        [:option {:value "svn"}  "svn"]
        [:option {:value "hg"}   "hg (Mercurial)"]]]]

     [:div.form-group
      [:label.form-label "Path"]
      [:input.form-control
       {:type        "text"
        :placeholder "/absolute/path/to/repo"
        :value       (:path form)
        :on-change   #(rf/dispatch [::events/update-add-repo-field
                                    :path (-> % .-target .-value)])}]]

     [:div.form-actions
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [::events/submit-add-repo])}
       "Add Repository"]
      [:button.btn.btn-secondary
       {:on-click #(rf/dispatch [::events/close-add-repo-form])}
       "Cancel"]]]))

(defn repos-page []
  (let [repos @(rf/subscribe [::subs/repos])
        form  @(rf/subscribe [::subs/add-repo-form])]
    [:div
     [:div.main-header
      [:h1 "Repositories"]
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [::events/open-add-repo-form])}
       "+ Add Repo"]]

     [:div.main-body
      (when (:open? form)
        [add-repo-form])

      (if (empty? repos)
        [:div.empty-state
         [:div.empty-state-icon "📂"]
         [:h2 "No repositories yet"]
         [:p "Add a repository to start analyzing your codebase for hotspots, coupling, and more."]
         [:button.btn.btn-primary
          {:on-click #(rf/dispatch [::events/open-add-repo-form])}
          "+ Add Your First Repo"]]
        [:div.repos-grid
         (for [repo (sort-by :name repos)]
           (repo-card repo))])]]))
