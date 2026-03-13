(ns codescene-lite.ui.subs
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

;; ── Repos ──────────────────────────────────────────────────────────────────────

(rf/reg-sub
 ::repos
 (fn [db _]
   (vals (:repos db))))

(rf/reg-sub
 ::repos-map
 (fn [db _]
   (:repos db)))

(rf/reg-sub
 ::active-repo-id
 (fn [db _]
   (:active-repo-id db)))

(rf/reg-sub
 ::active-repo
 :<- [::active-repo-id]
 :<- [::repos-map]
 (fn [[repo-id repos-map] _]
   (get repos-map repo-id)))

;; ── Report ─────────────────────────────────────────────────────────────────────

(rf/reg-sub
 ::report
 (fn [db [_ repo-id]]
   (get-in db [:reports repo-id])))

;; Active report — used by dashboard
(rf/reg-sub
 ::current-report
 (fn [db _]
   (let [repo-id (:active-repo-id db)]
     (get-in db [:reports repo-id]))))

;; ── UI State ───────────────────────────────────────────────────────────────────

(rf/reg-sub
 ::active-view
 (fn [db _]
   (get-in db [:ui :view])))

(rf/reg-sub
 ::active-tab
 (fn [db _]
   (get-in db [:ui :active-tab])))

(rf/reg-sub
 ::add-repo-form
 (fn [db _]
   (get-in db [:ui :add-repo-form])))

(rf/reg-sub
 ::filters
 (fn [db _]
   (get-in db [:ui :filters])))

(rf/reg-sub
 ::skip-cache
 (fn [db _]
   (get-in db [:ui :skip-cache])))

(rf/reg-sub
 ::config-open
 (fn [db _]
   (get-in db [:ui :config-open])))

(rf/reg-sub
 ::discovered-repos
 (fn [db _]
   (:discovered-repos db)))

(rf/reg-sub
 ::flash
 (fn [db _]
   (get-in db [:ui :flash])))

;; ── Derived: filtered report sections ─────────────────────────────────────────

(defn- apply-file-filter [rows pattern]
  (if (empty? pattern)
    rows
    (let [p (str/lower-case pattern)]
      (filter (fn [row]
                (str/includes? (str/lower-case (str (first row))) p))
              rows))))

(defn- apply-min-revs [rows min-revs col-idx]
  (if (or (nil? min-revs) (zero? min-revs))
    rows
    (filter (fn [row]
              (let [v (nth row col-idx nil)]
                (>= (js/parseFloat (str v)) min-revs)))
            rows)))

(rf/reg-sub
 ::filtered-rows
 :<- [::current-report]
 :<- [::filters]
 (fn [[report filters] [_ section-key revs-col-idx]]
   (let [section (get-in report [:data section-key])
         rows    (:rows section)
         pat     (:file-pattern filters)
         min-r   (:min-revs filters)]
     (-> rows
         (apply-file-filter pat)
         (apply-min-revs min-r (or revs-col-idx 1))))))

;; ── Derived: KPIs from report data ─────────────────────────────────────────────

(rf/reg-sub
 ::kpis
 :<- [::current-report]
 (fn [report _]
   (when (= :loaded (:status report))
     (let [data          (:data report)
           ;; Health score: average from code-health rows (col 1 = health-score)
           health-rows   (get-in data [:code-health :rows])
           avg-health    (when (seq health-rows)
                           (let [scores (keep #(js/parseFloat (str (nth % 1 nil))) health-rows)]
                             (when (seq scores)
                               (/ (reduce + scores) (count scores)))))
           critical-cnt  (when (seq health-rows)
                           (count (filter #(< (js/parseFloat (str (nth % 1 100))) 50) health-rows)))

           ;; Debt: from technical-debt summary
           td-summary    (get-in data [:technical-debt :summary])
           debt-score    (:overall-debt-score td-summary)
           bug-pct       (:bug-pct td-summary)
           refact-pct    (:refactor-pct td-summary)

           ;; Knowledge risk: files with very concentrated knowledge
           kl-rows       (get-in data [:knowledge-loss :rows])
           ;; risk-score is col 6 (index)
           high-risk     (when (seq kl-rows)
                           (count (filter #(> (js/parseFloat (str (nth % 6 0))) 70) kl-rows)))

           ;; Summary stats
           sum-rows      (get-in data [:summary :rows])
           sum-map       (when (seq sum-rows)
                           (into {} (map (fn [[k v]] [k v]) sum-rows)))]

       {:health-score  (when avg-health (Math/round avg-health))
        :critical-files critical-cnt
        :debt-score    (when debt-score (Math/round (double debt-score)))
        :bug-pct       (when bug-pct (Math/round (double bug-pct)))
        :refactor-pct  (when refact-pct (Math/round (double refact-pct)))
        :high-risk-files high-risk
        :total-commits (get sum-map "number-of-commits")
        :total-files   (get sum-map "number-of-entities")
        :total-authors (get sum-map "number-of-authors")}))))
