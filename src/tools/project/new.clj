(ns tools.project.new
  "Scaffolds a new Clojure project on disk.

  This is the first corner of what is meant to grow into `tools.project`:
  a library namespace (callable via `-T`, like `tools.build`) that a thin
  native-binary CLI (`cljp`) wraps."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- munge-path
  "Turn a project/namespace name into a filesystem-safe path segment."
  [s]
  (str/replace s #"[-.]" {"-" "_" "." "/"}))

(defn- render [template subs]
  (reduce (fn [s [k v]] (str/replace s (str "{{" (name k) "}}") (str v)))
          template subs))

(def ^:private deps-edn-template
  "{:paths [\"src\"]
 :deps {org.clojure/clojure {:mvn/version \"1.12.0\"}}
 :aliases
 {;; clj -T:build <task>
  :build
  {:deps {io.github.clojure/tools.build {:mvn/version \"0.10.5\"}}
   :ns-default build}

  ;; clj -X:test
  :test
  {:extra-paths [\"test\"]
   :extra-deps {io.github.cognitect-labs/test-runner
                {:git/tag \"v0.5.1\" :git/sha \"dfb30dd\"}}
   :exec-fn cognitect.test-runner.api/test}}}
")

(def ^:private build-clj-template
  "(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib '{{lib}})
(def version \"0.1.0\")
(def class-dir \"target/classes\")
(def basis (delay (b/create-basis {:project \"deps.edn\"})))
(def jar-file (format \"target/%s-%s.jar\" (name lib) version))

(defn clean [_]
  (b/delete {:path \"target\"}))

(defn jar [_]
  (clean nil)
  (b/copy-dir {:src-dirs [\"src\" \"resources\"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :src-dirs [\"src\"]
                  :class-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))
")

(def ^:private core-ns-template
  "(ns {{main-ns}}
  (:gen-class))

(defn -main [& _args]
  (println \"Hello, {{name}}!\"))
")

(def ^:private core-test-template
  "(ns {{main-ns}}-test
  (:require [clojure.test :refer [deftest is]]
            [{{main-ns}} :as core]))

(deftest smoke-test
  (is (= \"Hello, {{name}}!\\n\" (with-out-str (core/-main)))))
")

(def ^:private gitignore-template
  "/target/
/.cpcache/
.nrepl-port
.lsp/
.clj-kondo/.cache/
")

(def ^:private readme-template
  "# {{name}}

A new Clojure project, scaffolded with `cljp new`.

## Usage

Run the project directly:

    clj -M -m {{main-ns}}

Run the tests:

    clj -X:test

Build an uberjar (see `build.clj`, powered by `tools.build`):

    clj -T:build jar
")

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(defn new-project
  "Scaffold a new project on disk.

  Options:
    :name  (required) project name, e.g. \"my-app\" or \"my.group/my-app\"
    :dir   (optional) target directory, defaults to the project name

  Intended to be invoked as a tool, e.g.:
    clj -Sdeps '{:deps {io.github.<user>/tools.project {:mvn/version \"RELEASE\"}}}' \\
        -T tools.project.new new-project :name my-app

  or, once the native CLI exists, simply: `cljp new my-app`."
  [{:keys [name dir] :as opts}]
  (when-not name
    (throw (ex-info "Missing required option :name" {:opts opts})))
  (when-not (re-matches #"[a-zA-Z0-9][a-zA-Z0-9_.-]*(/[a-zA-Z0-9][a-zA-Z0-9_.-]*)?" name)
    (throw (ex-info (str "Invalid :name (expected something like \"my-app\" or \"my.group/my-app\"): " name)
                     {:name name})))
  (when (and dir (re-find #"(^|/)\.\.(/|$)" dir))
    (throw (ex-info (str "Invalid :dir (must not contain \"..\" segments): " dir)
                     {:dir dir})))
  (let [target-dir (or dir name)
        main-ns (-> name (str/split #"/") last)
        lib-sym (if (str/includes? name "/") name (str name "/" name))
        subs {:name name :main-ns main-ns :lib lib-sym}]
    (when (.exists (io/file target-dir))
      (throw (ex-info (str "Target directory already exists: " target-dir)
                       {:dir target-dir})))
    (write-file! (str target-dir "/deps.edn") deps-edn-template)
    (write-file! (str target-dir "/build.clj") (render build-clj-template subs))
    (write-file! (str target-dir "/.gitignore") gitignore-template)
    (write-file! (str target-dir "/README.md") (render readme-template subs))
    (write-file! (str target-dir "/src/" (munge-path main-ns) ".clj")
                 (render core-ns-template subs))
    (write-file! (str target-dir "/test/" (munge-path main-ns) "_test.clj")
                 (render core-test-template subs))
    (println (str "Created " target-dir "/"))
    {:dir target-dir :main-ns main-ns}))
