<#
.SYNOPSIS
    Genera tutte le icone dell'app (desktop e Android) dal logo ufficiale docs/images/logo.jfif.

.DESCRIPTION
    Usa solo System.Drawing di Windows (nessun software da installare). Rigenera:
      - composeApp/src/desktopMain/resources/brand/logo.jpg        logo completo per lo splash screen
      - composeApp/src/desktopMain/resources/brand/app_icon.png    icona della finestra (256 px)
      - composeApp/packaging/windows/KiwiSDRRecorder.ico           icona dell'eseguibile e degli installer
      - androidApp/src/main/res/drawable-nodpi/logo_full.jpg       logo completo per lo splash screen Android
      - androidApp/src/main/res/mipmap-*/ic_launcher_*.png         livelli dell'icona adattiva Android
      - androidApp/src/main/res/drawable-*/ic_notification.png     icona monocromatica della notifica

    Le icone usano solo il simbolo (kiwi e onde) senza la scritta, ritagliato dal logo e centrato
    sul colore di sfondo del logo. Eseguire dalla radice del repository:
        .\scripts\branding\Generate-Icons.ps1
#>
[CmdletBinding()]
param(
    [string]$Logo = "docs/images/logo.jfif"
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $root
Add-Type -AssemblyName System.Drawing

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public static class KiwiIconTools {
    // Area del simbolo nel logo originale 1408x768 (la scritta inizia sotto y = 480).
    public static readonly Rectangle MarkArea = new Rectangle(500, 128, 406, 340);

    public static Color Background(Bitmap logo) { return logo.GetPixel(8, 8); }

    static Bitmap Canvas(int size) {
        var bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(bmp)) g.Clear(Color.Transparent);
        return bmp;
    }

    static Graphics HighQuality(Bitmap bmp) {
        var g = Graphics.FromImage(bmp);
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.CompositingQuality = CompositingQuality.HighQuality;
        return g;
    }

    /** Simbolo su sfondo trasparente: alpha stimato dalla distanza dal colore di sfondo, colore "sbiancato" dallo sfondo. */
    public static Bitmap ExtractMark(Bitmap logo) {
        Color bg = Background(logo);
        var r = MarkArea;
        var mark = new Bitmap(r.Width, r.Height, PixelFormat.Format32bppArgb);
        for (int y = 0; y < r.Height; y++) {
            for (int x = 0; x < r.Width; x++) {
                Color p = logo.GetPixel(r.X + x, r.Y + y);
                int dist = Math.Max(Math.Abs(p.R - bg.R), Math.Max(Math.Abs(p.G - bg.G), Math.Abs(p.B - bg.B)));
                double a = Math.Min(1.0, Math.Max(0.0, (dist - 18) / 110.0));
                if (a <= 0) { mark.SetPixel(x, y, Color.Transparent); continue; }
                int cr = Clamp((p.R - (1 - a) * bg.R) / a);
                int cg = Clamp((p.G - (1 - a) * bg.G) / a);
                int cb = Clamp((p.B - (1 - a) * bg.B) / a);
                mark.SetPixel(x, y, Color.FromArgb((int)Math.Round(a * 255), cr, cg, cb));
            }
        }
        return mark;
    }

    static int Clamp(double v) { return (int)Math.Max(0, Math.Min(255, Math.Round(v))); }

    /** Simbolo centrato su un canvas quadrato: [markDiagonal] è la diagonale del simbolo in frazione del lato. */
    public static Bitmap CenteredMark(Bitmap mark, int size, double markDiagonal) {
        var bmp = Canvas(size);
        double diag = Math.Sqrt(mark.Width * (double)mark.Width + mark.Height * (double)mark.Height);
        double scale = size * markDiagonal / diag;
        float w = (float)(mark.Width * scale), h = (float)(mark.Height * scale);
        using (var g = HighQuality(bmp)) g.DrawImage(mark, (size - w) / 2f, (size - h) / 2f, w, h);
        return bmp;
    }

    /** Icona "a piastrella": quadrato arrotondato con il colore dello sfondo del logo e il simbolo al centro. */
    public static Bitmap Tile(Bitmap mark, Color bg, int size) {
        var bmp = Canvas(size);
        using (var g = HighQuality(bmp)) {
            float radius = size * 0.2f;
            using (var path = new GraphicsPath()) {
                float d = radius * 2;
                path.AddArc(0, 0, d, d, 180, 90);
                path.AddArc(size - d, 0, d, d, 270, 90);
                path.AddArc(size - d, size - d, d, d, 0, 90);
                path.AddArc(0, size - d, d, d, 90, 90);
                path.CloseFigure();
                using (var brush = new SolidBrush(bg)) g.FillPath(brush, path);
            }
            using (var centered = CenteredMark(mark, size, 1.0)) g.DrawImage(centered, 0, 0, size, size);
        }
        return bmp;
    }

    /** Versione bianca con lo stesso alpha (icona monocromatica Android e notifica). */
    public static Bitmap White(Bitmap source) {
        var bmp = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);
        for (int y = 0; y < source.Height; y++)
            for (int x = 0; x < source.Width; x++)
                bmp.SetPixel(x, y, Color.FromArgb(source.GetPixel(x, y).A, 255, 255, 255));
        return bmp;
    }

    /** File .ico con immagini PNG (formato supportato da Windows Vista in poi). */
    public static void WriteIco(Bitmap mark, Color bg, int[] sizes, string path) {
        var images = new byte[sizes.Length][];
        for (int i = 0; i < sizes.Length; i++) {
            using (var tile = Tile(mark, bg, sizes[i]))
            using (var ms = new MemoryStream()) {
                tile.Save(ms, ImageFormat.Png);
                images[i] = ms.ToArray();
            }
        }
        using (var fs = new FileStream(path, FileMode.Create))
        using (var w = new BinaryWriter(fs)) {
            w.Write((short)0); w.Write((short)1); w.Write((short)sizes.Length);
            int offset = 6 + 16 * sizes.Length;
            for (int i = 0; i < sizes.Length; i++) {
                w.Write((byte)(sizes[i] >= 256 ? 0 : sizes[i]));
                w.Write((byte)(sizes[i] >= 256 ? 0 : sizes[i]));
                w.Write((byte)0); w.Write((byte)0);
                w.Write((short)1); w.Write((short)32);
                w.Write(images[i].Length); w.Write(offset);
                offset += images[i].Length;
            }
            foreach (var img in images) w.Write(img);
        }
    }
}
'@

