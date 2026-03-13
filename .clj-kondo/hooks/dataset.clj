(ns hooks.dataset
  (:require [clj-kondo.hooks-api :as api]))

(defn def-ds [{:keys [node]}]
  (let [[name-node data-node] (rest (:children node))]
    {:node (api/list-node
            [(api/token-node 'def)
             name-node
             data-node])}))
