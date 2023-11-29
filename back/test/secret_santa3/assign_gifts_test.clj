(ns secret-santa3.assign-gifts-test
  (:require
    [clojure.test :refer [is deftest]]
    [secret-santa3.assign-gifts :as g]))

(deftest checking-giving-to-self
  (is (not (#'g/giving-to-self? ["allen" ["ben"]])))
  (is (#'g/giving-to-self? ["allen" ["allen" "ben"]]))
  (is (#'g/giving-to-self? ["allen" ["allen"]])))

(deftest checking-giving-to-anyone-twice
  (is (not (#'g/giving-to-anyone-twice? ["allen" ["ben"]])))
  (is (#'g/giving-to-anyone-twice? ["allen" ["ben" "ben"]]))
  (is (#'g/giving-to-anyone-twice? ["allen" ["patrick" "ben" "ben"]])))

(deftest checking-for-incompatible-assignments
  (is (not (#'g/incompatible-assignment? #{}
            ["allen" ["essie"]])))
  (is (#'g/incompatible-assignment? #{#{"allen" "essie"}}
       ["allen" ["essie"]])))

(deftest checking-everyone-gets-same-number-of-gifts
  (is (#'g/everyone-gets-same-number-of-gifts? {"allen" ["ben"]
                                                "ben" ["essie"]
                                                "essie" ["patrick"]
                                                "patrick" ["allen"]}))
  (is (not (#'g/everyone-gets-same-number-of-gifts? {"allen" ["ben"]
                                                     "ben" ["allen"]
                                                     "essie" ["patrick"]
                                                     "patrick" ["allen"]}))))

(deftest checking-for-valid-gift-assignments
  (let [state {:incompatible-pairs #{#{"allen" "essie"}}}]
    (is (#'g/valid-gifts-assignment? state
         {"allen" ["ben"]
          "ben" ["essie"]
          "essie" ["patrick"]
          "patrick" ["allen"]}))
    (is (not (#'g/valid-gifts-assignment? state
              {"allen" ["allen"]
               "ben" ["essie"]
               "essie" ["patrick"]
               "patrick" ["ben"]})))
    (is (not (#'g/valid-gifts-assignment? state
              {"allen" ["ben" "patrick"]
               "ben" ["essie" "essie"]
               "essie" ["patrick" "ben"]
               "patrick" ["allen" "allen"]})))
    (is (not (#'g/valid-gifts-assignment? state
              {"allen" ["ben"]
               "ben" ["essie"]
               "essie" ["allen"]
               "patrick" ["tom"]
               "tom" ["patrick"]})))
    (is (not (#'g/valid-gifts-assignment? state
              {"allen" ["ben"]
               "ben" ["essie"]
               "essie" ["ben"]
               "patrick" ["allen"]})))))
