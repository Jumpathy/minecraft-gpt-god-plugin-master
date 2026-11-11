# Window helper for the camera client (Windows only). Sends no keystrokes or mouse input and never
# focuses a window: captures use PrintWindow, placement uses no-activate flags, close is a WM_CLOSE
# message. (Posted key messages are useless anyway: 26.3's SDL window ignores them while unfocused.)
#   window.ps1 -Action find|capture|show|close|foreground|giveback -ProcessId <pid> [-Out file.png] [-Mode normal|background] [-Prev hwnd]
param(
    [Parameter(Mandatory = $true)][ValidateSet('find', 'capture', 'show', 'close', 'foreground', 'giveback')][string]$Action,
    [int]$ProcessId = 0,
    [int64]$Prev = 0,
    [string]$Out = '',
    [ValidateSet('normal', 'background')][string]$Mode = 'normal'
)
$ErrorActionPreference = 'Stop'

$src = @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;

public static class HarnessWin {
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc cb, IntPtr l);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetClassName(IntPtr h, StringBuilder sb, int n);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder sb, int n);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr v);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint a, uint b, bool attach);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();

    // Gives the foreground back to `prev` (the window the user had) when the game window took it.
    // Windows only lets the foreground thread change the foreground, so attach to it for the call.
    public static bool GiveBack(IntPtr game, IntPtr prev) {
        if (GetForegroundWindow() != game || !IsWindow(prev)) return false;
        uint pid; uint fg = GetWindowThreadProcessId(game, out pid);
        uint me = GetCurrentThreadId();
        AttachThreadInput(me, fg, true);
        bool ok = SetForegroundWindow(prev);
        AttachThreadInput(me, fg, false);
        return ok;
    }
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }

    public static string ClassOf(IntPtr h) { var sb = new StringBuilder(256); GetClassName(h, sb, 256); return sb.ToString(); }
    public static string TitleOf(IntPtr h) { var sb = new StringBuilder(512); GetWindowText(h, sb, 512); return sb.ToString(); }

    // The game window of a process. SDL (26.3) and GLFW also create hidden helper windows of the same
    // class, so the best candidate wins: a "Minecraft..." title, visible, game window class, largest area.
    public static IntPtr Find(uint pid) {
        IntPtr best = IntPtr.Zero; long bestScore = -1;
        EnumWindows((h, l) => {
            uint p; GetWindowThreadProcessId(h, out p);
            if (p != pid) return true;
            string c = ClassOf(h), t = TitleOf(h);
            RECT r; GetWindowRect(h, out r);
            long area = Math.Max(0L, (long)(r.Right - r.Left) * (r.Bottom - r.Top));
            long score = (t.StartsWith("Minecraft") ? 4L : 0L) + (IsWindowVisible(h) ? 2L : 0L)
                + ((c.StartsWith("GLFW") || c == "SDL_app") ? 1L : 0L);
            if (t.Length == 0 && !IsWindowVisible(h)) return true; // helper windows
            score = score * 100000000L + Math.Min(area, 99999999L);
            if (score > bestScore) { bestScore = score; best = h; }
            return true;
        }, IntPtr.Zero);
        return best;
    }

    // Client area through PrintWindow(PW_CLIENTONLY | PW_RENDERFULLCONTENT); works while the window is behind others.
    public static double Capture(IntPtr h, string file) {
        RECT r; GetClientRect(h, out r);
        int w = r.Right - r.Left, ht = r.Bottom - r.Top;
        if (w <= 0 || ht <= 0) throw new Exception("window has no client area (minimized?)");
        using (var bmp = new Bitmap(w, ht, PixelFormat.Format32bppArgb)) {
            using (var g = Graphics.FromImage(bmp)) {
                IntPtr hdc = g.GetHdc();
                bool ok = PrintWindow(h, hdc, 3);
                g.ReleaseHdc(hdc);
                if (!ok) throw new Exception("PrintWindow failed");
            }
            // Mean brightness of a sparse sample: ~0 means the capture came back black.
            long sum = 0; int n = 0;
            for (int y = 0; y < ht; y += Math.Max(1, ht / 40)) for (int x = 0; x < w; x += Math.Max(1, w / 40)) { var c = bmp.GetPixel(x, y); sum += c.R + c.G + c.B; n++; }
            bmp.Save(file, ImageFormat.Png);
            return n == 0 ? 0 : sum / (3.0 * n);
        }
    }
}
'@
Add-Type -TypeDefinition $src -ReferencedAssemblies System.Drawing
# Physical pixels, not DPI-virtualized ones (PER_MONITOR_AWARE_V2 = -4).
try { [HarnessWin]::SetProcessDpiAwarenessContext([IntPtr](-4)) | Out-Null } catch {}

if ($Action -eq 'foreground') {
    @{ ok = $true; hwnd = [int64][HarnessWin]::GetForegroundWindow() } | ConvertTo-Json -Compress
    exit 0
}
$h = [HarnessWin]::Find([uint32]$ProcessId)
if ($h -eq [IntPtr]::Zero) {
    @{ ok = $false; error = "no window for process $ProcessId" } | ConvertTo-Json -Compress
    exit 0
}

function Describe($h) {
    $r = New-Object HarnessWin+RECT; [HarnessWin]::GetWindowRect($h, [ref]$r) | Out-Null
    $c = New-Object HarnessWin+RECT; [HarnessWin]::GetClientRect($h, [ref]$c) | Out-Null
    @{
        ok = $true; hwnd = [int64]$h; class = [HarnessWin]::ClassOf($h); title = [HarnessWin]::TitleOf($h)
        visible = [HarnessWin]::IsWindowVisible($h); minimized = [HarnessWin]::IsIconic($h)
        foreground = ([HarnessWin]::GetForegroundWindow() -eq $h)
        x = $r.Left; y = $r.Top; width = $r.Right - $r.Left; height = $r.Bottom - $r.Top
        clientWidth = $c.Right - $c.Left; clientHeight = $c.Bottom - $c.Top
    }
}

switch ($Action) {
    'find' { Describe $h | ConvertTo-Json -Compress }
    'capture' {
        if (-not $Out) { throw '-Out is required' }
        $mean = [HarnessWin]::Capture($h, $Out)
        $d = Describe $h; $d.file = $Out; $d.meanBrightness = [math]::Round($mean, 1)
        $d | ConvertTo-Json -Compress
    }
    'show' {
        # SW_SHOWNOACTIVATE = 4 (also un-minimizes); SWP_NOSIZE|SWP_NOMOVE|SWP_NOACTIVATE = 0x13; HWND_BOTTOM = 1.
        # Only un-minimize; never show a hidden window (it would be a helper window, not the game).
        if ([HarnessWin]::IsIconic($h)) { [HarnessWin]::ShowWindow($h, 4) | Out-Null }
        if ($Mode -eq 'background') { [HarnessWin]::SetWindowPos($h, [IntPtr]1, 0, 0, 0, 0, 0x13) | Out-Null }
        Describe $h | ConvertTo-Json -Compress
    }
    'giveback' {
        $given = $false
        if ($Prev -ne 0) { $given = [HarnessWin]::GiveBack($h, [IntPtr]$Prev) }
        $d = Describe $h; $d.gaveBack = $given
        $d | ConvertTo-Json -Compress
    }
    'close' {
        [HarnessWin]::PostMessage($h, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        @{ ok = $true; closed = [int64]$h } | ConvertTo-Json -Compress
    }
}
