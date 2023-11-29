(ns secret-santa3.assign-gifts
  (:require
    [clojure.set :refer [subset? intersection]]))

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

(defn assign-gifts [state]
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
