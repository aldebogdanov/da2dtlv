(defproject algoflora/da2dtlv "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.datomic/peer "1.0.7075"]
                 [datalevin "0.9.5"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/tools.logging "1.3.0"]
                 [org.slf4j/slf4j-simple "1.7.3"]
                 [com.taoensso/timbre "6.5.0"]]

  :plugins [[io.taylorwood/lein-native-image "0.3.1"]]

  :native-image {:name "da2dtlv"
                 :graal-bin "./graalvm/macos-aarch64/graalvm-jdk-21.0.3+7.1/Contents/Home/bin/native-image"
                 ;; :opts [;"--static"
                 ;;        "--initialize-at-build-time"
                 ;;        "--initialize-at-run-time=da2dtlv.core"
                 ;;        "--initialize-at-build-time=clojure.core.server"
                 ;;        ;"--initialize-at-run-time=io.netty"
                 ;;        "--initialize-at-build-time=clojure.core"
                 ;;        "--initialize-at-run-time=clojure.lang.Intrinsics"
                 ;;        "--initialize-at-run-time=clojure.lang.PersistentTreeMap"
                 ;;        "--initialize-at-run-time=clojure.lang.LispReader"
                 ;;        "--initialize-at-run-time=clojure.lang.LispReader$ConditionalReader"
                 ;;        "--initialize-at-run-time=clojure.lang.Compiler"
                 ;;        "--initialize-at-run-time=clojure.lang.RT"
                 ;;        "--initialize-at-run-time=clojure.lang.Numbers"
                 ;;        "--initialize-at-run-time=clojure.lang.Numbers$BigDecimalOps"
                 ;;        "--trace-class-initialization=org.slf4j.MarkerFactory,clojure.lang.Compiler,org.apache.log4j.Category,org.apache.log4j.Logger,org.slf4j.LoggerFactory"]
                 }
  
  :jvm-opts ["--add-opens=java.base/java.nio=ALL-UNNAMED"
             "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"]
  
  :main ^:skip-aot da2dtlv.core
  :target-path "target/%s"
  :uberjar-name "da2dtlv.jar"
  :profiles {:dev     {:jvm-opts ["-Dexec-command=-main"]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "--add-opens=java.base/java.nio=ALL-UNNAMED"
                                  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                                  "-Dda2dtlv.exec-command='java -jar da2dtlv.jar'"]}
             :linux-amd64   {:native-image {:opts ["--target=amd64-linux" "--graalvm-home=./graalvm/graalvm-jdk-21.0.3+7.1-linux-x64"]}
                             :jvm-opts ["-Dda2dtlv.exec-command=da2dtlv"]}
             :linux-aarch64 {:native-image {:opts ["--target=aarch64-linux" "--graalvm-home=./graalvm/graalvm-jdk-21.0.3+7.1-linux-aarch64"]}
                             :jvm-opts ["-Dda2dtlv.exec-command=da2dtlv"]}
             :macos-amd64   {:native-image {:opts ["--target=darwin-amd64" "--graalvm-home=./graalvm/graalvm-jdk-21.0.3+7.1-macos-x64"]}
                             :jvm-opts ["-Dda2dtlv.exec-command=da2dtlv"]}
             :macos-aarch64 {:native-image {:opts ["--target=darwin-aarch64" #_"--graalvm-home=./graalvm/macos-aarch64/graalvm-jdk-21.0.3+7.1"]}
                             ;:aot :all
                             :jvm-opts ["-Dda2dtlv.exec-command=da2dtlv"]}
             :windows-amd64 {:native-image {:opts ["--target=amd64-windows" "--graalvm-home=./graalvm/graalvm-jdk-21.0.3+7.1-windows-x64"]}
                             :jvm-opts ["-Dda2dtlv.exec-command=da2dtlv"]}})
