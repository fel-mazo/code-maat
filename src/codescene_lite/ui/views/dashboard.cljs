(ns codescene-lite.ui.views.dashboard
    (:require
     [re-frame.core :as rf]
     [reagent.core :as r]
     [clojure.string :as str]
     [codescene-lite.ui.subs :as subs]
     [codescene-lite.ui.events :as events]
     [codescene-lite.ui.views.results.charts :as charts]
     [codescene-lite.ui.views.results.table :as table]))

;; ── Helpers ───────────────────────────────────────────────────────────────────

(defn- fmt [v]
       (when (some? v)
             (let [n (js/parseFloat (str v))]
                  (if (js/isNaN n) (str v) (.toLocaleString n)))))

(defn- health-color [score]
       (cond
        (nil? score)   "#a0aec0"
        (>= score 80)  "#38a169"
        (>= score 50)  "#dd6b20"
        :else          "#e53e3e"))

(defn- debt-color [score]
       (cond
        (nil? score) "#a0aec0"
        (< score 10) "#38a169"
        (< score 25) "#dd6b20"
        :else        "#e53e3e"))

(defn- section-data [report key]
       (get-in report [:data key]))

(defn- has-data? [section]
       (seq (:rows section)))

(defn- no-data-msg [label]
       [:div.empty-section
        [:p (str "No data for " label ". Run the report to populate this section.")]])

;; ── KPI Cards ─────────────────────────────────────────────────────────────────

(defn- kpi-card [{:keys [title value subtitle color icon]}]
       [:div.kpi-card {:style {:border-top-color (or color "#4c6ef5")}}
        [:div.kpi-icon icon]
        [:div.kpi-value {:style {:color (or color "#4c6ef5")}}
         (if (nil? value) "—" (str value))]
        [:div.kpi-title title]
        (when subtitle
              [:div.kpi-subtitle subtitle])])

(defn- kpi-row [kpis]
       [:div.kpi-row
        [kpi-card {:title    "Health Score"
                   :value    (when (:health-score kpis) (str (:health-score kpis) "/100"))
                   :subtitle (when (:critical-files kpis)
                                   (str (:critical-files kpis) " critical files"))
                   :color    (health-color (:health-score kpis))
                   :icon     "❤"}]
        [kpi-card {:title    "Debt Score"
                   :value    (when (:debt-score kpis) (str (:debt-score kpis) "%"))
                   :subtitle (when (:bug-pct kpis)
                                   (str (:bug-pct kpis) "% bug-fix commits"))
                   :color    (debt-color (:debt-score kpis))
                   :icon     "⚠"}]
        [kpi-card {:title    "High-Risk Files"
                   :value    (:high-risk-files kpis)
                   :subtitle "concentrated knowledge"
                   :color    (if (and (:high-risk-files kpis) (> (:high-risk-files kpis) 5))
                                 "#e53e3e" "#dd6b20")
                   :icon     "👤"}]
        [kpi-card {:title    "Total Commits"
                   :value    (fmt (:total-commits kpis))
                   :subtitle (when (:total-authors kpis)
                                   (str (:total-authors kpis) " authors"))
                   :color    "#4c6ef5"
                   :icon     "📝"}]
        [kpi-card {:title "Files Tracked"
                   :value (fmt (:total-files kpis))
                   :color "#20c997"
                   :icon  "📁"}]])

;; ── Tab Bar ───────────────────────────────────────────────────────────────────

(def tabs
     [{:id :overview :label "Overview"}
      {:id :hotspots :label "Hotspots"}
      {:id :health :label "Code Health"}
      {:id :knowledge :label "Knowledge Risk"}
      {:id :coupling :label "Coupling"}
      {:id :debt :label "Technical Debt"}])

