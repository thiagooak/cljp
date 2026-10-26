(ns tools.project.add
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z]))

(defn parse-spec
  "Parse \"group/artifact[@version]\" (or bare \"name\", meaning name/name)
  into {:lib symbol :version string-or-nil}."
  [spec]
  (let [[lib-str version & more] (str/split spec #"@" -1)]
    (when (or more (and version (str/blank? version)))
      (throw (ex-info (str "Invalid dependency (expected <lib>[@version]): " spec) {:spec spec})))
    (when-not (re-matches #"[A-Za-z0-9_.*+!?-]+(/[A-Za-z0-9_.*+!?-]+)?" lib-str)
      (throw (ex-info (str "Invalid library name: " lib-str) {:spec spec})))
    {:lib (symbol (if (str/includes? lib-str "/") lib-str (str lib-str "/" lib-str)))
     :version version}))

(defn- prerelease? [coord]
  (boolean (some->> (:mvn/version coord)
                    (re-find #"(?i)-(alpha|beta|rc|snapshot|m|cr|pre|preview|dev)[.-]?\d*$"))))

(defn pick-latest
  "Pick the newest coord from `coords` (as ordered by find-versions, oldest
  first), preferring Maven coords and stable releases."
  [coords]
  (let [mvn (filter :mvn/version coords)
        pool (if (seq mvn) mvn coords)]
    (last (or (seq (remove prerelease? pool)) pool))))

(defn- latest-coord [lib]
  (let [cmd ["clojure" "-X:deps" "find-versions" ":lib" (str lib) ":n" ":all"]
        _ (println (str "+ " (str/join " " cmd)))
        {:keys [exit out err]} (try (apply sh/sh cmd)
                                    (catch java.io.IOException _
                                      (throw (ex-info "The 'clojure' command was not found on PATH" {}))))]
    (when-not (zero? exit)
      (throw (ex-info (str "clojure find-versions failed:\n" (str/trim err)) {:lib lib})))
    (or (->> (str/split-lines out)
             (filter #(str/starts-with? % "{"))
             (map edn/read-string)
             pick-latest)
        (throw (ex-info (str "No versions found for " lib) {:lib lib})))))

(defn- coord->str [coord]
  (str "{" (str/join " " (map (fn [[k v]] (str k " " (pr-str v))) coord)) "}"))

(defn- row [zloc] (first (z/position zloc)))

(defn- append-entry
  "Append `k v` to the map at `m`; on a multi-line map the entry gets its own
  line, aligned with the first key."
  [m k v]
  (let [first-key (z/down m)
        multi? (and first-key (not= (row first-key) (row (z/rightmost first-key))))]
    (if multi?
      (-> m
          (z/append-child* (n/newlines 1))
          (z/append-child* (n/spaces (dec (second (z/position first-key)))))
          (z/append-child* k)
          (z/append-child* (n/spaces 1))
          (z/append-child* v))
      (-> m (z/append-child k) (z/append-child v)))))

(defn add-dep-string
  "Add (or update) `lib` with `coord` in the deps.edn text `s`.
  Returns {:content new-text :previous old-coord-or-nil}."
  [s lib coord]
  (let [root (z/of-string s {:track-position? true})
        _ (when-not (z/map? root)
            (throw (ex-info "deps.edn must contain a map" {})))
        deps (z/get root :deps)
        coord-node (z/node (z/of-string (coord->str coord)))]
    (cond
      (nil? deps)
      {:content (-> root (append-entry (n/keyword-node :deps) (n/coerce {lib coord})) z/root-string)}

      (not (z/map? deps))
      (throw (ex-info ":deps in deps.edn is not a map" {}))

      :else
      (if-let [existing (z/get deps lib)]
        {:content (-> existing (z/replace coord-node) z/root-string)
         :previous (z/sexpr existing)}
        {:content (-> deps (append-entry (n/coerce lib) coord-node) z/root-string)}))))

(defn add-dep
  "Add a dependency to deps.edn.

  Options:
    :lib      (required) qualified lib symbol
    :version  (optional) Maven version; the latest is looked up if omitted
    :file     (optional) path to deps.edn, defaults to \"deps.edn\""
  [{:keys [lib version file] :or {file "deps.edn"}}]
  (when-not (.exists (io/file file))
    (throw (ex-info (str "No " file " found in the current directory") {:file file})))
  (let [coord (if version {:mvn/version version} (latest-coord lib))
        {:keys [content previous]} (add-dep-string (slurp file) lib coord)]
    (if (= previous coord)
      (println (str lib " is already at " (coord->str coord) " in " file))
      (do
        (spit file content)
        (println (if previous
                   (str "Updated " lib " " (coord->str previous) " -> " (coord->str coord) " in " file)
                   (str "Added " lib " " (coord->str coord) " to " file)))))
    {:lib lib :coord coord :previous previous}))
