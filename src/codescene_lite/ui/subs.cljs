(ns codescene-lite.ui.subs
  (:require [re-frame.core :as rf]))

;; ── Repos ─────────────────────────────────────────────────────────────────

(rf/reg-sub
 ::repos
 (fn [db _]
   (vals (:repos db))))

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

(rf/reg-sub
 ::repos-map
 (fn [db _]
   (:repos db)))

;; ── Analyses Meta ─────────────────────────────────────────────────────────

(rf/reg-sub
 ::analyses-meta
 (fn [db _]
   (:analyses-meta db)))

(rf/reg-sub
 ::analyses-list
 :<- [::analyses-meta]
 (fn [meta _]
   (vals meta)))

;; ── Results ───────────────────────────────────────────────────────────────

(rf/reg-sub
 ::results
 (fn [db _]
   (:results db)))

(rf/reg-sub
 ::result
 (fn [db [_ repo-id analysis-name]]
   (get-in db [:results [repo-id analysis-name]])))

;; ── Jobs ──────────────────────────────────────────────────────────────────

(rf/reg-sub
 ::jobs
 (fn [db _]
   (:jobs db)))

(rf/reg-sub
 ::job
 (fn [db [_ job-id]]
   (get-in db [:jobs job-id])))

;; ── UI State ──────────────────────────────────────────────────────────────

(rf/reg-sub
 ::active-view
 (fn [db _]
   (get-in db [:ui :view])))

(rf/reg-sub
 ::add-repo-form
 (fn [db _]
   (get-in db [:ui :add-repo-form])))

(rf/reg-sub
 ::selected-analysis
 (fn [db _]
   (get-in db [:ui :selected-analysis])))

(rf/reg-sub
 ::analysis-options
 (fn [db _]
   (get-in db [:ui :analysis-options])))

(rf/reg-sub
 ::date-range
 (fn [db _]
   (get-in db [:ui :date-range])))

;; ── Derived ───────────────────────────────────────────────────────────────

(rf/reg-sub
 ::selected-analysis-meta
 :<- [::analyses-meta]
 :<- [::selected-analysis]
 (fn [[meta sel] _]
   (get meta sel)))

(rf/reg-sub
 ::active-result
 (fn [db [_ repo-id analysis-name]]
   (get-in db [:results [repo-id analysis-name]])))
