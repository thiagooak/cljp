(ns tools.project.new-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [tools.project.new :as new]))

(defn- delete-recursively [f]
  (when (.isDirectory f)
    (run! delete-recursively (.listFiles f)))
  (io/delete-file f true))

(defmacro with-temp-dir [[sym] & body]
  `(let [~sym (str (System/getProperty "java.io.tmpdir") "/cljp-test-" (random-uuid))]
     (try
       ~@body
       (finally (delete-recursively (io/file ~sym))))))

(deftest new-project-scaffolds-expected-files
  (with-temp-dir [tmp]
    (let [target (str tmp "/my-app")
          result (new/new-project {:name "my-app" :dir target})]
      (is (= target (:dir result)))
      (is (.exists (io/file target "deps.edn")))
      (is (.exists (io/file target "build.clj")))
      (is (.exists (io/file target "README.md")))
      (is (.exists (io/file target ".gitignore")))
      (is (.exists (io/file target "src" "my_app.clj")))
      (is (.exists (io/file target "test" "my_app_test.clj"))))))

(deftest new-project-refuses-existing-dir
  (with-temp-dir [tmp]
    (.mkdirs (io/file tmp "exists"))
    (is (thrown? clojure.lang.ExceptionInfo
                 (new/new-project {:name "exists" :dir (str tmp "/exists")})))))

(deftest new-project-requires-name
  (is (thrown? clojure.lang.ExceptionInfo (new/new-project {}))))

(deftest new-project-rejects-path-traversal
  (testing "in :name"
    (is (thrown? clojure.lang.ExceptionInfo (new/new-project {:name "../evil"}))))
  (testing "in :dir"
    (is (thrown? clojure.lang.ExceptionInfo
                 (new/new-project {:name "my-app" :dir "../evil"})))))
