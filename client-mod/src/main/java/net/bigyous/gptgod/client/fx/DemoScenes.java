package net.bigyous.gptgod.client.fx;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A few scenes written in the effect language, for /godfx scene and the screenshot tests. On a server the
 * god writes its own; these only show what the language can do.
 */
public final class DemoScenes {
    private DemoScenes() {
    }

    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        FxTextures.samples();
        ALL.put("nova", """
                {"layers":[
                 {"type":"particles","shape":"sphere","motion":"inward","radius":7,"count":500,"speed":0.32,"life":[14,20],"sprite":"spark","color":["#FFF6D0","#FFB040"],"size":[0.25,0.05],"drag":0},
                 {"type":"sound","key":"minecraft:block.beacon.power_select","pitch":0.6,"volume":2},
                 {"type":"particles","delay":20,"shape":"burst","count":900,"speed":0.9,"spread":0.6,"life":[20,45],"sprite":"mote","color":["#FFFFFF","#FF7A1C"],"size":[0.5,0.1],"drag":0.07},
                 {"type":"shockwave","delay":20,"radius":16,"duration":22,"sprite":"mote","color":["#FFF1C0","#FF9A30"],"size":[0.7,0.2],"offset":[0,0.2,0]},
                 {"type":"particles","delay":20,"shape":"burst","count":120,"speed":0.5,"life":[30,60],"sprite":"shard","color":["#FFE9A8","#B24BF3"],"size":[0.3,0.1],"gravity":0.02,"glow":0.2},
                 {"type":"screen","delay":20,"kind":"flash","intensity":0.5,"duration":8,"color":"#FFF4D6"},
                 {"type":"screen","delay":20,"kind":"shake","intensity":0.8,"duration":20},
                 {"type":"post","delay":20,"kind":"shock","intensity":1,"duration":30},
                 {"type":"post","delay":20,"kind":"aberration","intensity":0.9,"duration":24},
                 {"type":"post","kind":"bloom","intensity":0.8,"duration":70},
                 {"type":"post","kind":"blur","intensity":0.5,"duration":22},
                 {"type":"sound","delay":20,"key":"minecraft:entity.generic.explode","pitch":0.5,"volume":3}
                ]}""");
        ALL.put("pillar", """
                {"layers":[
                 {"type":"beam","height":40,"width":2.4,"duration":70,"rate":28,"color":["#FFFFFF","#FFD86A"]},
                 {"type":"particles","shape":"helix","radius":1.6,"height":14,"turns":4,"rate":14,"duration":70,"speed":0.12,"sprite":"star","color":["#FFF6D0","#FFC23A"],"size":[0.35,0.1],"life":[20,34]},
                 {"type":"particles","shape":"sigil","radius":4,"rate":22,"duration":70,"sprite":"mote","color":["#FFD86A","#FF8A1C"],"size":[0.22,0.08],"life":[10,18]},
                 {"type":"particles","shape":"disc","radius":4,"rate":6,"duration":70,"motion":"up","speed":0.08,"sprite":"rune","color":["#FFF1C0","#FFC23A"],"size":[0.5,0.3],"life":[30,50]},
                 {"type":"sky","color":"#FFE9A8","strength":0.5,"duration":80},
                 {"type":"post","kind":"rays","intensity":0.9,"duration":80,"offset":[0,8,0]},
                 {"type":"post","kind":"bloom","intensity":0.7,"duration":80},
                 {"type":"sound","key":"minecraft:block.beacon.activate","pitch":0.7,"volume":2}
                ]}""");
        ALL.put("judgement", """
                {"layers":[
                 {"type":"sky","color":"#14141C","strength":0.85,"duration":120},
                 {"type":"halo","radius":3,"height":14,"width":0.5,"count":16,"duration":110,"color":["#FFE9A8","#FF9A30"]},
                 {"type":"particles","shape":"sigil","radius":6,"rate":30,"duration":100,"sprite":"mote","color":["#FFD86A","#FF5A1C"],"size":[0.25,0.08],"life":[10,18]},
                 {"type":"lightning","delay":40,"duration":10,"height":14,"radius":0.3,"width":0.25,"count":2,"color":["#FFF6D0","#FFB040"]},
                 {"type":"beam","delay":44,"duration":36,"height":40,"width":1.6,"color":["#FFE9A8","#FF7A1C"]},
                 {"type":"shockwave","delay":44,"radius":18,"duration":26,"width":1.2,"color":["#FFF1C0","#FF7A1C"]},
                 {"type":"debris","delay":44,"count":60,"radius":6,"speed":0.45,"up":0.75,"spin":1.2},
                 {"type":"spikes","delay":46,"radius":7,"height":4,"count":28,"duration":70,"color":["#FFF6D0","#FF5A1C"]},
                 {"type":"particles","delay":44,"shape":"burst","count":500,"speed":0.7,"spread":0.7,"life":[20,50],"sprite":"ember","color":["#FFE9A8","#FF3A10"],"size":[0.3,0.06],"gravity":0.01},
                 {"type":"screen","delay":44,"kind":"shake","intensity":1,"duration":24},
                 {"type":"screen","delay":44,"kind":"flash","intensity":0.45,"duration":8,"color":"#FFF4D6"},
                 {"type":"post","delay":44,"kind":"shock","intensity":1,"duration":30},
                 {"type":"post","delay":44,"kind":"aberration","intensity":0.8,"duration":20},
                 {"type":"post","delay":40,"kind":"bloom","intensity":0.8,"duration":70},
                 {"type":"glyph","delay":50,"text":"JUDGED","color":"#FFE9A8","size":2.2,"duration":60,"offset":[0,4,0]},
                 {"type":"sound","delay":40,"key":"minecraft:entity.lightning_bolt.thunder","volume":3,"pitch":0.7},
                 {"type":"sound","delay":44,"key":"minecraft:entity.generic.explode","volume":3,"pitch":0.5}
                ]}""");
        ALL.put("sanctum", """
                {"layers":[
                 {"type":"dome","radius":9,"duration":120,"color":["#8FD0FF","#3A5AFF"],"alpha":0.8},
                 {"type":"tendrils","radius":7,"height":7,"count":10,"width":0.2,"duration":120,"color":["#BFE4FF","#5A2BD8"]},
                 {"type":"illusion","mode":"rise","count":14,"radius":8,"height":5,"life":110},
                 {"type":"illusion","mode":"float","count":30,"radius":7,"life":110},
                 {"type":"particles","shape":"column","radius":8,"height":6,"rate":10,"duration":110,"motion":"up","speed":0.05,"sprite":"star","color":["#FFFFFF","#8FD0FF"],"size":[0.2,0.05],"life":[30,50]},
                 {"type":"post","kind":"bloom","intensity":0.6,"duration":120},
                 {"type":"post","kind":"tint","intensity":0.25,"duration":120,"color":"#6FA8FF"},
                 {"type":"sound","key":"minecraft:block.beacon.activate","pitch":1.3,"volume":2}
                ]}""");
        ALL.put("rift", """
                {"layers":[
                 {"type":"rift","height":9,"width":2.2,"duration":110,"color":["#D9A6FF","#3A0A6A"],"spin":0},
                 {"type":"particles","shape":"sphere","motion":"inward","radius":9,"rate":24,"duration":100,"speed":0.3,"sprite":"spark","color":["#D9A6FF","#5A2BD8"],"size":[0.25,0.05],"life":[18,28],"offset":[0,4.5,0]},
                 {"type":"illusion","mode":"float","count":40,"radius":9,"life":100},
                 {"type":"post","kind":"blur","intensity":0.45,"duration":110,"offset":[0,4.5,0]},
                 {"type":"post","kind":"aberration","intensity":0.6,"duration":110},
                 {"type":"post","kind":"desaturate","intensity":0.6,"duration":110},
                 {"type":"post","kind":"bloom","intensity":0.7,"duration":110},
                 {"type":"sky","color":"#1E0A36","strength":0.8,"duration":115},
                 {"type":"sound","key":"minecraft:block.portal.trigger","pitch":0.5,"volume":2}
                ]}""");
        ALL.put("vortex", """
                {"layers":[
                 {"type":"particles","shape":"vortex","radius":6,"height":12,"rate":40,"duration":100,"speed":0.5,"sprite":"smoke","glow":0,"color":["#3A2A4A","#120A1E"],"size":[1.2,2.4],"life":[30,50],"alpha":0.7},
                 {"type":"particles","shape":"vortex","radius":5,"height":12,"rate":18,"duration":100,"speed":0.7,"sprite":"spark","color":["#C9A0FF","#5A2BD8"],"size":[0.3,0.08],"life":[20,36]},
                 {"type":"particles","shape":"ring","radius":6.5,"rate":10,"duration":100,"motion":"orbit","speed":0.4,"sprite":"ember","color":["#FF4A3A","#5A0C06"],"size":[0.25,0.1],"life":[24,40],"offset":[0,0.3,0]},
                 {"type":"sky","color":"#1E0A36","strength":0.8,"duration":110},
                 {"type":"screen","kind":"vignette","intensity":0.5,"duration":110,"color":"#1E0A36"},
                 {"type":"post","kind":"desaturate","intensity":0.7,"duration":110},
                 {"type":"post","kind":"tint","intensity":0.35,"duration":110,"color":"#7A3AD0"},
                 {"type":"post","kind":"grain","intensity":0.5,"duration":110},
                 {"type":"sound","key":"minecraft:entity.wither.ambient","pitch":0.5,"volume":2}
                ]}""");
        ALL.put("wings", """
                {"layers":[
                 {"type":"particles","shape":"wings","radius":4.5,"height":4,"rate":50,"duration":80,"motion":"fall","speed":0.02,"sprite":"mote","color":["#FFFFFF","#8FD0FF"],"size":[0.28,0.06],"life":[14,26],"spread":0.5},
                 {"type":"particles","shape":"wings","radius":4.5,"height":4,"rate":8,"duration":80,"motion":"fall","speed":0.04,"sprite":"shard","glow":0.3,"color":["#E8F6FF","#6FA8FF"],"size":[0.3,0.12],"life":[30,50]},
                 {"type":"particles","shape":"ring","radius":0.6,"rate":8,"duration":80,"sprite":"star","color":["#FFF6D0","#FFD86A"],"size":[0.22,0.1],"life":[8,14],"offset":[0,2.6,0]}
                ]}""");
        ALL.put("storm", """
                {"layers":[
                 {"type":"particles","shape":"disc","radius":12,"rate":20,"duration":120,"sprite":"smoke","glow":0,"color":["#2A2A34","#0E0E14"],"size":[3,5],"life":[40,60],"alpha":0.75,"offset":[0,14,0],"turbulence":0.02},
                 {"type":"particles","shape":"rain","radius":11,"height":14,"rate":60,"duration":120,"speed":0.9,"sprite":"ray","color":["#BFE4FF","#4A8AFF"],"size":[0.5,0.3],"life":[12,16],"drag":0},
                 {"type":"beam","delay":30,"duration":3,"count":140,"height":14,"width":1.2,"color":["#FFFFFF","#9AD0FF"],"offset":[3,0,-2]},
                 {"type":"screen","delay":30,"kind":"flash","intensity":0.5,"duration":5,"color":"#E8F0FF"},
                 {"type":"sound","delay":30,"key":"minecraft:entity.lightning_bolt.thunder","volume":3},
                 {"type":"beam","delay":75,"duration":3,"count":140,"height":14,"width":1.2,"color":["#FFFFFF","#9AD0FF"],"offset":[-4,0,3]},
                 {"type":"screen","delay":75,"kind":"flash","intensity":0.5,"duration":5,"color":"#E8F0FF"},
                 {"type":"sound","delay":75,"key":"minecraft:entity.lightning_bolt.thunder","volume":3,"pitch":0.8},
                 {"type":"sky","color":"#14141C","strength":0.85,"duration":130}
                ]}""");
        // The three below are written with formulas (field) and designed geometry (solid): shapes no list offers.
        ALL.put("galaxy", """
                {"layers":[
                 {"type":"field","count":1300,"duration":160,"sprite":"star","x":"cos(mod(i,3)/3*tau + (1+9*r)*0.55 + (rand(2)-0.5)*0.5 - t*0.02)*(1+9*r)","y":"6 + noise(i*0.37, t*0.02)*0.5","z":"sin(mod(i,3)/3*tau + (1+9*r)*0.55 + (rand(2)-0.5)*0.5 - t*0.02)*(1+9*r)","size":"0.1 + 0.22*rand(1)*(1-r)","shade":"r","color":["#FFFFFF","#FFE9A8","#C77DFF","#3A1A8A"]},
                 {"type":"field","count":220,"duration":160,"x":"sin(acos(1-2*r))*cos(rand(1)*tau)*(0.9+0.25*sin(t*0.15))","y":"6 + (1-2*r)*(0.9+0.25*sin(t*0.15))","z":"sin(acos(1-2*r))*sin(rand(1)*tau)*(0.9+0.25*sin(t*0.15))","size":0.35,"alpha":0.8,"shade":"rand(2)","color":["#FFFFFF","#FFD86A"]},
                 {"type":"post","kind":"bloom","intensity":0.7,"duration":160},
                 {"type":"sky","color":"#0A0618","strength":0.8,"duration":165}
                ]}""");
        ALL.put("henge", """
                {"layers":[
                 {"type":"solid","form":"box","size":[1.3,5,0.7],"count":10,"duration":170,"x":"cos(i/n*tau)*7","z":"sin(i/n*tau)*7","y":"-2.6 + 5.1*ease((t-i*4)/22)","yaw":"i/n*360 - 90","color":["#3A3A46","#9A9AB0"]},
                 {"type":"solid","form":"lathe","profile":[[1.7,0],[1.5,0.3],[0.55,0.6],[0.45,1.4],[1.15,1.9],[1.25,2.1]],"sides":16,"duration":170,"color":["#4A4038","#D8C8A0"]},
                 {"type":"solid","form":"prism","outline":[[0.8,0],[0.4,0.69],[-0.4,0.69],[-0.8,0],[-0.4,-0.69],[0.4,-0.69]],"height":1.8,"taper":0,"count":2,"pitch":"i*180","y":"4.6 + sin(t*0.08)*0.3","yaw":"t*3","delay":20,"duration":150,"color":["#5A2BD8","#F0E0FF"]},
                 {"type":"solid","form":"tube","light":true,"path":[[7,5.3,0],[6.06,5.3,3.5],[3.5,5.3,6.06],[0,5.3,7],[-3.5,5.3,6.06],[-6.06,5.3,3.5],[-7,5.3,0],[-6.06,5.3,-3.5],[-3.5,5.3,-6.06],[0,5.3,-7],[3.5,5.3,-6.06],[6.06,5.3,-3.5],[7,5.3,0]],"width":0.18,"sides":4,"delay":50,"duration":120,"color":"#FFE9A8"},
                 {"type":"field","count":320,"duration":170,"x":"cos(rand(1)*tau)*6.5*sqrt(r)","z":"sin(rand(1)*tau)*6.5*sqrt(r)","y":"mod(rand(2)*8 + t*0.04, 8)","size":0.1,"alpha":"sin(mod(rand(2)*8 + t*0.04, 8)/8*pi)","shade":"rand(3)","color":["#B24BF3","#FFE9A8"]},
                 {"type":"sound","key":"minecraft:block.beacon.activate","pitch":0.6,"volume":2}
                ]}""");
        // Painted textures (the two samples the mod carries, as a god's fx_paint would send them) on solids and particles.
        ALL.put("idol", """
                {"layers":[
                 {"type":"solid","form":"lathe","profile":[[1.6,0],[1.3,0.4],[0.9,0.6],[0.9,5],[1.4,5.4],[1.4,5.8]],"sides":16,"texture":"demo.veins","tile":[6,4],"duration":170},
                 {"type":"solid","form":"box","size":[2.2,2.2,2.2],"texture":"demo.veins","y":"7.7+sin(t*0.07)*0.3","yaw":"t*2","pitch":"t*1.3","duration":170},
                 {"type":"solid","form":"quads","light":true,"quads":[[[-2.5,1,3],[-2.5,6,3],[2.5,6,3],[2.5,1,3]],[[-2.5,1,-3],[-2.5,6,-3],[2.5,6,-3],[2.5,1,-3]]],"texture":"demo.sigil","alpha":"0.6+0.4*sin(t*0.15)","duration":170},
                 {"type":"field","count":60,"duration":170,"texture":"demo.sigil","x":"cos(i/n*tau+t*0.02)*5","z":"sin(i/n*tau+t*0.02)*5","y":"1+3*rand(1)+sin(t*0.05+i)","size":0.5}
                ]}""");
        ALL.put("orrery", """
                {"layers":[
                 {"type":"solid","form":"ball","radius":1.3,"sides":16,"light":true,"y":6,"scale":"1+0.06*sin(t*0.3)","duration":170,"color":["#FFB040","#FFF6D0"]},
                 {"type":"solid","form":"ball","radius":0.5,"sides":10,"count":6,"duration":170,"x":"cos(i*2.1 + t*0.09/(1+i*0.5))*(3+i*1.3)","z":"sin(i*2.1 + t*0.09/(1+i*0.5))*(3+i*1.3)","y":"6 + sin(i*1.7 + t*0.05)*0.3","scale":"0.6+0.7*rand(1)","color":["#1E3A7A","#9AD0FF"]},
                 {"type":"field","count":720,"duration":170,"x":"cos(i/120*tau)*(3+floor(i/120)*1.3)","z":"sin(i/120*tau)*(3+floor(i/120)*1.3)","y":6,"size":0.07,"alpha":0.55,"color":"#8FD0FF"},
                 {"type":"field","count":300,"duration":170,"sprite":"star","x":"sin(acos(1-2*r))*cos(rand(1)*tau)*13","y":"6+(1-2*r)*13","z":"sin(acos(1-2*r))*sin(rand(1)*tau)*13","size":0.14,"alpha":"0.35+0.65*sin(t*0.2+i)^2","shade":"rand(2)","color":["#FFFFFF","#C9A0FF"]},
                 {"type":"sky","color":"#05040C","strength":0.85,"duration":175},
                 {"type":"post","kind":"bloom","intensity":0.6,"duration":170}
                ]}""");
    }
}
