(ns sormilla.video
  (:import [com.twilight.h264.decoder AVFrame AVPacket H264Decoder MpegEncContext]
           [com.twilight.h264.player FrameUtils]
           [org.opencv.core CvType Mat MatOfByte]
           [org.opencv.highgui Highgui]
           [java.awt RenderingHints]
           [java.awt.image BufferedImage]
           [java.net Socket InetSocketAddress]
           [javax.imageio ImageIO]
           [java.io InputStream ByteArrayInputStream]
           [org.apache.commons.io IOUtils])
  (:require [sormilla.bin :as bin]
            [sormilla.drone-comm :as comm]
            [clojure.java.io :as io]))

; (set! *warn-on-reflection* true)

;;
;; I/O
;;

(defn read-input [^InputStream in buffer size]
  (IOUtils/readFully in buffer 0 size)
  buffer)

(defn skip-input [^InputStream in size]
  (.skip in size)
  in)

(defn init-video [s]
  (doto (.getOutputStream s)
    (.write (byte-array (map bin/ubyte [1 0 0 0])))
    (.flush)))

;;
;; Video stream parsing
;;

(defn signature [header]    (bin/get-int header 0))
(defn header-size [header]  (bin/get-short header 6))
(defn payload-size [header] (bin/get-int header 8))


;;
;; Video decoding
;;

(defn ba->ia [^bytes source ^ints target]
  (doseq [i (range (count source))]
    (aset-int target i (bit-and 0xFF (nth source i))))
  target)

(defn buffer->image [w h buffer]
  (let [image (BufferedImage. w h BufferedImage/TYPE_INT_RGB)]
    (.setRGB image 0 0 w h buffer 0 w)
    image))

(defn make-decoder []
  (let [codec           (H264Decoder.)
        context         (MpegEncContext/avcodec_alloc_context)
        packet          (AVPacket.)
        frame           (AVFrame/avcodec_alloc_frame)
        image-buffer    (byte-array (+ 65535 MpegEncContext/FF_INPUT_BUFFER_PADDING_SIZE))
        ibuffer         (int-array (+ 65535 MpegEncContext/FF_INPUT_BUFFER_PADDING_SIZE))
        got-picture?    (int-array [0])
        header-buffer   (byte-array 12)]
    
    (if-not (zero? (bit-and (.capabilities codec) H264Decoder/CODEC_CAP_TRUNCATED)) (throw (Exception. "need to configure CODEC_FLAG_TRUNCATED")))
    (if (neg? (.avcodec_open context codec)) (throw (Exception. "Could not open codec")))
    (.av_init_packet packet)
    
    (fn [in]
      (let [header       (read-input in header-buffer 12)
            header-size  (header-size header)
            image-size   (payload-size header)]
      
        (when-not (= (signature header) 0x45566150) (throw (Exception. "out of sync")))
        (skip-input in (- header-size 12))
        (read-input in image-buffer image-size)
        
        (set! (.size packet) image-size)
        (set! (.data_base packet) (ba->ia image-buffer ibuffer)) ; FIXME: size
        (set! (.data_offset packet) 0)
        
        (.avcodec_decode_video2 context frame got-picture? packet)
        (when (zero? (first got-picture?)) (throw (Exception. "Could not decode frame")))
        
        (let [picture (.displayPicture (.priv_data context))
              width (.imageWidth picture)
              height (.imageHeight picture)
              picture-buffer (int-array (* width height))]
          (FrameUtils/YUV2RGB picture picture-buffer)
          (buffer->image width height picture-buffer))))))

(defn update-image [img]
  (ImageIO/write img "png" (io/file "image.png")))


(defn parse-file []
  (let [in (io/input-stream (io/file "capture.h264"))
        decoder (make-decoder)]
    (try
      (doseq [i (range 10)]
        (ImageIO/write (decoder in) "png" (io/file (str "image-" i ".png"))))
      (println "success!")
      (catch Exception e
        (println "failure" e)
        (.printStackTrace e))
      (finally
        (try (.close in) (catch Exception e))))))


(comment

  (parse-file)

  ; ffmpeg -f h264 -an -i capture.h264 stream.m4v
  
(defn capture []
  (let [socket (doto (Socket.)
                 (.setSoTimeout 2000)
                 (.connect (InetSocketAddress. comm/drone-ip 5555)))
        in (io/input-stream socket)
        out (io/output-stream (io/file "capture.h264"))]
    (try
      (init-video socket)
      (doseq [n (range 1024)]
        (.write out (read-input in 4096)))
      (.flush out)
      (.close out)
      (println "success!")
      (catch Exception e
        (println "failure" e)
        (.printStackTrace e)
        (try (.close socket) (catch Exception e))
        (try (.close out) (catch Exception e))))))

  (comm/send-commands! [(comm/video-codec :h264-360p) (comm/video-frame-rate 15) comm/leds-active])
  (capture)
  (comm/send-commands! [comm/leds-reset])

(defn parse-file []
  (let [in (io/input-stream (io/file "capture.h264"))]
    (try
      (doseq [i (range 1)]
        (let [header-data  (read-input in 12)
              header       (parse-header header-data)]
          (when-not (= (:sig header) 0x45566150) (throw (Exception. "out of sync")))
          (skip-input in (- (:header-size header) 12))
          (let [frame (read-input in (:payload-size header))
                img (frame->image frame)]
            (update-image img))))
      (println "success!")
      (catch Exception e
        (println "failure" e)
        (.printStackTrace e))
      (finally
        (try (.close in) (catch Exception e))))))
  
)

#_(doseq [n (range 100)]
          (let [header-data  (read-input in 68)
                signature    (parse-signature header-data)
                header       (parse-header header-data)]
            (if (= signature "PaVE")
              (do
                (.write out header-data)
                (.write out (read-input in (:payload-size header))))
              (println "not a pave" n))))