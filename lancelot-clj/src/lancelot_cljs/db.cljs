(ns lancelot-cljs.db)

;; client db schema:
;;
;;     {
;;       :samples [{:md5 "<md5>" :sha1 "<sha1>"} ...]
;;       :sample "<md5>"
;;       :functions [{:va int} ...]
;;       :function int
;;       :blocks
;;       :edges
;;       :insns
;;     }
