(ns codescene-lite.engine.metadata
  "Analysis metadata registry.
   Drives both API documentation and the frontend's dynamic configuration panel.
   viz-type determines which chart component the frontend renders.")

(def analysis-metadata
  {"authors"
   {:description   "Number of unique authors per module — reveals organisational complexity"
    :columns       [:entity :n-authors :n-revs]
    :viz-type      :bar-chart
    :requires-churn? false
    :options       []}

   "revisions"
   {:description   "Hotspot: change frequency per module — the more a file changes, the higher the risk"
    :columns       [:entity :n-revs]
    :viz-type      :hotspot-bar
    :requires-churn? false
    :options       []}

   "hotspots"
   {:description   "Hotspot analysis — files that are both large AND frequently changed (highest technical debt risk)"
    :columns       [:entity :n-revs :code-size :hotspot-score]
    :viz-type      :hotspot-bar
    :requires-churn? true
    :async?        true
    :options       []}

   "coupling"
   {:description   "Logical coupling — modules that always change together are secretly coupled"
    :columns       [:entity :coupled :degree :average-revs]
    :viz-type      :network-graph
    :requires-churn? false
    :async?        true
    :options       [:min-revs :min-shared-revs :min-coupling
                    :max-coupling :max-changeset-size
                    :temporal-period :verbose-results]}

   "soc"
   {:description   "Sum of Coupling — total co-change frequency per module"
    :columns       [:entity :soc]
    :viz-type      :bar-chart
    :requires-churn? false
    :options       [:min-revs :min-shared-revs :min-coupling
                    :max-coupling :max-changeset-size]}

   "summary"
   {:description   "Overview statistics: commits, entities, authors"
    :columns       [:statistic :value]
    :viz-type      :summary-stats
    :requires-churn? false
    :options       []}

   "abs-churn"
   {:description   "Absolute code churn over time — lines added and deleted per date"
    :columns       [:date :added :deleted :commits]
    :viz-type      :time-series
    :requires-churn? true
    :options       []}

   "author-churn"
   {:description   "Code churn per author — who adds/deletes the most code"
    :columns       [:author :added :deleted :commits]
    :viz-type      :bar-chart
    :requires-churn? true
    :options       []}

   "entity-churn"
   {:description   "Code churn per file — files with the most churn are hotspots"
    :columns       [:entity :added :deleted :commits]
    :viz-type      :bar-chart
    :requires-churn? true
    :options       []}

   "entity-ownership"
   {:description   "Code ownership distribution per file"
    :columns       [:entity :author :added :deleted]
    :viz-type      :table
    :requires-churn? true
    :options       []}

   "main-dev"
   {:description   "Main developer per file by churn — who owns each module"
    :columns       [:entity :main-dev :added :total-added :ownership]
    :viz-type      :bar-chart
    :requires-churn? true
    :options       []}

   "refactoring-main-dev"
   {:description   "Main developer by refactoring patterns (deletion-weighted)"
    :columns       [:entity :main-dev :added :total-added :ownership]
    :viz-type      :bar-chart
    :requires-churn? true
    :options       []}

   "entity-effort"
   {:description   "Effort distribution — revision share per author per module"
    :columns       [:entity :author :author-revs :total-revs]
    :viz-type      :table
    :requires-churn? false
    :async?        true
    :options       []}

   "main-dev-by-revs"
   {:description   "Main developer by revision count (not churn)"
    :columns       [:entity :main-dev :author-revs :total-revs]
    :viz-type      :bar-chart
    :requires-churn? false
    :options       []}

   "fragmentation"
   {:description   "Entity fragmentation — how spread across authors is each module"
    :columns       [:entity :fractal-value :total-revs]
    :viz-type      :bar-chart
    :requires-churn? false
    :async?        true
    :options       []}

   "communication"
   {:description   "Team communication needs — Conway's Law applied to your codebase"
    :columns       [:entity :coupled :degree :average-revs]
    :viz-type      :network-graph
    :requires-churn? false
    :async?        true
    :options       [:min-revs :min-shared-revs :min-coupling
                    :max-coupling :max-changeset-size :team-map-file]}

   "messages"
   {:description   "Commit message word frequency — find recurring themes in your history"
    :columns       [:word :freq]
    :viz-type      :bar-chart
    :requires-churn? false
    :options       [:expression-to-match]}

   "age"
   {:description   "Code age by module — old untouched code may be forgotten complexity"
    :columns       [:entity :age-months]
    :viz-type      :heatmap
    :requires-churn? false
    :options       [:age-time-now]}})

(def option-specs
  "Metadata for each analysis option — used to render the frontend config panel."
  {:min-revs             {:label "Min revisions"         :type :int  :default 5}
   :min-shared-revs      {:label "Min shared revisions"  :type :int  :default 5}
   :min-coupling         {:label "Min coupling %"        :type :int  :default 30}
   :max-coupling         {:label "Max coupling %"        :type :int  :default 100}
   :max-changeset-size   {:label "Max changeset size"    :type :int  :default 30}
   :temporal-period      {:label "Temporal period (days)" :type :int :default nil}
   :verbose-results      {:label "Verbose details"       :type :bool :default false}
   :expression-to-match  {:label "Commit message regex"  :type :string :default nil}
   :age-time-now         {:label "Reference date (YYYY-MM-DD)" :type :date :default nil}
   :team-map-file        {:label "Team map CSV content"  :type :text :default nil}})

(defn async? [analysis-name]
  (true? (get-in analysis-metadata [analysis-name :async?])))
