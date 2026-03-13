(ns codescene-lite.ui.views.results.charts
  (:require [reagent.core :as r]
            [clojure.string :as str]
            ["recharts" :refer [BarChart Bar LineChart Line
                                XAxis YAxis CartesianGrid
                                Tooltip Legend ResponsiveContainer
                                Cell]]))

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

(defn coupling-chart-view
  "Horizontal BarChart of top coupling pairs sorted by degree.
   Columns expected: [entity coupled degree average-revs]"
  [{:keys [_columns rows]}]
  (let [sorted-rows (->> rows
                         (sort-by (fn [r] (- (js/parseFloat (str (nth r 2 0))))))
                         (take 25))
        data (clj->js
              (map (fn [r]
                     {"pair"   (str (nth r 0) "  →  " (nth r 1))
                      "degree" (js/parseFloat (str (nth r 2)))
                      "revs"   (js/parseFloat (str (nth r 3)))})
                   sorted-rows))]
    [:div.chart-container
     [:p.chart-subtitle "Top 25 coupling pairs — degree is % of shared commits (higher = stronger hidden dependency)"]
     (r/as-element
      (rc ResponsiveContainer {:width "100%" :height (max 400 (* (count sorted-rows) 26))}
          (rc BarChart {:data data :layout "vertical"
                        :margin #js {:top 5 :right 60 :left 200 :bottom 5}}
              (rc CartesianGrid {:strokeDasharray "3 3" :stroke "#e2e8f0"})
              (rc XAxis {:type "number" :domain #js [0 100]
                         :tick #js {:fontSize 11}
                         :tickFormatter (fn [v] (str v "%"))})
              (rc YAxis {:type "category" :dataKey "pair"
                         :tick #js {:fontSize 10} :width 195})
              (rc Tooltip {:formatter (fn [v _name]
                                        #js [(str v "%") "Coupling degree"])})
              (rc Legend {:wrapperStyle #js {:fontSize "12px"}})
              (rc Bar {:dataKey "degree" :name "Coupling degree %" :fill "#4c6ef5"}))))]))

(defn- age-color
  "Green = recently touched, amber = stale, red = forgotten."
  [months]
  (cond
    (< months 6)  "#20c997"
    (< months 24) "#f59f00"
    :else         "#fa5252"))

(defn age-chart-view
  "Horizontal BarChart of code age — oldest modules first, color-coded by recency.
   Columns expected: [entity age-months]"
  [{:keys [_columns rows]}]
  (let [sorted-rows (->> rows
                         (sort-by (fn [r] (- (js/parseFloat (str (nth r 1 0))))))
                         (take 40))
        data (mapv (fn [r]
                     {:entity     (nth r 0)
                      :age-months (js/parseFloat (str (nth r 1)))})
                   sorted-rows)]
    [:div.chart-container
     [:p.chart-subtitle
      "Top 40 oldest modules  ·  "
      [:span {:style {:color "#20c997"}} "■ < 6 mo"]
      "  "
      [:span {:style {:color "#f59f00"}} "■ 6–24 mo"]
      "  "
      [:span {:style {:color "#fa5252"}} "■ > 24 mo (forgotten)"]]
     (r/as-element
      (rc ResponsiveContainer {:width "100%" :height (max 400 (* (count sorted-rows) 22))}
          (rc BarChart {:data (clj->js data) :layout "vertical"
                        :margin #js {:top 5 :right 60 :left 120 :bottom 5}}
              (rc CartesianGrid {:strokeDasharray "3 3" :stroke "#e2e8f0"})
              (rc XAxis {:type "number" :tick #js {:fontSize 11}
                         :tickFormatter (fn [v] (str v " mo"))})
              (rc YAxis {:type "category" :dataKey "entity"
                         :tick #js {:fontSize 11} :width 115})
              (rc Tooltip {:formatter (fn [v] #js [(str v " months") "Age"])})
              (apply rc Bar {:dataKey "age-months" :name "Age (months)"}
                     (map-indexed
                      (fn [i row]
                        (rc Cell {:key i :fill (age-color (:age-months row))}))
                      data)))))]))

;; ── Summary stat cards ────────────────────────────────────────────────────

(def ^:private stat-meta
  "Well-known summary statistics — label and accent colour."
  {"number-of-commits"       {:label "Commits"      :color "#4c6ef5"}
   "number-of-entities"      {:label "Files"         :color "#20c997"}
   "number-of-authors"       {:label "Authors"       :color "#f59f00"}
   "number-of-added-lines"   {:label "Lines Added"   :color "#38a169"}
   "number-of-deleted-lines" {:label "Lines Deleted" :color "#fa5252"}})

(defn- humanize-stat [s]
  (or (:label (get stat-meta s))
      (-> s
          (str/replace #"number-of-" "")
          (str/replace #"-" " ")
          str/capitalize)))

(defn- stat-color [s]
  (or (:color (get stat-meta s)) "#4c6ef5"))

(defn- fmt-value [v]
  (let [n (js/parseFloat (str v))]
    (if (js/isNaN n) (str v) (.toLocaleString n))))

(defn summary-stats-view
  "Dashboard stat-card grid from [:statistic :value] rows."
  [{:keys [rows]}]
  [:div.stat-grid
   (for [[stat val] rows]
     [:div.stat-card {:key   stat
                      :style {:border-top-color (stat-color stat)}}
      [:div.stat-value {:style {:color (stat-color stat)}}
       (fmt-value val)]
      [:div.stat-label (humanize-stat stat)]])])

;; ── Dispatch ──────────────────────────────────────────────────────────────

(defn render-chart
  "Dispatch to the right chart component based on viz-type keyword."
  [viz-type data]
  (case viz-type
    :bar-chart      [bar-chart-view data]
    :hotspot-bar    [hotspot-bar-view data]
    :time-series    [time-series-view data]
    :network-graph  [coupling-chart-view data]
    :heatmap        [age-chart-view data]
    :summary-stats  [summary-stats-view data]
    :table          nil  ; table-only analyses — no chart overhead
    [bar-chart-view data]))
