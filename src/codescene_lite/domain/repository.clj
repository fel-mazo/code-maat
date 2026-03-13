(ns codescene-lite.domain.repository
  "Repository entity — creation, validation, and cache-key generation."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util UUID]))

(defn create
  "Build a new repository map from user-supplied fields.
   Assigns a UUID, timestamps it, and sets defaults."
  [{:keys [name path vcs description]
    :or   {vcs "git2" description ""}}]
  {:id          (str (UUID/randomUUID))
   :name        name
   :path        path
   :vcs         (or vcs "git2")
   :description (or description "")
   :created-at  (str (java.time.Instant/now))})

(defn valid? [{:keys [name path vcs]}]
  (and (seq name)
       (seq path)
       (contains? #{"git" "git2" "svn" "hg" "p4" "tfs"} vcs)))

(defn path-exists? [{:keys [path]}]
  (.exists (io/file path)))

(defn cache-key
  "Derives a cache key for a given analysis + options combination.
   Keys like 'coupling|min-coupling=30|min-revs=5' allow per-option caching.
   Nil and empty-string values are excluded so that omitted date ranges don't
   pollute the key (avoids 'revisions|from-date=|to-date=' noise)."
  [analysis-name options]
  (let [relevant (->> (dissoc options :analysis :version-control :log-file)
                      (remove (fn [[_ v]] (or (nil? v) (= "" v))))
                      (into {}))]
    (if (empty? relevant)
      analysis-name
      (let [pairs (->> relevant
                       (sort-by key)
                       (map (fn [[k v]] (str (name k) "=" v)))
                       (str/join "|"))]
        (str analysis-name "|" pairs)))))
