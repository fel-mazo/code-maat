(ns codescene-lite.ui.db)

(def default-db
  {:repos           {}   ; id -> repo-map
   :active-repo-id  nil
   :analyses-meta   {}   ; name -> {:viz-type, :options, :columns, :description}
   :results         {}   ; [repo-id analysis-name] -> {:status :loading/:loaded/:error, :data}
   :jobs            {}   ; job-id -> {:status :queued/:running/:done/:error, :result}
   :ui {:view              :repos   ; :repos | :repo-detail | :results
        :add-repo-form     {:open? false :name "" :path "" :vcs "git2" :error nil}
        :selected-analysis nil
        :analysis-options  {}
        :date-range        {:from "" :to ""}}})
