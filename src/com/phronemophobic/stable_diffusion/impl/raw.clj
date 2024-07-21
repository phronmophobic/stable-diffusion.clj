(ns com.phronemophobic.stable-diffusion.impl.raw
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.phronemophobic.clong.gen.jna :as gen])
  (:import
   java.io.PushbackReader
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.PointerByReference
   com.sun.jna.ptr.LongByReference
   com.sun.jna.Structure
   java.awt.image.BufferedImage)
  (:gen-class))

(defmacro with-tile [[tile-bind img] & body]
  `(let [img# ~img
         ~tile-bind (.getWritableTile img# 0 0)]
     (try
       ~@body
       (finally
         (.releaseWritableTile img# 0 0)))))

(defn sb-image->buffered-image
  [^Structure sb-image]
  (assert (= 3 (.readField sb-image "channel")))
  (let [

        width (.readField sb-image  "width")
        height (.readField sb-image "height")

        img (BufferedImage. width height BufferedImage/TYPE_3BYTE_BGR)
        ;; 3 bytes per pixel
        linesize (* 3 width)
        buf-ptr (-> sb-image
                    (.readField "data")
                    (.getPointer))

        get-buf (fn [y] (.getByteArray buf-ptr (* linesize y) linesize))]

    (with-tile [wraster img]
      (doseq [y (range height)]

        (.setDataElements wraster 0 y width 1
                          (get-buf y))))
    img))

(defn ^:private write-edn [w obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true

            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false

            *out* w]
    (pr obj)))

(defn dump-api []
  (let [outf (io/file
              "resources"
              "com"
              "phronemophobic"
              "stable_diffusion"
              "api.edn")]
    (.mkdirs (.getParentFile outf))
    (with-open [w (io/writer outf)]
      (write-edn w
                 ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api)
                  (.getCanonicalPath (io/file "../"
                                              "stable-diffusion.cpp"
                                              "stable-diffusion.h")))))))

(defn load-api []
  (with-open [rdr (io/reader
                   (io/resource
                    "com/phronemophobic/stable_diffusion/api.edn"))
              rdr (java.io.PushbackReader. rdr)]
    (edn/read rdr)))


(def lib
  (delay
    (com.sun.jna.NativeLibrary/getInstance "stable-diffusion")))

(def api
  (load-api))

(gen/def-api-lazy lib api)


