(ns docpkg.confluence
  (:require [clj-http.client :as client]))

(comment
  (def response (client/get (str "https://" domain-name "/wiki/rest/api/content/" content-id "?expand=body.storage,version")
                  {:basic-auth [user-name api-token]
                   :as :json}))
  (keys response)
  (:body response)
  (-> response :body :body :storage :value println)
  (def version-number (-> response :body :version :number))
  (client/put (str "https://" domain-name "/wiki/rest/api/content/" content-id "?expand=body.storage,version")
    {:form-params {:type "page"
                   :title "foo"
                   :body {:storage {:value "foo" :representation "storage"}}
                   :version {:number (inc version-number)}}
     :content-type :json
     :basic-auth [user-name api-token]})
  (def response (client/get (str "https://" domain-name "/wiki/rest/api/content/" content-id "/child/attachment?filename=test.html")
                  {:basic-auth [user-name api-token]
                   :as :json}))
  (-> response :body :results)
  (-> response :body :results (get 4))
  (client/put (str "https://" domain-name "/wiki/rest/api/content/" content-id "/child/attachment")
    {:multipart [{:name "pdf.feature" :part-name "file" :content (clojure.java.io/file "src/main/resources/features/pdf.feature")
                  :mime-type "text/html"}
                 {:name "comment" :content "My comment" :mime-type "text/plain" :encoding "utf-8"}]
     :basic-auth [user-name api-token]}))
  ;; can do String, InputStream, File, a byte-array
