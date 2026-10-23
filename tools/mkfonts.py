from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.ttLib import TTFont
import os

src = r"F:\KotlinProject\CandyIDE\desktop\src\main\resources\fonts\NotoSansSC-VF.ttf"
dst_dir = r"F:\KotlinProject\CandyIDE\desktop\src\main\resources\fonts"
for w in (400, 500, 700):
    font = TTFont(src)
    instantiateVariableFont(font, {"wght": w}, inplace=True)
    out = os.path.join(dst_dir, "NotoSansSC-%d.ttf" % w)
    font.save(out)
    font.close()
    print(out, os.path.getsize(out) // 1024, "KB")
