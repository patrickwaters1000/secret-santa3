(ns secret-santa3.core-test
  (:require [secret-santa3.core :as s]
            [clojure.test :refer [is deftest]]))

(deftest parsing-args
  (is (= {:names #{"allen" "ben" "patrick"}
          :incompatible-pairs #{#{"allen" "ben"}}
          :gifts 1}
         (#'s/parse-args ["-n" "allen" "-g" "1" "-n" "ben" "-n" "patrick" "-i" "ben,allen"]))))

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

(deftest building-initial-state
  (is (= {:names #{"allen" "patrick" "ben"},
          :incompatible-pairs #{#{"allen" "ben"}},
          :gifts 1,
          :ready #{},
          :gift-assignments nil}
         (#'s/initial-state
           ["-n" "allen" "-g" "1" "-n" "ben" "-n" "patrick" "-i" "ben,allen"]))))

(deftest checking-giving-to-self
  (is (not (#'s/giving-to-self? ["allen" ["ben"]])))
  (is (#'s/giving-to-self? ["allen" ["allen" "ben"]]))
  (is (#'s/giving-to-self? ["allen" ["allen"]])))

(deftest checking-giving-to-anyone-twice
  (is (not (#'s/giving-to-anyone-twice? ["allen" ["ben"]])))
  (is (#'s/giving-to-anyone-twice? ["allen" ["ben" "ben"]]))
  (is (#'s/giving-to-anyone-twice? ["allen" ["patrick" "ben" "ben"]])))

(deftest checking-for-incompatible-assignments
  (is (not (#'s/incompatible-assignment? #{}
                                         ["allen" ["essie"]])))
  (is (#'s/incompatible-assignment? #{#{"allen" "essie"}}
                                    ["allen" ["essie"]])))

(deftest checking-everyone-gets-same-number-of-gifts
  (is (#'s/everyone-gets-same-number-of-gifts? {"allen" ["ben"]
                                            "ben" ["essie"]
                                            "essie" ["patrick"]
                                            "patrick" ["allen"]}))
  (is (not (#'s/everyone-gets-same-number-of-gifts? {"allen" ["ben"]
                                                     "ben" ["allen"]
                                                     "essie" ["patrick"]
                                                     "patrick" ["allen"]}))))

(deftest checking-for-valid-gift-assignments
  (let [state {:incompatible-pairs #{#{"allen" "essie"}}}]
    (is (#'s/valid-gifts-assignment? state
                                     {"allen" ["ben"]
                                      "ben" ["essie"]
                                      "essie" ["patrick"]
                                      "patrick" ["allen"]}))
    (is (not (#'s/valid-gifts-assignment? state
                                          {"allen" ["allen"]
                                           "ben" ["essie"]
                                           "essie" ["patrick"]
                                           "patrick" ["ben"]})))
    (is (not (#'s/valid-gifts-assignment? state
                                          {"allen" ["ben" "patrick"]
                                           "ben" ["essie" "essie"]
                                           "essie" ["patrick" "ben"]
                                           "patrick" ["allen" "allen"]}
                                          )))
    (is (not (#'s/valid-gifts-assignment? state
                                          {"allen" ["ben"]
                                           "ben" ["essie"]
                                           "essie" ["allen"]
                                           "patrick" ["tom"]
                                           "tom" ["patrick"]})))
    (is (not (#'s/valid-gifts-assignment? state
                                          {"allen" ["ben"]
                                           "ben" ["essie"]
                                           "essie" ["ben"]
                                           "patrick" ["allen"]})))))

(deftest trying-to-assign-gifts
  (let [result (#'s/try-assigning-gifts 2 #{"allen" "ben" "patrick" "essie" "tom"})]
    (is (map? result))
    (is (every? string? (keys result)))
    (is (every? #(and (vector? %)
                      (= 2 (count %))
                      (every? string? %)) (vals result)))))

(deftest assigning-gifts
  (let [new-state (#'s/assign-gifts
                    {:incompatible-pairs #{#{"allen" "essie"}}
                     :gifts 2
                     :names #{"allen" "ben" "patrick" "essie" "tom"}
                     :max-retries 10000})]
    (is (#'s/valid-gifts-assignment?
          new-state
          (:gift-assignments new-state)))))