function Save-Png([System.Drawing.Bitmap]$Bitmap, [string]$Path) {
    New-Item -ItemType Directory -Force (Split-Path $Path) | Out-Null
    $Bitmap.Save((Join-Path $root $Path), [System.Drawing.Imaging.ImageFormat]::Png)
    $Bitmap.Dispose()
}

$logoPath = Join-Path $root $Logo
$logoBitmap = [System.Drawing.Bitmap]::FromFile($logoPath)
try {
    $bg = [KiwiIconTools]::Background($logoBitmap)
    $mark = [KiwiIconTools]::ExtractMark($logoBitmap)
    $hex = '#FF{0:X2}{1:X2}{2:X2}' -f $bg.R, $bg.G, $bg.B
    Write-Host "Sfondo del logo: $hex"

    # ── Desktop ──
    $desktopBrand = 'composeApp/src/desktopMain/resources/brand'
    New-Item -ItemType Directory -Force $desktopBrand | Out-Null
    Copy-Item $logoPath (Join-Path $desktopBrand 'logo.jpg') -Force
    Save-Png ([KiwiIconTools]::Tile($mark, $bg, 256)) "$desktopBrand/app_icon.png"
    [KiwiIconTools]::WriteIco($mark, $bg, [int[]](16, 20, 24, 32, 40, 48, 64, 128, 256), (Join-Path $root 'composeApp/packaging/windows/KiwiSDRRecorder.ico'))

    # ── Android ──
    $res = 'androidApp/src/main/res'
    New-Item -ItemType Directory -Force "$res/drawable-nodpi" | Out-Null
    Copy-Item $logoPath "$res/drawable-nodpi/logo_full.jpg" -Force
    $densities = [ordered]@{ mdpi = 1.0; hdpi = 1.5; xhdpi = 2.0; xxhdpi = 3.0; xxxhdpi = 4.0 }
    foreach ($density in $densities.Keys) {
        $factor = $densities[$density]
        # Icona adattiva: canvas di 108 dp, simbolo nella zona sicura (cerchio di 66 dp).
        $launcherSize = [int](108 * $factor)
        $foreground = [KiwiIconTools]::CenteredMark($mark, $launcherSize, 0.60)
        Save-Png ([KiwiIconTools]::White($foreground)) "$res/mipmap-$density/ic_launcher_monochrome.png"
        Save-Png $foreground "$res/mipmap-$density/ic_launcher_foreground.png"
        # Notifica: 24 dp, solo il canale alpha conta.
        $notificationSize = [int](24 * $factor)
        $notification = [KiwiIconTools]::CenteredMark($mark, $notificationSize, 1.08)
        Save-Png ([KiwiIconTools]::White($notification)) "$res/drawable-$density/ic_notification.png"
        $notification.Dispose()
    }
    $mark.Dispose()
    Write-Host "Colore per ic_launcher_background: $hex"
} finally {
    $logoBitmap.Dispose()
}
