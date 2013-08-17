(ns sormilla.math
  (:require [amalloy.ring-buffer :refer [ring-buffer]]))

(defn avg [coll]
  (/ (reduce + coll) (double (count coll))))

(defn lin-scale [fmin fmax tmin tmax]
  (let [r (/ (- tmax tmin) (- fmax fmin))]
    (fn [v] (double (+ (* (- v fmin) r) tmin)))))

(defn bound [lo hi]
  (fn [v]
    (if (< lo v hi) v (if (< lo v) hi lo))))

(defn abs [v]
  (if (pos? v) v (- v)))

(defn clip-to-zero [v]
  (if (< (abs v) 0.1) 0.0 (- v (if (pos? v) 0.1 -0.1))))

(defn averager [c]
  (let [buffer (atom (ring-buffer c))]
    (fn [v]
      (/ (reduce + (swap! buffer conj v)) c))))
