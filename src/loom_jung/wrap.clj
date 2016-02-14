(ns loom-jung.wrap
  (:require   [loom.graph :as l]
              [clojure.set :as set])
  (:import    (edu.uci.ics.jung.graph AbstractGraph)
              (edu.uci.ics.jung.graph.util EdgeType Pair)))

(defn distinct-edges [edges]
  ;; derived from clojure.core/distinct
  (let [step (fn step [es seen]
               (lazy-seq
                ((fn [[e :as es] seen]
                   (when-let [s (seq es)]
                     (let [[v w] e]
                       (if (or (contains? seen [v w])
                               (contains? seen [w v]))
                         (recur (rest s) seen)
                         (cons e (step (rest s) (conj seen e)))))))
                 es seen)))]
    (step edges #{})))

(defn wrap [loom-graph-atom]
  "Allows a Loom graph to be treated as a JUNG graph (primarily for
   visualization purposes). To facilitate dynamic visualizations,
   wrap Expects a Loom graph inside in an IDeref. A raw Loom graph will
   be accepted; otherwise it will try to build a new, undirected
   Loom graph from the argument. If you want to update the graph,
   you will need to supply your own atom or other dereffable."
  (if (instance? AbstractGraph loom-graph-atom)
    loom-graph-atom
    (let [lg (cond
               (instance? clojure.lang.IDeref loom-graph-atom)
               loom-graph-atom
               (l/graph? loom-graph-atom)
               (atom loom-graph-atom)
               :default
               (atom (l/graph loom-graph-atom)))]
      (proxy [AbstractGraph] []

        (addVertex [v]
          false)

        (addEdge [e endpoints edge-type]
          false)

        (containsEdge [e]
          (if (and (first (seq e)) (second (seq e)))
            (l/has-edge? @lg (first (seq e)) (second (seq e)))))

        (containsVertex [v]
          (l/has-node? @lg v))

        (getDefaultEdgeType []
          (if (l/directed? @lg)
            EdgeType/DIRECTED
            EdgeType/UNDIRECTED))

        (getEdgeCount
          ([]          (count (.getEdges this)))
          ([edge-type] (if (= edge-type (.getDefaultEdgeType this))
                         (count (.getEdges this))
                         0)))

        (getEdges
          ([]          (if (l/directed? @lg)
                           (l/edges @lg)
                           (distinct-edges (l/edges @lg))))
          ([edgeType]  (if (= edgeType (.getDefaultEdgeType this))
                         (if (l/directed? @lg)
                           (l/edges @lg)
                           (distinct-edges (l/edges @lg)))
                         #{})))

        (getEdgeType [e]
          (.getDefaultEdgeType this))

        (getIncidentEdges [v] ;; FIXME: Check if JUNG expects incoming edges as well
          (let [succ       (l/successors @lg v)
                succ-edges (for [w succ] [v w])]
            (if (l/directed? @lg)
              (let [pred       (l/predecessors @lg v)
                    pred-edges (for [u pred] [u v])]
                (concat pred-edges succ-edges))
              succ-edges)))

        (getNeighbors [v]
          (if (l/directed? @lg)
            (set/union (l/predecessors @lg v) (l/successors @lg v))
            (l/successors @lg v)))

        (getVertexCount []
          (count (l/nodes @lg)))

        (getVertices []
          (l/nodes @lg))

        (removeEdge [e]
          false)

        (removeVertex [e]
          false)

        (getEndpoints [e]
          (if (and (first (seq e))
                   (second (seq e))
                   (l/has-edge? @lg (first (seq e)) (second (seq e))))
            (Pair. (first e) (second e))))

        (getInEdges [v]
          (if (l/directed? @lg)
            (l/predecessors @lg v)
            (l/successors @lg v)))

        (getOutEdges [v]
          (l/successors @lg v))

        (getPredecessors [v]
          (if (l/directed? @lg)
            (l/predecessors @lg v)
            (l/successors @lg v)))

        (getDest [e]
          (if (and (l/directed? @lg)
                   (first (seq e))
                   (second (seq e))
                   (l/has-edge? @lg (first (seq e)) (second (seq e))))
            (second (seq e))))

        (getSource [e]
          (if (and (l/directed? @lg)
                   (first (seq e))
                   (second (seq e))
                   (l/has-edge? @lg (first (seq e)) (second (seq e))))
            (first (seq e))))

        (getSuccessors [v]
          (l/successors @lg v))

        (isDest [v e]
          (= v (.getDest this e)))

        (isSource [v e]
          (= v (.getSource this e)))))))

;; add changelistener for updates to atom
;; troubleshoot position problems with randomly generated graph