(defn- tab-bar [active-tab]
       [:div.tab-bar
        (for [{:keys [id label]} tabs]
             [:button.tab-btn
              {:key      id
               :class    (when (= id active-tab) "active")
               :on-click #(rf/dispatch [::events/set-tab id])}
              label])])

;; ── Overview Tab ──────────────────────────────────────────────────────────────

(defn- overview-tab [report]
       (let [summary  (section-data report :summary)
             churn    (section-data report :abs-churn)
             hotspots (section-data report :hotspots)]
            [:div.tab-content
     ;; Summary stat cards
             (when (has-data? summary)
                   [charts/render-chart :summary-stats summary])

     ;; Churn timeline
             (when (has-data? churn)
                   [:div
                    [:h3.section-title "Commit Activity Over Time"]
                    [:p.section-desc "Lines added and deleted per day — spikes signal high-activity (and potentially high-risk) periods."]
                    [charts/render-chart :time-series churn]])

     ;; Top hotspots mini view
             (when (has-data? hotspots)
                   [:div
                    [:h3.section-title "Top Hotspots"]
                    [:p.section-desc "Files that are both large and frequently changed — your biggest investment targets."]
                    [charts/render-chart :hotspot-bar {:columns (:columns hotspots)
                                                       :rows    (take 15 (:rows hotspots))}]])]))

;; ── Hotspots Tab ──────────────────────────────────────────────────────────────

(defn- hotspots-tab [report filters]
       (let [hotspots  (section-data report :hotspots)
             revisions (section-data report :revisions)
             pattern   (:file-pattern filters)]
            [:div.tab-content
             [:div.section-header
              [:div
               [:h3.section-title "Hotspot Analysis"]
               [:p.section-desc
                "Files ranked by size × change frequency. "
                "High hotspot-score = high complexity AND frequent changes = highest refactoring ROI."]]
              [:div.risk-legend
               [:span.risk-badge.risk-critical "Critical (score > 70)"]
               [:span.risk-badge.risk-warning "Warning (score 30–70)"]
               [:span.risk-badge.risk-healthy "Healthy (score < 30)"]]]

             (if (has-data? hotspots)
                 [:div
                  [charts/render-chart :hotspot-bar hotspots]
                  [:h3.section-title "All Files — Sorted by Risk"]
                  [table/data-table
                   {:columns (:columns hotspots)
                    :rows    (let [rows (:rows hotspots)]
                                  (if (empty? pattern) rows
                                      (filter #(str/includes? (str/lower-case (str (first %)))
                                                              (str/lower-case pattern))
                                              rows)))}]]
                 (if (has-data? revisions)
                     [:div
                      [:p.section-desc "Churn data unavailable — showing revision frequency instead."]
                      [charts/render-chart :hotspot-bar revisions]]
                     [no-data-msg "Hotspots"]))]))

;; ── Code Health Tab ───────────────────────────────────────────────────────────

(defn- health-score-bar [score]
       [:div.health-bar-wrap
        [:div.health-bar
         {:style {:width      (str (max 2 score) "%")
                  :background (health-color score)}}]
        [:span.health-bar-label {:style {:color (health-color score)}}
         (str score)]])

