(ns codescene-lite.router
  "Reitit route definitions and middleware stack."
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [muuntaja.core :as m]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [codescene-lite.middleware.error :as err]
            [codescene-lite.api.repos :as repos-api]
            [codescene-lite.api.analysis :as analysis-api]
            [codescene-lite.api.jobs :as jobs-api]))

(defn create-router [store job-queue]
  (ring/router
   [["/swagger.json"
     {:get {:no-doc true
            :swagger {:info {:title "codescene-lite API"
                             :description "Self-hosted code analysis"
                             :version "0.1.0"}}
            :handler (swagger/create-swagger-handler)}}]

    ["/health"
     {:get {:summary "Health check"
            :handler (fn [_] {:status 200 :body {:status "ok" :version "0.1.0"}})}}]

    ["/api"
     ["/analyses"
      {:get {:summary "List all available analyses with metadata"
             :handler (analysis-api/list-analyses nil)}}]

     ["/repos"
      {:get  {:summary "List all repositories"
              :handler (repos-api/list-repos store)}
       :post {:summary "Add a repository"
              :handler (repos-api/create-repo store)}}]

     ["/repos-discover"
      {:get {:summary "Discover git repos in mounted volumes (/repos)"
             :handler (repos-api/discover-repos store)}}]

     ["/repos/:id"
      {:get    {:summary "Get a repository"
                :handler (repos-api/get-repo store)}
       :put    {:summary "Update a repository"
                :handler (repos-api/update-repo store)}
       :delete {:summary "Delete a repository and its cached results"
                :handler (repos-api/delete-repo store)}}]

     ["/repos/:id/analyze"
      {:post {:summary "Run an analysis on a repository"
              :handler (analysis-api/run-analysis store job-queue)}}]

     ["/repos/:id/results"
      {:get {:summary "List cached analysis results for a repository"
             :handler (repos-api/list-results store)}}]

     ["/repos/:id/results/:analysis"
      {:get {:summary "Get a specific cached analysis result"
             :handler (repos-api/get-result store)}}]]

    ["/api/jobs/:id"
     {:get {:summary "Poll async job status"
            :handler (jobs-api/get-job store)}}]]

   {:data {:muuntaja   m/instance
           :middleware [swagger/swagger-feature
                        muuntaja/format-middleware
                        coercion/coerce-exceptions-middleware
                        coercion/coerce-request-middleware
                        coercion/coerce-response-middleware]}}))

(defn create-handler [store job-queue]
  (-> (ring/ring-handler
       (create-router store job-queue)
       (ring/routes
        (swagger-ui/create-swagger-ui-handler
         {:path   "/swagger-ui"
          :config {:validatorUrl nil}})
        (ring/create-resource-handler {:path "/"})
        (ring/create-default-handler
         {:not-found (constantly {:status 404 :body {:error "not found"}})})))
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified
      err/wrap-errors))
