(ns loom-jung.visualization
  (:require [seesaw.config :refer [Configurable]]
            [seesaw.options :refer [get-option-value apply-options option-map default-option option-provider]]
            [seesaw.to-widget :refer [ToWidget]]
            [seesaw.widget-options :only [widget-option-provider]])
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

;; (defn make-layout [g x y repulsion stretch]
;;   (let [l (SpringLayout. g)]
;;     (update-layout l x y repulsion stretch)
;;     l))

;; (defn make-static-layout [g vertex-xy-fn w h]
;;   (let [l (StaticLayout. g (make-transformer vertex-xy-fn) (Dimension. w h))]
;;     l))

;; (defn make-visualizer [layout x y]
;;   (let [d  (Dimension. x y)
;;         vm (DefaultVisualizationModel. layout
;;              )
;;         vv (doto (VisualizationViewer. vm d)
;;              (.setBackground Color/WHITE))]
;;     vv))

;; (defn jung-visualizer [jgh]
;;   (let [g  (:jg jgh)
;;         l  (make-layout g 650 650 100 0.75)
;;         vv (make-visualizer l 650 650)]
;;     vv))

(extend-protocol Configurable
  VisualizationViewer
  (config* [target name] (get-option-value target name))
  (config!* [target args] (apply-options target args)))

(defmacro r-set [method]
  `(fn [vv# o#] (-> vv# .getRenderer (. ~method o#)))  )

(defmacro r-get [method]
  `(fn [vv# ] (-> vv# .getRenderer (. ~method)))  )

(defmacro vm-set [method]
  `(fn [vv# o#] (-> vv# .getVisualizationModel (. ~method o#)))  )

(defmacro vm-get [method]
  `(fn [vv# ] (-> vv# .getVisualizationModel (. ~method)))  )

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
   (default-option :layout
                   (vm-set setGraphLayout)
                   (vm-get getGraphLayout)
                   )
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
