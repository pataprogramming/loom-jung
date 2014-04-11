(ns loom-jung.graph
  (:use     [operas.core])
  (:require [taoensso.timbre :as timbre
             :refer (trace debug info warn error fatal spy with-log-level)])
  (:import (java.io StringWriter)
           (java.awt Color Dimension Paint Polygon Shape Stroke BasicStroke Point)
           (javax.swing JFrame BoxLayout JPanel JButton JTextArea)
           (edu.uci.ics.jung.io GraphMLWriter)
           (edu.uci.ics.jung.graph UndirectedSparseGraph DirectedSparseGraph)
           (edu.uci.ics.jung.graph.util Pair EdgeType)
           (edu.uci.ics.jung.algorithms.layout SpringLayout StaticLayout)
           (edu.uci.ics.jung.visualization DefaultVisualizationModel
                                           VisualizationViewer)
           (edu.uci.ics.jung.visualization.renderers BasicEdgeRenderer Renderer$Vertex)
           (org.apache.commons.collections15 Predicate Transformer)))

(defn make-transformer [f]
     (proxy [Transformer] []
       (transform [o]
         (f o))))

(defn make-predicate [f]
     (proxy [Predicate] []
       (evaluate [o]
         (f o))))

(defn jung-directed-graph []
  (DirectedSparseGraph.))

(defn jung-undirected-graph []
  (UndirectedSparseGraph.))

(defrecord JungGraphHandler [jg properties])

(defn jung-graph-handler
  ([]  (JungGraphHandler. (jung-undirected-graph) (atom {})))
  ([g] (JungGraphHandler. g (ref {}))))

(defn jung-add-vertex! [jgh v]
  (if (.addVertex (:jg jgh) v)
    v))

(defn jung-remove-vertex! [jgh v]
  (swap! (:properties jgh) #(dissoc % v))
  (if (.removeVertex (:jg jgh ) v)
    v))

(defn jung-add-edge! [jgh v w]
  (if (and (.containsVertex (:jg jgh) v)
           (.containsVertex (:jg jgh) w))
    (if (.containsEdge (:jg jgh) [w v])
      [w v]
      (do
        (if (.addEdge (:jg jgh) [v w] v w)
          [v w])))))

(defn jung-remove-edge! [jgh v w]
  (if (.containsEdge (:jg jgh) [v w])
    (if (.removeEdge (:jg jgh) [v w])
      [v w])
    (if (.containsEdge (:jg jgh) [w v])
      (if (.removeEdge (:jg jgh) [w v])
        [w v]))))

(defn jung-empty! [jgh]
  ;; FIXME: Race conditions! Need java lock for these functions
  (dosync
   (ref-set (:properties jgh) {}))
  (let [g  (:jg jgh)]
    (println "removing vertices " (.getVertices g))
    (doseq [e (vec (.getEdges g))] (.removeEdge g e))
    (doseq [v (vec (.getVertices g))] (.removeVertex g v))
    (println g)))

(defn jung-set-property! [jgh v key val]
  (if (.containsVertex (:jg jgh) v)
    (dosync
     (alter (:properties jgh) #(assoc-in % [v key] val)))))

;;; FIXME: DRY!
(defn jung-loc-step [old-val {:keys [val action func] :as val-action}]
  (case action
    :set
    val
    :assoc ; :val should be [key value] for map in mem loc
    (apply assoc old-val val)
    :dissoc
    (dissoc old-val val)
    :conj
    (conj old-val val)
    :disj
    (disj old-val val)
    :update
    (apply func old-val val)))

(defn jung-update-property! [jgh v key val-action]
  (if (.containsVertex (:jg jgh) v)
    (dosync
     (alter (:properties jgh) #(assoc-in % [v key]
                                         (jung-loc-step (get-in % [v key]) val-action))))))

(defn jung-clear-property! [jgh v key]
  (dosync
   (ref-set (:properties jgh) #(dissoc-in % [v key]))))

(defn jung-get-property [jgh v key]
  (get-in @(:properties jgh) [v key]))

(defn apply-event-to-graph [jgh [k a1 a2 a3]]
  #_(debug "applying: " (str k " " a1 " " a2 " " a3))
  (case k
    :add-vertex
    (do
      (jung-add-vertex! jgh a1)
      (if (and a2 a3)
        (jung-set-property! jgh a1 a2 a3 )))
    :remove-vertex
    (do
      (debug "GRAPH REMOVE applying: " (str k " " a1 " " a2 " " a3))
      (jung-remove-vertex! jgh a1))
    :add-edge
    (jung-add-edge! jgh a1 a2)
    :remove-edge
    (jung-remove-edge! jgh a1 a2)
    :set-property
    (do
      #_(debug (str "SET-PROPERTY " a1 " " a2 " " a3))
      (jung-set-property! jgh a1 a2 a3))
    :clear-property
    (jung-clear-property! jgh a1 a2)
    :cycle
    #_(trace "new cycle" a1)
    :clear
    (doseq [v (.getVertices (:jg jgh))]
      (jung-remove-vertex! jgh a1))
    #_(trace "couldn't match : " k)))

(defn update-layout [layout x y repulsion stretch]
  (doto layout
    ;(.setSize (Dimension. x y))
    (.setRepulsionRange (int repulsion))
    (.setStretch stretch)))

(defn make-layout [g x y repulsion stretch]
  (let [l (SpringLayout. g)]
    (update-layout l x y repulsion stretch)
    l))

(defn make-static-layout [g vertex-xy-fn w h]
  (let [l (StaticLayout. g (make-transformer vertex-xy-fn) (Dimension. w h))]
    l))

(defn make-visualizer [layout x y]
  (let [d  (Dimension. x y)
        vm (DefaultVisualizationModel. layout ;d
             )
        vv (doto (VisualizationViewer. vm d)
             (.setBackground Color/WHITE))]
    vv))

(defn set-edge-renderer [jvh er]
  ;; er should be an instance of EdgeRenderer
  (let [r (.getRenderer (:visualizer jvh))]
    (.setEdgeRenderer r er)))

(defn set-vertex-label-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setVertexLabelTransformer rc (make-transformer f))
    jvh))

(defn set-vertex-shape-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setVertexShapeTransformer rc (make-transformer f))
    jvh))

(defn set-vertex-stroke-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setVertexStrokeTransformer rc (make-transformer f))
    jvh))

(defn set-vertex-draw-paint-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setVertexDrawPaintTransformer rc (make-transformer f))
    jvh))

(defn set-vertex-fill-paint-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setVertexFillPaintTransformer rc (make-transformer f))
    jvh))

