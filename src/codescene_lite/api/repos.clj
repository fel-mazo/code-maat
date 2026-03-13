(ns codescene-lite.api.repos
  "HTTP handlers for /api/repos — repository CRUD."
  (:require [codescene-lite.store.edn-store :as store]
            [codescene-lite.domain.repository :as repo])
  (:import [java.io File]))

(defn list-repos [store]
  (fn [_request]
    {:status 200 :body (vec (store/list-repos store))}))

(defn get-repo [store]
  (fn [{{:keys [id]} :path-params}]
    (if-let [r (store/get-repo store id)]
      {:status 200 :body r}
      {:status 404 :body {:error (str "Repository not found: " id)}})))

(defn create-repo [store]
  (fn [{:keys [body-params]}]
    (let [fields body-params]
      (cond
        (not (repo/valid? fields))
        {:status 400 :body {:error "name and path are required"}}

        (not (repo/path-exists? fields))
        {:status 400 :body {:error (str "Path does not exist: " (:path fields))}}

        :else
        (let [r (repo/create fields)]
          {:status 201 :body (store/save-repo! store r)})))))

(defn update-repo [store]
  (fn [{:keys [body-params] {:keys [id]} :path-params}]
    (if-let [existing (store/get-repo store id)]
      (let [updated (merge existing
                           (select-keys body-params
                                        [:name :path :description
                                         :bug-prefixes :refactor-prefixes]))]
        {:status 200 :body (store/save-repo! store updated)})
      {:status 404 :body {:error (str "Repository not found: " id)}})))

(defn delete-repo [store]
  (fn [{{:keys [id]} :path-params}]
    (if (store/get-repo store id)
      (do
        (store/delete-repo! store id)
        {:status 204 :body nil})
      {:status 404 :body {:error (str "Repository not found: " id)}})))

(defn list-results [store]
  (fn [{{:keys [id]} :path-params}]
    (if (store/get-repo store id)
      {:status 200 :body (vec (store/list-results store id))}
      {:status 404 :body {:error (str "Repository not found: " id)}})))

(defn get-result [store]
  (fn [{{:keys [id analysis]} :path-params}]
    (if (store/get-repo store id)
      (if-let [r (store/get-result store id analysis)]
        {:status 200 :body r}
        {:status 404 :body {:error (str "No cached result for analysis: " analysis)}})
      {:status 404 :body {:error (str "Repository not found: " id)}})))

;; ── Discovery ────────────────────────────────────────────────────────────────

(defn- find-git-dirs
  "BFS walk of `root` up to `max-depth`, returning paths that contain a .git dir."
  [root max-depth]
  (when (.isDirectory (File. ^String root))
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [root 0])
           found []]
      (if (empty? queue)
        found
        (let [[path depth] (peek queue)
              dir          (File. ^String path)]
          (cond
            (not (.isDirectory dir))
            (recur (pop queue) found)

            (.exists (File. dir ".git"))
            (recur (pop queue) (conj found path))

            (< depth max-depth)
            (let [children (->> (or (.listFiles dir) (make-array File 0))
                                (filter #(.isDirectory ^File %))
                                (map #(vector (.getAbsolutePath ^File %) (inc depth))))]
              (recur (into (pop queue) children) found))

            :else
            (recur (pop queue) found)))))))

(defn discover-repos [store]
  (fn [_request]
    (let [known-paths (set (map :path (store/list-repos store)))
          found       (->> (find-git-dirs "/repos" 4)
                           (remove known-paths)
                           (map (fn [path]
                                  {:name (.getName (File. ^String path))
                                   :path path}))
                           vec)]
      {:status 200 :body found})))
