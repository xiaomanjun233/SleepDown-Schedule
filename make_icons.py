
from pathlib import Path
from collections import deque
from PIL import Image
src = Path(r'C:/Users/23085/Desktop/ChatGPT Image 2026年5月20日 17_04_52.png')
root = Path(r'D:/Android studio/CourseSchedule/app/src/main/res')
img = Image.open(src).convert('RGBA')
w,h = img.size
pix = img.load()
seen = set()
q = deque()
def dark_edge(p):
    r,g,b,a = p
    return a > 0 and r < 26 and g < 26 and b < 26
for x in range(w):
    if dark_edge(pix[x,0]): q.append((x,0))
    if dark_edge(pix[x,h-1]): q.append((x,h-1))
for y in range(h):
    if dark_edge(pix[0,y]): q.append((0,y))
    if dark_edge(pix[w-1,y]): q.append((w-1,y))
while q:
    x,y = q.popleft()
    if (x,y) in seen or x < 0 or x >= w or y < 0 or y >= h: continue
    if not dark_edge(pix[x,y]): continue
    seen.add((x,y))
    pix[x,y] = (0,0,0,0)
    q.extend(((x+1,y),(x-1,y),(x,y+1),(x,y-1)))
alpha = img.getchannel('A')
bbox = alpha.getbbox()
if bbox:
    img = img.crop(bbox)
# Fill the launcher square aggressively. Keep only a tiny 1% safe inset.
side = max(img.size)
square = Image.new('RGBA', (side, side), (0,0,0,0))
square.alpha_composite(img, ((side-img.width)//2, (side-img.height)//2))
inset = int(side * 0.01)
canvas = Image.new('RGBA', (side, side), (0,0,0,0))
scaled = square.resize((side - inset*2, side - inset*2), Image.Resampling.LANCZOS)
canvas.alpha_composite(scaled, (inset, inset))
icons = {'mipmap-mdpi':48, 'mipmap-hdpi':72, 'mipmap-xhdpi':96, 'mipmap-xxhdpi':144, 'mipmap-xxxhdpi':192}
for folder,size in icons.items():
    d = root / folder
    d.mkdir(parents=True, exist_ok=True)
    out = canvas.resize((size,size), Image.Resampling.LANCZOS)
    out.save(d / 'ic_launcher.png')
    out.save(d / 'ic_launcher_round.png')
(root / 'drawable').mkdir(exist_ok=True)
canvas.resize((512,512), Image.Resampling.LANCZOS).save(root / 'drawable' / 'ic_launcher_preview.png')
print('regenerated fill icon', canvas.size, 'content', img.size)
