(ns lancelot-cljs.events
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          dispatch
                          dispatch-sync
                          ]]
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [venia.core :as v]
   [lancelot-cljs.utils :as utils]
   ))

(reg-event-db
 :initialize-db
 (fn [_ _]
   {}))

(reg-event-fx
 :load-samples
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri "/graphql"
                 :params {:query (v/graphql-query {:venia/queries [[:samples [:md5 :sha1]]]})}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:loaded-samples]
                 :on-failure [:errored-samples]}}))

(reg-event-db
 :loaded-samples
 (fn [db [_ response]]
   (prn "loaded samples: " response)
   (merge db {:samples (get-in response [:data :samples])})))

(reg-event-db
 :errored-samples
 (fn [db error]
   (prn "errored-samples: " error)
   db))

(reg-event-db
 :select-sample
 (fn [db [_ sample-md5]]
   (prn "select-sample: " db sample-md5)
   (dispatch [:load-functions])
   (assoc db :sample sample-md5)))

(reg-event-db
 :unselect-sample
 (fn [db _]
   (prn "unselect-sample")
   (-> db
       (dissoc :function)
       (dissoc :sample))))

(reg-event-fx
 :load-functions
 (fn [{db :db} _]
   (prn "load-functions")
   {:http-xhrio {:method :get
                 :uri "/graphql"
                 :params {:query (v/graphql-query {:venia/queries [[:sample_by_md5 {:md5 (:sample db)}
                                                                    [[:entrypoint [:va]]
                                                                     [:exports [:va]]
                                                                    ]]]})}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:loaded-functions]
                 :on-failure [:errored-functions]}}))

(reg-event-db
 :loaded-functions
 (fn [db [_ response]]
   (let [exports (get-in response [:data :sample_by_md5 :exports])
         entrypoint (get-in response [:data :sample_by_md5 :entrypoint])
         functions (conj exports entrypoint)]
     (prn "loaded functions: " functions)
     (assoc db :functions functions))))

(reg-event-db
 :errored-functions
 (fn [db error]
   (prn "errored-functions: " error)
   db))

(reg-event-db
 :select-function
 (fn [db [_ function-va]]
   (prn "select-function: " function-va)
   (dispatch [:load-function function-va])
   (-> db
       (assoc :function function-va)
       (dissoc :blocks)
       (dissoc :edges)
       (dissoc :insns))))

(reg-event-db
 :unselect-function
 (fn [db _]
   (prn "unselect-function")
   (dissoc db :function)))

(reg-event-fx
 :load-function
 (fn [{db :db} [_ function-va]]
   (prn "load-function" function-va)
   {:http-xhrio {:method :get
                 :uri "/graphql"
                 :params {:query (v/graphql-query
                                  {:venia/queries [[:function_by_md5_va {:md5 (:sample db)
                                                                         :va (:function db)}
                                                    [[:blocks
                                                      [:va
                                                       [:edges_to
                                                        [[:src [:va]]
                                                         [:dst [:va]]
                                                         :type]]
                                                       [:edges_from
                                                        [[:src [:va]]
                                                         [:dst [:va]]
                                                         :type]]
                                                       [:insns
                                                        [:va
                                                         :mnem
                                                         :opstr
                                                         :size]]]
                                                    ]]]]})}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:loaded-function]
                 :on-failure [:errored-function]}}))


(defn compute-edge-id
  [edge]
  (str (:src edge) "->" (:dst edge) "|" (:type edge)))

(defn add-edge-id
  [edge]
  (assoc edge :id (compute-edge-id edge)))

(defn api->edge
  [e]
  (-> e
      (update :type #(keyword (subs % 1)))  ; :type looks like ":cjmp", so trim the leading colon, and make it a keyword.
      (assoc :src (get-in e [:src :va]))    ; de-nest the src/dst addresses.
      (assoc :dst (get-in e [:dst :va]))
      (assoc :id (compute-edge-id e))))

(reg-event-db
 :loaded-function
 (fn [db [_ response]]
   (let [blocks (get-in response [:data :function_by_md5_va :blocks])
         edges (concat (flatten (map :edges_to blocks))
                       (flatten (map :edges_from blocks)))
         edges' (into #{} (map api->edge edges))
         insns (flatten (map :insns blocks))]
     (prn "loaded function" insns)
     (merge db {:blocks (utils/index-by :va blocks)
                :edges edges'
                :insns insns}))))

(reg-event-db
 :errored-function
 (fn [db error]
   (prn "errored-function: " error)
   db))
