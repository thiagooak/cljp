(ns build
  "Build tasks for cljp itself, via clj -T:build."
  (:require [clojure.tools.build.api :as b]))

(def uber-file "target/cljp.jar")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber
  "Package an uberjar at target/cljp.jar,
   ready for `native-image -jar target/cljp.jar`."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[cljp.main]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'cljp.main}))
