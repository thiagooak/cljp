(ns cljp.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [tools.project.new :as new]))

(def ^:private usage
  "cljp - a native-image CLI for Clojure projects

Usage:
  cljp new <name> [--dir <path>]
  cljp version
  cljp help

Commands:
  new      Scaffold a new project
  version  Print version (also --version)
  help     Show this message
")

(def version
  (if-let [r (io/resource "cljp/version.txt")]
    (str/trim (slurp r))
    "dev"))

(def ^:private new-cli-options
  [["-d" "--dir PATH" "Target directory (defaults to <name>)"]])

(defn- cmd-new [args]
  (let [{:keys [options arguments errors]} (cli/parse-opts args new-cli-options)]
    (when (seq errors)
      (throw (ex-info (str/join "\n" errors) {})))
    (when-not (= 1 (count arguments))
      (throw (ex-info "Usage: cljp new <name> [--dir <path>]" {})))
    (new/new-project {:name (first arguments) :dir (:dir options)})
    0))

(defn- cmd-help [_args]
  (print usage)
  (flush)
  0)

(defn- cmd-version [_args]
  (println (str "cljp " version))
  (flush)
  0)

(defn dispatch
  "Run a parsed command line, returning a process exit code."
  [[command & args]]
  (try
    (case command
      "new" (cmd-new args)
      ("version" "--version") (cmd-version args)
      ("help" "--help" "-h" nil) (cmd-help args)
      (do
        (binding [*out* *err*]
          (println (str "Unknown command: " command))
          (print usage))
        1))
    (catch clojure.lang.ExceptionInfo e
      (binding [*out* *err*]
        (println (str "Error: " (ex-message e))))
      1)))

(defn -main [& args]
  (let [code (dispatch args)]
    (System/exit code)))
