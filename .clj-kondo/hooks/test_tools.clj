(ns hooks.test-tools
  (:require [clj-kondo.hooks-api :as api]))

;; (def-data-driven-with-vcs-test name test-data & body)
;; expands to (deftest name (let [log-file nil options nil] body...))
;; so clj-kondo understands the implicit `log-file` and `options` bindings.
(defn def-data-driven-with-vcs-test [{:keys [node]}]
  (let [[name-node _test-data & body] (rest (:children node))]
    {:node (api/list-node
            [(api/token-node 'clojure.test/deftest)
             name-node
             (api/list-node
              [(api/token-node 'let)
               (api/vector-node [(api/token-node 'log-file) (api/token-node 'nil)
                                 (api/token-node 'options)  (api/token-node 'nil)])
               (api/list-node (list* (api/token-node 'do) body))])])}))
