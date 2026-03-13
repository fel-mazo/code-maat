(ns codescene-lite.ui.views.results.table
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(def page-size 50)

(defn- escape-csv-field [v]
  (let [s (str v)]
    (if (re-find #"[,\"\n]" s)
      (str "\"" (clojure.string/replace s "\"" "\"\"") "\"")
      s)))

(defn- rows->csv [cols rows]
  (clojure.string/join "\n"
                       (cons (clojure.string/join "," (map escape-csv-field cols))
                             (map (fn [row] (clojure.string/join "," (map escape-csv-field row)))
                                  rows))))

(defn- download-csv! [cols rows filename]
  (let [blob (js/Blob. #js [(rows->csv cols rows)] #js {:type "text/csv;charset=utf-8;"})
        url  (js/URL.createObjectURL blob)
        a    (doto (js/document.createElement "a")
               (aset "href" url)
               (aset "download" filename)
               (.click))]
    (js/URL.revokeObjectURL url)))

(defn- sort-rows [rows col-idx ascending?]
  (let [comparator (fn [a b]
                     (let [va (nth a col-idx nil)
                           vb (nth b col-idx nil)
                           ;; Try numeric comparison first
                           na (js/parseFloat va)
                           nb (js/parseFloat vb)]
                       (if (and (js/isFinite na) (js/isFinite nb))
                         (compare na nb)
                         (compare (str va) (str vb)))))]
    (if ascending?
      (sort comparator rows)
      (sort #(comparator %2 %1) rows))))

(defn- numeric? [val]
  (and (some? val) (js/isFinite (js/parseFloat (str val)))))

(defn data-table
  "Paginated, sortable table.
   Props: {:columns [\"col1\" \"col2\" ...] :rows [[v1 v2 ...] ...]}"
  [{:keys [columns rows]}]
  (let [state (r/atom {:page 0 :sort-col nil :sort-asc true})]
    (fn [{:keys [columns rows]}]
      (let [{:keys [page sort-col sort-asc]} @state
            sorted-rows  (if (some? sort-col)
                           (sort-rows rows sort-col sort-asc)
                           rows)
            total-pages  (max 1 (js/Math.ceil (/ (count sorted-rows) page-size)))
            safe-page    (min page (dec total-pages))
            page-rows    (take page-size (drop (* safe-page page-size) sorted-rows))
            total-rows   (count rows)]

        [:div
         [:div.table-container
          [:table.data-table
           [:thead
            [:tr
             (map-indexed
              (fn [idx col]
                [:th
                 {:key      idx
                  :class    "sortable"
                  :on-click #(swap! state (fn [s]
                                            (if (= sort-col idx)
                                              (update s :sort-asc not)
                                              (assoc s :sort-col idx :sort-asc true))))}
                 col
                 (when (= sort-col idx)
                   [:span.sort-indicator (if sort-asc " ▲" " ▼")])])
              columns)]]
           [:tbody
            (map-indexed
             (fn [row-idx row]
               [:tr
                {:key row-idx}
                (map-indexed
                 (fn [col-idx val]
                   [:td
                    {:key   col-idx
                     :class (when (numeric? val) "numeric")}
                    (str val)])
                 row)])
             page-rows)]]]

         [:div.table-pagination
          [:span (str "Showing " (inc (* safe-page page-size)) "–"
                      (min (* (inc safe-page) page-size) total-rows)
                      " of " total-rows " rows")]
          [:div.pagination-controls
           [:button.btn.btn-secondary.btn-sm
            {:on-click #(swap! state update :page (fn [p] (max 0 (dec p))))
             :disabled (= safe-page 0)}
            "← Prev"]
           [:span {:style {:padding "0 0.5rem"}}
            (str "Page " (inc safe-page) " / " total-pages)]
           [:button.btn.btn-secondary.btn-sm
            {:on-click #(swap! state update :page (fn [p] (min (dec total-pages) (inc p))))
             :disabled (= safe-page (dec total-pages))}
            "Next →"]
           [:button.btn.btn-secondary.btn-sm
            {:on-click #(download-csv! columns rows "results.csv")}
            "Export CSV"]]]]))))
