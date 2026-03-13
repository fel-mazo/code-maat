(ns codescene-lite.api.repos
  "HTTP handlers for /api/repos — repository CRUD."
  (:require [ring.util.response :as resp]
            [codescene-lite.store.edn-store :as store]
            [codescene-lite.domain.repository :as repo]))

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
        {:status 400 :body {:error "name, path, and vcs are required. vcs must be one of: git, git2, svn, hg, p4, tfs"}}

        (not (repo/path-exists? fields))
        {:status 400 :body {:error (str "Path does not exist: " (:path fields))}}

        :else
        (let [r (repo/create fields)]
          {:status 201 :body (store/save-repo! store r)})))))

(defn update-repo [store]
  (fn [{:keys [body-params] {:keys [id]} :path-params}]
    (if-let [existing (store/get-repo store id)]
      (let [updated (merge existing (select-keys body-params [:name :path :vcs :description]))]
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
