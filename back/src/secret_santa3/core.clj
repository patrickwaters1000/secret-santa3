(ns secret-santa3.core
  (:require
    [cheshire.core :as json]
    [clojure.set :refer [subset? intersection]]
    [clojure.string :as string]
    [compojure.core :refer :all]
    [org.httpkit.server :refer [run-server]]
    [ring.middleware.params :as rmp])
  (:gen-class))

(def state (atom nil))

(defn- parse-args [args]
  {:pre [(even? (count args))]}
  (reduce (fn [acc [k-arg v-arg]]
            (case k-arg
              "-n" (update acc :names conj v-arg)
              "-g" (assoc acc :gifts (Integer/parseInt v-arg))
              "-i" (update acc :incompatible-pairs conj (set (string/split v-arg #",")))
              "-r" (assoc acc :max-retries (Integer/parseInt v-arg))))
          {:names #{}
           :incompatible-pairs #{}
           :max-retries 1000}
          (partition 2 args)))

(defn- check-incompatible-pairs [state]
  (and (every? #(= 2 (count %)) (:incompatible-pairs state))
       (every? #(subset? % (:names state)) (:incompatible-pairs state))))

(defn- initial-state [args]
  {:post [(check-incompatible-pairs %)]}
  (-> (parse-args args)
      (assoc :ready #{}
             :gift-assignments nil)))

(defn- all-ready? [state]
  (println (format "Names = %s, ready = %s"
                   (:names state)
                   (:ready state)))
  (= (set (:names state))
     (set (:ready state))))

(defn- giving-to-self? [[k vs]]
  (contains? (set vs) k))

(defn- giving-to-anyone-twice? [[_ vs]]
  (< (count (set vs))
     (count vs)))

(defn- everyone-gets-same-number-of-gifts? [gift-assignments]
  (->> gift-assignments
       (mapcat (comp vec val))
       frequencies
       vals
       (apply =)))

(defn- incompatible-assignment? [incompatible-pairs [k vs]]
  (not (empty? (intersection incompatible-pairs
                             (set (for [v vs] (hash-set k v)))))))

(defn- valid-gifts-assignment? [state a]
  (let [i (:incompatible-pairs state)]
    (and (every? (comp not giving-to-self?) a)
         (every? (comp not giving-to-anyone-twice?) a)
         (every? (comp not (partial incompatible-assignment? i)) a)
         (everyone-gets-same-number-of-gifts? a))))

(defn- assign-one-round-of-gifts [names]
  (zipmap names (->> names (map vector) shuffle)))

(defn- try-assigning-gifts [gifts names]
  (->> (repeatedly #(assign-one-round-of-gifts names))
       (take gifts)
       (reduce (partial merge-with into))))

(defn- assign-gifts [state]
  (let [{:keys [gifts
                names
                max-retries]} state]
    (loop [retries max-retries]
      (let [candidate (try-assigning-gifts gifts names)]
        (cond
          (valid-gifts-assignment? state candidate)
            (assoc state :gift-assignments candidate)
          (zero? retries)
            (throw (Exception. "Retries exhausted"))
          :else
            (recur (dec retries)))))))

(defn- set-ready [state-1 n]
  (let [state-2 (update state-1 :ready conj n)]
    (if (all-ready? state-2)
      (assign-gifts state-2)
      state-2)))

(defn- gift-assignment [state name]
  (when-let [ga (:gift-assignments state)]
    (get ga name)))

(defn- client-state [state name]
  {:name name
   :names (:names state)
   :ready (:ready state)
   :giftAssignments (gift-assignment state name)})

(defroutes app
  (GET "/" [] (slurp "../front/dist/index.html"))
  (GET "/main.js" [] (slurp "../front/dist/main.js"))
  (GET "/names" []
    (json/generate-string
      (client-state @state nil)))
  (POST "/ready" {body :body}
    (let [n (json/parse-string (slurp body))]
      (println n " is ready")
      (swap! state set-ready n)
      (json/generate-string
        (client-state @state n))))
  (POST "/poll" {body :body}
    (let [n (json/parse-string (slurp body))]
      (json/generate-string
        (client-state @state n)))))

(defn -main [& args]
  (reset! state (initial-state args))
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 443}))
