(ns loom-jung.graph
  (:require [seesaw.config :refer [Configurable]]
            [seesaw.options :refer [get-option-value apply-options option-map default-option option-provider]]
            [seesaw.to-widget :refer [ToWidget]]
            [seesaw.widget-options :only [widget-option-provider]])
  ;; (:require [taoensso.timbre :as timbre
  ;;            :refer (trace debug info warn error fatal spy with-log-level)])
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

(defn dissoc-in [coll keys key] ; FIXME: switch to private
  (assoc-in coll keys (dissoc (get-in coll keys) key)))

(defn make-transformer [f]
  (if (instance? Transformer f)
    f
    (reify
      Transformer
      (transform [this o]
        (f o))
      clojure.lang.IFn
      (invoke [this arg] (.transform this arg))
      (applyTo [this [arg]] (.transform this arg)))))

(defn make-predicate [f]
  (if (instance? Predicate f)
    f
    (reify
      Predicate
      (evaluate [this o]
        (f (boolean o)))
      clojure.lang.IFn
      (invoke [this arg] (.evaluate this arg))
      (applyTo [this [arg]] (.evaluate this arg)))))

(defn jung-directed-graph []
  (DirectedSparseGraph.))

(defn jung-undirected-graph []
  (UndirectedSparseGraph.))

(defrecord JungGraphHandler [jg properties])

(defn jung-graph-handler
  ([]  (JungGraphHandler. (jung-undirected-graph) (atom {})))
  ([g] (JungGraphHandler. g (atom {}))))

(defn jung-add-vertex! [jgh v]
  (when (.addVertex (:jg jgh) v)
    v))

(defn jung-remove-vertex! [jgh v]
  (swap! (:properties jgh) #(dissoc % v))
  (when (.removeVertex (:jg jgh ) v)
    v))

(defn jung-add-edge! [jgh v w]
  (when (and (.containsVertex (:jg jgh) v)
           (.containsVertex (:jg jgh) w))
    (when (.containsEdge (:jg jgh) [w v])
      [w v]
      (do
        (when (.addEdge (:jg jgh) [v w] v w)
          [v w])))))

(defn jung-remove-edge! [jgh v w]
  (if (.containsEdge (:jg jgh) [v w])
    (when (.removeEdge (:jg jgh) [v w])
      [v w])
    (when (.containsEdge (:jg jgh) [w v])
      (when (.removeEdge (:jg jgh) [w v])
        [w v]))))

(defn jung-empty! [jgh]
  ;; FIXME: Race conditions! Need java lock for these functions
  (dosync
   (swap! (:properties jgh) {}))
  (let [g  (:jg jgh)]
    (println "removing vertices " (.getVertices g))
    (doseq [e (vec (.getEdges g))] (.removeEdge g e))
    (doseq [v (vec (.getVertices g))] (.removeVertex g v))
    (println g)))

(defn jung-set-property! [jgh v key val]
  (when (.containsVertex (:jg jgh) v)
    (dosync
     (alter (:properties jgh) #(assoc-in % [v key] val)))))

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
  (when (.containsVertex (:jg jgh) v)
    (dosync
     (alter (:properties jgh) #(assoc-in % [v key]
                                         (jung-loc-step (get-in % [v key]) val-action))))))

(defn jung-clear-property! [jgh v key]
  (dosync
   (swap! (:properties jgh) #(dissoc-in % [v key]))))

(defn jung-get-property [jgh v key]
  (get-in @(:properties jgh) [v key]))

(defn apply-event-to-graph [jgh [k a1 a2 a3]]
  #_(debug "applying: " (str k " " a1 " " a2 " " a3))
  (case k
    :add-vertex
    (do
      (jung-add-vertex! jgh a1)
      (when (and a2 a3)
        (jung-set-property! jgh a1 a2 a3 )))
    :remove-vertex
    (do
      #_(debug "GRAPH REMOVE applying: " (str k " " a1 " " a2 " " a3))
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

(defn jung-visualizer [jgh]
  (let [g  (:jg jgh)
        l  (make-layout g 650 650 100 0.75)
        vv (make-visualizer l 650 650)]
    vv))

(defn jung-static-visualizer [jgh vertex-xy-fn]
  (let [g  (:jg jgh)
        l  (make-static-layout g vertex-xy-fn 650 650)
        vv (make-visualizer l 650 650)]
    vv))

(extend-protocol Configurable
  VisualizationViewer
  (config* [target name] (get-option-value target name))
  (config!* [target args] (apply-options target args)))

(defmacro r-set [method]
  `(fn [vv# o#] (-> vv# .getRenderer (. ~method o#)))  )

(defmacro r-get [method]
  `(fn [vv# ] (-> vv# .getRenderer (. ~method)))  )

(defmacro rc-set-transform [method]
  `(fn [vv# f#] (-> vv# .getRenderContext (. ~method (make-transformer f#)))))

(defmacro rc-set-predicate [method]
  `(fn [vv# f#] (-> vv# .getRenderContext (. ~method (make-predicate f#)))))

(defmacro rc-get [method]
  `(fn [vv#] (-> vv# .getRenderContext (. ~method))))

(defmacro rc-get-predicate [method]
  `(fn [vv#] (-> vv# .getRenderContext (. ~method) boolean)))

(defn vertex-renderer! [vv f]
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
                        x y w h true))))]
    (-> vv (.getRenderer) (.setVertexRenderer vr))))

(def jung-visualizer-options
  (option-map
   (default-option :vertex-label
                   (rc-set-transform setVertexLabelTransformer)
                   (rc-get getVertexLabelTransformer)
                   "fn mapping from vertex to string to be used as a text label")
   (default-option :vertex-draw
                   (rc-set-transform setVertexDrawPaintTransformer)
                   (rc-get getVertexDrawPaintTransformer)
                   "fn mapping from vertex to Paint for drawing (e.g., outline color)")
   (default-option :vertex-fill
                   (rc-set-transform setVertexFillPaintTransformer)
                   (rc-get getVertexFillPaintTransformer)
                   "fn mapping from vertex to Paint for filling (inside the outline)")
   (default-option :vertex-shape
                   (rc-set-transform setVertexShapeTransformer)
                   (rc-get getVertexShapeTransformer)
                   "fn mapping from vertex to Shape")
   (default-option :vertex-stroke
                   (rc-set-transform setVertexStrokeTransformer)
                   (rc-get getVertexStrokeTransformer)
                   "fn mapping from vertex to Stroke (used for outlines")
   (default-option :vertex-font
                   (rc-set-transform setVertexFontTransformer)
                   (rc-get getVertexFontTransormer)
                   "fn mapping from vertex to Font (used for vertex labels)")
   (default-option :edge-label
                   (rc-set-transform setEdgeLabelTransformer)
                   (rc-get setEdgeLabelTransformer)
                   "fn mapping from edge to String (used as a text label)")
   (default-option :edge-draw
                   (rc-set-transform setEdgeDrawPaintTransformer)
                   (rc-get getEdgeDrawPaintTransformer)
                   "fn mapping from edge to Paint (e.g., edge colors)")
   (default-option :edge-stroke
                   (rc-set-transform setEdgeStrokeTransformer)
                   (rc-get getEdgeStrokeTransformer)
                   "fn mapping from edge to Stroke (edge weight, dotting, etc.)")
   (default-option :edge-shape
                   (rc-set-transform setEdgeShapeTransformer)
                   (rc-get getEdgeShapeTransformer)
                   "fn mapping from edge to Shape (for exotic edges)")
   (default-option :arrow?
                   (rc-set-predicate setEdgeArrowPredicate)
                   (rc-get-predicate getEdgeArrowPredicate)
                   "predicate on [context, edge] to determine arrowhead presence")
   (default-option :arrow-draw
                   (rc-set-transform setArrowDrawPaintTransformer)
                   (rc-get getArrowDrawPaintTransformer)
                   "fn mapping from [context, edge] to Paint")
   (default-option :arrow-fill
                   (rc-set-transform setArrowFillPaintTransformer)
                   (rc-get getFillDrawPaintTransformer)
                   "fn mapping from [context, edge] to Paint (inside arrowheads)")
   (default-option :arrow-stroke
                   (rc-set-transform setEdgeArrowStrokeTransformer)
                   (rc-get getFillDrawPaintTransformer)
                   "fn mapping from [context, edge] to Paint (inside arrowheads)")
   (default-option :vertex-renderer
                   vertex-renderer!
                   (r-get getEdgeRenderer)
                   "set a JUNG VertexRenderer")
   (default-option :edge-renderer
                   (r-set setEdgeRenderer)
                   (r-get getEdgeRenderer)
                   "set a JUNG EdgeRenderer")))

(option-provider VisualizationViewer jung-visualizer-options)
