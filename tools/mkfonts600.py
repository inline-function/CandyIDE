from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.ttLib import TTFont
import os

src = r"F:\KotlinProject\CandyIDE\desktop\src\main\resources\fonts\NotoSansSC-VF.ttf"
dst_dir = r"F:\KotlinProject\CandyIDE\desktop\src\main\resources\fonts"
font = TTFont(src)
instantiateVariableFont(font, {"wght": 600}, inplace=True)
out = os.path.join(dst_dir, "NotoSansSC-600.ttf")
font.save(out)
font.close()
print(out, os.path.getsize(out) // 1024, "KB")
