import json, sys
D={'UP':(-1,0),'DOWN':(1,0),'LEFT':(0,-1),'RIGHT':(0,1)}
L=[
("""
....
.aA.
.Bb.
....""",dict(A='RIGHT',B='LEFT')),
("""
bbB.
.A.c
.a.c
.a.C""",dict(A='UP',B='RIGHT',C='DOWN')),
("""
D....
d...b
aaaAb
....B
..Ccc""",dict(A='RIGHT',B='DOWN',C='LEFT',D='UP')),
("""
..Bbb
..A.b
aaacc
dEeC.
ddD..""",dict(A='UP',B='LEFT',C='DOWN',D='RIGHT',E='LEFT')),
("""
.....f
aaaA.f
...B.F
.cCbD.
.c.bd.
.c.Ee.""",dict(A='RIGHT',B='UP',C='RIGHT',D='UP',E='LEFT',F='DOWN')),
("""
Aa..Gg
.a...g
.aaBbg
C...b.
ccdDbE
.....e""",dict(A='LEFT',B='LEFT',C='UP',D='RIGHT',E='UP',G='LEFT')),
("""
bAaaaad
bCcc..d
b.eeeED
BFffffg
i..hhHg
iJjjj.g
IkkkkKG""",dict(A='LEFT',B='DOWN',C='LEFT',D='DOWN',E='RIGHT',F='LEFT',G='DOWN',H='RIGHT',I='DOWN',J='LEFT',K='RIGHT')),
("""
.bbbbbB
...A...
...aCcc
...aaac
.eeEDfc
Gedddf.
ggFfff.""",dict(A='UP',B='RIGHT',C='LEFT',D='UP',E='RIGHT',F='LEFT',G='UP')),
]

L9=("""
M.LllllF
mmeeeeEf
j...bGgf
jjJDb..f
.dddbbBa
IkkcccCa
iK.c..ha
iiicHhhA""",dict(A='DOWN',B='RIGHT',C='RIGHT',D='UP',E='RIGHT',F='UP',G='LEFT',H='LEFT',I='UP',J='RIGHT',K='DOWN',L='LEFT',M='UP'))
rows=L9[0].strip('\n').split('\n')
rot=["".join(rows[len(rows)-1-r][c] for r in range(len(rows))) for c in range(len(rows[0]))]
cw=dict(UP='RIGHT',RIGHT='DOWN',DOWN='LEFT',LEFT='UP')
L += [L9, ("\n".join(rot), {k:cw[v] for k,v in L9[1].items()})]

out=sys.argv[1]
for n,(art,dirs) in enumerate(L,1):
    g=[r for r in art.strip('\n').split('\n')]
    R,C=len(g),len(g[0]); assert all(len(r)==C for r in g),n
    arrows=[]; used=set()
    for (r,row) in enumerate(g):
        for c,ch in enumerate(row):
            if ch.isupper():
                cells=[(r,c)]; seen={(r,c)}
                while True:
                    cr,cc=cells[-1]
                    nb=[(cr+a,cc+b) for a,b in D.values() if 0<=cr+a<R and 0<=cc+b<C and g[cr+a][cc+b]==ch.lower() and (cr+a,cc+b) not in seen]
                    assert len(nb)<=1,(n,ch,nb)
                    if not nb: break
                    cells.append(nb[0]); seen.add(nb[0])
                dr,dc=D[dirs[ch]]
                if len(cells)>1: assert cells[1]==(r-dr,c-dc),(n,ch,'neck')
                arrows.append(dict(dir=dirs[ch],cells=[list(x) for x in cells])); used|=seen
    letters=sum(1 for row in g for ch in row if ch!='.')
    assert letters==len(used),(n,'orphan body cells')
    # greedy solve
    occ={tuple(x):i for i,a in enumerate(arrows) for x in a['cells']}; alive=set(range(len(arrows))); depth=0
    while alive:
        free=[]
        for i in alive:
            a=arrows[i]; dr,dc=D[a['dir']]; r,c=a['cells'][0]; r+=dr;c+=dc; ok=True
            while 0<=r<R and 0<=c<C:
                if (r,c) in occ: ok=False;break
                r+=dr;c+=dc
            if ok: free.append(i)
        assert free,(n,'UNSOLVABLE',[(arrows[i]['dir'],arrows[i]['cells'][0]) for i in alive])
        for i in free:
            alive.discard(i)
            for x in arrows[i]['cells']: del occ[tuple(x)]
        depth+=1
    print(f"level {n}: {R}x{C} arrows={len(arrows)} depth={depth}")
    json.dump(dict(rows=R,cols=C,arrows=arrows),open(f"{out}/{n}.json","w"),separators=(',',':'))
