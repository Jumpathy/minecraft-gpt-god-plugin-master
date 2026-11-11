package net.bigyous.gptgodmc.voice;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.bigyous.gptgodmc.GPTGOD;

/** Fetches voice models and the Piper engine on first use. */
final class Downloads {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private Downloads() {
    }

    static void fetch(String url, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        GPTGOD.LOGGER.info("Downloading " + url + " → " + target);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(15))
                .header("User-Agent", "gptgodmc-plugin").GET().build();
        try {
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                throw new IOException("HTTP " + res.statusCode() + " for " + url);
            }
            long total = res.headers().firstValueAsLong("content-length").orElse(-1);
            try (InputStream in = new BufferedInputStream(res.body()); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[1 << 16];
                long done = 0;
                long nextLog = 10L << 20;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (done >= nextLog) {
                        GPTGOD.LOGGER.info(String.format("  %d MB%s", done >> 20, total > 0 ? " of " + (total >> 20) + " MB" : ""));
                        nextLog += 10L << 20;
                    }
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static void unzip(Path archive, Path dest) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                Path out = safe(dest, e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Minimal tar.gz extraction (regular files, directories, symlinks, GNU long names). */
    static void untarGz(Path archive, Path dest) throws IOException {
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[512];
            String longName = null;
            while (in.readNBytes(header, 0, 512) == 512) {
                if (isZero(header)) break;
                String name = longName != null ? longName : str(header, 0, 100);
                longName = null;
                String prefix = str(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long size = Long.parseLong(str(header, 124, 12).trim().isEmpty() ? "0" : str(header, 124, 12).trim(), 8);
                int mode = str(header, 100, 8).trim().isEmpty() ? 0644 : Integer.parseInt(str(header, 100, 8).trim(), 8);
                char type = (char) header[156];
                long padded = (size + 511) / 512 * 512;
                if (type == 'L') {
                    byte[] data = in.readNBytes((int) padded);
                    longName = new String(data, 0, (int) size, StandardCharsets.UTF_8).replace("\0", "");
                    continue;
                }
                Path out = safe(dest, name);
                if (type == '5') {
                    Files.createDirectories(out);
                } else if (type == '2') {
                    String link = str(header, 157, 100);
                    Files.createDirectories(out.getParent());
                    Files.deleteIfExists(out);
                    try {
                        Files.createSymbolicLink(out, Path.of(link));
                    } catch (IOException | UnsupportedOperationException ex) {
                        GPTGOD.LOGGER.debug("Could not create symlink " + out + " -> " + link);
                    }
                } else if (type == '0' || type == '\0') {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        long left = size;
                        byte[] buf = new byte[1 << 16];
                        while (left > 0) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                            if (n < 0) throw new IOException("Truncated archive");
                            os.write(buf, 0, n);
                            left -= n;
                        }
                    }
                    if ((mode & 0111) != 0) {
                        out.toFile().setExecutable(true, false);
                    }
                    in.skipNBytes(padded - size);
                    continue;
                }
                in.skipNBytes(padded);
            }
        }
    }

    private static Path safe(Path dest, String name) throws IOException {
        Path out = dest.resolve(name).normalize();
        if (!out.startsWith(dest.normalize())) {
            throw new IOException("Archive entry escapes the target folder: " + name);
        }
        return out;
    }

    private static String str(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }
}