(defn- health-tab [report filters]
       (let [health  (section-data report :code-health)
             pattern (:file-pattern filters)]
            [:div.tab-content
             [:div.section-header
              [:div
               [:h3.section-title "Code Health Scores"]
               [:p.section-desc
                "Composite 0–100 score per file. Combines hotspot risk (60%) and knowledge concentration (40%). "
                [:span {:style {:color "#38a169"}} "80–100 healthy"]
                " · "
                [:span {:style {:color "#dd6b20"}} "50–80 needs attention"]
                " · "
                [:span {:style {:color "#e53e3e"}} "0–50 critical"]]]]
             (if (has-data? health)
                 (let [rows     (:rows health)
                       filtered (if (empty? pattern) rows
                                    (filter #(str/includes? (str/lower-case (str (first %)))
                                                            (str/lower-case pattern))
                                            rows))
                       worst-20 (take 20 filtered)]
                      [:div
          ;; Visual health bars for top-20 worst
                       [:div.health-list
                        (for [row worst-20]
                             (let [entity (nth row 0)
                                   score  (js/parseFloat (str (nth row 1 100)))]
                                  [:div.health-row {:key entity}
                                   [:div.health-entity
                                    {:title entity}
                                    (last (str/split entity #"/"))]
                                   [health-score-bar score]]))]

                       [:h3.section-title {:style {:margin-top "2rem"}} "Full Health Report"]
                       [table/data-table {:columns (:columns health) :rows filtered}]])
                 [no-data-msg "Code Health"])]))

;; ── Knowledge Risk Tab ────────────────────────────────────────────────────────

(defn- knowledge-tab [report filters]
       (let [kl      (section-data report :knowledge-loss)
             pattern (:file-pattern filters)]
            [:div.tab-content
             [:div.section-header
              [:div
               [:h3.section-title "Knowledge Risk (Bus Factor)"]
               [:p.section-desc
                "Files where knowledge is concentrated in one person. "
                "When that person leaves, you lose institutional knowledge. "
                "High risk-score = high concentration = succession planning priority."]]]
             (if (has-data? kl)
                 (let [rows     (:rows kl)
                       filtered (if (empty? pattern) rows
                                    (filter #(str/includes? (str/lower-case (str (first %)))
                                                            (str/lower-case pattern))
                                            rows))
                       high-risk (filter #(> (js/parseFloat (str (nth % 6 0))) 70) filtered)]
                      [:div
                       (when (seq high-risk)
                             [:div
                              [:div.alert.alert-warning
                               (str (count high-risk) " file(s) at critical knowledge risk — consider pairing or documentation sprints.")]
                              [charts/render-chart :hotspot-bar
                               {:columns ["entity" "risk-score"]
                                :rows    (map #(vector (first %) (nth % 6 0)) (take 20 high-risk))}]])
                       [:h3.section-title {:style {:margin-top "1.5rem"}} "All Files — Knowledge Distribution"]
                       [table/data-table {:columns (:columns kl) :rows filtered}]])
                 [no-data-msg "Knowledge Risk"])]))

;; ── Coupling Tab ──────────────────────────────────────────────────────────────

(defn- coupling-tab [report filters]
       (let [coupling (section-data report :coupling)
             pattern  (:file-pattern filters)]
            [:div.tab-content
             [:div.section-header
              [:div
               [:h3.section-title "Logical Coupling"]
               [:p.section-desc
                "Modules that always change together are secretly coupled — Conway's Law in action. "
                "High coupling degree = architectural dependency that isn't explicit in the code."]]]
             (if (has-data? coupling)
                 (let [rows     (:rows coupling)
                       filtered (if (empty? pattern) rows
                                    (filter (fn [r]
                                                (or (str/includes? (str/lower-case (str (first r))) (str/lower-case pattern))
                                                    (str/includes? (str/lower-case (str (second r))) (str/lower-case pattern))))
                                            rows))]
                      [:div
                       [charts/render-chart :network-graph {:columns (:columns coupling) :rows filtered}]
                       [:h3.section-title {:style {:margin-top "1.5rem"}} "Coupling Details"]
                       [table/data-table {:columns (:columns coupling) :rows filtered}]])
                 [no-data-msg "Coupling"])]))

;; ── Technical Debt Tab ────────────────────────────────────────────────────────

(defn- commit-type-donut [bug-pct refact-pct feat-pct]
       [:div.commit-breakdown
        [:div.breakdown-bar
         [:div.breakdown-segment
          {:style {:width (str bug-pct "%") :background "#e53e3e"}
           :title (str "Bug fixes: " (Math/round (double bug-pct)) "%")}]
         [:div.breakdown-segment
          {:style {:width (str refact-pct "%") :background "#4c6ef5"}
           :title (str "Refactoring: " (Math/round (double refact-pct)) "%")}]
         [:div.breakdown-segment
          {:style {:width (str feat-pct "%") :background "#38a169"}
           :title (str "Features: " (Math/round (double feat-pct)) "%")}]]
        [:div.breakdown-legend
         [:span.legend-dot {:style {:background "#e53e3e"}}]
         [:span (str "Bug fixes " (Math/round (double bug-pct)) "%")]
         [:span.legend-dot {:style {:background "#4c6ef5" :margin-left "1rem"}}]
         [:span (str "Refactoring " (Math/round (double refact-pct)) "%")]
         [:span.legend-dot {:style {:background "#38a169" :margin-left "1rem"}}]
         [:span (str "Features " (Math/round (double feat-pct)) "%")]]])

(defn- debt-tab [report filters]
       (let [td      (section-data report :technical-debt)
             summary (get-in report [:data :technical-debt :summary])
             pattern (:file-pattern filters)]
            [:div.tab-content
             [:div.section-header
              [:div
               [:h3.section-title "Technical Debt Breakdown"]
               [:p.section-desc
                "Commit classification by type. Bug-fix % measures reactive work — "
                "the higher it is, the more your team is fighting fires instead of shipping features."]]]

     ;; Repo-level summary
             (when summary
                   [:div.debt-summary-grid
                    [:div.debt-stat-card {:style {:border-color (debt-color (:overall-debt-score summary))}}
                     [:div.debt-stat-value
                      {:style {:color (debt-color (:overall-debt-score summary))}}
                      (str (Math/round (double (or (:overall-debt-score summary) 0))) "%")]
                     [:div.debt-stat-label "Debt Score"]
                     [:div.debt-stat-sub "bug-fix commit ratio"]]
                    [:div.debt-stat-card
                     [:div.debt-stat-value {:style {:color "#e53e3e"}}
                      (fmt (:bug-commits summary))]
                     [:div.debt-stat-label "Bug-Fix Commits"]
                     [:div.debt-stat-sub (str (Math/round (double (or (:bug-pct summary) 0))) "% of total")]]
                    [:div.debt-stat-card
                     [:div.debt-stat-value {:style {:color "#4c6ef5"}}
                      (fmt (:refactor-commits summary))]
                     [:div.debt-stat-label "Refactoring Commits"]
                     [:div.debt-stat-sub (str (Math/round (double (or (:refactor-pct summary) 0))) "% of total")]]
                    [:div.debt-stat-card
                     [:div.debt-stat-value {:style {:color "#38a169"}}
                      (fmt (:feature-commits summary))]
                     [:div.debt-stat-label "Feature Commits"]
                     [:div.debt-stat-sub (str (Math/round (double (or (:feature-pct summary) 0))) "% of total")]]])

             (when (and summary
                        (:bug-pct summary) (:refactor-pct summary) (:feature-pct summary))
                   [:div {:style {:margin-bottom "2rem"}}
                    [:h3.section-title "Commit Type Distribution"]
                    [commit-type-donut
                     (:bug-pct summary)
                     (:refactor-pct summary)
                     (:feature-pct summary)]])

             (if (has-data? td)
                 (let [rows     (:rows td)
                       filtered (if (empty? pattern) rows
                                    (filter #(str/includes? (str/lower-case (str (first %)))
                                                            (str/lower-case pattern))
                                            rows))
                       top-debt (take 25 filtered)]
                      [:div
                       [:h3.section-title "Top Debt Files — by Bug-Fix Commit Ratio"]
                       [:p.section-desc "Files with the highest proportion of bug-fix commits are your debt hotspots."]
                       [charts/render-chart :hotspot-bar
                        {:columns ["entity" "debt-score"]
                         :rows    (map #(vector (first %) (nth % 5 0)) top-debt)}]
                       [:h3.section-title {:style {:margin-top "1.5rem"}} "Full Debt Breakdown"]
                       [table/data-table {:columns (:columns td) :rows filtered}]])
                 [:p.section-desc "Run the report to see per-file debt data."])]))

;; ── Config Panel ──────────────────────────────────────────────────────────────

(defn- config-panel [repo]
       (let [local (r/atom {:bug-prefixes      (str/join ", " (or (:bug-prefixes repo) []))
                            :refactor-prefixes (str/join ", " (or (:refactor-prefixes repo) []))})]
            (fn [repo]
                [:div.config-panel
                 [:div.config-panel-title "Branch / Commit Prefix Configuration"]
                 [:p.config-desc
                  "Comma-separated prefixes used to classify commits. "
                  "Supports conventional-commit format (e.g. \"fix:\", \"fix(\", \"fix \")."]
                 [:div.config-row
                  [:div.form-group
                   [:label.form-label "Bug-Fix Prefixes"]
                   [:input.form-control
                    {:type        "text"
                     :placeholder "fix, bug, hotfix, revert"
                     :value       (:bug-prefixes @local)
                     :on-change   #(swap! local assoc :bug-prefixes (-> % .-target .-value))}]
                   [:div.form-hint "Commits starting with these will be counted as bug fixes"]]
                  [:div.form-group
                   [:label.form-label "Refactoring Prefixes"]
                   [:input.form-control
                    {:type        "text"
                     :placeholder "refactor, chore, cleanup, perf"
                     :value       (:refactor-prefixes @local)
                     :on-change   #(swap! local assoc :refactor-prefixes (-> % .-target .-value))}]
                   [:div.form-hint "Commits starting with these will be counted as refactoring"]]]
                 [:div.form-actions
                  [:button.btn.btn-primary
                   {:on-click
                    (fn []
                        (let [parse-pfx (fn [s]
                                            (->> (str/split s #",")
                                                 (map str/trim)
                                                 (filter seq)
                                                 vec))]
                             (rf/dispatch [::events/save-repo-config (:id repo)
                                           {:bug-prefixes      (parse-pfx (:bug-prefixes @local))
                                            :refactor-prefixes (parse-pfx (:refactor-prefixes @local))}])))}
                   "Save Configuration"]
                  [:button.btn.btn-ghost
                   {:on-click #(rf/dispatch [::events/toggle-config-panel])}
                   "Close"]]])))

;; ── Filter Bar ────────────────────────────────────────────────────────────────

(defn- filter-bar [filters skip-cache]
       [:div.filter-bar
        [:div.filter-group
         [:label.filter-label "From"]
         [:input.form-control.filter-input
          {:type      "date"
           :value     (or (:from filters) "")
           :on-change #(rf/dispatch [::events/update-filter :from (-> % .-target .-value)])}]]
        [:div.filter-group
         [:label.filter-label "To"]
         [:input.form-control.filter-input
          {:type      "date"
           :value     (or (:to filters) "")
           :on-change #(rf/dispatch [::events/update-filter :to (-> % .-target .-value)])}]]
        [:div.filter-group
         [:label.filter-label "Filter files"]
         [:input.form-control.filter-input
          {:type        "text"
           :placeholder "e.g. src/main"
           :value       (or (:file-pattern filters) "")
           :on-change   #(rf/dispatch [::events/update-filter :file-pattern (-> % .-target .-value)])}]]
        [:label.filter-checkbox
         [:input {:type      "checkbox"
                  :checked   skip-cache
                  :on-change #(rf/dispatch [::events/toggle-skip-cache])}]
         "Skip cache"]])

;; ── Running / Loading State ───────────────────────────────────────────────────

(defn- running-view [phase]
       [:div.loading-state
        [:div.spinner.spinner-lg]
        [:span.loading-label
         (case phase
               :analyzing      "Running analyses..."
               :generating-log "Generating git log..."
               "Running full analysis report...")]
        [:p.loading-sub "This can take a minute for large repositories."]])

;; ── Main Dashboard ────────────────────────────────────────────────────────────

(defn dashboard-page []
      (let [repo        @(rf/subscribe [::subs/active-repo])
            report      @(rf/subscribe [::subs/current-report])
            kpis        @(rf/subscribe [::subs/kpis])
            active-tab  @(rf/subscribe [::subs/active-tab])
            filters     @(rf/subscribe [::subs/filters])
            skip-cache  @(rf/subscribe [::subs/skip-cache])
            config-open @(rf/subscribe [::subs/config-open])]

           (if-not repo
                   [:div.main-body [:div.empty-state [:p "No repository selected."]]]

                   [:div
       ;; Header
                    [:div.main-header
                     [:div
                      [:h1 (:name repo)]
                      [:div.repo-path (:path repo)]
                      (when (:cached-at report)
                            [:div.cached-at "Last analyzed: " (:cached-at report)])]
                     [:div.header-actions
                      [:button.btn.btn-ghost
                       {:on-click #(rf/dispatch [::events/toggle-config-panel])}
                       "⚙ Configure"]
                      [:button.btn.btn-primary
                       {:on-click #(rf/dispatch [::events/run-report (:id repo)])
                        :disabled (= :running (:status report))}
                       (if (= :running (:status report)) "Analyzing..." "Run Report")]]]

       ;; Config panel (collapsible)
                    (when config-open
                          [:div.main-body {:style {:padding-bottom 0}}
                           [config-panel repo]])

       ;; Filter bar
                    [:div.filter-bar-wrap
                     [filter-bar filters skip-cache]]

       ;; Body
                    [:div.main-body

                     (case (:status report)

                           :running
                           [running-view (:phase report)]

                           :error
                           [:div.alert.alert-error
                            [:strong "Report failed: "] (:error report)
                            [:button.btn.btn-primary {:style    {:margin-left "1rem"}
                                                      :on-click #(rf/dispatch [::events/run-report (:id repo)])}
                             "Retry"]]

                           :loaded
                           [:div
           ;; KPI Row
                            (when kpis [kpi-row kpis])

           ;; Tabs
                            [tab-bar active-tab]

           ;; Tab Content
                            (case active-tab
                                  :overview  [overview-tab report]
                                  :hotspots  [hotspots-tab report filters]
                                  :health    [health-tab report filters]
                                  :knowledge [knowledge-tab report filters]
                                  :coupling  [coupling-tab report filters]
                                  :debt      [debt-tab report filters]
                                  [overview-tab report])]

          ;; No report yet
                           [:div.empty-state
                            [:div.empty-state-icon "📊"]
                            [:h2 "No report yet"]
                            [:p "Click Run Report to generate a full analysis of this repository."]
                            [:p.empty-hint "The report runs: hotspots, code health, knowledge risk, logical coupling, and technical debt — all in one pass."]
                            [:button.btn.btn-primary.btn-lg
                             {:on-click #(rf/dispatch [::events/run-report (:id repo)])}
                             "Run Full Report"]])]])))
