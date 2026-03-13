(ns hooks.data-driven
  (:require [clj-kondo.hooks-api :as api]))

;; (def-dd-test name [param values] & body)
;; expands to (deftest name (let [param nil] body...))
;; so clj-kondo understands the `param` binding inside body.
(defn def-dd-test [{:keys [node]}]
  (let [[name-node args-node & body] (rest (:children node))
        [param-node] (rest (:children args-node))]
    {:node (api/list-node
            [(api/token-node 'clojure.test/deftest)
             name-node
             (api/list-node
              [(api/token-node 'let)
               (api/vector-node [param-node (api/token-node 'nil)])
               (api/list-node (list* (api/token-node 'do) body))])])}))
