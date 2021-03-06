(ns lancelot-cljs.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as str]
            [lancelot_cljs.utils :as utils]
            [lancelot_cljs.layout.klay :as klay]
            [lancelot_cljs.layout.dagre :as dagre]
            ))

(def <sub (comp deref re-frame.core/subscribe))
(def >evt re-frame.core/dispatch)

(defn hex-format
  "format the given number into a hex string.

   example::
       => (hex-format 10)
       '0xA'
  "
  [n]
  (str "0x" (str/upper-case (.toString n 16))))

(defn parse-hex [#^String s]
  (js/parseInt s))

;;;
;;; geometric/canvas drawing components
;;;


(defn canvas
  "
  component that renders the given children on a pan-able canvas.
  mouse click-drag pans the canvas, while mouse wheel in/out zooms it.

  example::
      => [canvas
          [:h1#title 'hello world!']]
  "
  ([meta children]
   ;; this works by constructing a viewport over a "canvas" div containing the children.
   ;; as the client moves the mouse to pan, we translate the canvas.
   ;;
   ;; we capture events at the viewport layer, and apply the translations to the canvas layer.
   ;;
   ;; the size of the canvas may exceed that of the viewport; its no problem, as we can use CSS to clip it.
   ;;
   (let [state (reagent/atom {:shift-left 0    ; the x translation of the canvas
                              :shift-top 0     ; the y translation of the canvas
                              :dragging false  ; is the user currently dragging?
                              :drag-x 0        ; the x delta since the user started dragging, 0 if not dragging
                              :drag-y 0        ; the y delta since the user started dragging, 0 if not dragging
                              :zoom 1.0})]     ; the zoom scale of the canvas
     (fn []
       [:div.canvas-viewport
        (merge
         meta
         {:on-wheel
          ;; handle zooming in/out.
          ;; just a simple update to the zoom translation.
          (fn [e]
            (.preventDefault e)
            (let [zoom-factor 1.1
                  delta (.-deltaY e)]
              (if (> 0 delta)
                (swap! state update :zoom #(* zoom-factor %))
                (swap! state update :zoom #(/ % zoom-factor)))))
          :on-mouse-down
          ;; handle the mouse starting a drag.
          ;; on drag, we capture a few things:
          ;;  - that dragging is in progress,
          ;;  - where the dragging began, and
          ;;  - the x-y delta from when when dragging began (always (0, 0) for :on-mouse-down)
          ;;
          ;; when we apply the translation to the canvas layer, the x-y coordinate gets calculated from:
          ;;
          ;;    (+ initial-location drag-delta)
          ;;
          ;; where drag-delta is:
          ;;
          ;;    (- current-drag-location drag-start)
          (fn [e]
            (.preventDefault e)
            (let [evt (or e (js/event))
                  client-x (.-clientX evt)
                  client-y (.-clientY evt)]
              (swap! state merge {:dragging true
                                  :down-x client-x
                                  :down-y client-y
                                  :drag-x 0
                                  :drag-y 0})))
          :on-mouse-move
          ;; handle mouse continuing a drag.
          ;; we've already recorded where the drag started, so just need to update the drag-delta.
          (fn [e]
            (.preventDefault e)
            (when (:dragging @state)
              (let [evt (or e (js/event))
                    client-x (.-clientX evt)
                    client-y (.-clientY evt)]
                (swap! state merge {:drag-x (- client-x (:down-x @state))
                                    :drag-y (- client-y (:down-y @state))}))))
          :on-mouse-up
          ;; handle the mouse ending a drag.
          ;; now that the drag is complete, we commit the delta to the canvas layer position.
          (fn [e]
            (.preventDefault e)
            (when (:dragging @state)
              (let [evt (or e (js/event))
                    client-x (.-clientX evt)
                    client-y (.-clientY evt)]
                (swap! state #(-> %
                                  (dissoc :down-x)
                                  (dissoc :down-y)
                                  (merge {:dragging false
                                          :drag-x 0
                                          :drag-y 0
                                          :shift-left (+ (:shift-left @state)
                                                         (- client-x (:down-x @state)))
                                          :shift-top (+ (:shift-top @state)
                                                        (- client-y (:down-y @state)))}))))))
          :on-mouse-leave
          ;; handle when the mouse exceeds the bounds of the viewport.
          ;; commit the current drag, and end it.
          ;; note this is a duplication of :on-mouse-up. TODO: refactor code.
          ;;
          ;; note: don't try to change this to use :on-mouse-out.
          ;; that event fires when the mouse enters another element, which happens often during a lagging drag.
          (fn [e]
            (when (:dragging @state)
              (.preventDefault e)
              (let [evt (or e (js/event))
                    client-x (.-clientX evt)
                    client-y (.-clientY evt)]
                (swap! state #(-> %
                                  (dissoc :down-x)
                                  (dissoc :down-y)
                                  (merge {:dragging false
                                          :drag-x 0
                                          :drag-y 0
                                          :shift-left (+ (:shift-left @state)
                                                         (- client-x (:down-x @state)))
                                          :shift-top (+ (:shift-top @state)
                                                        (- client-y (:down-y @state)))}))))))

          })
        [:div.canvas
         {:style {:position "relative"               ; position relative so that children can be absolute relative to this element.
                  :transform-origin "center center"
                  :transform
                  (let [{:keys [zoom drag-x drag-y shift-left shift-top]} @state]
                    (str
                     "translate(" (+ drag-x shift-left) "px, "
                     (+ drag-y shift-top)  "px) "
                     "scale(" zoom  ") "))}}
         children]])))
  ([children] (canvas {} children))
  ([] (canvas {} [:div.empty])))

(defn positioned
  "wrap the given children with a div at the given x-y coordinates.
   this is useful when you want to place an element on a canvas at a specific place.

   example::

       => [canvas
           [positioned {:x 1 :y 10}
            [:div#title 'hello world!']]
           [positioned {:x 1 :y 12}
            [:div#trailer 'goodbye world!']]]
  "
  [{:keys [x y]} children]
  [:div.laid-out
   {:style {:position "absolute"  ; absolute position is relative to origin of the canvas.
            :float "left"
            :display "inline"
            :top (str y "em")
            :left (str x "em")}}
   children])

(def sqrt (.-sqrt js/Math))
(def PI (.-PI js/Math))
(def atan2 (.-atan2 js/Math))

;; these line drawing algorithms ripped directly from:
;;  http://stackoverflow.com/questions/4270485/drawing-lines-on-html-page

(defn geoline
  "draw a 'line' from the given x-y coordinates with the given length and angle.
   the coordinates are given in ems.
   the angle is given in radians.

   you should probably CSS-style the line with:

       border-top: <line-width>px solid #<color>;
  "
  [x y length angle]
  [:div.line
   {:style {:width (str length "em")
            :transform (str "rotate(" angle "rad)")
            :top (str y "em")
            :left (str x "em")}}])

(defn line
  "draw a 'line' between the two given x-y coordinates."
  [x2 y2 x1 y1]
  (let [a (- x1 x2)
        b (- y1 y2)
        c (sqrt
           (+
            (* a a)
            (* b b)))
        sx (/ (+ x1 x2) 2)
        sy (/ (+ y1 y2) 2)
        x (- sx (/ c 2))
        y sy
        alpha (- PI (atan2 (- b) a))]
    [geoline x y c alpha]))

;;;
;;; application components
;;;


(defn sample-list
  [samples]
  [:ul.sample-list
   (for [{md5 :md5} samples]
     ^{:key md5}
     [:li.sample-entry
      [:a.sample-link
       {:href "#"
        :on-click #(dispatch [:select-sample md5])}
       md5]])])

(defn function-list
  [functions]
  [:ul.function-list
   (for [{va :va} functions]
     ^{:key va}
     [:li.function-entry
      [:a.function-link
       {:href "#"
        :on-click #(dispatch [:select-function va])}
       (hex-format va)]])])

(defn function-nav-bar
  []
  (let [value (reagent/atom "")
        submit (fn []
                 (let [va (parse-hex @value)]
                   (when (and (number? va) (not (js/isNaN va)))
                     (dispatch [:select-function va]))))]
    (fn []
      [:div.function-nav-bar
       [:input {:type "text"
                :value @value
                :on-change (fn [evt]
                             (let [v (-> evt .-target .-value)]
                               (reset! value v)))
                :on-key-press (fn [e]
                                (when (= 13 (.-charCode e))
                                  (submit)))}]
       [:input {:type "button"
                :value "go"
                :on-click submit}]])))

(defn format-insn
  [insn]
  (str (hex-format (:va insn)) " " (:mnem insn) " " (:opstr insn)))

(defn insn-list
  [insns]
  [:ul
   (for [insn (sort :va insns)]
     ^{:key (:va insn)}
     [:div (format-insn insn)])])

(defn basic-block
  [va]
  (let [block @(subscribe [:basic-block va])]
    [:div.basic-block
     [:div.bb-header "basic block " (hex-format (:va block))]
     [:div.bb-content
      [:table
       [:thead]
       [:tbody
        (for [insn (:insns block)]
          ^{:key (:va insn)}
          [:tr.insn
           [:td.va (hex-format (:va insn))]
           [:td.padding-1 {:style {:min-width "1em"}}]
           ;; TODO: re-enable bytes
           [:td.bytes #_(str/upper-case (:bytes insn))]
           [:td.padding-2 {:style {:min-width "1em"}}]
           [:td.mnem (:mnem insn)]
           [:td.padding-3 {:style {:min-width "1em"}}]
           [:td.operands (:opstr insn)]
           [:td.padding-4 {:style {:min-width "1em"}}]
           [:td.comments (when (and (:comments insn)
                                    (not= "" (:comments insn)))
                           (str ";  " (:comments insn)))]])]]]]))

(defn compute-bb-height
  "compute the height, in lines, of the given basic block.
   when dealing when with em-based coordinate system, this is the height of the basic block component.
  "
  [bb]
  (let [insn-count (count (:insns bb))
        ;; assume header size is 1em,
        ;; which is defined in the css style.
        header-size 1]
    (+ header-size insn-count)))

(defn compute-bb-width
  "compute the width, in characters, of the given basic block
   when dealing when with em-based coordinate system, this is the width of the basic block component.
  "
  [bb]
  ;; the following constants are defined in the basic-block component.
  (let [padding-1-size 1
        padding-2-size 1
        padding-3-size 1
        padding-4-size 1
        bytes-size 12
        mnem-size 6
        operands-size (apply max (map #(count (:opstr %)) (:insns bb)))
        comments-size (apply max (map #(count (:comments %)) (:insns bb)))]
    (+ padding-1-size
       padding-2-size
       padding-3-size
       padding-4-size
       bytes-size
       mnem-size
       operands-size)))

(defn layout-cfg-klay
  "invoke the klay library to layout the given basic blocks and edges.
   the library will call the given success or error handlers, as appropriate.
  "
  [basic-blocks edges s e]
  (when (< 0 (count (remove nil? basic-blocks)))
    (let [bbs (map #(-> %
                        (assoc :width (compute-bb-width %))
                        (assoc :height (compute-bb-height %))
                        (dissoc :edges_to)
                        (dissoc :edges_from))
                   basic-blocks)
          g (klay/make)
          g (reduce klay/add-node g bbs)
          g (reduce klay/add-edge g edges)]
      (klay/layout g
                   (fn [r]
                     (s {:nodes (klay/get-nodes r)
                         :edges (klay/get-edges r)}))
                   (fn [err]
                     (e {:msg "klay: error"
                         :error err}))))))

(defn layout-cfg
  "invoke the layout library to layout the given blocks and edges.
   the library will call the given success or error handlers, as appropriate.

   Args:
    basic-blocks: sequence of maps with keys:
      - :va
      - :insns
    edges: sequence of maps with keys:
      - :id
      - :src
      - :dst
      - :type
    s: function accepting arguments:
      - nodes: sequence of maps with keys:
        - :id: the va of the basic block
        - :x
        - :y
      - edges: sequence of maps with keys:
        - :id
        - :type
        - :points: sequence of maps with keys:
          - :x
          - :y
    e: function accepting arguments:
      - error: map of error details
  "
  [basic-blocks edges s e]
  (layout-cfg-klay basic-blocks edges s e))
;;(layout-cfg-dagre basic-blocks s e))

(defn edge-line
  "draw a control-flow graph multi-segment line"
  [edge]
  [:div
   {:class (condp = (:type edge)
             :fall-through "edge-false"
             :cjmp "edge-true"
             :jmp "edge-jmp"
             "edge-unk")}
   (doall
    (for [pair (partition 2 1 (:points edge))]
      (let [{x1 :x y1 :y} (first pair)
            {x2 :x y2 :y} (second pair)]
        ^{:key (str x1 "-" y1 "-" x2 "-" y2)}
        [line x1 y1 x2 y2])))])

;; TODO: dup with events.cljs
(defn compute-edge-id
  [edge]
  (str (:src edge) "->" (:dst edge) "|" (:type edge)))

(defn add-edge-id
  [edge]
  (assoc edge :id (compute-edge-id edge)))

(defn function-graph
  "display a control-flow graph of the given basic blocks and edges.
  "
  [basic-blocks edges]
  ;; the implementation here is a little tricky because the layout engine is async.
  ;; so, the `layout` atom contains the positions for nodes/edges.
  ;; we'll populate this as soon as its available.
  ;; until then, the blocks have a random placement.
  (let [;; the edges are passed directly to `edge-line`.
        ;; the nodes are x-y coordinates that must be correlated with existing basic blocks.
        ;; the :id of the node matches the :va of the basic block.
        layout (reagent/atom {:nodes {}
                              :edges []})]
    (layout-cfg
     basic-blocks edges
     (fn [{layout-nodes :nodes layout-edges :edges}]
       (swap! layout merge {:nodes (utils/index-by :id layout-nodes)
                            :edges (map add-edge-id layout-edges)}))
     (fn [err] (prn "layout error: " err)))
    (fn [_ _]
      ;; note that we ignore the arguments to this re-render.
      ;; instead we use the results of the layout from local state.
      [canvas
       (concat (doall
                (for [va (map :va basic-blocks)]
                  ^{:key va}
                  [positioned
                   (get-in @layout [:nodes va])
                   [basic-block va]]))
               (doall
                (for [edge (:edges @layout)]
                  ^{:key (:id edge)}
                  [edge-line edge])))])))

(defn nav-bar
  []
  [:ul.nav
   [:li.home
    {:on-click #(dispatch [:unselect-sample])}
    "root"]
   (when (<sub [:sample-selected?])
     [:li.sep "/"])
   (when (<sub [:sample-selected?])
     [:li.sample
      {:on-click #(dispatch [:unselect-function])}
      (<sub [:sample])])
   (when (<sub [:function-selected?])
     [:li.sep "/"])
   (when (<sub [:function-selected?])
     [:li.function (<sub [:function])])])

(defn dis-app
  []
  [:div#dis-app
   [nav-bar]
   (when (not (<sub [:sample-selected?]))
     (if (not @(subscribe [:samples-loaded?]))
       [:div#loading-samples "loading samples..."]
       (sample-list @(subscribe [:samples]))))
   (when (and (<sub [:sample-selected?])
              (not (<sub [:function-selected?])))
     (if (not (<sub [:functions-loaded?]))
       [:div#loading-functions "loading functions..."]
       [:div
        [function-list (<sub [:functions])]
        [function-nav-bar]]))
   (when (and (<sub [:function-selected?])
              (<sub [:function-loaded?]))
     [:section#basic-blocks
      [function-graph (<sub [:blocks]) (<sub [:edges])]])])
