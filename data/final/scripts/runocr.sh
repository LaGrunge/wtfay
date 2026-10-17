#!/bin/bash
p=$1
[ -s ocr/p$p.txt ] && exit 0
ddjvu -format=tiff -page=$p -quality=100 argo.djvu ocr/p$p.tif && OMP_THREAD_LIMIT=1 tesseract ocr/p$p.tif ocr/p$p -l rus --psm 1 2>/dev/null; rm -f ocr/p$p.tif
