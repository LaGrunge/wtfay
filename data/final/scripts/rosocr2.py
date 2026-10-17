import sys, subprocess, os, numpy as np
from PIL import Image
P="/home/lagrunge/Downloads/Russian spelling dictionary about 200,000 words.pdf"
p=int(sys.argv[1]); tag=f"{p:03d}"
out=f"ros2/o{tag}.txt"
if os.path.exists(out) and os.path.getsize(out)>0: sys.exit(0)
base=f"ros2/i{tag}"
subprocess.run(["pdftoppm","-f",str(p),"-l",str(p),"-r","300","-gray","-png",P,base],check=True)
f=[x for x in os.listdir("ros2") if x.startswith(f"i{tag}")][0]; f="ros2/"+f
im=Image.open(f).convert('L'); a=np.array(im)<128
h,w=a.shape
y0,y1=int(h*0.075),int(h*0.94)
prof=a[y0:y1].sum(0)
ink=np.where(prof>3)[0]
L,R=ink.min(),ink.max()
sm=np.convolve(prof,np.ones(15)/15,'same')
cuts=[L-5]
q=(R-L)/4
for k in (1,2,3):
    c=int(L+k*q); lo,hi=c-70,c+70
    cuts.append(lo+int(np.argmin(sm[lo:hi])))
cuts.append(R+5)
texts=[]
for i in range(4):
    crop=im.crop((max(0,cuts[i]),y0,min(w,cuts[i+1]),y1))
    cp=f"ros2/c{tag}_{i}.png"; crop.save(cp)
    r=subprocess.run(["tesseract",cp,"-","-l","rus","--psm","6"],capture_output=True,text=True,env={**os.environ,"OMP_THREAD_LIMIT":"1"})
    texts.append(r.stdout); os.remove(cp)
os.remove(f)
open(out,"w").write("\n".join(texts))
