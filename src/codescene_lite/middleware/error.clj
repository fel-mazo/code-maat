(ns codescene-lite.middleware.error
  "Consistent error response shaping for the Reitit handler stack.")

(defn wrap-errors
  "Ring middleware that catches unhandled exceptions and returns a
   consistent {:error message} JSON body with appropriate status codes."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data    (ex-data e)
              status  (get data :status 500)
              message (or (:message data) (.getMessage e))]
          {:status  status
           :headers {"Content-Type" "application/json"}
           :body    {:error message}}))
      (catch IllegalArgumentException e
        {:status  400
         :headers {"Content-Type" "application/json"}
         :body    {:error (.getMessage e)}})
      (catch Exception e
        {:status  500
         :headers {"Content-Type" "application/json"}
         :body    {:error (str "Internal error: " (.getMessage e))}}))))

(defn not-found-handler
  "Default 404 handler for unmatched routes."
  [_request]
  {:status  404
   :headers {"Content-Type" "application/json"}
   :body    {:error "Not found"}})
