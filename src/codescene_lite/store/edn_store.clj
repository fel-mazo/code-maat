(ns codescene-lite.store.edn-store
  "Atom-backed EDN file persistence.

   On startup: slurp existing EDN files into atoms.
   On every mutation: persist atom state to disk via spit.
   Thread safety: all mutations go through swap! with pure functions.

   File layout:
     <data-dir>/repos.edn     — {:repos {<uuid> <repo-map>}}
     <data-dir>/results/      — one file per repo: <repo-id>.edn
     <data-dir>/jobs.edn      — {:jobs {<job-id> <job-map>}} (ephemeral)"
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; ── Internal helpers ──────────────────────────────────────────────────────

(defn- ensure-dir! [path]
  (.mkdirs (io/file path)))

(defn- read-edn-file [file]
  (when (.exists file)
    (edn/read-string (slurp file))))

(defn- write-edn-file! [file data]
  (spit file (pr-str data)))

(defn- repos-file [data-dir]
  (io/file data-dir "repos.edn"))

(defn- jobs-file [data-dir]
  (io/file data-dir "jobs.edn"))

(defn- results-file [data-dir repo-id]
  (io/file data-dir "results" (str repo-id ".edn")))

;; ── Store record ─────────────────────────────────────────────────────────

(defrecord EdnStore [data-dir repos-atom jobs-atom])

(defn create-store
  "Initialise the EDN store, loading existing data from disk."
  [{:keys [data-dir]}]
  (ensure-dir! data-dir)
  (ensure-dir! (io/file data-dir "results"))
  (let [repos (or (read-edn-file (repos-file data-dir)) {:repos {}})
        jobs  {:jobs {}}]                ; jobs are ephemeral — don't load from disk
    (->EdnStore data-dir
                (atom repos)
                (atom jobs))))

;; ── Repo CRUD ─────────────────────────────────────────────────────────────

(defn list-repos [{:keys [repos-atom]}]
  (vals (:repos @repos-atom)))

(defn get-repo [{:keys [repos-atom]} id]
  (get-in @repos-atom [:repos id]))

(defn save-repo! [{:keys [repos-atom data-dir] :as store} repo]
  (swap! repos-atom assoc-in [:repos (:id repo)] repo)
  (write-edn-file! (repos-file data-dir) @repos-atom)
  repo)

(defn delete-repo! [{:keys [repos-atom data-dir] :as store} id]
  (swap! repos-atom update :repos dissoc id)
  (write-edn-file! (repos-file data-dir) @repos-atom)
  ;; Also remove any cached results for this repo
  (let [rf (results-file data-dir id)]
    (when (.exists rf) (.delete rf)))
  nil)

;; ── Result cache ──────────────────────────────────────────────────────────

(defn- load-results [data-dir repo-id]
  (or (read-edn-file (results-file data-dir repo-id))
      {:results {}}))

(defn get-result [{:keys [data-dir]} repo-id cache-key]
  ;; Try direct cache-key lookup first; fall back to searching by :analysis name
  ;; so that the frontend can load results by plain analysis name even when
  ;; the cache-key includes option suffixes.
  (let [results (:results (load-results data-dir repo-id))]
    (or (get results cache-key)
        (->> (vals results)
             (filter #(= cache-key (:analysis %)))
             first))))

(defn list-results [{:keys [data-dir]} repo-id]
  (->> (vals (:results (load-results data-dir repo-id)))
       (map (fn [r] {:analysis-name (:analysis r) :cached-at (:cached-at r)}))))

(defn save-result! [{:keys [data-dir]} repo-id cache-key result]
  (let [rf   (results-file data-dir repo-id)
        data (load-results data-dir repo-id)
        updated (assoc-in data [:results cache-key] result)]
    (write-edn-file! rf updated)
    result))

;; ── Job management ────────────────────────────────────────────────────────

(defn get-job [{:keys [jobs-atom]} job-id]
  (get-in @jobs-atom [:jobs job-id]))

(defn save-job! [{:keys [jobs-atom]} job]
  (swap! jobs-atom assoc-in [:jobs (:id job)] job)
  job)

(defn update-job! [{:keys [jobs-atom]} job-id f & args]
  (apply swap! jobs-atom update-in [:jobs job-id] f args))
