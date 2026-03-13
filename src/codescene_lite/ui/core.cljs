(ns codescene-lite.ui.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [codescene-lite.ui.events :as events]
   [codescene-lite.ui.views.app :as views.app]
   ;; Ensure http-fx is loaded (registers the :http-xhrio effect)
   [day8.re-frame.http-fx]))

(defn ^:export init []
  (rf/dispatch-sync [::events/initialize-db])
  (rf/dispatch-sync [::events/init])
  (rdom/render [views.app/root]
               (.getElementById js/document "app")))

(defn ^:dev/after-load reload []
  (rdom/render [views.app/root]
               (.getElementById js/document "app")))
