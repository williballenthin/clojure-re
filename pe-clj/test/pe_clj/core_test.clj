(ns pe-clj.core-test
  (:require [clojure.test :refer :all]
            [pe-clj.core :refer :all]
            [clojure.java.io :as io]))

(def fixtures (.getPath (clojure.java.io/resource "fixtures")))
(def kern32 (io/file fixtures "kernel32.dll"))

(deftest pe-header-test
  (let [pe (read-pe kern32)]
    (testing "dos header"
      (is (= 0x5A4D (get-in pe [:dos-header :e_magic])))
      (is (= 0xF0   (get-in pe [:dos-header :e_lfanew]))))
    (testing "signature"
      (is (= 0x4550 (get-in pe [:nt-header :signature]))))
    (testing "signature"
      (is (= IMAGE_FILE_MACHINE_I386 (get-in pe [:nt-header :file-header :Machine]))))
    (testing "optional header"
      (is (= IMAGE_NT_OPTIONAL_HDR32_MAGIC (get-in pe [:nt-header :optional-header :Magic])))
      (is (= 0xDA000 (get-in pe [:nt-header :optional-header :SizeOfCode])))
      (is (= 0x1695 (get-in pe [:nt-header :optional-header :AddressOfEntryPoint])))
      (is (= 0x68900000 (get-in pe [:nt-header :optional-header :ImageBase]))))
    (testing "data directories"
      (is (= 0xCE220 (get-in pe [:nt-header :optional-header :data-directories 0 :rva])))
      (is (= 0xC985 (get-in pe [:nt-header :optional-header :data-directories 0 :size])))
      (is (= 0x0 (get-in pe [:nt-header :optional-header :data-directories 0xF :rva])))
      (is (= 0x0 (get-in pe [:nt-header :optional-header :data-directories 0xF :size]))))
    (testing "section headers"
      (is (= ".text" (get-in pe [:section-headers ".text" :Name])))
      (is (= 0xd9c0d (get-in pe [:section-headers ".text" :VirtualSize])))
      (is (= ".reloc" (get-in pe [:section-headers ".reloc" :Name])))
      (is (= 0xfdc0 (get-in pe [:section-headers ".reloc" :VirtualSize]))))))


(deftest pe-sections-test
  (let [pe (read-pe kern32)]
    (testing ".text"
      (is (= 0x90 (bit-and 0xFF (.get (get-section-data pe ".text"))))))
    (testing ".data"
      (is (= 0x00 (bit-and 0xFF (.get (get-section-data pe ".data"))))))
    (testing ".idata"
      (is (= 0x23 (bit-and 0xFF (.get (get-section-data pe ".idata"))))))
    (testing ".rsrc"
      (is (= 0x00 (bit-and 0xFF (.get (get-section-data pe ".rsrc"))))))
    (testing ".reloc"
      (is (= 0x00 (bit-and 0xFF (.get (get-section-data pe ".reloc"))))))))


(let [pe (read-pe kern32)]
  (get-in pe [:section-headers]))