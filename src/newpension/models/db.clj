(ns newpension.models.db
  (:use korma.core
        [korma.db :only [defdb with-db]])
  (:import (java.sql Timestamp))
  (:require [newpension.models.schema :as schema]
            ))

(defdb dboracle schema/db-oracle)
(declare users olds functions audits rolefunc roleuser userlog division t_oldsocrel needs needsums)  ;;数据声明

;;数据库表实体及各实体关联
;;用户表
(defentity users
  (pk :userid)
  (table :xt_user)
  (belongs-to roleuser {:fk :userid})
  (database dboracle))

;;老年人主表
(defentity olds
  (table :t_oldpeople)
  (database dboracle))

;;系统功能表
(defentity functions
  (pk :functionid)
  (table :xt_function)
  (has-one rolefunc {:fk :functionid})
  (database dboracle))

;;业务审核表
(defentity audits
  (table :opaudit)
  (belongs-to userlog {:fk :opseno})
  (database dboracle))

;;功能权限表
(defentity rolefunc
  (table :xt_rolefunc)
  (belongs-to roleuser {:fk :roleid})
  (database dboracle))

;;用户权限表
(defentity roleuser
  (pk :roleid)
  (table :xt_roleuser)
  (belongs-to users {:fk :userid})
  (database dboracle))

;;操作日志表
(defentity userlog
  (pk :opseno)
  (table :xt_userlog)
  (database dboracle))

;;输入框下拉选项列表
(defentity aa10
  (pk :prseno)
  (table :aa10)
  (database dboracle))

;;行政区划表
(defentity division
  (pk :dvcode)
  (table :division)
  (has-many division {:fk :dvhigh})
  (database dboracle))

;;家庭成员关系表
(defentity t_oldsocrel
  (pk :lrgx_id)
  (table :t_oldsocrel)
  (database dboracle))

;;人员评估表
(defentity needs
  (table :t_needassessment)
  (database dboracle))

;;人员评估汇总表
(defentity needsums
  (table :t_needassessmentsum)
  (database dboracle))

 ;;数据库操作函数
 ;;用户登录
(defn get-user
  ( [name pwd] (first
                 (select users
                   (where {:loginname name :passwd pwd}))))
  ( [name] (first
             (select users
               (where {:loginname name})))))

;;根据关键字获取该表自增主键
(defn get-max [keywords]
  (first
    (case keywords
      "olds" (select olds
               (aggregate (max :lr_id) :max))
      "userlog" (select userlog
                  (aggregate (max :opseno) :max))
      "audits" (select audits
                 (aggregate (max :auditid) :max))
      "famillyref" (select t_oldsocrel
                     (aggregate (max :lrgx_id) :max)))))

;;查询养老信息
(defn get-olds
  ( [] (select olds                  ;;查询所有养老信息
         (fields :lr_id :name :gender :birthd :identityid :address)))
  ( [keyword] (select olds           ;;根据关键字模糊查询
                (where {:name [like (str "%" (if (nil? keyword) "" keyword) "%")]}))))

;;根据主键查看养老信息
(defn get-old [id]
  (first
    (select olds
      (where {:lr_id  id}))))

;;新增养老信息
(defn create-old [old]
  (insert olds
    (values old)))

;;新增养老家庭成员信息
(defn insert-oldsocrel [fiels]
  (insert t_oldsocrel
    (values fiels)))

(defn sele_oldsocrel [gx_name]
  (select t_oldsocrel
    (where {:gx_name [= (str gx_name)]})))

;;修改养老信息
(defn update-old [old id]
  (update olds
    (set-fields old)
    (where {:lr_id id})))

;;修改养老家庭成员信息
(defn update-oldsorel [oldsorel lrgx_id]
  (update t_oldsocrel
    (set-fields oldsorel)
    (where {:lrgx_id lrgx_id})))

;;删除家庭成员关系表
(defn dele-oldsorel [lrgx_id]
  (delete t_oldsocrel
    (where {:lrgx_id lrgx_id})))

;;修改养老信息表状态
(defn update-oldstatus [status id]
  (update olds
    (set-fields {:status status})
    (where {:lr_id id})))

;;删除养老信息
(defn delete-old [id]
  (delete olds
    (where {:lr_id id})))

;;根据功能id查询操作日志
(defn get-userlogs [functionid]
  (select userlog
    (fields :opseno :digest :tprkey :username :bsnyue :bstime )
    (where {:functionid functionid})
    (order :opseno :desc)))      ;;降序排列

;;新增操作日志
(defn create-userlog [opseno digest tprkey functionid dvcode loginname username]
  (insert userlog
    (values {:opseno opseno :digest digest :tprkey tprkey :functionid functionid :dvcode dvcode :loginname loginname :username username})))

