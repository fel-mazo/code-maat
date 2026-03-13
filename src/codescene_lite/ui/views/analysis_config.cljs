(ns codescene-lite.ui.views.analysis-config
  (:require
   [re-frame.core :as rf]
   [codescene-lite.ui.subs :as subs]
   [codescene-lite.ui.events :as events]))

(defn- date-range-inputs []
  (let [date-range @(rf/subscribe [::subs/date-range])]
    [:div.config-panel
     [:div.config-panel-title "Date Range (optional)"]
     [:div.config-row
      [:div.form-group
       [:label.form-label "From"]
       [:input.form-control
        {:type        "date"
         :value       (or (:from date-range) "")
         :on-change   #(rf/dispatch [::events/update-date-range
                                     :from (-> % .-target .-value)])}]]
      [:div.form-group
       [:label.form-label "To"]
       [:input.form-control
        {:type        "date"
         :value       (or (:to date-range) "")
         :on-change   #(rf/dispatch [::events/update-date-range
                                     :to (-> % .-target .-value)])}]]]]))

(defn- render-option [option-meta current-options]
  (let [{:keys [key label type choices default]} option-meta
        kw-key  (keyword key)
        value   (get current-options kw-key (or default ""))]
    [:div.form-group
     {:key key}
     [:label.form-label (or label key)]
     (cond
       ;; Select / dropdown option
       (or (= type "select") (seq choices))
       [:select.form-control
        {:value     (str value)
         :on-change #(rf/dispatch [::events/update-option kw-key
                                   (-> % .-target .-value)])}
        (when (empty? value)
          [:option {:value ""} "-- select --"])
        (for [choice choices]
          [:option {:key choice :value (str choice)} (str choice)])]

       ;; Number input
       (contains? #{"int" "number"} type)
       [:input.form-control
        {:type      "number"
         :value     (str value)
         :on-change #(rf/dispatch [::events/update-option kw-key
                                   (js/parseInt (-> % .-target .-value))])}]

       ;; Checkbox / boolean
       (contains? #{"bool" "boolean"} type)
       [:label {:style {:display "flex" :align-items "center" :gap "0.5rem" :cursor "pointer"}}
        [:input
         {:type      "checkbox"
          :checked   (boolean value)
          :on-change #(rf/dispatch [::events/update-option kw-key
                                    (-> % .-target .-checked)])}]
        [:span {:style {:font-size "0.875rem"}} (or label key)]]

       ;; Textarea for multi-line text
       (= type "text")
       [:textarea.form-control
        {:rows        6
         :style       {:font-family "var(--font-mono)" :font-size "0.8rem"}
         :placeholder (str default)
         :value       (str value)
         :on-change   #(rf/dispatch [::events/update-option kw-key
                                     (-> % .-target .-value)])}]

       ;; Date input
       (= type "date")
       [:input.form-control
        {:type        "date"
         :value       (str value)
         :on-change   #(rf/dispatch [::events/update-option kw-key
                                     (-> % .-target .-value)])}]

       ;; Default: text input
       :else
       [:input.form-control
        {:type        "text"
         :placeholder (str default)
         :value       (str value)
         :on-change   #(rf/dispatch [::events/update-option kw-key
                                     (-> % .-target .-value)])}])]))

(defn analysis-config-panel []
  (let [analysis-meta @(rf/subscribe [::subs/selected-analysis-meta])
        options       @(rf/subscribe [::subs/analysis-options])
        repo          @(rf/subscribe [::subs/active-repo])
        selected      @(rf/subscribe [::subs/selected-analysis])]
    (when (and analysis-meta repo selected)
      [:div
       {:on-key-down (fn [e]
                       (when (= "Enter" (.-key e))
                         (rf/dispatch [::events/run-analysis (:id repo) selected])))}
       [date-range-inputs]

       (let [option-list (:options analysis-meta)]
         (when (seq option-list)
           [:div.config-panel
            [:div.config-panel-title (str "Options for " selected)]
            [:div.config-row
             (for [opt option-list]
               (render-option opt options))]]))])))
