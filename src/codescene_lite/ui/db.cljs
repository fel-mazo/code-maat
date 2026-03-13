(ns codescene-lite.ui.db)

(def default-db
  {:repos           {}   ; id -> repo-map
   :active-repo-id  nil
   :reports         {}   ; repo-id -> {:status :loading/:running/:loaded/:error
                         ;              :data :cached-at :job-id :phase :error}
   :jobs            {}   ; job-id -> {:status :queued/:running/:done/:error :phase}
   :active-polls    {}   ; job-id -> token (cancel stale polls)
   :discovered-repos nil ; nil=not fetched, :loading, or [{:name :path}]
   :ui {:view          :repos       ; :repos | :dashboard
        :active-tab    :overview    ; :overview | :hotspots | :knowledge
                                    ; | :coupling | :debt | :health
        :add-repo-form {:open? false :name "" :path "" :error nil}
        :config-open   false        ; branch-prefix settings panel
        :filters       {:from "" :to "" :file-pattern "" :min-revs 0}
        :skip-cache    false
        :flash         nil}})