;;查询满足用户和功能权限的审核信息
(defn get-audits [functionid loginname dvcode]
  (select audits
    (fields :auditid :aulevel :auflag :audesc :auendflag)                      ;;页面显示内容
    (with userlog
      (fields :opseno :digest :tprkey :username :bsnyue :bstime)
      (where
        (and {:functionid functionid } (or {:dvcode [like (str  dvcode "%")]} (= dvcode "330100")))))
    (where  {:auendflag "0"                         ;;要满足的条件：审核未完成，审核等级达到权限要求
             :aulevel [in (map #(str %)
                            (map #(dec %)
                              (map #(Integer/parseInt %)
                                (map :location
                                  (select functions
                                    (fields :location)
                                    (where {:nodetype "2" })
                                    (with rolefunc
                                      (fields)
                                      (with roleuser
                                        (fields)
                                        (with users
                                          (fields)
                                          (where {:loginname loginname })))))))))]})
    (order :opseno :desc)))           ;;降序排列

;;查询满足用户和功能权限的回退信息
(defn get-backaudits [functionid loginname dvcode]
  (select audits
    (fields :auditid :aulevel :auflag :audesc :auendflag)
    (with userlog
      (fields :opseno :digest :tprkey :username :bsnyue :bstime)
      (where {:functionid functionid :dvcode  dvcode })  ;;直接回退
      )
    (where {:auflag "0" :aulevel [> 0]})
    ;    (where  {:auflag "0"                             ;;逐级回退
    ;             :aulevel [in (into (map :location
    ;                                  (select functions
    ;                                    (fields :location)
    ;                                    (where {:nodetype "2" })
    ;                                    (with rolefunc
    ;                                      (fields)
    ;                                      (with roleuser
    ;                                        (fields)
    ;                                        (with users
    ;                                          (fields)
    ;                                          (where {:loginname loginname }))))))
    ;                                (map #(str %) (map #(inc %) (map #(Integer/parseInt %) (map :location
    ;                                  (select functions
    ;                                    (fields :location)
    ;                                    (where {:nodetype "2" })
    ;                                    (with rolefunc
    ;                                      (fields)
    ;                                      (with roleuser
    ;                                        (fields)
    ;                                        (with users
    ;                                          (fields)
    ;                                          (where {:loginname loginname }))))))))))]})
    (order :opseno :desc)))            ;;降序排列

;;根据外键查询审核信息
(defn get-audit [id]
  (first
    (select audits
      (with userlog
        (where {:tprkey id})))))

;;新增审核信息
(defn create-audit [opseno auditid]
  (insert audits
    (values {:opseno opseno :auditid auditid :auflag "0" :aulevel "0" :auendflag "0"})))

;;修改审核信息
(defn update-audit [aulevel auflag aaa027 audesc auuser auopseno auendflag auditid opseno]
  (update audits
    (set-fields {:aulevel aulevel :auflag auflag  :audesc audesc
                 :audate (new Timestamp (System/currentTimeMillis)) :opseno opseno
                 :auuser auuser :auopseno auopseno :auendflag auendflag
                 })
    (where {:auditid  auditid})))

;;删除审核信息
(defn delete-audit [id]
  (delete audits
    (where {:auditid id})))

;;查询满足满足用户权限的功能
(defn get-funcs [username parent]
  (select functions
    (where (and {:parent parent
                 :functionid [in
                              (subselect rolefunc
                                (fields :functionid)
                                (with roleuser
                                  (fields)
                                  (with users
                                    (fields)
                                    (where {:username username}))))]}  {:functionid [not= "weouDjr2ji7k5w4EA0Ws"]}))
    (order :orderno)))

;;获取输入框下拉选项列表
(defn get-inputlist [aaa100]
  (select aa10
    (where {:aaa100 aaa100})))

;;获取行政区划的选项列表
(defn get-divisionlist [dvhigh]
  (select division
    (fields :dvcode :dvhigh :totalname :dvname)
    (where {:dvhigh dvhigh})))

;;查询家庭成员关系表
(defn get-oldsocrel [lr_id]
  (select t_oldsocrel
    (where {:lr_id lr_id})))

;;查询评估信息
(defn get-needs []
  (select needs
    (order :pg_id :desc)))

;;新增评估信息
(defn create-need [need]
  (insert needs
    (values need)))

;;查询评估汇总信息
(defn get-needsum [id]
  (first
    (select needsums
      (where {:pg_id id}))))

;;新增评估汇总信息
(defn create-needsum [needsum]
  (insert needsum
    (values needsum)))