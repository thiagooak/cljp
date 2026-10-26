(ns build
  "Build tasks for cljp itself, via clj -T:build."
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]))

(def uber-file "target/cljp.jar")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn- version
  "The git tag at HEAD without its leading `v` (v0.1.0 -> 0.1.0), or dev"
  []
  (let [tag (some-> (b/git-process {:git-args ["tag" "--points-at" "HEAD" "--list" "v*"]})
                    str/split-lines
                    first)]
    (if (str/blank? tag)
      "dev"
      (str/replace tag #"^v" ""))))

(defn uber
  "Package an uberjar at target/cljp.jar,
   ready for `native-image -jar target/cljp.jar`."
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/write-file {:path (str class-dir "/cljp/version.txt")
                 :string (version)})
  (b/compile-clj {:basis @basis
                  :ns-compile '[cljp.main]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'cljp.main}))
