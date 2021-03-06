(ns facial-rec.face-feature
  (:require [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :refer [py. py.. py.-] :as py]
            [tech.v2.datatype :as dtype]))



(require-python 'mxnet
                '(mxnet ndarray module io model))
(require-python 'cv2)
(require-python '[numpy :as np])


(defn load-model
  [& {:keys [model-path checkpoint]
      :or {model-path "models/recognition/model"
           checkpoint 0}}]
  (let [[sym arg-params aux-params] (mxnet.model/load_checkpoint model-path checkpoint)
        all-layers (py. sym get_internals)
        target-layer (py/get-item all-layers "fc1_output")
        model (mxnet.module/Module :symbol target-layer
                                   :context (mxnet/cpu)
                                   :label_names nil)]
    (py. model bind :data_shapes [["data" [1 3 112 112]]])
    (py. model set_params arg-params aux-params)
    model))

(defonce model (load-model))



(defn face->feature
  [img-path]
  (py/with-gil-stack-rc-context
    (if-let [new-img (cv2/imread img-path)]
      (let [new-img (cv2/cvtColor new-img cv2/COLOR_BGR2RGB)
            new-img (np/transpose new-img [2 0 1])
            input-blob (np/expand_dims new-img :axis 0)
            data (mxnet.ndarray/array input-blob)
            batch (mxnet.io/DataBatch :data [data])]
        (py. model forward batch :is_train false)
        (-> (py. model get_outputs)
            first
            (py. asnumpy)
            (#(dtype/make-container :java-array :float32 %))))
      (throw (Exception. (format "Failed to load img: %s" img-path))))))
