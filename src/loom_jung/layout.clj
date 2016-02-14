(ns loom-jung.layout
  (:require [seesaw.config :refer [Configurable]]
            [seesaw.options :refer [get-option-value apply-options option-map bean-option default-option option-provider]]
            [seesaw.util :refer [cond-doto to-dimension]]
            [seesaw.widget-options :only [widget-option-provider]])
  (:import (edu.uci.ics.jung.algorithms.layout Layout CircleLayout FRLayout ISOMLayout SpringLayout StaticLayout)))

(defn- to-list [coll]
  (if (list? coll)
    coll
    (java.util.ArrayList. (seq coll))))

(defn- ^java.awt.geom.Point2D$Float to-point2d-f [[x y]]
  (java.awt.geom.Point2D$Float. (float x) (float y)))

(defn make-point-transformer [f]
  (if (instance? Transformer f)
    f
    (reify
      Transformer
      (transform [this o]
        (to-point2d-f (f o)))
      clojure.lang.IFn
      (invoke [this arg] (.transform this arg))
      (applyTo [this [arg]] (.transform this arg)))))

(extend-protocol Configurable
  Layout
  (config* [target name] (get-option-value target name))
  (config!* [target args] (apply-options target args)))

(def graph-layout-options
  (option-map
   (default-option :intializer #(.setInitializer %1 (make-point-transformer %2)))
   (default-option :size #(.setSize %1 (to-dimension %2)) #(.getSize %)
                   "Set the size of the visualization's space")))

(def circle-layout-options
  (merge
   graph-layout-options
   (option-map
    (bean-option :radius Double double nil
                 "Set the radius of the layout's circle")
    (default-option :vertex-comparator
                    #(.setVertexOrder %1 #^java.util.Comparator %2)
                    #(.getVertexOrder %)
                    "Set the comparator that will be used to order the vertices")
    (default-option :vertex-list
                    #(.setVertexOrder %1 #^java.util.List. (to-list %2))
                    #(.getVertexOrder %)
                    "Use an ordered list to place the vertices around the circle"))))

(option-provider CircleLayout circle-layout-options)

(defn circle-layout [g & {:keys [width height size :as opts]}]
  (cond-doto ^CircleLayout (apply-options (CircleLayout. g)
                              (dissoc opts :width :height))
    (and (not size)
         (or width height)) (.setSize (or width 650) (or height 650))))

(def spring-layout-options
  (merge
   graph-layout-options
   (option-map
    (bean-option :force-multiplier Double double nil
                 "Set the edge length force multiplier")
    (bean-option :repulsion-range Integer #(Integer. %) nil
                 "Set the node repulsion range")
    (bean-option :stretch Double double nil
                 "Set the stretch parameter"))))

(option-provider SpringLayout spring-layout-options)

(defn spring-layout [g & {:keys [width height size] :as opts}]
  (cond-doto ^SpringLayout (apply-options (SpringLayout. g)
                             (dissoc opts :width :height))
    (and (not size)
         (or width height)) (.setSize (or width 600) (or height 600))))

(defn lock!
  ([l]
   (.lock l true))
  ([l v]
   (.lock l v true)))

(defn unlock!
  ([l]
   (.lock l false))
  ([l v]
   (.lock l v false)))

(defn locked? [l v]
  (.isLocked l v))

(defn location! [l v [x y]]
  (.setLocation l v (to-point2d-f [x y])))

(defn location [l v]
  (let [pos (.transform l v)]
    [(.getX pos) (.getY pos)]))

;; (defn update-layout [layout x y repulsion stretch]
;;   (doto layout
;;     ;(.setSize (Dimension. x y))
;;     (.setRepulsionRange (int repulsion))
;;     (.setStretch stretch)))

;; (defn make-static-layout [g vertex-xy-fn w h]
;;   (let [l (StaticLayout. g (make-transformer vertex-xy-fn) (Dimension. w h))]
;;     l))
