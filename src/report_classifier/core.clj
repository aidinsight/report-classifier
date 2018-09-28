(ns report-classifier.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [report-classifier.watson :as watson]
            [clojure.string :as str]))

(def classifier-ids ["f33041x451-nlc-442" "f33041x451-nlc-450"])

(defn theme->clusters [theme]
  (println "THEME" theme)
  (let [mapping {"Recovery and Reconstruction"      [:shelter :early-recovery :logistics]
                 "Water Sanitation Hygiene"         [:water-sanitation]
                 "Coordination"                     [:coordination]
                 "Health"                           [:health :nutrition]
                 "Shelter and Non-Food Items",      [:shelter]
                 "Food and Nutrition"               [:food-security :nutrition]
                 "Education"                        [:education]
                 "Safety and Security"              [:protection :logistics]
                 "Logistics and Telecommunications" [:emergency-telecommunications]
                 "Protection and Human Rights"      [:protection]}]
    (get mapping theme [])))

(defn- top-classes [config text]
  (map
    #(-> (watson/classify (:watson config) % text)
         :body
         :top_class)
    classifier-ids))

(defn classify [config text]
  (distinct (mapcat theme->clusters (top-classes config text))))

;; Usage: lein run config.edn Text to classify here
(defn -main [& args]
  (if-let [config-file (first args)]
    (let [config (-> config-file io/file slurp edn/read-string)]
      (clojure.pprint/pprint (classify config (str/join " " (drop 2 args)))))
    (println "Specify EDN config file")))