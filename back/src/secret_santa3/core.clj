(ns secret-santa3.core
  (:require
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clojure.core.async :as a]
    [clojure.java.io :as io]
    [clojure.set :refer [subset?]]
    [clojure.string :as string]
    [compojure.core :refer :all]
    [org.httpkit.server :refer [run-server]]
    [ring.middleware.params :as rmp]
    [ring.util.response :as response]
    [secret-santa3.assign-gifts :as g])
  (:import
    (org.joda.time DateTime))
  (:gen-class))

(defrecord UserInfo
  [^String user-name ;; Can be null
   ^DateTime last-seen])

(defrecord AppState
  [names ;; List of strings. The people participating in the gift exchange.
   gifts ;; How many gifts each person will be assigned to give.
   incompatible-pairs ;; List of list of strings. Pairs of names that will not
   ;; be assigned to give gifts to each other.
   max-retries ;; Maximum attempts to calculate gift assignments.
   max-latency-millis ;; Maximum number of milliseconds between polls for
   ;; a user to remain connected.
   token->user-info ;; Users that are currently connected to the app.
   gift-assignments ;; Map of strings to list of strings. An assignment of
   ;; which users each user will give gifts to.
   ])

(def state (atom nil))

(defn- check-incompatible-pairs [state]
  (and (every? #(= 2 (count %)) (:incompatible-pairs state))
       (every? #(subset? % (:names state)) (:incompatible-pairs state))))

(defn- parse-args [args]
  {:pre [(even? (count args))]
   :post [(check-incompatible-pairs %)]}
  (reduce (fn [acc [k-arg v-arg]]
            (case k-arg
              "-n" (update acc :names conj v-arg)
              "-g" (assoc acc :gifts (Integer/parseInt v-arg))
              "-i" (update acc :incompatible-pairs conj (set (string/split v-arg #",")))
              "-r" (assoc acc :max-retries (Integer/parseInt v-arg))
              "-l" (assoc acc :max-latency-millis (Integer/parseInt v-arg))))
          {:names #{}
           :incompatible-pairs #{}
           :max-retries 1000
           :max-latency-millis 2500}
          (partition 2 args)))

(defn- initial-state [args]
  (-> (parse-args args)
      (map->AppState)
      (assoc :token->user-info {}
             :gift-assignments nil)))

(defn- assign-gifts-if-everyone-is-connected [state]
  (if (= (set (:names state))
         (set (->> (:token->user-info state)
                   (vals)
                   (map :user-name)
                   (set))))
    (g/assign-gifts state)
    state))

(defn- issue-token! [state]
  (let [token (rand-int Integer/MAX_VALUE)
        user-info (map->UserInfo
                    {:last-seen (t/now)})]
    (println (format "Issuing token %d to new user." token))
    (swap! state assoc-in [:token->user-info token] user-info)
    {:token token}))

(defn- identify [state token user-name]
  {:pre [(instance? AppState state)
         (integer? token)
         (string? user-name)
         (not (empty? user-name))]}
  (let [user-info (get (:token->user-info state) token)]
    (cond
      (nil? user-info)
        (do (println (format "User with invalid token %d claimed to be %s."
                             token
                             user-name))
            state)
      (some? (:user-name user-info))
        (do (println (format "%s is now claiming to be %s."
                             (:user-name user-info)
                             user-name))
            state)
      :else
        (do (println (format "User with token %d identifies as %s."
                             token
                             user-name))
            (-> state
                (update-in [:token->user-info token]
                  assoc
                  :user-name user-name
                  :last-seen (t/now))
                assign-gifts-if-everyone-is-connected)))))

(defn- poll [state token]
  (let [user-info (get (:token->user-info state) token)]
    (if (nil? user-info)
      (do (println (format "Got poll with unrecognized token %d." token))
          state)
      (update-in state
        [:token->user-info token]
        assoc
        :last-seen (t/now)))))

(defn- client-view [state token]
  (let [{:keys [gift-assignments
                token->user-info]} state
        user-name (get-in token->user-info [token :user-name])]
    (when (and gift-assignments
               (nil? user-name))
      (println
        (format (str "Cannot send gift assignments because there is no username "
                     "associated with token %d.")
                token)))
    {:everyone (:names state)
     :connected (->> (:token->user-info state)
                     (vals)
                     (map :user-name)
                     (remove nil?)
                     (sort))
     :giftAssignments (get gift-assignments user-name)}))

(defroutes app
  (GET "/" []
    (io/resource "index.html"))
  (GET "/gift.svg" []
    (do (println "Someone wants a gift")
        (io/resource "gift.png")))
  (GET "/main.js" []
    (io/resource "main.js"))
  (POST "/token" []
    (json/generate-string
      (issue-token! state)))
  (POST "/identify" {body :body}
    (let [data (json/parse-string (slurp body))
          token (get data "token")
          username (get data "username")
          _ (println (format "/identify with data = %s" data))
          new-state (swap! state identify token username)
          ;;token (when token (Integer/parseInt token))
          ]
      (json/generate-string
        (client-view new-state token))))
  (POST "/poll" {body :body}
    (let [{:keys [token]} (json/parse-string (slurp body))
          token (when token
                  (Integer/parseInt token))
          new-state (swap! state poll token)]
      (json/generate-string
        (client-view new-state token)))))

(defn- disconnect-unresponsive-users [state]
  (let [{:keys [max-latency-millis]} state
        now (t/now)]
    (update state
      :token->user-info
      (fn [token->user-info]
        (reduce-kv (fn [m token user-info]
                     (if (< (t/in-millis
                              (t/interval (:last-seen user-info)
                                          now))
                            max-latency-millis)
                       (assoc m token (assoc user-info :last-seen now))
                       m))
                   {}
                   token->user-info)))))

(defn -main [& args]
  (reset! state (initial-state args))
  (println "Ready!")
  (a/go-loop []
    (swap! state disconnect-unresponsive-users)
    (Thread/sleep 100)
    (recur))
  (run-server (rmp/wrap-params app)
              {:port 443}))
