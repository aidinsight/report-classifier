(ns report-classifier.watson
  (:require [cheshire.core :as ch]
            [clj-http.client :as http]
            [cemerick.url :refer [url-encode]]))

(defn- with-parsed-body [response]
  (update-in response [:body] ch/parse-string true))

;;
;;curl -i --user "{username}":"{password}" -F training_data=@{path_to_file}/weather_data_train.csv -F training_metadata="{\"language\":\"en\",\"name\":\"TutorialClassifier\"}" "https://gateway.watsonplatform.net/natural-language-classifier/api/v1/classifiers"
;;

(defn list-classifiers [{:keys [username password api-base-url] :as watson-config}]
  (-> (http/get
        (str api-base-url "/v1/classifiers")
        {:basic-auth [username password]})
      with-parsed-body))

(defn create-classifier [{:keys [username password api-base-url]} csv-file-name name]
  (-> (http/post
        (str api-base-url "/v1/classifiers")
        {:basic-auth       [username password]
         :multipart        [{:name    "training_metadata"
                             :content (ch/generate-string {:language "en" :name name})}
                            {:name "training_data" :content (clojure.java.io/file csv-file-name)}]
         :throw-exceptions false})
      with-parsed-body))


(defn get-classifier [{:keys [username password api-base-url]} classifier-id]
  (-> (http/get
        (str api-base-url "/v1/classifiers/" classifier-id)
        {:basic-auth [username password]})
      with-parsed-body))

;; TODO Check that text is within maximum size of 2048 characters
(defn classify [{:keys [username password api-base-url]} classifier-id text]
  (let [url (str
              api-base-url
              "/v1/classifiers/"
              classifier-id
              "/classify?text="
              (url-encode text))
        _ (println "URL" url)]
    (-> (http/get
          url
          {:basic-auth [username password]})
        with-parsed-body)))

(comment


  (def temp-config
    {:username     "<username>"
     :password     "<password>"
     :api-base-url "https://gateway.watsonplatform.net/natural-language-classifier/api"})

  (create-classifier temp-config "resources/classifier_1.csv" "ReliefWebClassifier1")
  (create-classifier temp-config "resources/classifier_2.csv" "ReliefWebClassifier2")

  (clojure.pprint/print (list-classifiers temp-config))

  (clojure.pprint/pprint (get-classifier temp-config "f33041x451-nlc-442"))
  (clojure.pprint/pprint (get-classifier temp-config "f33041x451-nlc-450"))

  )
