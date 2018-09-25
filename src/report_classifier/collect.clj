(ns report-classifier.collect
  (:require [cheshire.core :as ch]
            [clj-http.client :as http]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]))

(defn- with-parsed-body [response]
  (update-in response [:body] ch/parse-string true))

(defn- list-reports
  ([offset limit]
   (->
     (http/get
       (str "https://api.reliefweb.int/v1/reports?appname=apidoc"
            "&limit=" limit
            "&offset=" offset))
     with-parsed-body))
  ([] (list-reports 0 10)))

(defn- get-report [id]
  (->
    (http/get
      (str "https://api.reliefweb.int/v1/reports/" id))
    with-parsed-body))

(defn- summary [{{data :data} :body}]
  (let [report-fields (-> data first :fields)
        id (:id report-fields)
        languages (map :code (:language report-fields))
        body (:body report-fields)
        themes (map :name (:theme report-fields))
        disaster-types (map :name (:disaster_type report-fields))]
    {:disaster-types disaster-types :id id :languages languages :body body :themes themes}))

(defn debug->> [message x]
  (println message x)
  x)

(defn- single? [coll]
  (= (count coll) 1))

(defn- single-themed-reports
  "Collect single-themed reports that have a body and disaster type."
  [offset limit]
  (->> (list-reports offset limit)
       :body
       :data
       (map (comp summary get-report :id))
       (remove #(-> % :body nil?))
       (remove #(-> % :disaster-types empty?))
       (filter #(-> % :themes single?))
       (filter #(= ["en"] (:languages %)))))                ; English-only, for now

;;
;; https://console.bluemix.net/docs/services/natural-language-classifier/best-practices.html#best-practices-for-classifiers
;;
;; Guidelines for good training
;;
;; * Limit the length of input text to fewer than 60 words.
;; * Limit the number of classes to several hundred classes.
;;   Support for larger numbers of classes might be included in later versions of the service.
;; * Make sure that each class is matched with at least 5 - 10 records when each text record has only one class.
;;   This number provides enough training on that class.
;; * Evaluate the need for multiple classes. Two common reasons drive multiple classes:
;;    * When the text is vague, identifying a single class is not always clear.
;;    * When experts interpret the text in different ways, multiple classes support those interpretations.
;;      However, if many texts in your training data include multiple classes, or if some texts have more than three classes,
;;      you might need to refine the classes. For example, review whether the classes are hierarchical.
;;      If they are hierarchical, include the leaf node as the class.

(defn enough-themes?
  "Ideally we need at least 5 reports for each theme. But ignore low frequency items to allow termination."
  [theme-counts]
  (when (seq theme-counts)                                  ; need some, at least
    (let [total-themes (apply + (vals theme-counts))
          ;; If we have some very low frequency themes, ignore them
          low-frequency? (fn [value] (< value (* 0.005 total-themes)))]
      (>= (apply
            min
            (remove low-frequency? (vals theme-counts))) 5))))

(defn collect-reports []
  (loop [collected []
         offset 0
         limit 20
         theme-counts {}]
    (if (enough-themes? theme-counts)
      collected
      (let [_ (println "themes-counts" theme-counts)
            _ (println "Retrieving reports" (inc offset) "-" (+ offset limit) " ...")
            reports (single-themed-reports offset limit)
            updated (concat collected reports)]
        (recur
          updated
          (+ offset limit)
          limit
          (reduce
            (fn [acc report]
              (update-in acc [(-> report :themes first)] (fnil inc 0)))
            theme-counts
            reports))))))

(def ^:private watson-max-classes-per-classifier 8)

(def ^:private watson-max-fragment-length 1024)

(defn- paragraphs
  ([text max-length]
   (as-> text $
         (str/split $ #"\n")
         (map str/trim $)
         (filter #(< 40 (count %) (inc max-length)) $)))
  ([text] (paragraphs text watson-max-fragment-length)))

(defn csv-data [reports]
  (mapcat
    (fn [rep]
      (map
        (fn [fragment] [fragment (-> rep :themes first)])
        (paragraphs (rep :body))))
    reports) )

(defn- save-classifier-data [file-suffix reports]
  (let [out-file (str "classifier" file-suffix ".csv")]
    (println "Writing " out-file "...")
    (clojure.pprint/pprint reports)
    (with-open [writer (io/writer out-file)]
      (csv/write-csv
        writer
        (csv-data reports)
        :quote? string?))))

(defn- first-theme [report]
  (-> report :themes first))

(defn -main [& args]
  (let [reports-by-theme (group-by first-theme (collect-reports))]
    (loop [classifier-number 1
           remaining-reports reports-by-theme]
      (when (seq remaining-reports)
        (let [classifier-reports (take watson-max-classes-per-classifier remaining-reports)]
          (do
            (save-classifier-data
              (str "_" classifier-number)
              (mapcat (fn [[_ report]] report) classifier-reports))
            (recur
              (inc classifier-number)
              (drop watson-max-classes-per-classifier remaining-reports))))))))