(defn get-vertex-font-function [jvh]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.getVertexFontTransformer rc)))

(defn set-vertex-font-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setVertexFontTransformer rc (make-transformer f))
    jvh))

(defn get-edge-label-renderer [jvh]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.getEdgeLabelRenderer rc)))

(defn set-edge-label-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeLabelTransformer rc (make-transformer f))
    jvh))

(defn set-edge-stroke-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeStrokeTransformer rc (make-transformer f))
    jvh))

(defn set-edge-draw-paint-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeDrawPaintTransformer rc (make-transformer f))
    jvh))

(defn set-arrow-draw-paint-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setArrowDrawPaintTransformer rc (make-transformer f))
    jvh))

(defn set-edge-draw-paint-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeDrawPaintTransformer rc (make-transformer f))
    jvh))

(defn get-edge-shape-function [jvh]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.getEdgeShapeTransformer rc)))

(defn set-edge-shape-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeShapeTransformer rc (make-transformer f))
    jvh))

(defn set-arrow-fill-paint-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setArrowFillPaintTransformer rc (make-transformer f))
    jvh))

(defn set-arrow-stroke-function [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeArrowStrokeTransformer rc (make-transformer f))
    jvh))

(defn set-edge-arrow-predicate [jvh f]
  (let [rc (.getRenderContext (:visualizer jvh))]
    (.setEdgeArrowPredicate rc (make-predicate f))
    jvh))

(defn set-vertex-renderer [jvh f]
  (let [vr (reify Renderer$Vertex
             (paintVertex [this render-context layout vertex]
               (let [graphics-context (.getGraphicsContext render-context)
                     component        (f vertex)
                     center           (.transform layout vertex)
                     size             (.getPreferredSize component)
                     w                (.width size)
                     h                (.height size)
                     x                (- (.getX center) (/ w 2))
                     y                (- (.getY center) (/ h 2))]
                 (.draw graphics-context component
                        (.getRendererPane render-context)
                        x y w h true))))
        vv (:visualizer jvh)]
    (-> vv (.getRenderer) (.setVertexRenderer vr))))


(defrecord JungVisualizerHandler [graph layout visualizer])

(defn jung-visualizer-handler [jgh]
  (let [g (:jg jgh)
        l (make-layout g 650 650 100 0.75)
        v (make-visualizer l 650 650)]
    (JungVisualizerHandler. g l v)))

(defn jung-static-visualizer-handler [jgh vertex-xy-fn]
  (let [g (:jg jgh)
        l (make-static-layout g vertex-xy-fn 650 650)
        v (make-visualizer l 650 650)]
    (JungVisualizerHandler. g l v)))
