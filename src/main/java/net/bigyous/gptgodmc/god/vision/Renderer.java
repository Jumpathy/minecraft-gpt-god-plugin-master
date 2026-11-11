package net.bigyous.gptgodmc.god.vision;

import java.awt.image.BufferedImage;

/**
 * A small voxel ray tracer. Blocks are drawn in their map colors with face shading, light,
 * distance fog and thin block-edge lines (which make blocky scenes much easier to read).
 */
public final class Renderer {
    private static final double NEAR = 0.3;

    public record Camera(double x, double y, double z, double yaw, double pitch, double fovDegrees) {
    }

    public enum OverheadMode {
        SURFACE, CAVE, XRAY
    }

    private Renderer() {
    }

    // ---- Direction helpers (Minecraft yaw: 0 = south/+z, 90 = west/-x; pitch: positive = down) ----

    static double[] forward(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new double[] { -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch) };
    }

    static double[] right(double yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new double[] { -Math.cos(yaw), 0, -Math.sin(yaw) };
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    /** Yaw/pitch that look from (fx,fy,fz) at (tx,ty,tz). */
    public static double[] lookAt(double fx, double fy, double fz, double tx, double ty, double tz) {
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        double yaw = Math.toDegrees(-Math.atan2(dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[] { yaw, pitch };
    }

    // ---- Perspective and panorama ---------------------------------------------------------------

    public static BufferedImage perspective(Scene s, Camera cam, int w, int h, double maxDist, boolean xray) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double[] depth = new double[w * h];
        double[] f = forward(cam.yaw(), cam.pitch());
        double[] r = right(cam.yaw());
        double[] u = cross(r, f);
        double tanHalf = Math.tan(Math.toRadians(cam.fovDegrees()) / 2);
        double aspect = (double) w / h;
        double[] d = new double[3];
        double[] out = new double[1];
        for (int py = 0; py < h; py++) {
            double sy = (1 - 2 * (py + 0.5) / h) * tanHalf;
            for (int px = 0; px < w; px++) {
                double sx = (2 * (px + 0.5) / w - 1) * tanHalf * aspect;
                d[0] = f[0] + sx * r[0] + sy * u[0];
                d[1] = f[1] + sx * r[1] + sy * u[1];
                d[2] = f[2] + sx * r[2] + sy * u[2];
                normalize(d);
                img.setRGB(px, py, trace(s, cam.x(), cam.y(), cam.z(), d[0], d[1], d[2], maxDist, xray, out));
                depth[py * w + px] = out[0];
            }
        }
        // Labels for players, named creatures and the vessel.
        for (Scene.Box b : s.boxes) {
            if (b.label() == null) continue;
            double vx = b.x() - cam.x();
            double vy = b.y() + b.height() + 0.4 - cam.y();
            double vz = b.z() - cam.z();
            double zf = vx * f[0] + vy * f[1] + vz * f[2];
            if (zf < 0.5 || zf > maxDist) continue;
            double xr = (vx * r[0] + vy * r[1] + vz * r[2]) / (zf * tanHalf * aspect);
            double yu = (vx * u[0] + vy * u[1] + vz * u[2]) / (zf * tanHalf);
            int px = (int) ((xr + 1) / 2 * w);
            int py = (int) ((1 - yu) / 2 * h);
            if (px < 0 || py < 0 || px >= w || py >= h) continue;
            double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (!xray && depth[py * w + px] < dist - 1.5) continue;
            int scale = dist < 24 ? 2 : 1;
            String label = b.label() + (dist >= 12 ? " " + Math.round(dist) + "M" : "");
            int lx = Math.max(2, Math.min(w - PixelFont.width(label, scale) - 2, px - PixelFont.width(label, scale) / 2));
            int ly = Math.max(2, py - PixelFont.height(scale) - 2);
            PixelFont.draw(img, label, lx, ly, labelColor(b), scale);
        }
        return img;
    }

    /** 360° equirectangular view: left edge faces north, then east, south, west. */
    public static BufferedImage panorama(Scene s, double x, double y, double z, int w, int h, double maxDist,
            boolean xray) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double[] out = new double[1];
        double topPitch = -60;
        double bottomPitch = 75;
        for (int py = 0; py < h; py++) {
            double pitch = topPitch + (bottomPitch - topPitch) * (py + 0.5) / h;
            for (int px = 0; px < w; px++) {
                double yaw = 180 + 360.0 * (px + 0.5) / w;
                double[] d = forward(yaw, pitch);
                img.setRGB(px, py, trace(s, x, y, z, d[0], d[1], d[2], maxDist, xray, out));
            }
        }
        String[] dirs = { "N", "E", "S", "W" };
        for (int i = 0; i < 4; i++) {
            int px = w * i / 4;
            PixelFont.draw(img, dirs[i], px + 4, 4, 0xFFFFFF, 2);
            for (int py = 0; py < h; py += 3) {
                img.setRGB(Math.min(w - 1, px), py, 0xFFFFFF);
            }
        }
        for (Scene.Box b : s.boxes) {
            if (b.label() == null) continue;
            double dx = b.x() - x;
            double dy = b.y() + b.height() + 0.4 - y;
            double dz = b.z() - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > maxDist || dist < 0.5) continue;
            double[] yp = lookAt(0, 0, 0, dx, dy, dz);
            double yaw = ((yp[0] - 180) % 360 + 360) % 360;
            int px = (int) (yaw / 360 * w);
            int py = (int) ((yp[1] - topPitch) / (bottomPitch - topPitch) * h);
            if (py < 0 || py >= h) continue;
            String label = b.label() + " " + Math.round(dist) + "M";
            PixelFont.draw(img, label, px - PixelFont.width(label, 1) / 2, py - 9, labelColor(b), 1);
        }
        return img;
    }

    // ---- The ray tracer -----------------------------------------------------------------------------

    /** Returns the pixel color; out[0] receives the hit distance (maxDist when nothing was hit). */
    static int trace(Scene s, double ox, double oy, double oz, double dx, double dy, double dz, double maxDist,
            boolean xray, double[] out) {
        Scene.Voxels v = s.voxels;
        Palette p = s.palette;

        // Nearest creature box along the ray.
        double boxT = Double.MAX_VALUE;
        Scene.Box boxHit = null;
        int boxFace = 1;
        for (Scene.Box b : s.boxes) {
            double[] hit = intersect(ox, oy, oz, dx, dy, dz, b);
            if (hit != null && hit[0] > NEAR && hit[0] < boxT) {
                boxT = hit[0];
                boxHit = b;
                boxFace = (int) hit[1];
            }
        }

        int ix = (int) Math.floor(ox);
        int iy = (int) Math.floor(oy);
        int iz = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? (ix + 1 - ox) : (ox - ix)) * tDeltaX;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? (iy + 1 - oy) : (oy - iy)) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? (iz + 1 - oz) : (oz - iz)) * tDeltaZ;

        double t = 0;
        int face = -1;
        double transmit = 1.0;
        double accR = 0, accG = 0, accB = 0;
        double limit = Math.min(maxDist, boxT);
        int fog = xray ? XRAY_VOID : horizonColor(s);
        int ghost = 0;
        boolean inRock = false;

        while (t <= limit) {
            if ((iy < v.minY() && stepY < 0) || (iy > v.maxY() && stepY > 0)) {
                break;
            }
            int code = v.block(ix, iy, iz);
            if (code == Scene.Voxels.UNLOADED) {
                out[0] = t;
                return composite(fog, transmit, accR, accG, accB);
            }
            if (xray && t > NEAR) {
                boolean rock = code > 0 && p.xrayHidden[code];
                if (rock != inRock && ghost > 0) {
                    // A cave wall: rock meets open air. Draw it as a faint glowing sheet.
                    double a = 0.16 * transmit;
                    accR += 0x5A * a;
                    accG += 0x9C * a;
                    accB += 0xD8 * a;
                    transmit *= 0.9;
                }
                inRock = rock;
            }
            if (code > 0 && t > NEAR) {
                byte kind = p.kind[code];
                boolean hidden = xray && p.xrayHidden[code];
                if (hidden) {
                    ghost++;
                }
                if (!hidden && kind == Palette.WATER) {
                    double a = 0.22 * transmit;
                    accR += red(p.rgb[code]) * a;
                    accG += green(p.rgb[code]) * a;
                    accB += blue(p.rgb[code]) * a;
                    transmit *= 0.78;
                } else if (!hidden && kind == Palette.GLASS) {
                    double a = 0.18 * transmit;
                    accR += red(p.rgb[code]) * a;
                    accG += green(p.rgb[code]) * a;
                    accB += blue(p.rgb[code]) * a;
                    transmit *= 0.82;
                } else if (!hidden && (kind == Palette.SOLID || kind == Palette.GLOW)) {
                    out[0] = t;
                    int color = shadeBlock(s, code, kind, ix, iy, iz, face, stepX, stepY, stepZ, ox + dx * t,
                            oy + dy * t, oz + dz * t, xray);
                    if (xray) {
                        // Rock in front of a find dims it a little, so depth still reads.
                        if (!p.precious[code]) color = mix(color, XRAY_VOID, Math.min(0.6, ghost * 0.01));
                    } else {
                        color = mix(color, fog, Math.min(0.85, Math.pow(t / maxDist, 1.6) * 0.85));
                    }
                    return composite(color, transmit, accR, accG, accB);
                }
                if (transmit < 0.12) {
                    out[0] = t;
                    return composite(0x1D3D8A, transmit, accR, accG, accB);
                }
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                ix += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
                face = 0;
            } else if (tMaxY < tMaxZ) {
                iy += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
                face = 1;
            } else {
                iz += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                face = 2;
            }
        }

        if (boxHit != null && boxT <= maxDist) {
            out[0] = boxT;
            int c = boxHit.rgb();
            if (!boxHit.glow()) {
                c = scale(c, boxFace == 1 ? 1.0 : boxFace == 0 ? 0.75 : 0.88);
                if (!xray) {
                    c = mix(c, fog, Math.min(0.7, Math.pow(boxT / maxDist, 1.6) * 0.7));
                }
            }
            return composite(c, transmit, accR, accG, accB);
        }
        out[0] = maxDist;
        if (xray) {
            // Rays through solid rock glow faintly; open caves stay dark, so cave shapes show.
            return composite(mix(XRAY_VOID, XRAY_ROCK, Math.min(1, ghost / 140.0)), transmit, accR, accG, accB);
        }
        return composite(t >= maxDist ? fog : sky(s, dy), transmit, accR, accG, accB);
    }

    private static final int XRAY_VOID = 0x07080D;
    private static final int XRAY_ROCK = 0x48505E;

    private static int shadeBlock(Scene s, int code, byte kind, int ix, int iy, int iz, int face, int stepX,
            int stepY, int stepZ, double hx, double hy, double hz, boolean xray) {
        Palette p = s.palette;
        int base = p.rgb[code];
        double faceShade;
        int lx = ix, ly = iy, lz = iz;
        if (face == 1) {
            faceShade = stepY < 0 ? 1.0 : 0.55;
            ly -= stepY;
        } else if (face == 0) {
            faceShade = 0.78;
            lx -= stepX;
        } else if (face == 2) {
            faceShade = 0.88;
            lz -= stepZ;
        } else {
            faceShade = 1.0;
        }
        double lightFactor;
        if (xray || kind == Palette.GLOW) {
            lightFactor = 1.0;
        } else {
            int light = s.voxels.light(lx, ly, lz, s.daylight);
            double floor = s.dimension == Scene.Dimension.OVERWORLD ? 0.22 : 0.45;
            lightFactor = floor + (1 - floor) * light / 15.0;
        }
        // Thin dark lines on block edges.
        double fa;
        double fb;
        if (face == 0) {
            fa = frac(hy);
            fb = frac(hz);
        } else if (face == 1) {
            fa = frac(hx);
            fb = frac(hz);
        } else {
            fa = frac(hx);
            fb = frac(hy);
        }
        double edge = Math.min(Math.min(fa, 1 - fa), Math.min(fb, 1 - fb)) < 0.05 ? 0.8 : 1.0;
        if (xray && p.precious[code]) {
            return scale(base, Math.max(0.85, faceShade) * edge);
        }
        return scale(base, faceShade * lightFactor * edge);
    }

    /** Ray vs creature box. Returns {t, faceAxis} or null. */
    private static double[] intersect(double ox, double oy, double oz, double dx, double dy, double dz, Scene.Box b) {
        double[] min = { b.x() - b.halfWidth(), b.y(), b.z() - b.halfWidth() };
        double[] max = { b.x() + b.halfWidth(), b.y() + b.height(), b.z() + b.halfWidth() };
        double[] o = { ox, oy, oz };
        double[] d = { dx, dy, dz };
        double tNear = -Double.MAX_VALUE;
        double tFar = Double.MAX_VALUE;
        int axis = 1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (o[i] < min[i] || o[i] > max[i]) return null;
                continue;
            }
            double t1 = (min[i] - o[i]) / d[i];
            double t2 = (max[i] - o[i]) / d[i];
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tNear) {
                tNear = t1;
                axis = i;
            }
            tFar = Math.min(tFar, t2);
            if (tNear > tFar || tFar < 0) return null;
        }
        return new double[] { tNear, axis };
    }

    // ---- Overhead maps -------------------------------------------------------------------------------

    public static BufferedImage overhead(Scene s, int cx, int cy, int cz, int radius, int size, OverheadMode mode) {
        BufferedImage img = terrain(s.voxels, s.palette, cx, cy, cz, radius, size, mode);
        double blocksPerPx = 2.0 * radius / size;
        return annotate(img, s, cx, cz, radius, size, mode, blocksPerPx);
    }

    /** The bare top-down ground (no marks, labels or grid): north up, cx/cz in the middle. */
    static BufferedImage terrain(Scene.Voxels v, Palette p, int cx, int cy, int cz, int radius, int size,
            OverheadMode mode) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double blocksPerPx = 2.0 * radius / size;
        int[] heights = new int[size * size];
        int[] colors = new int[size * size];
        for (int py = 0; py < size; py++) {
            int wz = (int) Math.floor(cz - radius + (py + 0.5) * blocksPerPx);
            for (int px = 0; px < size; px++) {
                int wx = (int) Math.floor(cx - radius + (px + 0.5) * blocksPerPx);
                int idx = py * size + px;
                heights[idx] = Integer.MIN_VALUE;
                colors[idx] = switch (mode) {
                    case SURFACE -> surfaceColumn(v, p, wx, wz, heights, idx);
                    case CAVE -> caveColumn(v, p, wx, cy, wz, heights, idx);
                    case XRAY -> xrayColumn(v, p, wx, cy, wz, heights, idx);
                };
            }
        }
        // Map-style relief: brighter where the ground rises toward the north.
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int idx = py * size + px;
                int c = colors[idx];
                if (py > 0 && heights[idx] != Integer.MIN_VALUE && heights[idx - size] != Integer.MIN_VALUE
                        && mode != OverheadMode.XRAY) {
                    int diff = heights[idx] - heights[idx - size];
                    c = scale(c, diff > 0 ? 1.12 : diff < 0 ? 0.82 : 1.0);
                }
                img.setRGB(px, py, c);
            }
        }
        return img;
    }

    private static BufferedImage annotate(BufferedImage img, Scene s, int cx, int cz, int radius, int size,
            OverheadMode mode, double blocksPerPx) {
        // Chunk grid.
        if (mode == OverheadMode.SURFACE && blocksPerPx < 0.6) {
            for (int px = 0; px < size; px++) {
                int wx = (int) Math.floor(cx - radius + (px + 0.5) * blocksPerPx);
                int wxNext = (int) Math.floor(cx - radius + (px + 1.5) * blocksPerPx);
                if (Math.floorMod(wx, 16) == 15 && Math.floorMod(wxNext, 16) == 0) {
                    for (int py = 0; py < size; py += 2) img.setRGB(px, py, mix(img.getRGB(px, py) & 0xFFFFFF, 0xFFFFFF, 0.25));
                }
            }
            for (int py = 0; py < size; py++) {
                int wz = (int) Math.floor(cz - radius + (py + 0.5) * blocksPerPx);
                int wzNext = (int) Math.floor(cz - radius + (py + 1.5) * blocksPerPx);
                if (Math.floorMod(wz, 16) == 15 && Math.floorMod(wzNext, 16) == 0) {
                    for (int px = 0; px < size; px += 2) img.setRGB(px, py, mix(img.getRGB(px, py) & 0xFFFFFF, 0xFFFFFF, 0.25));
                }
            }
        }
        // Markers.
        for (Scene.Marker m : s.markers) {
            int px = (int) ((m.x() - (cx - radius)) / blocksPerPx);
            int py = (int) ((m.z() - (cz - radius)) / blocksPerPx);
            if (px < 0 || py < 0 || px >= size || py >= size) continue;
            dot(img, px, py, 2, 0xFFFFFF);
            PixelFont.draw(img, m.label(), px + 5, py - 3, 0xF0F0F0, 1);
        }
        for (Scene.Box b : s.boxes) {
            int px = (int) ((b.x() - (cx - radius)) / blocksPerPx);
            int py = (int) ((b.z() - (cz - radius)) / blocksPerPx);
            if (px < 0 || py < 0 || px >= size || py >= size) continue;
            if (b.glow()) {
                diamond(img, px, py, 6, b.rgb());
            } else {
                dot(img, px, py, b.player() ? 4 : 2, b.rgb());
            }
            if (b.label() != null && (b.player() || b.glow())) {
                PixelFont.draw(img, b.label(), px + 7, py - 3, labelColor(b), b.player() || b.glow() ? 2 : 1);
            }
        }
        // Crosshair on the target and the compass.
        int c = size / 2;
        for (int i = -6; i <= 6; i++) {
            if (Math.abs(i) > 2) {
                img.setRGB(c + i, c, 0xFFFFFF);
                img.setRGB(c, c + i, 0xFFFFFF);
            }
        }
        PixelFont.draw(img, "N", size - 20, 6, 0xFFFFFF, 2);
        for (int i = 0; i < 8; i++) {
            img.setRGB(size - 15, 22 + i, 0xFFFFFF);
        }
        PixelFont.draw(img, (cx - radius) + "," + (cz - radius), 4, 4, 0xE0E0E0, 1);
        String br = (cx + radius) + "," + (cz + radius);
        PixelFont.draw(img, br, size - PixelFont.width(br, 1) - 4, size - 11, 0xE0E0E0, 1);
        return img;
    }

    private static int surfaceColumn(Scene.Voxels v, Palette p, int x, int z, int[] heights, int idx) {
        int top = v.highestY(x, z);
        if (top == Integer.MIN_VALUE) {
            return 0x191919;
        }
        int waterDepth = 0;
        int waterColor = 0;
        for (int y = Math.min(v.maxY(), top + 1); y >= v.minY(); y--) {
            int code = v.block(x, y, z);
            if (code <= 0) continue;
            byte k = p.kind[code];
            if (k == Palette.EMPTY || k == Palette.GLASS) continue;
            if (k == Palette.WATER) {
                if (waterDepth == 0) {
                    heights[idx] = y;
                    waterColor = p.rgb[code];
                }
                waterDepth++;
                if (waterDepth > 12) break;
                continue;
            }
            if (waterDepth > 0) {
                return mix(scale(waterColor, 1.0 - Math.min(0.5, waterDepth * 0.05)), p.rgb[code], 0.2);
            }
            heights[idx] = y;
            return p.rgb[code];
        }
        return waterDepth > 0 ? scale(waterColor, 0.5) : 0x101010;
    }

    private static int caveColumn(Scene.Voxels v, Palette p, int x, int cy, int z, int[] heights, int idx) {
        for (int y = cy + 1; y >= Math.max(v.minY(), cy - 24); y--) {
            int code = v.block(x, y, z);
            if (code == Scene.Voxels.UNLOADED) return 0x191919;
            if (code <= 0) continue;
            byte k = p.kind[code];
            if (k == Palette.EMPTY || k == Palette.GLASS) continue;
            heights[idx] = y;
            if (y >= cy + 1) {
                return scale(p.rgb[code], 0.4);
            }
            return scale(p.rgb[code], Math.max(0.35, 1.0 - (cy - y) * 0.05));
        }
        return 0x0E0E14;
    }

    private static int xrayColumn(Scene.Voxels v, Palette p, int x, int cy, int z, int[] heights, int idx) {
        for (int y = Math.min(v.maxY(), cy + 20); y >= Math.max(v.minY(), cy - 40); y--) {
            int code = v.block(x, y, z);
            if (code == Scene.Voxels.UNLOADED) return 0x191919;
            if (code <= 0) continue;
            byte k = p.kind[code];
            if (k == Palette.EMPTY || k == Palette.WATER || p.xrayHidden[code]) continue;
            heights[idx] = y;
            if (p.precious[code]) return p.rgb[code];
            return scale(p.rgb[code], 0.75);
        }
        return 0x2D2D32;
    }

    // ---- Colors --------------------------------------------------------------------------------------

    static int horizonColor(Scene s) {
        return switch (s.dimension) {
            case NETHER -> 0x461410;
            case END -> 0x1A0F24;
            default -> {
                int day = 0xBED7FF;
                int night = 0x141A30;
                int c = mix(night, day, (s.daylight - 0.15) / 0.85);
                c = mix(c, 0xFF9A55, s.sunsetGlow * 0.6);
                yield s.storm ? scale(c, 0.6) : c;
            }
        };
    }

    static int sky(Scene s, double dy) {
        if (s.dimension != Scene.Dimension.OVERWORLD) {
            return horizonColor(s);
        }
        int zenith = mix(0x05070F, 0x5A8CF0, (s.daylight - 0.15) / 0.85);
        if (s.storm) zenith = scale(zenith, 0.6);
        int horizon = horizonColor(s);
        if (dy < 0) {
            return scale(horizon, 0.75);
        }
        return mix(horizon, zenith, Math.min(1, dy * 1.6));
    }

    private static int labelColor(Scene.Box b) {
        return b.glow() ? 0xE6B3FF : b.player() ? 0xFFFFFF : 0xFFE9A8;
    }

    private static int composite(int color, double transmit, double accR, double accG, double accB) {
        int r = (int) Math.min(255, red(color) * transmit + accR);
        int g = (int) Math.min(255, green(color) * transmit + accG);
        int b = (int) Math.min(255, blue(color) * transmit + accB);
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (red(a) + (red(b) - red(a)) * t);
        int g = (int) (green(a) + (green(b) - green(a)) * t);
        int bl = (int) (blue(a) + (blue(b) - blue(a)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int c, double f) {
        int r = (int) Math.max(0, Math.min(255, red(c) * f));
        int g = (int) Math.max(0, Math.min(255, green(c) * f));
        int b = (int) Math.max(0, Math.min(255, blue(c) * f));
        return (r << 16) | (g << 8) | b;
    }

    static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    static int blue(int c) {
        return c & 0xFF;
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static void normalize(double[] d) {
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d[0] /= len;
        d[1] /= len;
        d[2] /= len;
    }

    private static void dot(BufferedImage img, int cx, int cy, int r, int rgb) {
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int y = -r - 1; y <= r + 1; y++) {
                int px = cx + x;
                int py = cy + y;
                if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) continue;
                int d2 = x * x + y * y;
                if (d2 <= r * r) img.setRGB(px, py, rgb);
                else if (d2 <= (r + 1) * (r + 1)) img.setRGB(px, py, 0x101010);
            }
        }
    }

    private static void diamond(BufferedImage img, int cx, int cy, int r, int rgb) {
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int y = -r - 1; y <= r + 1; y++) {
                int px = cx + x;
                int py = cy + y;
                if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) continue;
                int d = Math.abs(x) + Math.abs(y);
                if (d <= r) img.setRGB(px, py, rgb);
                else if (d == r + 1) img.setRGB(px, py, 0xFFFFFF);
            }
        }
    }
}
