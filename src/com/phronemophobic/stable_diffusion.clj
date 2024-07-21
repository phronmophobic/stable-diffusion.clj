(ns com.phronemophobic.stable-diffusion
  (:require [com.phronemophobic.stable-diffusion.impl.raw :as raw]
            [clojure.java.io :as io])
  (:import
   javax.imageio.ImageIO
   java.awt.image.BufferedImage)
  (:gen-class))

(defn ^:private ->bool [o]
  (if o
    1
    0))


;; If you wish to generate images multiple times, simply set "free_params_immediately" to false.
(defn new-sd-ctx [{:keys [model-path
                          vae-path
                          taesd-path
                          controlnet-path
                          lora-model-dir
                          embeddings-path
                          stacked-id-embeddings-path
                          vae-decode-only
                          vae-tiling
                          free-params-immediately
                          n-threads
                          wtype
                          rng-type
                          schedule
                          clip-on-cpu
                          control-net-cpu
                          vae-on-cpu]}]
  (raw/new_sd_ctx model-path
                  
                  (or vae-path "")
                  (or taesd-path "")
                  (or controlnet-path "")
                  (or lora-model-dir "")
                  (or embeddings-path "")
                  (or stacked-id-embeddings-path "")
                  (->bool (if (nil? vae-decode-only)
                            true
                            vae-decode-only))
                  (->bool vae-tiling)
                  (->bool free-params-immediately)
                  (or n-threads (raw/get_num_physical_cores))
                  (or wtype raw/SD_TYPE_COUNT)
                  (or rng-type raw/CUDA_RNG)
                  (or schedule raw/DEFAULT)
                  (->bool clip-on-cpu)
                  (->bool control-net-cpu)
                  (->bool vae-on-cpu)))

(defn txt2img [ctx {:keys [prompt
                           negative-prompt
                           clip-skip
                           cfg-scale
                           width
                           height
                           sample-method
                           sample-steps
                           seed
                           batch-count
                           ;; control-cond
                           control-strength
                           style-strength
                           normalize-input
                           input-id-images-path
                           ]}]
  (let [sb-image
        (raw/txt2img ctx
                     prompt
                     (or negative-prompt "")
                     (or clip-skip -1)
                     (or cfg-scale 7.0)
                     (or width 512)
                     (or height 512)
                     (or sample-method raw/EULER_A)
                     (or sample-steps 20)
                     (or seed 42)
                     (or batch-count 1)
                     
                     nil ;; control-cond
                     (or control-strength 0.9)
                     (or style-strength 20.0)
                     (->bool normalize-input)
                     (or input-id-images-path ""))]
    (raw/sb-image->buffered-image sb-image)))

(defn save-png [bufimg f]
  (with-open [os (clojure.java.io/output-stream f)]
    (ImageIO/write ^BufferedImage bufimg "png" os)))

(defn ^:private ->img [ctx prompt path]
  (let [img (txt2img ctx {:prompt prompt
                          :sample-steps 4
                          :cfg-scale 1.0
                          ;; :clip-skip 2
                          :width 1024
                          :height 768
                          :sample-method raw/LCM
                          :seed -1
                          :batch-count 1
                          })]
    (save-png img path)
    img))


(comment
  (raw/sd_set_log_callback
   (fn [level msg user]
     (print (.getString (.getPointer msg) 0))
     (flush)
     nil)
   nil)
  ;; typedef void (*sd_progress_cb_t)(int step, int steps, float time, void* data);
  (raw/sd_set_progress_callback (fn [step steps t _]
                                  (println "progress:" step "/" steps)) nil)

  (def lightning {:model-path (.getCanonicalPath
                               (io/file
                                ".."
                                "stable-diffusion.cpp"
                                "models"
                                "sdxl_lightning_2step.q4_1.gguf"))
                  ;;:schedule raw/KARRAS
                  })

  (def ctx
    (new-sd-ctx lightning))

  (time
   (->img
    ctx
    "cats wearing top hats"
    "cats.png"))


  ,)


