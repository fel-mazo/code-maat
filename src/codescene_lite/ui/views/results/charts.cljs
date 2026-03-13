(ns codescene-lite.ui.views.results.charts
  (:require [reagent.core :as r]))

;; ── Recharts interop ──────────────────────────────────────────────────────

(def ^:private recharts (js/require "recharts"))

(def ^:private BarChart      (.-BarChart recharts))
(def ^:private Bar            (.-Bar recharts))
(def ^:private LineChart      (.-LineChart recharts))
(def ^:private Line           (.-Line recharts))
(def ^:private XAxis          (.-XAxis recharts))
(def ^:private YAxis          (.-YAxis recharts))
(def ^:private CartesianGrid  (.-CartesianGrid recharts))
(def ^:private Tooltip        (.-Tooltip recharts))
(def ^:private Legend         (.-Legend recharts))
(def ^:private ResponsiveContainer (.-ResponsiveContainer recharts))

;; ── Colour palette ────────────────────────────────────────────────────────

(def chart-colors
  ["#4c6ef5" "#20c997" "#f59f00" "#fa5252" "#7950f2"
   "#15aabf" "#82c91e" "#ff922b" "#cc5de8" "#339af0"])

;; ── Helpers ───────────────────────────────────────────────────────────────

(defn- js-data
  "Convert rows + columns to array-of-objects for Recharts."
  [columns rows]
  (clj->js
   (map (fn [row]
          (into {} (map-indexed (fn [i col] [col (nth row i nil)]) columns)))
        rows)))

(defn- numeric-columns
  "Return column names (excluding index 0) that appear numeric in the first few rows."
  [columns rows]
  (let [sample (take 10 rows)]
    (->> (rest columns)
         (filter (fn [col]
                   (let [idx (.indexOf (clj->js columns) col)]
                     (every? (fn [row]
                               (js/isFinite (js/parseFloat (str (nth row idx nil)))))
                             sample)))))))

;; ── Chart components ──────────────────────────────────────────────────────

(defn- rc [component props & children]
  (apply r/create-element component (clj->js props) children))

(defn bar-chart-view
  "Vertical BarChart — first column as x-axis, numeric columns as bars."
  [{:keys [columns rows]}]
  (let [x-col    (first columns)
        num-cols (or (seq (numeric-columns columns rows)) [(second columns)])
        data     (js-data columns rows)]
    [:div.chart-container
     (r/as-element
      (rc ResponsiveContainer {:width "100%" :height 320}
          (apply rc BarChart {:data data :margin #js {:top 5 :right 20 :left 10 :bottom 5}}
                 (rc CartesianGrid {:strokeDasharray "3 3" :stroke "#e2e8f0"})
                 (rc XAxis {:dataKey x-col :tick #js {:fontSize 11}
                            :angle -30 :textAnchor "end" :height 55})
                 (rc YAxis {:tick #js {:fontSize 11}})
                 (rc Tooltip {})
                 (rc Legend {:wrapperStyle #js {:fontSize "12px"}})
                 (map-indexed
                  (fn [i col]
                    (rc Bar {:key col :dataKey col :fill (nth chart-colors (mod i 10))}))
                  num-cols))))]))

(defn hotspot-bar-view
  "Horizontal BarChart sorted by value descending (top hotspots)."
  [{:keys [columns rows]}]
  (let [val-col  (or (second columns) (first columns))
        label-col (first columns)
        ;; Sort by value desc, take top 30
        sorted-rows (take 30
                         (sort-by (fn [row]
                                    (- (js/parseFloat (str (second row)))))
                                  rows))
        data     (js-data columns sorted-rows)]
    [:div.chart-container
     (r/as-element
      (rc ResponsiveContainer {:width "100%" :height (max 300 (* (count sorted-rows) 22))}
          (rc BarChart {:data data :layout "vertical"
                        :margin #js {:top 5 :right 30 :left 120 :bottom 5}}
              (rc CartesianGrid {:strokeDasharray "3 3" :stroke "#e2e8f0"})
              (rc XAxis {:type "number" :tick #js {:fontSize 11}})
              (rc YAxis {:type "category" :dataKey label-col
                         :tick #js {:fontSize 11} :width 115})
              (rc Tooltip {})
              (rc Bar {:dataKey val-col :fill (first chart-colors)}))))]))

(defn time-series-view
  "LineChart with date on x-axis. For abs-churn: added/deleted lines."
  [{:keys [columns rows]}]
  (let [date-col  (first columns)
        num-cols  (or (seq (numeric-columns columns rows)) [(second columns)])
        data      (js-data columns rows)]
    [:div.chart-container
     (r/as-element
      (rc ResponsiveContainer {:width "100%" :height 320}
          (apply rc LineChart {:data data :margin #js {:top 5 :right 20 :left 10 :bottom 5}}
                 (rc CartesianGrid {:strokeDasharray "3 3" :stroke "#e2e8f0"})
                 (rc XAxis {:dataKey date-col :tick #js {:fontSize 10}
                            :angle -30 :textAnchor "end" :height 55})
                 (rc YAxis {:tick #js {:fontSize 11}})
                 (rc Tooltip {})
                 (rc Legend {:wrapperStyle #js {:fontSize "12px"}})
                 (map-indexed
                  (fn [i col]
                    (rc Line {:key col :type "monotone" :dataKey col
                              :stroke (nth chart-colors (mod i 10))
                              :dot false :strokeWidth 2}))
                  num-cols))))]))

(defn network-graph-placeholder [_]
  [:div.chart-container
   [:div.chart-placeholder
    "Coupling network graph (D3) — coming soon"]])

(defn heatmap-placeholder [_]
  [:div.chart-container
   [:div.chart-placeholder
    "Code age heatmap (D3) — coming soon"]])

;; ── Dispatch ──────────────────────────────────────────────────────────────

(defn render-chart
  "Dispatch to the right chart component based on viz-type keyword."
  [viz-type data]
  (case viz-type
    :bar-chart      [bar-chart-view data]
    :hotspot-bar    [hotspot-bar-view data]
    :time-series    [time-series-view data]
    :network-graph  [network-graph-placeholder data]
    :heatmap        [heatmap-placeholder data]
    ;; Default: hotspot bar for anything that looks like ranked data
    [bar-chart-view data]))
