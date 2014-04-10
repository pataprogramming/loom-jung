(ns operas.loomwrap
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
  (let [lg loom-graph-atom]
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

      (getIncidentEdges [v]
        (let [succ       (l/neighbors @lg v)
              succ-edges (for [w succ] [v w])]
          (if (l/directed? @lg)
            (let [pred       (l/incoming @lg v)
                  pred-edges (for [u pred] [u v])]
              (concat pred-edges succ-edges))
            succ-edges)))

      (getNeighbors [v]
        (if (l/directed? @lg)
          (set/union (l/incoming @lg v) (l/neighbors @lg v))
          (l/neighbors @lg v)))

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
          (l/incoming @lg v)
          (l/neighbors @lg v)))

      (getOutEdges [v]
        (l/neighbors @lg v))

      (getPredecessors [v]
        (if (l/directed? @lg)
          (l/incoming @lg v)
          (l/neighbors @lg v)))

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
        (l/neighbors @lg v))

      (isDest [v e]
        (= v (.getDest this e)))

      (isSource [v e]
        (= v (.getSource this e))))))
