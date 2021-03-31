(ns shadow-git-inject.core-test
    (:require
      [clojure.test :refer [deftest is]]
      [shadow-git-inject.core :refer [hook]]))

(deftest hook-test
   (let [build-state {:target           :browser
                      :compiler-options {:closure-defines {'app/version   :shadow-git-inject/version
                                                           'app/timestamp :shadow-git-inject/build-iso-date-time
                                                           'app/username  :shadow-git-inject/username}}}
         build-state' (hook build-state)]
        (is (string? (get-in build-state' [:compiler-options :closure-defines 'app/version])))
        (is (string? (get-in build-state' [:compiler-options :closure-defines 'app/timestamp])))
        (is (string? (get-in build-state' [:compiler-options :closure-defines 'app/username])))))
