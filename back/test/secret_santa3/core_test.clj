(ns secret-santa3.core-test
  (:require
    [clj-time.core :as t]
    [clojure.string :as string]
    [clojure.test :refer [is deftest]]
    [secret-santa3.core :as s])
  (:import
    (java.io StringWriter)))

(deftest checking-incompatible-pairs
  (is (#'s/check-incompatible-pairs
       {:incompatible-pairs #{#{"allen" "ben"}}
        :names #{"allen" "ben"}}))
  (is (not (#'s/check-incompatible-pairs
            {:incompatible-pairs #{#{"allen" "ben"}}
             :names #{"allen" "patrick"}})))
  (is (not (#'s/check-incompatible-pairs
            {:incompatible-pairs #{#{"allen" "ben" "patrick"}}
             :names #{"allen" "ben" "patrick"}}))))

(deftest parsing-args
  (is (= {:names #{"allen" "ben" "patrick"}
          :incompatible-pairs #{#{"allen" "ben"}}
          :gifts 1
          :max-retries 1000}
         (#'s/parse-args ["-n" "allen"
                          "-g" "1"
                          "-n" "ben"
                          "-n" "patrick"
                          "-i" "ben,allen"]))))

(deftest assigning-gifts-if-everyone-is-connected
  (let [state (s/map->AppState
                {:names #{"allen" "ben" "patrick"}
                 :gifts 1
                 :incompatible-pairs #{}
                 :token->user-info {1 (s/map->UserInfo
                                        {:user-name "patrick"})}})]
    (is (= state (#'s/assign-gifts-if-everyone-is-connected state))))
  (let [state (s/map->AppState
                {:names #{"allen" "ben" "patrick"}
                 :gifts 1
                 :max-retries Integer/MAX_VALUE
                 :incompatible-pairs #{}
                 :token->user-info {1 (s/map->UserInfo
                                        {:user-name "allen"})
                                    2 (s/map->UserInfo
                                        {:user-name "ben"})
                                    3 (s/map->UserInfo
                                        {:user-name "patrick"})}})
        gift-assignments (:gift-assignments
                           (#'s/assign-gifts-if-everyone-is-connected state))]
    (is (= 3 (count (set (keys gift-assignments)))))
    (is (every? #(contains? #{"allen" "ben" "patrick"} %)
                (keys gift-assignments)))
    (is (every? #(and (vector? %)
                      (= 1 (count %))
                      (contains? #{"allen" "ben" "patrick"} (first %)))
                (vals gift-assignments)))))

(deftest issuing-tokens
  (with-redefs [t/now (constantly (t/date-time 2023 12 2))]
    (with-open [w (StringWriter.)]
      (binding [*out* w]
        (let [state (atom
                      (s/map->AppState
                        {:names #{"allen" "ben" "patrick"}
                         :gifts (int 1)
                         :incompatible-pairs #{}
                         :token->user-info {1 (s/map->UserInfo
                                                {:user-name "patrick"
                                                 :last-seen (t/date-time 2023 12 1)})}}))
              token (#'s/issue-token! state)]
          (is (integer? token))
          (is (string/starts-with? (.toString w) "Issuing token"))
          (is (= 2 (-> @state
                       (get :token->user-info)
                       (keys)
                       (count))))
          (is (= (s/map->UserInfo
                   {:last-seen (t/date-time 2023 12 2)})
                 (get-in @state [:token->user-info token]))))))))

(deftest identifying-users
  (with-redefs [t/now (constantly (t/date-time 2023 12 3))]
    (let [state (s/map->AppState
                  {:names #{"allen" "ben" "patrick"}
                   :gifts (int 1)
                   :incompatible-pairs #{}
                   :token->user-info {1 (s/map->UserInfo
                                          {:user-name "patrick"
                                           :last-seen (t/date-time 2023 12 1)})
                                      2 (s/map->UserInfo
                                          {:last-seen (t/date-time 2023 12 2)})}})]
      (with-open [w (StringWriter.)]
        (binding [*out* w]
          (let [new-state (#'s/identify state 1 "allen")]
            (is (string/includes? (.toString w) "is now claiming to be"))
            (is (= state new-state)))))
      (with-open [w (StringWriter.)]
        (binding [*out* w]
          (let [new-state (#'s/identify state 2 "allen")]
            (is (string/includes? (.toString w) "identifies as"))
            (is (= (s/map->UserInfo
                     {:user-name "allen"
                      :last-seen (t/date-time 2023 12 3)})
                   (get-in new-state [:token->user-info 2]))))))
      (with-open [w (StringWriter.)]
        (binding [*out* w]
          (let [new-state (#'s/identify state 3 "allen")]
            (is (string/starts-with? (.toString w) "User with invalid token"))
            (is (= state new-state))))))))

(deftest polling
  (with-redefs [t/now (constantly (t/date-time 2023 12 2))]
    (let [state (s/map->AppState
                  {:names #{"allen" "ben" "patrick"}
                   :gifts (int 1)
                   :incompatible-pairs #{}
                   :token->user-info {1 (s/map->UserInfo
                                          {:user-name "patrick"
                                           :last-seen (t/date-time 2023 12 1)})}})]
      (with-open [w (StringWriter.)]
        (binding [*out* w]
          (let [new-state (#'s/poll state 1)]
            (is (empty? (.toString w)))
            (is (= (s/map->UserInfo
                     {:user-name "patrick"
                      :last-seen (t/date-time 2023 12 2)})
                   (get-in new-state [:token->user-info 1]))))))
      (with-open [w (StringWriter.)]
        (binding [*out* w]
          (let [new-state (#'s/poll state 2)]
            (is (string/includes? (.toString w) "unrecognized token"))
            (is state new-state)))))))

(deftest disconnecting-unresponsive-users
  (with-redefs [t/now (constantly (t/date-time 2023 12 1 0 0 5))]
    (let [state (s/map->AppState
                  {:max-latency-millis 2500
                   :token->user-info
                     {1 (s/map->UserInfo
                          {:user-name "allen"
                           :last-seen (t/date-time 2023 12 1 0 0 2)})
                      2 (s/map->UserInfo
                          {:user-name "ben"
                           :last-seen (t/date-time 2023 12 1 0 0 3)})}})]
      (is (= {2 (s/map->UserInfo
                  {:user-name "ben"
                   :last-seen (t/date-time 2023 12 1 0 0 5)})}
             (-> (#'s/disconnect-unresponsive-users state)
                 (get :token->user-info)))))))
